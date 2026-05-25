import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'
import type { CoachSummaryPromptPayload } from './coachSummaryPrompt'
import type { CoachSummaryConfidence, CoachSummaryReportV1 } from '../types/coachSummaryReport'

export const RANKPEEK_SERVER_COACH_SUMMARY_ENDPOINT = '/api/analysis/coach-summary'

export interface GenerateCoachSummaryReportParams {
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
  inputHash,
  snapshotSchemaVersion,
  dataQualityConfidence,
  promptPayload
}: GenerateCoachSummaryReportParams): Promise<GenerateCoachSummaryReportResult> {
  try {
    const response = await fetch(`${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_COACH_SUMMARY_ENDPOINT}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        inputHash,
        snapshotSchemaVersion,
        promptVersion: promptPayload.promptVersion,
        dataQualityConfidence,
        systemPrompt: promptPayload.systemPrompt,
        userPrompt: promptPayload.userPrompt
      })
    })

    if (!response.ok) {
      return {
        ok: false,
        message: `rankpeek-server request failed: HTTP ${response.status}`
      }
    }

    const payload = await response.json() as ApiResponse<CoachSummaryAiResponseData>
    if (!payload.success) {
      return {
        ok: false,
        message: payload.error?.message || payload.error?.code || 'rankpeek-server returned an error'
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
