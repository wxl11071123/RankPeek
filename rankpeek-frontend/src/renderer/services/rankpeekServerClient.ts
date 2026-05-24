export const RANKPEEK_SERVER_BASE_URL = 'http://127.0.0.1:18080'
export const RANKPEEK_SERVER_DIAGNOSTICS_ENDPOINT = '/api/server/diagnostics'

const RANKPEEK_SERVER_UNAVAILABLE_MESSAGE = 'rankpeek-server 暂不可用，请确认 Ubuntu/WSL 服务已启动'

interface RankPeekServerDiagnosticsResponse {
  success?: boolean
  data?: {
    service?: string
    mode?: string
    version?: string
  }
  error?: {
    message?: string
  } | null
}

interface RankPeekServerApiResponse<T> {
  success?: boolean
  data?: T
  error?: {
    message?: string
  } | null
}

export interface CnChampionMeta {
  tierScope: string
  championId: number
  avgKda?: number | null
  avgGold?: number | null
  avgDamage?: number | null
  dataSourceNote?: string | null
}

export interface OpggChampionStats {
  games: number
  winRate?: number | null
  pickRate?: number | null
  banRate?: number | null
  kda?: number | null
}

export interface OpggBuildOption {
  label: string
  ids: number[]
  order?: number[]
  games?: number | null
  winRate?: number | null
  pickRate?: number | null
}

export interface OpggChampionDetail {
  championId: number
  championName?: string | null
  mode: string
  region: string
  tier: string
  position: string
  version?: string | null
  updatedAt?: string | null
  stats: OpggChampionStats
  summonerSpells: OpggBuildOption[]
  runes: OpggBuildOption[]
  skillOrders: OpggBuildOption[]
  starterItems: OpggBuildOption[]
  boots: OpggBuildOption[]
  coreItems: OpggBuildOption[]
}

export interface OpggChampionCounter {
  championId: number
  games: number
  wins?: number | null
}

export interface OpggChampionPositionStats {
  position: string
  tier?: number | null
  rank?: number | null
  stats: OpggChampionStats
  counters: OpggChampionCounter[]
}

export interface OpggChampionListItem {
  championId: number
  tier?: number | null
  rank?: number | null
  stats: OpggChampionStats
  positions: OpggChampionPositionStats[]
}

export interface OpggChampionList {
  mode: string
  region: string
  tier: string
  version?: string | null
  updatedAt?: string | null
  items: OpggChampionListItem[]
}

export interface OpggChampionDetailRequest {
  championId: number
  mode: string
  region: string
  tier: string
  position: string
}

export interface OpggChampionListRequest {
  mode: string
  region: string
  tier: string
}

export type RankPeekServerDiagnosticsCheck =
  | {
    available: true
    service: string
    mode: string
    version: string
  }
  | {
    available: false
    message: string
  }

export async function checkRankPeekServerDiagnostics(): Promise<RankPeekServerDiagnosticsCheck> {
  try {
    const response = await fetch(`${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_DIAGNOSTICS_ENDPOINT}`, {
      method: 'GET',
      headers: { Accept: 'application/json' }
    })

    if (!response.ok) {
      return { available: false, message: `${RANKPEEK_SERVER_UNAVAILABLE_MESSAGE}（HTTP ${response.status}）` }
    }

    const payload = await parseDiagnosticsResponse(response)
    if (payload.success === false) {
      return { available: false, message: payload.error?.message || RANKPEEK_SERVER_UNAVAILABLE_MESSAGE }
    }
    if (payload.success !== true || !payload.data) {
      return { available: false, message: RANKPEEK_SERVER_UNAVAILABLE_MESSAGE }
    }

    return {
      available: true,
      service: payload.data.service || 'rankpeek-server',
      mode: payload.data.mode || 'unknown',
      version: payload.data.version || 'unknown'
    }
  } catch {
    return { available: false, message: RANKPEEK_SERVER_UNAVAILABLE_MESSAGE }
  }
}

async function parseDiagnosticsResponse(response: Response): Promise<RankPeekServerDiagnosticsResponse> {
  try {
    return await response.json() as RankPeekServerDiagnosticsResponse
  } catch {
    return {}
  }
}

const championMetaCache = new Map<string, Promise<CnChampionMeta | null>>()

export function getLatestChampionMeta(championId: number, tierScope: string): Promise<CnChampionMeta | null> {
  const normalizedTier = tierScope.trim().toUpperCase()
  if (!Number.isFinite(championId) || championId <= 0 || !normalizedTier) {
    return Promise.resolve(null)
  }

  const cacheKey = `${championId}:${normalizedTier}`
  if (championMetaCache.has(cacheKey)) {
    return championMetaCache.get(cacheKey) as Promise<CnChampionMeta | null>
  }

  const endpoint = `/api/cn-meta/champions/${encodeURIComponent(String(championId))}/latest?tierScope=${encodeURIComponent(normalizedTier)}`
  const request = fetch(`${RANKPEEK_SERVER_BASE_URL}${endpoint}`, {
    method: 'GET',
    headers: { Accept: 'application/json' }
  })
    .then(async (response): Promise<CnChampionMeta | null> => {
      if (!response.ok) {
        championMetaCache.delete(cacheKey)
        return null
      }
      const payload = await parseServerJson<CnChampionMeta[]>(response)
      if (payload.success === false || !Array.isArray(payload.data) || payload.data.length === 0) {
        return null
      }
      return payload.data[0] || null
    })
    .catch(() => {
      championMetaCache.delete(cacheKey)
      return null
    })

  championMetaCache.set(cacheKey, request)
  return request
}

export async function getOpggChampionDetail(query: OpggChampionDetailRequest): Promise<OpggChampionDetail | null> {
  if (!Number.isFinite(query.championId) || query.championId <= 0 || !query.mode || !query.region) {
    return null
  }

  const params = new URLSearchParams()
  params.set('mode', query.mode)
  params.set('region', query.region)
  if (query.tier) params.set('tier', query.tier)
  if (query.position) params.set('position', query.position)

  const endpoint = `/api/opgg/champions/${encodeURIComponent(String(query.championId))}/detail?${params.toString()}`
  const response = await fetch(`${RANKPEEK_SERVER_BASE_URL}${endpoint}`, {
    method: 'GET',
    headers: { Accept: 'application/json' }
  })
  const payload = await parseServerJson<OpggChampionDetail>(response)
  if (!response.ok || payload.success === false) {
    throw new Error(payload.error?.message || `OP.GG detail request failed: HTTP ${response.status}`)
  }
  return payload.data || null
}

export async function getOpggChampionList(query: OpggChampionListRequest): Promise<OpggChampionList | null> {
  if (!query.mode || !query.region) {
    return null
  }

  const params = new URLSearchParams()
  params.set('mode', query.mode)
  params.set('region', query.region)
  params.set('tier', query.tier || 'all')

  const endpoint = `/api/opgg/champions?${params.toString()}`
  const response = await fetch(`${RANKPEEK_SERVER_BASE_URL}${endpoint}`, {
    method: 'GET',
    headers: { Accept: 'application/json' }
  })
  const payload = await parseServerJson<OpggChampionList>(response)
  if (!response.ok || payload.success === false) {
    throw new Error(payload.error?.message || `OP.GG champion list request failed: HTTP ${response.status}`)
  }
  return payload.data || null
}

async function parseServerJson<T>(response: Response): Promise<RankPeekServerApiResponse<T>> {
  try {
    return await response.json() as RankPeekServerApiResponse<T>
  } catch {
    return {}
  }
}
