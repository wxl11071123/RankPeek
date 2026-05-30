import test from 'node:test'
import assert from 'node:assert/strict'
import {
  generateCoachSummaryReport,
  RANKPEEK_SERVER_COACH_SUMMARY_ENDPOINT
} from './coachSummaryAiClient.ts'
import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'
import { storeRankPeekAuthSession } from './rankpeekAuthClient.ts'

class MemoryStorage {
  private values = new Map<string, string>()

  getItem(key: string) {
    return this.values.get(key) ?? null
  }

  setItem(key: string, value: string) {
    this.values.set(key, value)
  }

  removeItem(key: string) {
    this.values.delete(key)
  }
}

const report = {
  schemaVersion: 'coach_summary_report.v1',
  analysisType: 'coach_summary',
  inputHash: 'coach-hash-1',
  title: '资源团前先站稳',
  summary: '最近20局显示资源团前死亡偏多。',
  verdict: {
    label: '中期资源处理需要收紧',
    score: 72,
    confidence: 'medium',
    summary: '你有稳定的英雄池和可用的节奏点，但资源刷新前的死亡会把优势送回去。'
  },
  keyFindings: [],
  trainingPlan: [],
  championAdvice: [],
  chartBlocks: [],
  warnings: [],
  metadata: {
    modelName: 'deepseek-v4-flash',
    promptVersion: 'coach_summary.prompt.v2',
    generatedAt: '2026-05-25T00:00:00.000Z',
    snapshotSchemaVersion: 'coach_summary_input_snapshot.v2',
    dataQualityConfidence: 'medium'
  }
}

test('generateCoachSummaryReport posts prompt payload to rankpeek-server', async () => {
  const calls: Array<{ url: string; init: RequestInit }> = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), init: init ?? {} })
    return new Response(JSON.stringify({
      success: true,
      data: {
        report,
        usage: {
          provider: 'deepseek',
          model: 'deepseek-v4-flash',
          promptTokens: 100,
          completionTokens: 50,
          totalTokens: 150
        }
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const result = await generateCoachSummaryReport({
      accessToken: 'access-token',
      inputHash: 'coach-hash-1',
      snapshotSchemaVersion: 'coach_summary_input_snapshot.v2',
      dataQualityConfidence: 'medium',
      promptPayload: {
        promptVersion: 'coach_summary.prompt.v2',
        systemPrompt: 'system prompt',
        userPrompt: '{"currentSnapshotText":"最近20局走势"}'
      }
    })

    assert.equal(result.ok, true)
    assert.equal(result.ok && result.report.title, '资源团前先站稳')
    assert.equal(result.ok && result.usage?.totalTokens, 150)
    assert.equal(calls.length, 1)
    assert.equal(calls[0]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_COACH_SUMMARY_ENDPOINT}`)
    assert.equal(calls[0]?.init.method, 'POST')
    assert.deepEqual(calls[0]?.init.headers, {
      'Content-Type': 'application/json',
      Authorization: 'Bearer access-token',
      'X-RankPeek-Idempotency-Key': 'coach-summary:coach-hash-1'
    })
    const body = JSON.parse(String(calls[0]?.init.body))
    assert.equal(body.inputHash, 'coach-hash-1')
    assert.equal(body.snapshotSchemaVersion, 'coach_summary_input_snapshot.v2')
    assert.equal(body.promptVersion, 'coach_summary.prompt.v2')
    assert.equal(body.dataQualityConfidence, 'medium')
    assert.equal(body.systemPrompt, 'system prompt')
    assert.match(body.userPrompt, /最近20局走势/)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('generateCoachSummaryReport returns a failed result for server errors', async () => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async () => new Response(JSON.stringify({
    success: false,
    error: {
      code: 'AI_SERVER_DISABLED',
      message: 'DeepSeek AI is not enabled'
    }
  }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })) as typeof fetch

  try {
    const result = await generateCoachSummaryReport({
      accessToken: 'access-token',
      inputHash: 'coach-hash-1',
      snapshotSchemaVersion: 'coach_summary_input_snapshot.v2',
      dataQualityConfidence: 'medium',
      promptPayload: {
        promptVersion: 'coach_summary.prompt.v2',
        systemPrompt: 'system prompt',
        userPrompt: '{}'
      }
    })

    assert.equal(result.ok, false)
    assert.equal(result.ok ? '' : result.message, '请求无法完成，请稍后再试。')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('generateCoachSummaryReport fails before fetch when no auth token is available', async () => {
  const originalFetch = globalThis.fetch
  let called = false
  globalThis.fetch = (async () => {
    called = true
    return new Response('{}')
  }) as typeof fetch

  try {
    const result = await generateCoachSummaryReport({
      inputHash: 'coach-hash-1',
      snapshotSchemaVersion: 'coach_summary_input_snapshot.v2',
      dataQualityConfidence: 'medium',
      promptPayload: {
        promptVersion: 'coach_summary.prompt.v2',
        systemPrompt: 'system prompt',
        userPrompt: '{}'
      }
    })

    assert.equal(result.ok, false)
    assert.equal(result.ok ? '' : result.message, '请先登录 RankPeek 账号后再使用 AI 分析。')
    assert.equal(called, false)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('generateCoachSummaryReport maps credit errors to user-facing text instead of HTTP codes', async () => {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })
  storeRankPeekAuthSession({
    user: {
      id: 1,
      email: 'admin@rankpeek.local',
      displayName: 'RankPeek Admin',
      role: 'ADMIN',
      status: 'ACTIVE'
    },
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    expiresInSeconds: 3600
  })

  const originalFetch = globalThis.fetch
  globalThis.fetch = (async () => new Response(JSON.stringify({
    success: false,
    data: null,
    error: {
      code: 'INSUFFICIENT_CREDITS',
      message: 'Credit balance is insufficient'
    }
  }), {
    status: 402,
    headers: { 'Content-Type': 'application/json' }
  })) as typeof fetch

  try {
    const result = await generateCoachSummaryReport({
      inputHash: 'coach-hash-1',
      snapshotSchemaVersion: 'coach_summary_input_snapshot.v2',
      dataQualityConfidence: 'medium',
      promptPayload: {
        promptVersion: 'coach_summary.prompt.v2',
        systemPrompt: 'system prompt',
        userPrompt: '{}'
      }
    })

    assert.equal(result.ok, false)
    assert.equal(result.ok ? '' : result.message, 'AI 分析次数不足，请充值后再试。')
  } finally {
    globalThis.fetch = originalFetch
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
})

test('generateCoachSummaryReport refreshes an expired stored access token and retries once', async () => {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })
  storeRankPeekAuthSession({
    user: {
      id: 1,
      email: 'admin@rankpeek.local',
      displayName: 'RankPeek Admin',
      role: 'ADMIN',
      status: 'ACTIVE'
    },
    accessToken: 'expired-access-token',
    refreshToken: 'refresh-token',
    expiresInSeconds: 3600
  })

  const calls: Array<{ url: string; init: RequestInit }> = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), init: init ?? {} })
    if (calls.length === 1) {
      return new Response(JSON.stringify({
        success: false,
        data: null,
        error: {
          code: 'ACCESS_TOKEN_INVALID',
          message: 'Invalid or expired access token'
        }
      }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      })
    }
    if (calls.length === 2) {
      return new Response(JSON.stringify({
        success: true,
        data: {
          accessToken: 'rotated-access-token',
          refreshToken: 'rotated-refresh-token',
          expiresInSeconds: 3600
        },
        error: null
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    }
    return new Response(JSON.stringify({
      success: true,
      data: { report },
      error: null
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const result = await generateCoachSummaryReport({
      inputHash: 'coach-hash-1',
      snapshotSchemaVersion: 'coach_summary_input_snapshot.v2',
      dataQualityConfidence: 'medium',
      promptPayload: {
        promptVersion: 'coach_summary.prompt.v2',
        systemPrompt: 'system prompt',
        userPrompt: '{}'
      }
    })

    assert.equal(result.ok, true)
    assert.equal(calls.length, 3)
    assert.equal(calls[0]?.init.headers?.['Authorization' as keyof HeadersInit], 'Bearer expired-access-token')
    assert.match(calls[1]?.url ?? '', /\/api\/auth\/refresh$/)
    assert.equal(calls[2]?.init.headers?.['Authorization' as keyof HeadersInit], 'Bearer rotated-access-token')
  } finally {
    globalThis.fetch = originalFetch
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
})
