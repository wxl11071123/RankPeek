export const POSTGAME_LADU_LEVELS = ['夯', '顶级', '人上人', 'NPC', '拉完了'] as const

export type PostgameLaduLevel = typeof POSTGAME_LADU_LEVELS[number]

export interface PostgameAiStructuredPlayer {
  level: PostgameLaduLevel
  playerRef: string
  championName: string
  championId?: number | null
  side?: string
  role?: string
  phrase: string
}

export interface PostgameAiStructuredLevel {
  label: PostgameLaduLevel
  players: PostgameAiStructuredPlayer[]
}

export interface PostgameAiStructuredResult {
  schemaVersion: 'postgame_review_result.v1'
  levels: PostgameAiStructuredLevel[]
  players: PostgameAiStructuredPlayer[]
  summary: string
}

export interface PostgameAiReviewRosterPlayer {
  playerRef: string
  championName: string
  championId: number | null
  side?: string
  role?: string
  isSelf?: boolean
  iconUrl?: string
}

export type PostgameAiStructuredParseResult =
  | { ok: true; result: PostgameAiStructuredResult }
  | { ok: false; error: string }

export type PostgameAiStructuredPartialParseResult =
  | { ok: true; result: PostgameAiStructuredResult; partial: boolean }
  | { ok: false; error: string }

export function parsePostgameAiStructuredResult(text: string): PostgameAiStructuredParseResult {
  const jsonText = extractJsonObject(text)
  if (!jsonText) {
    return { ok: false, error: '未找到结构化 JSON' }
  }

  let payload: unknown
  try {
    payload = JSON.parse(jsonText)
  } catch {
    return { ok: false, error: '结构化 JSON 解析失败' }
  }

  if (!payload || typeof payload !== 'object') {
    return { ok: false, error: '结构化结果不是对象' }
  }

  const source = payload as Record<string, unknown>
  const rawLevels = readArray(source.levels)
  const summary = readNonEmptyString(source.summary)
  if (!summary) {
    return { ok: false, error: '结构化结果缺少 summary' }
  }

  const levels = normalizeLevels(rawLevels)
  const players = levels.flatMap(level => level.players)
  if (players.length !== 10) {
    return { ok: false, error: `结构化结果必须包含 10 个玩家，当前为 ${players.length} 个` }
  }

  return {
    ok: true,
    result: {
      schemaVersion: 'postgame_review_result.v1',
      levels,
      players,
      summary
    }
  }
}

export function parsePartialPostgameAiStructuredResult(text: string): PostgameAiStructuredPartialParseResult {
  const complete = parsePostgameAiStructuredResult(text)
  if (complete.ok) {
    return { ok: true, result: complete.result, partial: false }
  }

  const source = stripJsonFence(text)
  const playersByKey = new Map<string, PostgameAiStructuredPlayer>()
  for (const label of POSTGAME_LADU_LEVELS) {
    for (const rawPlayer of extractCompletedPlayersForLevel(source, label)) {
      const player = normalizePlayer(rawPlayer, label)
      if (player && !playersByKey.has(player.playerRef)) {
        playersByKey.set(player.playerRef, player)
      }
    }
  }

  const players = [...playersByKey.values()]
  const summary = extractCompleteJsonStringField(source, 'summary')
  if (!players.length && !summary) {
    return { ok: false, error: complete.error }
  }

  const levels = normalizeLevelsFromPlayers(players)
  return {
    ok: true,
    partial: true,
    result: {
      schemaVersion: 'postgame_review_result.v1',
      levels,
      players: levels.flatMap(level => level.players),
      summary
    }
  }
}

function normalizeLevels(rawLevels: unknown[]): PostgameAiStructuredLevel[] {
  const playersByLevel = new Map<PostgameLaduLevel, PostgameAiStructuredPlayer[]>()
  for (const label of POSTGAME_LADU_LEVELS) {
    playersByLevel.set(label, [])
  }

  for (const rawLevel of rawLevels) {
    if (!rawLevel || typeof rawLevel !== 'object') {
      continue
    }
    const source = rawLevel as Record<string, unknown>
    const label = normalizeLaduLevel(readNonEmptyString(source.label) || readNonEmptyString(source.level))
    if (!label) {
      continue
    }

    for (const rawPlayer of readArray(source.players)) {
      const player = normalizePlayer(rawPlayer, label)
      if (player) {
        playersByLevel.get(label)?.push(player)
      }
    }
  }

  return POSTGAME_LADU_LEVELS.map(label => ({
    label,
    players: playersByLevel.get(label) ?? []
  }))
}

function normalizeLevelsFromPlayers(players: PostgameAiStructuredPlayer[]): PostgameAiStructuredLevel[] {
  const playersByLevel = new Map<PostgameLaduLevel, PostgameAiStructuredPlayer[]>()
  for (const label of POSTGAME_LADU_LEVELS) {
    playersByLevel.set(label, [])
  }

  for (const player of players) {
    playersByLevel.get(player.level)?.push(player)
  }

  return POSTGAME_LADU_LEVELS.map(label => ({
    label,
    players: playersByLevel.get(label) ?? []
  }))
}

function normalizePlayer(rawPlayer: unknown, fallbackLevel: PostgameLaduLevel): PostgameAiStructuredPlayer | null {
  if (!rawPlayer || typeof rawPlayer !== 'object') {
    return null
  }

  const source = rawPlayer as Record<string, unknown>
  const level = normalizeLaduLevel(readNonEmptyString(source.level)) ?? fallbackLevel
  const playerRef = readNonEmptyString(source.playerRef) || readNonEmptyString(source.label)
  const championName = readNonEmptyString(source.championName)
  const phrase = readNonEmptyString(source.phrase) || readNonEmptyString(source.reason)
  if (!playerRef || !championName || !phrase) {
    return null
  }

  const championId = readPositiveInteger(source.championId)
  return {
    level,
    playerRef,
    championName,
    ...(championId !== null ? { championId } : {}),
    ...(readNonEmptyString(source.side) ? { side: readNonEmptyString(source.side) as string } : {}),
    ...(readNonEmptyString(source.role) ? { role: readNonEmptyString(source.role) as string } : {}),
    phrase
  }
}

function normalizeLaduLevel(value: string): PostgameLaduLevel | null {
  const normalized = value.trim()
  if (!normalized) {
    return null
  }
  if (normalized.toUpperCase() === 'NPC') {
    return 'NPC'
  }
  return POSTGAME_LADU_LEVELS.find(level => level === normalized) ?? null
}

function extractJsonObject(text: string): string {
  const withoutFence = stripJsonFence(text)
  const start = withoutFence.indexOf('{')
  if (start < 0) {
    return ''
  }

  let depth = 0
  let inString = false
  let escaped = false
  for (let index = start; index < withoutFence.length; index += 1) {
    const char = withoutFence[index]
    if (escaped) {
      escaped = false
      continue
    }
    if (char === '\\') {
      escaped = true
      continue
    }
    if (char === '"') {
      inString = !inString
      continue
    }
    if (inString) {
      continue
    }
    if (char === '{') {
      depth += 1
    } else if (char === '}') {
      depth -= 1
      if (depth === 0) {
        return withoutFence.slice(start, index + 1)
      }
    }
  }

  return ''
}

function stripJsonFence(text: string): string {
  return text.trim()
    .replace(/^```(?:json)?\s*/i, '')
    .replace(/\s*```$/i, '')
}

function extractCompletedPlayersForLevel(text: string, label: PostgameLaduLevel): unknown[] {
  const players: unknown[] = []
  const labelPattern = new RegExp(`"label"\\s*:\\s*${escapeRegExp(JSON.stringify(label))}`, 'g')
  let match: RegExpExecArray | null
  while ((match = labelPattern.exec(text)) !== null) {
    const arrayStart = findPlayersArrayStart(text, match.index + match[0].length)
    if (arrayStart < 0) {
      continue
    }
    players.push(...parseCompletedObjectsInArray(text, arrayStart))
  }
  return players
}

function findPlayersArrayStart(text: string, fromIndex: number): number {
  const playersPattern = /"players"\s*:\s*\[/g
  playersPattern.lastIndex = fromIndex
  const match = playersPattern.exec(text)
  if (!match) {
    return -1
  }
  return match.index + match[0].lastIndexOf('[')
}

function parseCompletedObjectsInArray(text: string, arrayStart: number): unknown[] {
  const objects: unknown[] = []
  let objectStart = -1
  let objectDepth = 0
  let inString = false
  let escaped = false

  for (let index = arrayStart + 1; index < text.length; index += 1) {
    const char = text[index]
    if (escaped) {
      escaped = false
      continue
    }
    if (char === '\\') {
      escaped = true
      continue
    }
    if (char === '"') {
      inString = !inString
      continue
    }
    if (inString) {
      continue
    }
    if (char === ']' && objectDepth === 0) {
      break
    }
    if (char === '{') {
      if (objectDepth === 0) {
        objectStart = index
      }
      objectDepth += 1
      continue
    }
    if (char === '}') {
      objectDepth -= 1
      if (objectDepth === 0 && objectStart >= 0) {
        const objectText = text.slice(objectStart, index + 1)
        try {
          objects.push(JSON.parse(objectText) as unknown)
        } catch {
          // Ignore malformed or still-streaming objects.
        }
        objectStart = -1
      }
    }
  }

  return objects
}

function extractCompleteJsonStringField(text: string, fieldName: string): string {
  const fieldPattern = new RegExp(`"${escapeRegExp(fieldName)}"\\s*:\\s*"`, 'g')
  const match = fieldPattern.exec(text)
  if (!match) {
    return ''
  }

  const valueStart = match.index + match[0].length - 1
  let escaped = false
  for (let index = valueStart + 1; index < text.length; index += 1) {
    const char = text[index]
    if (escaped) {
      escaped = false
      continue
    }
    if (char === '\\') {
      escaped = true
      continue
    }
    if (char === '"') {
      try {
        const parsed = JSON.parse(text.slice(valueStart, index + 1)) as unknown
        return readNonEmptyString(parsed)
      } catch {
        return ''
      }
    }
  }
  return ''
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function readArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function readNonEmptyString(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function readPositiveInteger(value: unknown): number | null {
  if (typeof value === 'number' && Number.isInteger(value) && value > 0) {
    return value
  }
  if (typeof value !== 'string' || !value.trim()) {
    return null
  }
  const parsed = Number.parseInt(value, 10)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}
