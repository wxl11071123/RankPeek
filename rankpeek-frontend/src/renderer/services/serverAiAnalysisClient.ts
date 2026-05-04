import type { AiAnalysisResultInput } from '../types/localDatabase'
import type {
  ServerAiAnalysisError,
  ServerAiAnalysisErrorBody,
  ServerAiAnalysisJob,
  ServerAiAnalysisJobAccepted,
  ServerAiAnalysisResult,
  ServerAiAnalysisType,
  ServerAiDeliveryMode,
  ServerAiJobRequest,
  ServerAiStreamEvent,
  ServerAiStreamRequest,
  ServerAiStreamSubmitResult
} from '../types/serverAiAnalysis'

export type {
  ServerAiAnalysisError,
  ServerAiAnalysisErrorBody,
  ServerAiAnalysisJob,
  ServerAiAnalysisJobAccepted,
  ServerAiAnalysisJobStatus,
  ServerAiAnalysisResult,
  ServerAiAnalysisType,
  ServerAiDeliveryMode,
  ServerAiJobRequest,
  ServerAiStreamEvent,
  ServerAiStreamRequest,
  ServerAiStreamSubmitResult
} from '../types/serverAiAnalysis'

export interface SubmitServerAiAnalysisStreamHandlers {
  onEvent?: (event: ServerAiStreamEvent) => void
  onFinal?: (result: ServerAiAnalysisResult) => void
  onError?: (error: ServerAiAnalysisErrorBody) => void
}

export interface SubmitServerAiAnalysisStreamOptions {
  signal?: AbortSignal
}

export interface CreateServerAiAnalysisRequestParams {
  analysisType: ServerAiAnalysisType
  accountPuuid: string
  accountDisplayName?: string
  snapshotSchemaVersion: number
  inputHash: string
  snapshot: unknown
  appVersion?: string
  platform?: string
}

export interface ToLocalAiAnalysisResultPayloadParams {
  result: ServerAiAnalysisResult
  accountPuuid: string
  subjectKey?: string
}

const AI_DISABLED_ERROR: ServerAiAnalysisErrorBody = {
  code: 'AI_SERVER_DISABLED',
  message: 'AI 服务尚未接入',
  retryable: false
}

const STREAM_ABORTED_ERROR: ServerAiAnalysisErrorBody = {
  code: 'STREAM_ABORTED',
  message: 'AI stream request was aborted',
  retryable: false
}

export const SERVER_AI_STREAM_ENDPOINT = '/api/v1/ai/analysis/stream'
export const SERVER_AI_JOBS_ENDPOINT = '/api/v1/ai/analysis/jobs'

export function isServerAiEnabled(): boolean {
  return false
}

export function getServerAiDeliveryMode(analysisType: ServerAiAnalysisType): ServerAiDeliveryMode {
  switch (analysisType) {
    case 'pregame':
    case 'postgame':
    case 'compliment':
      return 'stream'
    case 'entertainment_index':
    case 'report':
    case 'coach_summary':
    case 'account_overview':
      return 'async_job'
  }
}

export function createServerAiRequestId(): string {
  return `rankpeek-ai-${Date.now()}-${createReadableRandomSegment()}`
}

export function getServerAiAnalysisJobEndpoint(jobId: string): string {
  return `${SERVER_AI_JOBS_ENDPOINT}/${encodeURIComponent(jobId)}`
}

export function createServerAiAnalysisRequest(
  params: CreateServerAiAnalysisRequestParams
): ServerAiStreamRequest | ServerAiJobRequest {
  const deliveryMode = getServerAiDeliveryMode(params.analysisType)
  const baseRequest = {
    requestId: createServerAiRequestId(),
    analysisType: params.analysisType,
    deliveryMode,
    accountPuuid: params.accountPuuid,
    ...(nonEmptyString(params.accountDisplayName) ? { accountDisplayName: params.accountDisplayName.trim() } : {}),
    inputHash: params.inputHash,
    snapshotSchemaVersion: params.snapshotSchemaVersion,
    snapshot: params.snapshot,
    client: {
      appName: 'RankPeek' as const,
      ...(nonEmptyString(params.appVersion) ? { appVersion: params.appVersion.trim() } : {}),
      ...(nonEmptyString(params.platform) ? { platform: params.platform.trim() } : {})
    }
  }

  if (deliveryMode === 'stream') {
    return {
      ...baseRequest,
      deliveryMode: 'stream'
    }
  }

  return {
    ...baseRequest,
    deliveryMode: 'async_job'
  }
}

export async function submitServerAiAnalysisStream(
  request: ServerAiStreamRequest,
  handlers: SubmitServerAiAnalysisStreamHandlers = {},
  options: SubmitServerAiAnalysisStreamOptions = {}
): Promise<ServerAiStreamSubmitResult> {
  if (options.signal?.aborted) {
    return toErrorResult(STREAM_ABORTED_ERROR)
  }

  if (!isServerAiEnabled()) {
    return toErrorResult(AI_DISABLED_ERROR)
  }

  return postServerAiStreamRequest(request, handlers, options)
}

export async function submitServerAiAnalysisJob(
  request: ServerAiJobRequest
): Promise<ServerAiAnalysisJobAccepted | ServerAiAnalysisError> {
  if (!isServerAiEnabled()) {
    return toErrorResult(AI_DISABLED_ERROR)
  }

  return postServerAiJobRequest(request)
}

export async function getServerAiAnalysisJob(
  jobId: string
): Promise<ServerAiAnalysisJob | ServerAiAnalysisError> {
  if (!isServerAiEnabled()) {
    return toErrorResult(AI_DISABLED_ERROR)
  }

  return getServerAiJobRequest(jobId)
}

export function toLocalAiAnalysisResultPayload({
  result,
  accountPuuid,
  subjectKey
}: ToLocalAiAnalysisResultPayloadParams): AiAnalysisResultInput {
  return {
    accountPuuid,
    analysisType: result.analysisType,
    subjectKey: subjectKey ?? null,
    gameVersion: result.metadata.gameVersion ?? null,
    modelName: result.metadata.modelName ?? null,
    promptVersion: result.metadata.promptVersion ?? null,
    inputHash: result.metadata.inputHash,
    outputJson: JSON.stringify(result)
  }
}

async function postServerAiStreamRequest(
  request: ServerAiStreamRequest,
  handlers: SubmitServerAiAnalysisStreamHandlers,
  options: SubmitServerAiAnalysisStreamOptions
): Promise<ServerAiStreamSubmitResult> {
  void request
  void handlers
  void options

  return toErrorResult({
    code: 'UNKNOWN',
    message: 'AI stream transport is reserved but not implemented',
    retryable: false
  })
}

async function postServerAiJobRequest(
  request: ServerAiJobRequest
): Promise<ServerAiAnalysisJobAccepted | ServerAiAnalysisError> {
  void request

  return toErrorResult({
    code: 'UNKNOWN',
    message: 'AI job transport is reserved but not implemented',
    retryable: false
  })
}

async function getServerAiJobRequest(jobId: string): Promise<ServerAiAnalysisJob | ServerAiAnalysisError> {
  void jobId

  return toErrorResult({
    code: 'UNKNOWN',
    message: 'AI job polling transport is reserved but not implemented',
    retryable: false
  })
}

function toErrorResult(error: ServerAiAnalysisErrorBody): ServerAiAnalysisError {
  return {
    ok: false,
    error
  }
}

function createReadableRandomSegment(): string {
  const cryptoApi = globalThis.crypto
  if (cryptoApi?.randomUUID) {
    return cryptoApi.randomUUID().replace(/-/g, '').slice(0, 10).toLowerCase()
  }

  return Math.random().toString(36).slice(2, 12)
}

function nonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}
