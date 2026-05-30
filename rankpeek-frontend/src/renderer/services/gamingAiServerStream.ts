import type { GamingAiInputPlayer, GamingAiInputSnapshot, GamingAiTeamSnapshot } from './gamingAiInputSnapshot.ts'
import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'
import {
  getStoredRankPeekAuthSession,
  refreshStoredRankPeekAuthSession
} from './rankpeekAuthClient.ts'

export const RANKPEEK_SERVER_GAMING_STREAM_ENDPOINT = '/api/analysis/pregame/stream'
const GAMING_AI_LOGIN_REQUIRED_MESSAGE = '请先登录 RankPeek 账号后再使用 AI 分析。'
const GAMING_AI_LOGIN_EXPIRED_MESSAGE = '登录状态已失效，请重新登录后再试。'
const GAMING_AI_INSUFFICIENT_CREDITS_MESSAGE = 'AI 分析次数不足，请充值后再试。'
const GAMING_AI_RATE_LIMIT_MESSAGE = '请求太频繁，请稍后再试。'
const GAMING_AI_UNAVAILABLE_MESSAGE = 'AI 服务暂时不可用，请稍后再试。'
const GAMING_AI_BAD_REQUEST_MESSAGE = '请求无法完成，请稍后再试。'

export type GamingAiStreamState =
  | 'idle'
  | 'preparing'
  | 'streaming'
  | 'completed'
  | 'failed'

export interface GamingAiStreamRequest {
  mode: 'teammate' | 'opponent'
  snapshotSchemaVersion: string
  snapshot: GamingAiInputSnapshot
  allyTeamTags: string[]
  enemyTeamTags: string[]
}

export interface GamingAiPlayerStreamVerdict {
  playerKey: string
  label: string
  tone?: 'carry' | 'stable' | 'risk' | 'weak' | 'unknown'
  reason?: string
}

export interface GamingAiPlayerInsightEvent {
  playerKey: string
  label: string
  tone?: 'carry' | 'stable' | 'risk' | 'weak' | 'unknown'
  text: string
}

export type GamingAiStreamEvent =
  | { type: 'start'; title?: string }
  | { type: 'delta'; text: string }
  | { type: 'section'; title: string }
  | ({ type: 'player_verdict' } & GamingAiPlayerStreamVerdict)
  | ({ type: 'player_insight' } & GamingAiPlayerInsightEvent)
  | { type: 'done' }
  | { type: 'error'; message: string }

export function flattenGamingAiSnapshotTags(snapshot: GamingAiInputSnapshot): {
  allyTeamTags: string[]
  enemyTeamTags: string[]
} {
  if (snapshot.mode === 'opponent') {
    return {
      allyTeamTags: [],
      enemyTeamTags: [formatTeamSnapshotText(snapshot.opponentSnapshot)]
    }
  }

  return {
    allyTeamTags: [formatTeamSnapshotText(snapshot.teammateSnapshot)],
    enemyTeamTags: []
  }
}

export function createGamingAiStreamRequest(snapshot: GamingAiInputSnapshot): GamingAiStreamRequest {
  const flattened = flattenGamingAiSnapshotTags(snapshot)

  return {
    mode: snapshot.mode,
    snapshotSchemaVersion: snapshot.schemaVersion,
    snapshot,
    allyTeamTags: flattened.allyTeamTags,
    enemyTeamTags: flattened.enemyTeamTags
  }
}

export async function streamGamingAiAnalysis(
  request: GamingAiStreamRequest,
  handlers: {
    onEvent?: (event: GamingAiStreamEvent) => void
    onDelta?: (text: string) => void
    onError?: (message: string) => void
    onDone?: () => void
  },
  options: { signal?: AbortSignal } = {}
): Promise<{ ok: true } | { ok: false; message: string }> {
  try {
    const session = getStoredRankPeekAuthSession()
    if (!session?.accessToken) {
      return emitFailedGamingAiStream(GAMING_AI_LOGIN_REQUIRED_MESSAGE, handlers)
    }

    let response = await postGamingAiStreamRequest(request, session.accessToken, options.signal)

    if (response.status === 401 && session?.refreshToken) {
      const refreshResult = await refreshStoredRankPeekAuthSession()
      if (!refreshResult.ok) {
        return emitFailedGamingAiStream(GAMING_AI_LOGIN_EXPIRED_MESSAGE, handlers)
      }
      response = await postGamingAiStreamRequest(request, refreshResult.session.accessToken, options.signal)
    }

    if (!response.ok) {
      return emitFailedGamingAiStream(await readGamingAiHttpErrorMessage(response), handlers)
    }

    if (!response.body) {
      return emitFailedGamingAiStream(GAMING_AI_UNAVAILABLE_MESSAGE, handlers)
    }

    const contentType = response.headers.get('Content-Type') || ''
    if (contentType.includes('text/event-stream')) {
      await parseSseStream(response.body, handlers)
    } else {
      await parseNdjsonStream(response.body, handlers)
    }

    return { ok: true }
  } catch {
    if (options.signal?.aborted) {
      return { ok: false, message: '请求已取消' }
    }

    return emitFailedGamingAiStream(GAMING_AI_UNAVAILABLE_MESSAGE, handlers)
  }
}

function emitFailedGamingAiStream(
  message: string,
  handlers: Parameters<typeof streamGamingAiAnalysis>[1]
): { ok: false; message: string } {
  emitStreamEvent({ type: 'error', message }, handlers)
  return { ok: false, message }
}

async function readGamingAiHttpErrorMessage(response: Response): Promise<string> {
  const payload = await readGamingAiErrorPayload(response)
  return toGamingAiUserFacingErrorMessage(response.status, payload?.error?.code)
}

async function readGamingAiErrorPayload(response: Response): Promise<{
  error?: {
    code?: string
  } | null
} | null> {
  try {
    const payload = await response.json() as unknown
    return payload && typeof payload === 'object' ? payload as { error?: { code?: string } | null } : null
  } catch {
    return null
  }
}

function toGamingAiUserFacingErrorMessage(status: number, code?: string): string {
  if (status === 401 || code === 'ACCESS_TOKEN_INVALID' || code === 'REFRESH_TOKEN_INVALID') {
    return GAMING_AI_LOGIN_EXPIRED_MESSAGE
  }
  if (status === 402 || code === 'INSUFFICIENT_CREDITS') {
    return GAMING_AI_INSUFFICIENT_CREDITS_MESSAGE
  }
  if (status === 429 || code === 'RATE_LIMIT_EXCEEDED') {
    return GAMING_AI_RATE_LIMIT_MESSAGE
  }
  if (status >= 500) {
    return GAMING_AI_UNAVAILABLE_MESSAGE
  }
  return GAMING_AI_BAD_REQUEST_MESSAGE
}

async function postGamingAiStreamRequest(
  request: GamingAiStreamRequest,
  accessToken: string | undefined,
  signal: AbortSignal | undefined
): Promise<Response> {
  return fetch(`${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_GAMING_STREAM_ENDPOINT}`, {
    method: 'POST',
    headers: createStreamRequestHeaders(accessToken),
    body: JSON.stringify(request),
    signal
  })
}

function createStreamRequestHeaders(accessToken: string | undefined): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }
  return headers
}

async function parseSseStream(
  body: ReadableStream<Uint8Array>,
  handlers: Parameters<typeof streamGamingAiAnalysis>[1]
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  for (;;) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }

    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split(/\r?\n\r?\n/)
    buffer = parts.pop() ?? ''
    for (const part of parts) {
      parseSseBlock(part, handlers)
    }
  }

  buffer += decoder.decode()
  if (buffer.trim()) {
    parseSseBlock(buffer, handlers)
  }
}

async function parseNdjsonStream(
  body: ReadableStream<Uint8Array>,
  handlers: Parameters<typeof streamGamingAiAnalysis>[1]
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  for (;;) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      parseNdjsonLine(line, handlers)
    }
  }

  buffer += decoder.decode()
  if (buffer.trim()) {
    parseNdjsonLine(buffer, handlers)
  }
}

function parseSseBlock(block: string, handlers: Parameters<typeof streamGamingAiAnalysis>[1]): void {
  let eventName = 'message'
  const dataLines: string[] = []

  for (const rawLine of block.split(/\r?\n/)) {
    const line = rawLine.trimEnd()
    if (!line || line.startsWith(':')) {
      continue
    }
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim()
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart())
    }
  }

  emitParsedStreamEvent(eventName, dataLines.join('\n'), handlers)
}

function parseNdjsonLine(line: string, handlers: Parameters<typeof streamGamingAiAnalysis>[1]): void {
  const trimmed = line.trim()
  if (!trimmed) {
    return
  }

  try {
    const event = JSON.parse(trimmed) as Partial<GamingAiStreamEvent>
    if (typeof event.type === 'string') {
      emitParsedStreamEvent(event.type, JSON.stringify(event), handlers)
      return
    }
  } catch {
    // Plain text lines are treated as streaming deltas.
  }

  emitStreamEvent({ type: 'delta', text: trimmed }, handlers)
}

function emitParsedStreamEvent(
  eventName: string,
  data: string,
  handlers: Parameters<typeof streamGamingAiAnalysis>[1]
): void {
  const payload = parsePayload(data)
  const normalizedEventName = eventName === 'message' ? readString(payload, 'type') || 'delta' : eventName

  if (normalizedEventName === 'start') {
    emitStreamEvent({ type: 'start', title: readString(payload, 'title') || readText(payload) || undefined }, handlers)
    return
  }
  if (normalizedEventName === 'section') {
    emitStreamEvent({ type: 'section', title: readString(payload, 'title') || readText(payload) || data }, handlers)
    return
  }
  if (normalizedEventName === 'delta') {
    emitStreamEvent({ type: 'delta', text: readString(payload, 'text') || readText(payload) || data }, handlers)
    return
  }
  if (normalizedEventName === 'player_verdict') {
    const playerKey = readString(payload, 'playerKey')
    const label = readString(payload, 'label')
    if (playerKey && label) {
      const tone = readVerdictTone(payload)
      const reason = readString(payload, 'reason')
      emitStreamEvent(createPlayerVerdictEvent({
        playerKey,
        label,
        ...(tone ? { tone } : {}),
        ...(reason ? { reason } : {})
      }), handlers)
    }
    return
  }
  if (normalizedEventName === 'player_insight') {
    const playerKey = readString(payload, 'playerKey')
    const label = readString(payload, 'label')
    const text = readString(payload, 'text')
    if (playerKey && label && text) {
      const tone = readVerdictTone(payload)
      emitStreamEvent(createPlayerInsightEvent({
        playerKey,
        label,
        text,
        ...(tone ? { tone } : {})
      }), handlers)
    }
    return
  }
  if (normalizedEventName === 'done') {
    emitStreamEvent({ type: 'done' }, handlers)
    return
  }
  if (normalizedEventName === 'error') {
    emitStreamEvent({ type: 'error', message: readString(payload, 'message') || readText(payload) || data }, handlers)
  }
}

function emitStreamEvent(
  event: GamingAiStreamEvent,
  handlers: Parameters<typeof streamGamingAiAnalysis>[1]
): void {
  handlers.onEvent?.(event)

  if (event.type === 'delta') {
    handlers.onDelta?.(event.text)
    return
  }
  if (event.type === 'error') {
    handlers.onError?.(event.message)
    return
  }
  if (event.type === 'done') {
    handlers.onDone?.()
  }
}

function parsePayload(data: string): unknown {
  if (!data.trim()) {
    return ''
  }

  try {
    return JSON.parse(data)
  } catch {
    return data
  }
}

function readString(payload: unknown, key: string): string {
  if (!payload || typeof payload !== 'object') {
    return ''
  }
  const value = (payload as Record<string, unknown>)[key]
  return typeof value === 'string' ? value : ''
}

function readText(payload: unknown): string {
  return typeof payload === 'string' ? payload : ''
}

function createPlayerVerdictEvent(verdict: GamingAiPlayerStreamVerdict): GamingAiStreamEvent {
  const event = { ...verdict } as GamingAiStreamEvent
  Object.defineProperty(event, 'type', {
    value: 'player_verdict',
    enumerable: false
  })
  return event
}

function createPlayerInsightEvent(insight: GamingAiPlayerInsightEvent): GamingAiStreamEvent {
  const event = { ...insight } as GamingAiStreamEvent
  Object.defineProperty(event, 'type', {
    value: 'player_insight',
    enumerable: false
  })
  return event
}

function readVerdictTone(payload: unknown): GamingAiPlayerStreamVerdict['tone'] | undefined {
  const tone = readString(payload, 'tone')
  if (
    tone === 'carry' ||
    tone === 'stable' ||
    tone === 'risk' ||
    tone === 'weak' ||
    tone === 'unknown'
  ) {
    return tone
  }
  return undefined
}

function formatPlayerTagLine(player: GamingAiInputPlayer): string {
  return player.summaryLine || player.key
}

function formatTeamSnapshotText(snapshot: GamingAiTeamSnapshot): string {
  return snapshot.text || snapshot.players.map(player => formatPlayerTagLine(player)).join('\n\n')
}
