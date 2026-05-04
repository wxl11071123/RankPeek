export type ServerAiAnalysisType =
  | 'pregame'
  | 'postgame'
  | 'compliment'
  | 'entertainment_index'
  | 'report'
  | 'coach_summary'
  | 'account_overview'

export type ServerAiDeliveryMode = 'stream' | 'async_job'

export interface ServerAiClientInfo {
  appName: 'RankPeek'
  appVersion?: string
  platform?: string
}

export interface ServerAiAnalysisBaseRequest {
  requestId: string
  analysisType: ServerAiAnalysisType
  deliveryMode: ServerAiDeliveryMode
  accountPuuid: string
  accountDisplayName?: string
  inputHash: string
  snapshotSchemaVersion: number
  snapshot: unknown
  client: ServerAiClientInfo
}

export interface ServerAiStreamRequest extends ServerAiAnalysisBaseRequest {
  deliveryMode: 'stream'
}

export interface ServerAiJobRequest extends ServerAiAnalysisBaseRequest {
  deliveryMode: 'async_job'
}

export type ServerAiStreamEvent =
  | {
      type: 'start'
      requestId: string
      analysisType: ServerAiAnalysisType
      createdAt: string
    }
  | {
      type: 'section_start'
      sectionId: string
      title: string
    }
  | {
      type: 'delta'
      sectionId?: string
      text: string
    }
  | {
      type: 'section_end'
      sectionId: string
    }
  | {
      type: 'final'
      result: ServerAiAnalysisResult
    }
  | {
      type: 'error'
      error: ServerAiAnalysisErrorBody
    }
  | {
      type: 'done'
    }

export interface ServerAiAnalysisResult {
  analysisType: ServerAiAnalysisType
  title: string
  summary: string
  verdict?: string
  confidence?: number
  sections: Array<{
    title: string
    body: string
    severity?: 'info' | 'success' | 'warning' | 'danger'
    bullets?: string[]
  }>
  metadata: {
    modelName?: string
    promptVersion?: string
    gameVersion?: string
    versionContextHash?: string
    inputHash: string
    generatedAt: string
  }
}

export interface ServerAiAnalysisErrorBody {
  code:
    | 'AI_SERVER_DISABLED'
    | 'NETWORK_ERROR'
    | 'UNAUTHORIZED'
    | 'RATE_LIMITED'
    | 'INSUFFICIENT_DATA'
    | 'SERVER_ERROR'
    | 'STREAM_ABORTED'
    | 'INVALID_DELIVERY_MODE'
    | 'UNKNOWN'
  message: string
  retryable: boolean
}

export interface ServerAiAnalysisError {
  ok: false
  error: ServerAiAnalysisErrorBody
}

export interface ServerAiStreamSubmitAccepted {
  ok: true
  requestId: string
  deliveryMode: 'stream'
}

export type ServerAiStreamSubmitResult = ServerAiStreamSubmitAccepted | ServerAiAnalysisError

export interface ServerAiAnalysisJobAccepted {
  ok: true
  jobId: string
  status: 'queued' | 'running' | 'completed'
  reusedExisting?: boolean
  estimatedSeconds?: number
}

export type ServerAiAnalysisJobStatus =
  | 'queued'
  | 'running'
  | 'completed'
  | 'failed'
  | 'cancelled'

export interface ServerAiAnalysisJob {
  ok: true
  jobId: string
  status: ServerAiAnalysisJobStatus
  analysisType: ServerAiAnalysisType
  accountPuuid: string
  inputHash: string
  createdAt: string
  updatedAt: string
  result?: ServerAiAnalysisResult
  error?: ServerAiAnalysisErrorBody
}
