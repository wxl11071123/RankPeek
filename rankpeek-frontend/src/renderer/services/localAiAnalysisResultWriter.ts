import type {
  AiAnalysisResult,
  DatabaseResult,
  LocalDatabaseAPI
} from '../types/localDatabase'
import type { ServerAiAnalysisResult } from '../types/serverAiAnalysis'
import { toLocalAiAnalysisResultPayload } from './serverAiAnalysisClient.ts'

type AiAnalysisSaveDatabase = Pick<LocalDatabaseAPI, 'saveAnalysisResult'>

export interface SaveServerAiFinalResultToLocalParams {
  result: ServerAiAnalysisResult
  accountPuuid: string
  subjectKey?: string
  expectedInputHash?: string
  database?: AiAnalysisSaveDatabase | null
}

export interface LocalAiResultSaveResult {
  success: boolean
  id?: number
  error?: string
}

export async function saveServerAiFinalResultToLocal({
  result,
  accountPuuid,
  subjectKey,
  expectedInputHash,
  database
}: SaveServerAiFinalResultToLocalParams): Promise<LocalAiResultSaveResult> {
  const trimmedPuuid = accountPuuid.trim()
  if (!trimmedPuuid) {
    return {
      success: false,
      error: 'Missing accountPuuid'
    }
  }

  const inputHash = result.metadata?.inputHash
  if (typeof inputHash !== 'string' || inputHash.trim().length === 0) {
    return {
      success: false,
      error: 'Missing inputHash'
    }
  }

  if (expectedInputHash !== undefined && inputHash !== expectedInputHash) {
    return {
      success: false,
      error: 'Input hash mismatch'
    }
  }

  const databaseApi = database ?? getRendererDatabase()
  if (!databaseApi) {
    return {
      success: false,
      error: 'Local database unavailable'
    }
  }

  try {
    const payload = toLocalAiAnalysisResultPayload({
      result,
      accountPuuid: trimmedPuuid,
      subjectKey
    })
    const saved = await databaseApi.saveAnalysisResult(payload)
    return toSaveResult(saved)
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : String(error)
    }
  }
}

function toSaveResult(result: DatabaseResult<AiAnalysisResult>): LocalAiResultSaveResult {
  if (!result.success) {
    return {
      success: false,
      error: result.error
    }
  }

  return {
    success: true,
    id: result.data.id
  }
}

function getRendererDatabase(): AiAnalysisSaveDatabase | null {
  if (typeof window === 'undefined') {
    return null
  }

  return window.electronAPI?.database ?? null
}
