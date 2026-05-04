import test from 'node:test'
import assert from 'node:assert/strict'
import type {
  ServerAiAnalysisResult,
  ServerAiJobRequest,
  ServerAiStreamRequest
} from '../types/serverAiAnalysis.ts'
import * as serverAiClient from './serverAiAnalysisClient.ts'
import {
  createServerAiAnalysisRequest,
  createServerAiRequestId,
  getServerAiAnalysisJobEndpoint,
  getServerAiDeliveryMode,
  getServerAiAnalysisJob,
  isServerAiEnabled,
  SERVER_AI_JOBS_ENDPOINT,
  SERVER_AI_STREAM_ENDPOINT,
  submitServerAiAnalysisJob,
  submitServerAiAnalysisStream,
  toLocalAiAnalysisResultPayload
} from './serverAiAnalysisClient.ts'

const snapshot = {
  schemaVersion: 1,
  accountPuuid: 'account-puuid',
  inputHash: 'input-hash-1',
  aggregate: {
    totalMatches: 3
  }
}

const finalResult: ServerAiAnalysisResult = {
  analysisType: 'postgame',
  title: 'Post-game Review',
  summary: 'A clean final result from the server.',
  verdict: 'Keep the tempo around objectives.',
  confidence: 0.87,
  sections: [
    {
      title: 'Deaths',
      body: 'Two deaths happened before neutral objectives.',
      severity: 'warning',
      bullets: ['Reset earlier', 'Ward before walking river']
    }
  ],
  metadata: {
    modelName: 'rankpeek-server-model',
    promptVersion: 'server-prompt-v1',
    gameVersion: '15.8',
    versionContextHash: 'version-context-hash',
    inputHash: 'input-hash-1',
    generatedAt: '2026-04-29T12:00:00.000Z'
  }
}

function makeStreamRequest(): ServerAiStreamRequest {
  const request = createServerAiAnalysisRequest({
    analysisType: 'postgame',
    accountPuuid: 'account-puuid',
    accountDisplayName: 'RankPeek#0001',
    snapshotSchemaVersion: 1,
    inputHash: 'input-hash-1',
    snapshot,
    appVersion: '1.0.0',
    platform: 'win32'
  })

  assert.equal(request.deliveryMode, 'stream')
  return request
}

function makeJobRequest(): ServerAiJobRequest {
  const request = createServerAiAnalysisRequest({
    analysisType: 'account_overview',
    accountPuuid: 'account-puuid',
    snapshotSchemaVersion: 1,
    inputHash: 'input-hash-1',
    snapshot
  })

  assert.equal(request.deliveryMode, 'async_job')
  return request
}

test('server AI is disabled by default and request ids are readable', () => {
  assert.equal(isServerAiEnabled(), false)
  assert.match(createServerAiRequestId(), /^rankpeek-ai-\d+-[a-z0-9]+$/)
  assert.equal(SERVER_AI_STREAM_ENDPOINT, '/api/v1/ai/analysis/stream')
  assert.equal(SERVER_AI_JOBS_ENDPOINT, '/api/v1/ai/analysis/jobs')
  assert.equal(getServerAiAnalysisJobEndpoint('job/with space'), '/api/v1/ai/analysis/jobs/job%2Fwith%20space')
})

test('analysis types map to the expected delivery modes', () => {
  assert.equal(getServerAiDeliveryMode('pregame'), 'stream')
  assert.equal(getServerAiDeliveryMode('postgame'), 'stream')
  assert.equal(getServerAiDeliveryMode('compliment'), 'stream')
  assert.equal(getServerAiDeliveryMode('entertainment_index'), 'async_job')
  assert.equal(getServerAiDeliveryMode('report'), 'async_job')
  assert.equal(getServerAiDeliveryMode('coach_summary'), 'async_job')
  assert.equal(getServerAiDeliveryMode('account_overview'), 'async_job')
})

test('request builder chooses delivery mode and preserves client metadata', () => {
  const streamRequest = makeStreamRequest()
  assert.equal(streamRequest.analysisType, 'postgame')
  assert.equal(streamRequest.deliveryMode, 'stream')
  assert.equal(streamRequest.client.appName, 'RankPeek')
  assert.equal(streamRequest.client.appVersion, '1.0.0')
  assert.equal(streamRequest.client.platform, 'win32')
  assert.equal(streamRequest.snapshotSchemaVersion, 1)
  assert.equal(streamRequest.inputHash, 'input-hash-1')
  assert.deepEqual(streamRequest.snapshot, snapshot)

  const jobRequest = makeJobRequest()
  assert.equal(jobRequest.analysisType, 'account_overview')
  assert.equal(jobRequest.deliveryMode, 'async_job')
  assert.equal(jobRequest.client.appName, 'RankPeek')
})

test('disabled stream submission returns AI_SERVER_DISABLED without dispatching handlers', async () => {
  let eventCount = 0
  let finalCount = 0
  let errorCount = 0

  const result = await submitServerAiAnalysisStream(
    makeStreamRequest(),
    {
      onEvent: () => { eventCount += 1 },
      onFinal: () => { finalCount += 1 },
      onError: () => { errorCount += 1 }
    }
  )

  assert.deepEqual(result, {
    ok: false,
    error: {
      code: 'AI_SERVER_DISABLED',
      message: 'AI 服务尚未接入',
      retryable: false
    }
  })
  assert.equal(eventCount, 0)
  assert.equal(finalCount, 0)
  assert.equal(errorCount, 0)
})

test('already aborted stream submission returns STREAM_ABORTED before disabled handling', async () => {
  const controller = new AbortController()
  controller.abort()

  const result = await submitServerAiAnalysisStream(makeStreamRequest(), {}, {
    signal: controller.signal
  })

  assert.deepEqual(result, {
    ok: false,
    error: {
      code: 'STREAM_ABORTED',
      message: 'AI stream request was aborted',
      retryable: false
    }
  })
})

test('disabled async job methods return AI_SERVER_DISABLED', async () => {
  const accepted = await submitServerAiAnalysisJob(makeJobRequest())
  const job = await getServerAiAnalysisJob('job-1')

  assert.equal(accepted.ok, false)
  assert.equal(accepted.error.code, 'AI_SERVER_DISABLED')
  assert.equal(accepted.error.retryable, false)
  assert.equal(job.ok, false)
  assert.equal(job.error.code, 'AI_SERVER_DISABLED')
})

test('final server result maps to local ai_analysis_results payload', () => {
  const payload = toLocalAiAnalysisResultPayload({
    result: finalResult,
    accountPuuid: 'account-puuid',
    subjectKey: 'match:998877'
  })

  assert.deepEqual(payload, {
    accountPuuid: 'account-puuid',
    analysisType: 'postgame',
    subjectKey: 'match:998877',
    gameVersion: '15.8',
    modelName: 'rankpeek-server-model',
    promptVersion: 'server-prompt-v1',
    inputHash: 'input-hash-1',
    outputJson: JSON.stringify(finalResult)
  })
})

test('stream delta events are not exposed as local persistence payloads', () => {
  assert.equal('toLocalAiAnalysisDeltaPayload' in serverAiClient, false)
})
