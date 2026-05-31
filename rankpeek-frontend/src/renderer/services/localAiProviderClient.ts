import {
  parseLocalJson,
  RANKPEEK_LOCAL_SERVICE_BASE_URL
} from './rankpeekLocalServiceClient.ts'

export interface LocalAiPricing {
  currency: string
  inputCacheHitCnyPerMillionTokens: number
  inputCacheMissCnyPerMillionTokens: number
  outputCnyPerMillionTokens: number
}

export interface LocalAiSettings {
  enabled: boolean
  providerId: string
  baseUrl: string
  model: string
  apiKeySaved: boolean
  apiKeyMasked?: string | null
  temperature: number
  maxTokens: number
  pricing?: LocalAiPricing | null
}

export interface SaveLocalAiSettingsRequest extends Omit<LocalAiSettings, 'apiKeySaved' | 'apiKeyMasked'> {
  apiKey?: string
  saveApiKey: boolean
}

export interface LocalAiProviderProfile {
  id: string
  label: string
  dialect: string
  defaultBaseUrl: string
  models: string[]
  supportsPromptCacheUsage: boolean
}

export async function getLocalAiProviders(): Promise<LocalAiProviderProfile[]> {
  const response = await fetch(`${RANKPEEK_LOCAL_SERVICE_BASE_URL}/api/v1/ai/providers`)
  const payload = await parseLocalJson<{ providers?: LocalAiProviderProfile[] }>(response)
  if (!response.ok || payload.success === false) {
    throw new Error(readLocalApiErrorMessage(payload.error?.message))
  }
  return payload.data?.providers ?? []
}

export async function getLocalAiSettings(): Promise<LocalAiSettings> {
  const response = await fetch(`${RANKPEEK_LOCAL_SERVICE_BASE_URL}/api/v1/ai/settings`)
  const payload = await parseLocalJson<LocalAiSettings>(response)
  if (!response.ok || payload.success === false || !payload.data) {
    throw new Error(readLocalApiErrorMessage(payload.error?.message))
  }
  return payload.data
}

export async function saveLocalAiSettings(request: SaveLocalAiSettingsRequest): Promise<LocalAiSettings> {
  const response = await fetch(`${RANKPEEK_LOCAL_SERVICE_BASE_URL}/api/v1/ai/settings`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request)
  })
  const payload = await parseLocalJson<LocalAiSettings>(response)
  if (!response.ok || payload.success === false || !payload.data) {
    throw new Error(readLocalApiErrorMessage(payload.error?.message))
  }
  return payload.data
}

function readLocalApiErrorMessage(message?: string): string {
  return message?.trim() || 'Local RankPeek AI settings are unavailable.'
}
