import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'
import {
  getStoredRankPeekAuthSession,
  refreshStoredRankPeekAuthSession
} from './rankpeekAuthClient.ts'
import type { CoachSummaryPromptPayload } from './coachSummaryPrompt'
import type { CoachSummaryConfidence, CoachSummaryReportV1 } from '../types/coachSummaryReport'

export const RANKPEEK_SERVER_COACH_SUMMARY_ENDPOINT = '/api/analysis/coach-summary'
const AUTH_LOGIN_REQUIRED_MESSAGE = 'RankPeek account login is required'
const IDEMPOTENCY_HEADER = 'X-RankPeek-Idempotency-Key'

export interface GenerateCoachSummaryReportParams {
  accessToken?: string
  inputHash: string
  snapshotSchemaVersion: string
  dataQualityConfidence: CoachSummaryConfidence
  promptPayload: CoachSummaryPromptPayload
}

export interface CoachSummaryAiTokenUsage {
  provider: string
  model: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  promptCacheHitTokens?: number
  promptCacheMissTokens?: number
}

export type GenerateCoachSummaryReportResult = {
  ok: true
  report: CoachSummaryReportV1
  usage?: CoachSummaryAiTokenUsage
} | {
  ok: false
  message: string
}

interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: {
    code?: string
    message?: string
  }
}

interface CoachSummaryAiResponseData {
  report?: CoachSummaryReportV1
  usage?: CoachSummaryAiTokenUsage
}

export async function generateCoachSummaryReport({
  accessToken,
  inputHash,
  snapshotSchemaVersion,
  dataQualityConfidence,
  promptPayload
}: GenerateCoachSummaryReportParams): Promise<GenerateCoachSummaryReportResult> {
  const session = accessToken ? null : getStoredRankPeekAuthSession()
  const requestAccessToken = accessToken ?? session?.accessToken
  if (!requestAccessToken) {
    return { ok: false, message: AUTH_LOGIN_REQUIRED_MESSAGE }
  }

  try {
    let response = await postCoachSummaryRequest(
      requestAccessToken,
      inputHash,
      snapshotSchemaVersion,
      dataQualityConfidence,
      promptPayload
    )

    if (response.status === 401 && !accessToken && session?.refreshToken) {
      const refreshResult = await refreshStoredRankPeekAuthSession()
      if (!refreshResult.ok) {
        return { ok: false, message: refreshResult.message }
      }
      response = await postCoachSummaryRequest(
        refreshResult.session.accessToken,
        inputHash,
        snapshotSchemaVersion,
        dataQualityConfidence,
        promptPayload
      )
    }

    const payload = await parseApiResponse<CoachSummaryAiResponseData>(response)
    if (!response.ok || !payload.success) {
      return {
        ok: false,
        message: payload.error?.message
          || payload.error?.code
          || `rankpeek-server request failed: HTTP ${response.status}`
      }
    }
    if (!payload.data?.report) {
      return {
        ok: false,
        message: 'rankpeek-server returned an empty coach summary report'
      }
    }

    return {
      ok: true,
      report: payload.data.report,
      usage: payload.data.usage
    }
  } catch (error) {
    return {
      ok: false,
      message: error instanceof Error ? error.message : String(error)
    }
  }
}

async function postCoachSummaryRequest(
  accessToken: string,
  inputHash: string,
  snapshotSchemaVersion: string,
  dataQualityConfidence: CoachSummaryConfidence,
  promptPayload: CoachSummaryPromptPayload
): Promise<Response> {
  return fetch(`${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_COACH_SUMMARY_ENDPOINT}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
      [IDEMPOTENCY_HEADER]: `coach-summary:${inputHash}`
    },
    body: JSON.stringify({
      inputHash,
      snapshotSchemaVersion,
      promptVersion: promptPayload.promptVersion,
      dataQualityConfidence,
      systemPrompt: promptPayload.systemPrompt,
      userPrompt: promptPayload.userPrompt
    })
  })
}

async function parseApiResponse<T>(response: Response): Promise<ApiResponse<T>> {
  try {
    return await response.json() as ApiResponse<T>
  } catch {
    return {
      success: false,
      error: {
        code: 'BAD_RESPONSE',
        message: 'rankpeek-server returned an invalid response'
      }
    }
  }
}
