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
