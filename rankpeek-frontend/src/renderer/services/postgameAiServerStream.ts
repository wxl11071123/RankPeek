import type { PostgameAiInputSnapshot, PostgameAiMode } from './postgameAiInputSnapshot.ts'
import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'
import { getStoredRankPeekAuthSession } from './rankpeekAuthClient.ts'

export const RANKPEEK_SERVER_POSTGAME_STREAM_ENDPOINT = '/api/analysis/postgame/stream'

export type PostgameAiStreamState =
  | 'idle'
  | 'preparing'
  | 'streaming'
  | 'completed'
  | 'failed'

export interface PostgameAiStreamRequest {
  mode: PostgameAiMode
  snapshotSchemaVersion: string
  snapshot: PostgameAiInputSnapshot
}

export interface PostgameAiTokenCostEstimate {
  currency: 'CNY'
  inputCacheHitCny: number
  inputCacheMissCny: number
  outputCny: number
  totalCny: number
  pricing: {
    inputCacheHitCnyPerMillionTokens: number
    inputCacheMissCnyPerMillionTokens: number
    outputCnyPerMillionTokens: number
  }
}

export interface PostgameAiTokenUsage {
  provider: string
  model: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  promptCacheHitTokens: number
  promptCacheMissTokens: number
  cost: PostgameAiTokenCostEstimate
}

export type PostgameAiStreamEvent =
  | { type: 'start'; title?: string }
  | { type: 'section'; title: string }
  | { type: 'delta'; text: string }
  | { type: 'usage'; usage: PostgameAiTokenUsage }
  | { type: 'done' }
  | { type: 'error'; message: string }

export interface PostgameAiStreamHandlers {
  onEvent?: (event: PostgameAiStreamEvent) => void
  onSection?: (title: string) => void
  onDelta?: (text: string) => void
  onUsage?: (usage: PostgameAiTokenUsage) => void
  onError?: (message: string) => void
  onDone?: () => void
}

const DEEPSEEK_MAINLAND_PRICING_CNY_PER_MILLION: Record<string, PostgameAiTokenCostEstimate['pricing']> = {
  'deepseek-v4-flash': {
    inputCacheHitCnyPerMillionTokens: 0.02,
    inputCacheMissCnyPerMillionTokens: 1,
    outputCnyPerMillionTokens: 2
  },
  'deepseek-chat': {
    inputCacheHitCnyPerMillionTokens: 0.02,
    inputCacheMissCnyPerMillionTokens: 1,
    outputCnyPerMillionTokens: 2
  },
  'deepseek-reasoner': {
    inputCacheHitCnyPerMillionTokens: 1,
    inputCacheMissCnyPerMillionTokens: 4,
    outputCnyPerMillionTokens: 16
  },
  'deepseek-v4-pro': {
    inputCacheHitCnyPerMillionTokens: 1,
    inputCacheMissCnyPerMillionTokens: 4,
    outputCnyPerMillionTokens: 16
  }
}

export function createPostgameAiStreamRequest(
  snapshot: PostgameAiInputSnapshot,
  mode: PostgameAiMode
): PostgameAiStreamRequest {
  return {
    mode,
    snapshotSchemaVersion: snapshot.schemaVersion,
    snapshot
  }
}

export function estimatePostgameAiTokenCostCny(
  usage: Pick<PostgameAiTokenUsage, 'model' | 'promptCacheHitTokens' | 'promptCacheMissTokens' | 'completionTokens'>
): PostgameAiTokenCostEstimate {
  const pricing = DEEPSEEK_MAINLAND_PRICING_CNY_PER_MILLION[normalizeDeepSeekModel(usage.model)]
    ?? DEEPSEEK_MAINLAND_PRICING_CNY_PER_MILLION['deepseek-v4-flash']
  const inputCacheHitCny = roundCurrency(usage.promptCacheHitTokens * pricing.inputCacheHitCnyPerMillionTokens / 1_000_000)
  const inputCacheMissCny = roundCurrency(usage.promptCacheMissTokens * pricing.inputCacheMissCnyPerMillionTokens / 1_000_000)
  const outputCny = roundCurrency(usage.completionTokens * pricing.outputCnyPerMillionTokens / 1_000_000)

  return {
    currency: 'CNY',
    inputCacheHitCny,
    inputCacheMissCny,
    outputCny,
    totalCny: roundCurrency(inputCacheHitCny + inputCacheMissCny + outputCny),
    pricing
  }
}

export async function streamPostgameAiAnalysis(
  request: PostgameAiStreamRequest,
  handlers: PostgameAiStreamHandlers,
  options: { signal?: AbortSignal } = {}
): Promise<{ ok: true } | { ok: false; message: string }> {
  try {
    const accessToken = getStoredRankPeekAuthSession()?.accessToken
    const response = await fetch(`${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_POSTGAME_STREAM_ENDPOINT}`, {
      method: 'POST',
      headers: createStreamRequestHeaders(accessToken),
      body: JSON.stringify(request),
      signal: options.signal
    })

    if (!response.ok) {
      const message = `rankpeek-server request failed: HTTP ${response.status}`
      emitStreamEvent({ type: 'error', message }, handlers)
      return { ok: false, message }
    }

    if (!response.body) {
      const message = 'rankpeek-server is unavailable'
      emitStreamEvent({ type: 'error', message }, handlers)
      return { ok: false, message }
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
      return { ok: false, message: 'request cancelled' }
    }

    const message = 'rankpeek-server is unavailable'
    emitStreamEvent({ type: 'error', message }, handlers)
    return { ok: false, message }
  }
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
  handlers: PostgameAiStreamHandlers
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
  handlers: PostgameAiStreamHandlers
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

function parseSseBlock(block: string, handlers: PostgameAiStreamHandlers): void {
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

function parseNdjsonLine(line: string, handlers: PostgameAiStreamHandlers): void {
  const trimmed = line.trim()
  if (!trimmed) {
    return
  }

  try {
    const event = JSON.parse(trimmed) as Partial<PostgameAiStreamEvent>
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
  handlers: PostgameAiStreamHandlers
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
  if (normalizedEventName === 'usage') {
    const usage = readTokenUsage(payload)
    if (usage) {
      emitStreamEvent({ type: 'usage', usage }, handlers)
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

function emitStreamEvent(event: PostgameAiStreamEvent, handlers: PostgameAiStreamHandlers): void {
  handlers.onEvent?.(event)

  if (event.type === 'section') {
    handlers.onSection?.(event.title)
    return
  }
  if (event.type === 'delta') {
    handlers.onDelta?.(event.text)
    return
  }
  if (event.type === 'error') {
    handlers.onError?.(event.message)
    return
  }
  if (event.type === 'usage') {
    handlers.onUsage?.(event.usage)
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

function readTokenUsage(payload: unknown): PostgameAiTokenUsage | null {
  const source = readObject(readObject(payload)?.usage) || readObject(payload)
  if (!source) {
    return null
  }

  const model = readString(source, 'model') || 'deepseek-v4-flash'
  const rawUsage = {
    provider: readString(source, 'provider') || 'deepseek',
    model,
    promptTokens: readUsageNumber(source, 'promptTokens', 'prompt_tokens'),
    completionTokens: readUsageNumber(source, 'completionTokens', 'completion_tokens'),
    totalTokens: readUsageNumber(source, 'totalTokens', 'total_tokens'),
    promptCacheHitTokens: readUsageNumber(source, 'promptCacheHitTokens', 'prompt_cache_hit_tokens'),
    promptCacheMissTokens: readUsageNumber(source, 'promptCacheMissTokens', 'prompt_cache_miss_tokens')
  }

  return {
    ...rawUsage,
    cost: estimatePostgameAiTokenCostCny(rawUsage)
  }
}

function readObject(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null
}

function readUsageNumber(payload: Record<string, unknown>, camelKey: string, snakeKey: string): number {
  return readNonNegativeNumber(payload[camelKey]) || readNonNegativeNumber(payload[snakeKey])
}

function readNonNegativeNumber(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? Math.floor(value) : 0
}

function normalizeDeepSeekModel(model: string): string {
  return model.trim().toLowerCase()
}

function roundCurrency(value: number): number {
  return Number(value.toFixed(12))
}
