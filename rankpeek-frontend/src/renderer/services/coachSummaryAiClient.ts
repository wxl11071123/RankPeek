import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'
import {
  getStoredRankPeekAuthSession,
  refreshStoredRankPeekAuthSession
} from './rankpeekAuthClient.ts'
import type { CoachSummaryPromptPayload } from './coachSummaryPrompt'
import type { CoachSummaryConfidence, CoachSummaryReportV1 } from '../types/coachSummaryReport'

export const RANKPEEK_SERVER_COACH_SUMMARY_ENDPOINT = '/api/analysis/coach-summary'
const COACH_SUMMARY_LOGIN_REQUIRED_MESSAGE = '请先登录 RankPeek 账号后再使用 AI 分析。'
const COACH_SUMMARY_LOGIN_EXPIRED_MESSAGE = '登录状态已失效，请重新登录后再试。'
const COACH_SUMMARY_INSUFFICIENT_CREDITS_MESSAGE = 'AI 分析次数不足，请充值后再试。'
const COACH_SUMMARY_RATE_LIMIT_MESSAGE = '请求太频繁，请稍后再试。'
const COACH_SUMMARY_UNAVAILABLE_MESSAGE = 'AI 服务暂时不可用，请稍后再试。'
const COACH_SUMMARY_BAD_REQUEST_MESSAGE = '请求无法完成，请稍后再试。'
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
    return { ok: false, message: COACH_SUMMARY_LOGIN_REQUIRED_MESSAGE }
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
        return { ok: false, message: COACH_SUMMARY_LOGIN_EXPIRED_MESSAGE }
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
        message: toCoachSummaryUserFacingErrorMessage(response.status, payload.error?.code)
      }
    }
    if (!payload.data?.report) {
      return {
        ok: false,
        message: COACH_SUMMARY_UNAVAILABLE_MESSAGE
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
      message: COACH_SUMMARY_UNAVAILABLE_MESSAGE
    }
  }
}

function toCoachSummaryUserFacingErrorMessage(status: number, code?: string): string {
  if (status === 401 || code === 'ACCESS_TOKEN_INVALID' || code === 'REFRESH_TOKEN_INVALID') {
    return COACH_SUMMARY_LOGIN_EXPIRED_MESSAGE
  }
  if (status === 402 || code === 'INSUFFICIENT_CREDITS') {
    return COACH_SUMMARY_INSUFFICIENT_CREDITS_MESSAGE
  }
  if (status === 429 || code === 'RATE_LIMIT_EXCEEDED') {
    return COACH_SUMMARY_RATE_LIMIT_MESSAGE
  }
  if (status >= 500) {
    return COACH_SUMMARY_UNAVAILABLE_MESSAGE
  }
  return COACH_SUMMARY_BAD_REQUEST_MESSAGE
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
