import { apiClient } from '@/api/httpClient'
import { QUEUE_ID } from './constants'

export const DEFAULT_ANALYSIS_QUEUE_MODE = QUEUE_ID.SOLO_5X5

const VALID_MATCH_QUEUE_MODES = new Set<number>([
  0,
  QUEUE_ID.SOLO_5X5,
  QUEUE_ID.FLEX_SR,
  QUEUE_ID.NORMAL,
  QUEUE_ID.ARAM,
  QUEUE_ID.HEXTECH_ARAM
])

let cachedDefaultMatchQueueMode: number | null = null
let pendingDefaultMatchQueueMode: Promise<number> | null = null

export function normalizeMatchQueueMode(value: unknown): number {
  const rawValue = typeof value === 'number'
    ? value
    : typeof value === 'string'
      ? Number.parseInt(value, 10)
      : Number.NaN

  return VALID_MATCH_QUEUE_MODES.has(rawValue) ? rawValue : 0
}

export async function getDefaultMatchQueueMode(forceReload = false): Promise<number> {
  if (!forceReload && cachedDefaultMatchQueueMode != null) {
    return cachedDefaultMatchQueueMode
  }

  if (!forceReload && pendingDefaultMatchQueueMode) {
    return pendingDefaultMatchQueueMode
  }

  pendingDefaultMatchQueueMode = (async () => {
    try {
      const value = await apiClient.getConfigValue('settings.match.defaultQueueMode')
      cachedDefaultMatchQueueMode = normalizeMatchQueueMode(value)
    } catch (error) {
      console.warn('Failed to load default match queue mode, fallback to all modes', error)
      cachedDefaultMatchQueueMode = 0
    } finally {
      pendingDefaultMatchQueueMode = null
    }

    return cachedDefaultMatchQueueMode
  })()

  return pendingDefaultMatchQueueMode
}

export function setCachedDefaultMatchQueueMode(value: unknown): number {
  cachedDefaultMatchQueueMode = normalizeMatchQueueMode(value)
  return cachedDefaultMatchQueueMode
}
