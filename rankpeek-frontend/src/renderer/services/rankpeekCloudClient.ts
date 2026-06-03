export type RankPeekFeedbackCategory = 'bug' | 'suggestion' | 'other'

export interface RankPeekFeedbackInput {
  message: string
  contact?: string
  category?: RankPeekFeedbackCategory
}

export interface RankPeekAppMetadata {
  appVersion: string
  platform: string
  locale: string
  installationId: string
}

export interface RankPeekFeedbackPayload {
  category: RankPeekFeedbackCategory
  contact: string
  message: string
  appVersion: string
  platform: string
  locale: string
  installationId: string
}

export interface RankPeekFeedbackResult {
  id: string
  notificationQueued: boolean
}

export interface RankPeekAnnouncement {
  id: string
  title: string
  body: string
  level: 'info' | 'warning' | 'critical'
  linkUrl: string | null
  startsAt: string | null
  endsAt: string | null
}

export interface RankPeekAnnouncementQuery {
  version: string
  platform: string
  locale: string
  channel?: string
}

interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: {
    code?: string
    message?: string
  }
}

interface CloudClientOptions {
  baseUrl?: string
  storage?: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>
}

const viteEnv = (import.meta as ImportMeta & { env?: Record<string, string | undefined> }).env
const DEFAULT_CLOUD_API_BASE_URL = 'https://api.rankpeek.cn'
const INSTALLATION_ID_STORAGE_KEY = 'rankpeek.cloud.installationId'
const DISMISSED_ANNOUNCEMENTS_STORAGE_KEY = 'rankpeek.cloud.dismissedAnnouncements'

export const RANKPEEK_CLOUD_API_BASE_URL = normalizeCloudApiBaseUrl(
  viteEnv?.VITE_RANKPEEK_CLOUD_API_BASE_URL || DEFAULT_CLOUD_API_BASE_URL
)

export function normalizeCloudApiBaseUrl(value: string): string {
  const normalized = value.trim().replace(/\/+$/, '')
  return normalized || DEFAULT_CLOUD_API_BASE_URL
}

export function buildRankPeekFeedbackPayload(
  input: RankPeekFeedbackInput,
  metadata: RankPeekAppMetadata
): RankPeekFeedbackPayload {
  return {
    category: normalizeFeedbackCategory(input.category),
    contact: input.contact?.trim() || '',
    message: input.message.trim(),
    appVersion: metadata.appVersion.trim() || 'unknown',
    platform: metadata.platform.trim() || 'unknown',
    locale: metadata.locale.trim() || 'zh-CN',
    installationId: metadata.installationId.trim()
  }
}

export async function submitRankPeekFeedback(
  input: RankPeekFeedbackInput,
  metadata: RankPeekAppMetadata,
  options: CloudClientOptions = {}
): Promise<RankPeekFeedbackResult> {
  const response = await fetch(`${resolveBaseUrl(options)}/app/feedback`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(buildRankPeekFeedbackPayload(input, metadata))
  })

  return readApiResponse<RankPeekFeedbackResult>(response)
}

export async function fetchRankPeekAnnouncements(
  query: RankPeekAnnouncementQuery,
  options: CloudClientOptions = {}
): Promise<RankPeekAnnouncement[]> {
  const params = new URLSearchParams({
    version: query.version,
    platform: query.platform,
    locale: query.locale,
    channel: query.channel || 'stable'
  })

  try {
    const response = await fetch(`${resolveBaseUrl(options)}/app/announcements?${params.toString()}`, {
      method: 'GET'
    })
    const announcements = await readApiResponse<RankPeekAnnouncement[]>(response)
    const storage = options.storage ?? getBrowserStorage()
    return announcements.filter(announcement => !isRankPeekAnnouncementDismissed(announcement.id, storage))
  } catch {
    return []
  }
}

export function getOrCreateCloudInstallationId(
  storage: Pick<Storage, 'getItem' | 'setItem'> | null = getBrowserStorage(),
  uuidFactory = createUuid
): string {
  const stored = storage?.getItem(INSTALLATION_ID_STORAGE_KEY)?.trim()
  if (stored) {
    return stored
  }

  const id = `rp-${uuidFactory()}`
  storage?.setItem(INSTALLATION_ID_STORAGE_KEY, id)
  return id
}

export function dismissRankPeekAnnouncement(
  id: string,
  storage: Pick<Storage, 'getItem' | 'setItem'> | null = getBrowserStorage()
): void {
  const trimmedId = id.trim()
  if (!trimmedId) {
    return
  }

  const ids = readDismissedAnnouncementIds(storage)
  ids.add(trimmedId)
  storage?.setItem(DISMISSED_ANNOUNCEMENTS_STORAGE_KEY, JSON.stringify(Array.from(ids).slice(-80)))
}

export function isRankPeekAnnouncementDismissed(
  id: string,
  storage: Pick<Storage, 'getItem'> | null = getBrowserStorage()
): boolean {
  return readDismissedAnnouncementIds(storage).has(id)
}

function readDismissedAnnouncementIds(storage: Pick<Storage, 'getItem'> | null): Set<string> {
  try {
    const raw = storage?.getItem(DISMISSED_ANNOUNCEMENTS_STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    return new Set(Array.isArray(parsed) ? parsed.filter(item => typeof item === 'string') : [])
  } catch {
    return new Set()
  }
}

async function readApiResponse<T>(response: Response): Promise<T> {
  let payload: ApiResponse<T> | null = null
  try {
    payload = await response.json()
  } catch {
    payload = null
  }

  if (!response.ok || !payload?.success) {
    const message = payload?.error?.message || `RankPeek cloud request failed with status ${response.status}`
    throw new Error(message)
  }

  return payload.data as T
}

function normalizeFeedbackCategory(category?: RankPeekFeedbackCategory): RankPeekFeedbackCategory {
  return category === 'bug' || category === 'suggestion' || category === 'other' ? category : 'other'
}

function resolveBaseUrl(options: CloudClientOptions): string {
  return normalizeCloudApiBaseUrl(options.baseUrl || RANKPEEK_CLOUD_API_BASE_URL)
}

function getBrowserStorage(): Storage | null {
  try {
    return typeof window !== 'undefined' ? window.localStorage : null
  } catch {
    return null
  }
}

function createUuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}
