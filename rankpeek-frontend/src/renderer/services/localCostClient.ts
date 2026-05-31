import {
  parseLocalJson,
  RANKPEEK_LOCAL_SERVICE_BASE_URL
} from './rankpeekLocalServiceClient.ts'

export interface LocalCostSummary {
  from: string
  to: string
  totalCostCny: number
}

export interface LocalCostEvent {
  id: number
  eventType: string
  provider?: string | null
  model?: string | null
  source?: string | null
  amountCny?: number | null
  currency?: string | null
  quantity?: number | null
  metadataRawJson?: string | null
  createdAt: number
}

export async function getLocalCostSummary(params: { from?: string; to?: string } = {}): Promise<LocalCostSummary> {
  const response = await fetch(`${RANKPEEK_LOCAL_SERVICE_BASE_URL}/api/v1/costs/summary${toQueryString(params)}`)
  const payload = await parseLocalJson<LocalCostSummary>(response)
  if (!response.ok || payload.success === false || !payload.data) {
    throw new Error(readLocalApiErrorMessage(payload.error?.message))
  }
  return payload.data
}

export async function getLocalCostEvents(params: {
  type?: string
  limit?: number
  offset?: number
} = {}): Promise<LocalCostEvent[]> {
  const response = await fetch(`${RANKPEEK_LOCAL_SERVICE_BASE_URL}/api/v1/costs/events${toQueryString(params)}`)
  const payload = await parseLocalJson<{ events?: LocalCostEvent[] }>(response)
  if (!response.ok || payload.success === false) {
    throw new Error(readLocalApiErrorMessage(payload.error?.message))
  }
  return payload.data?.events ?? []
}

function toQueryString(params: Record<string, string | number | undefined>): string {
  const searchParams = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      searchParams.set(key, String(value))
    }
  }
  const query = searchParams.toString()
  return query ? `?${query}` : ''
}

function readLocalApiErrorMessage(message?: string): string {
  return message?.trim() || 'Local RankPeek cost data is unavailable.'
}
