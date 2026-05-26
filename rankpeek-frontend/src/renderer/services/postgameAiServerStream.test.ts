import test from 'node:test'
import assert from 'node:assert/strict'
import type { PostgameAiInputSnapshot } from './postgameAiInputSnapshot.ts'
import {
  createPostgameAiStreamRequest,
  estimatePostgameAiTokenCostCny,
  RANKPEEK_SERVER_POSTGAME_STREAM_ENDPOINT,
  streamPostgameAiAnalysis
} from './postgameAiServerStream.ts'
import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'

function createSnapshot(): PostgameAiInputSnapshot {
  return {
    schemaVersion: 'postgame_ai_input_snapshot.v3',
    analysisType: 'postgame',
    builtAt: '2026-05-13T00:00:00.000Z',
    inputHash: 'hash-1',
    analysisBrief: {
      schemaVersion: 'postgame_analysis_brief.v1',
      language: 'zh-CN',
      matchFacts: ['本局为排位赛。'],
      teamFacts: ['我方数据可用。'],
      playerFacts: ['【你｜我方打野｜凯隐】7/5/11。'],
      timelineFacts: ['缺少 timeline，不能分析具体时间点、死亡前视野或资源交换。'],
      dataQualityFacts: ['缺少 timeline。']
    }
  }
}

test('creates a postgame stream request that wraps mode, schema, and a mode-neutral snapshot', () => {
  const snapshot = createSnapshot()
  const request = createPostgameAiStreamRequest(snapshot, 'review')

  assert.equal(request.mode, 'review')
  assert.equal(request.snapshotSchemaVersion, 'postgame_ai_input_snapshot.v3')
  assert.equal(request.snapshot, snapshot)
  assert.equal('mode' in request.snapshot, false)
  assert.equal('players' in request.snapshot, false)
  assert.equal('timeline' in request.snapshot, false)
})

test('review and praise stream requests reuse the same postgame snapshot', () => {
  const snapshot = createSnapshot()
  const review = createPostgameAiStreamRequest(snapshot, 'review')
  const praise = createPostgameAiStreamRequest(snapshot, 'praise')

  assert.equal(review.mode, 'review')
  assert.equal(praise.mode, 'praise')
  assert.equal(review.snapshot, snapshot)
  assert.equal(praise.snapshot, snapshot)
  assert.deepEqual(review.snapshot, praise.snapshot)
})

test('streams postgame AI analysis events from an SSE response', async () => {
  const request = createPostgameAiStreamRequest(createSnapshot(), 'review')
  const events: string[] = []
  const deltas: string[] = []
  const sections: string[] = []
  const usages: unknown[] = []
  const originalFetch = globalThis.fetch

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    assert.equal(String(url), `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_POSTGAME_STREAM_ENDPOINT}`)
    assert.equal(init?.method, 'POST')
    assert.deepEqual(init?.headers, { 'Content-Type': 'application/json' })
    assert.equal(JSON.parse(String(init?.body)).snapshotSchemaVersion, 'postgame_ai_input_snapshot.v3')

    const encoder = new TextEncoder()
    return new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('event: start\ndata: {"title":"mock"}\n\n'))
        controller.enqueue(encoder.encode('event: section\ndata: {"title":"Data"}\n\n'))
        controller.enqueue(encoder.encode('event: delta\ndata: {"text":"accepted"}\n\n'))
        controller.enqueue(encoder.encode('event: usage\ndata: {"provider":"deepseek","model":"deepseek-v4-flash","promptTokens":2100,"completionTokens":140,"totalTokens":2240,"promptCacheHitTokens":0,"promptCacheMissTokens":2100}\n\n'))
        controller.enqueue(encoder.encode('event: done\ndata: done\n\n'))
        controller.close()
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' }
    })
  }) as typeof fetch

  try {
    const result = await streamPostgameAiAnalysis(request, {
      onEvent: event => events.push(event.type),
      onSection: title => sections.push(title),
      onDelta: text => deltas.push(text),
      onUsage: usage => usages.push(usage)
    })

    assert.deepEqual(result, { ok: true })
    assert.deepEqual(events, ['start', 'section', 'delta', 'usage', 'done'])
    assert.deepEqual(sections, ['Data'])
    assert.deepEqual(deltas, ['accepted'])
    assert.deepEqual(usages, [{
      provider: 'deepseek',
      model: 'deepseek-v4-flash',
      promptTokens: 2100,
      completionTokens: 140,
      totalTokens: 2240,
      promptCacheHitTokens: 0,
      promptCacheMissTokens: 2100,
      cost: {
        currency: 'CNY',
        inputCacheHitCny: 0,
        inputCacheMissCny: 0.0021,
        outputCny: 0.00028,
        totalCny: 0.00238,
        pricing: {
          inputCacheHitCnyPerMillionTokens: 0.02,
          inputCacheMissCnyPerMillionTokens: 1,
          outputCnyPerMillionTokens: 2
        }
      }
    }])
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('estimates mainland DeepSeek token cost with cache hit, cache miss, and output tokens', () => {
  const cost = estimatePostgameAiTokenCostCny({
    provider: 'deepseek',
    model: 'deepseek-v4-flash',
    promptTokens: 3000,
    completionTokens: 400,
    totalTokens: 3400,
    promptCacheHitTokens: 1000,
    promptCacheMissTokens: 2000
  })

  assert.deepEqual(cost, {
    currency: 'CNY',
    inputCacheHitCny: 0.00002,
    inputCacheMissCny: 0.002,
    outputCny: 0.0008,
    totalCny: 0.00282,
    pricing: {
      inputCacheHitCnyPerMillionTokens: 0.02,
      inputCacheMissCnyPerMillionTokens: 1,
      outputCnyPerMillionTokens: 2
    }
  })
})

test('streams postgame AI analysis events from an NDJSON response including errors', async () => {
  const request = createPostgameAiStreamRequest(createSnapshot(), 'praise')
  const events: string[] = []
  const errors: string[] = []
  const originalFetch = globalThis.fetch

  globalThis.fetch = (async () => {
    const encoder = new TextEncoder()
    return new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('{"type":"start","title":"mock"}\n'))
        controller.enqueue(encoder.encode('{"type":"section","title":"Quality"}\n'))
        controller.enqueue(encoder.encode('{"type":"delta","text":"quality accepted"}\n'))
        controller.enqueue(encoder.encode('{"type":"error","message":"mock warning"}\n'))
        controller.close()
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/x-ndjson' }
    })
  }) as typeof fetch

  try {
    const result = await streamPostgameAiAnalysis(request, {
      onEvent: event => events.push(event.type),
      onError: message => errors.push(message)
    })

    assert.deepEqual(result, { ok: true })
    assert.deepEqual(events, ['start', 'section', 'delta', 'error'])
    assert.deepEqual(errors, ['mock warning'])
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('returns a failed result instead of throwing when rankpeek-server is unavailable', async () => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async () => {
    throw new TypeError('fetch failed')
  }) as typeof fetch

  try {
    const result = await streamPostgameAiAnalysis(createPostgameAiStreamRequest(createSnapshot(), 'review'), {})

    assert.equal(result.ok, false)
    assert.match(result.ok ? '' : result.message, /rankpeek-server/)
  } finally {
    globalThis.fetch = originalFetch
  }
})
