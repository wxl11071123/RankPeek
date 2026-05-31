import {
  parseLocalJson,
  RANKPEEK_LOCAL_SERVICE_BASE_URL
} from './rankpeekLocalServiceClient.ts'

export interface LocalCostSummary {
  from: string
  to: string
  aiCostCny: number
  manualCostCny: number
  totalCostCny: number
  eventCount: number
  manualItemCount: number
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

export interface LocalManualCostItem {
  id: number
  label: string
  category: string
  amountCny: number
  cadence: 'one_time' | 'monthly' | 'yearly'
  effectiveDate: string
  note?: string | null
  active: boolean
  createdAt: number
  updatedAt: number
}

export interface LocalManualCostRequest {
  label: string
  category: string
  amountCny: number
  cadence: 'one_time' | 'monthly' | 'yearly'
  effectiveDate: string
  note?: string | null
  active?: boolean
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

export async function getLocalManualCosts(): Promise<LocalManualCostItem[]> {
  const response = await fetch(`${RANKPEEK_LOCAL_SERVICE_BASE_URL}/api/v1/costs/manual`)
  const payload = await parseLocalJson<{ items?: LocalManualCostItem[] }>(response)
  if (!response.ok || payload.success === false) {
    throw new Error(readLocalApiErrorMessage(payload.error?.message))
  }
  return payload.data?.items ?? []
}

export async function createLocalManualCost(request: LocalManualCostRequest): Promise<LocalManualCostItem> {
  const response = await fetch(`${RANKPEEK_LOCAL_SERVICE_BASE_URL}/api/v1/costs/manual`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request)
  })
  const payload = await parseLocalJson<LocalManualCostItem>(response)
  if (!response.ok || payload.success === false || !payload.data) {
    throw new Error(readLocalApiErrorMessage(payload.error?.message))
  }
  return payload.data
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
