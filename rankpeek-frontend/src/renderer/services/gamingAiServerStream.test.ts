import test from 'node:test'
import assert from 'node:assert/strict'
import type { GamingAiInputSnapshot, GamingAiTeamSnapshot } from './gamingAiInputSnapshot.ts'
import {
  createGamingAiStreamRequest,
  flattenGamingAiSnapshotTags,
  RANKPEEK_SERVER_GAMING_STREAM_ENDPOINT,
  streamGamingAiAnalysis
} from './gamingAiServerStream.ts'
import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'
import {
  clearStoredRankPeekAuthSession,
  getStoredRankPeekAuthSession,
  storeRankPeekAuthSession
} from './rankpeekAuthClient.ts'

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

function createSnapshot(): GamingAiInputSnapshot {
  const base = {
    generatedAt: '2026-05-13T00:00:00.000Z',
    phase: 'ChampSelect',
    queueId: 420,
    queueName: 'Ranked Solo'
  } as const
  const allySummaryLine = 'W#1234 当前位置：打野，tag：高胜率、稳定C、高伤，场均击杀/死亡/助攻：7.0/3.0/8.0，平均KDA：3.1，胜率：55.0%，伤转：152.3%，样本数：20，参团率：12.5%。'
  const enemySummaryLine = 'Hidden#CN1 当前位置：上路，战绩状态：战绩隐藏，tag：无，场均击杀/死亡/助攻：未知/未知/未知，平均KDA：未知，胜率：未知，伤转：未知，样本数：0，参团率：未知。'
  const teammateSnapshot: GamingAiTeamSnapshot = {
    schemaVersion: 'gaming_ai_team_snapshot.v1',
    side: 'ally',
    text: `当前snapshot时间：${base.generatedAt}。模式：${base.queueName}。用户ID：Self#CN1。阵营：我方。\n\n${allySummaryLine}`,
    players: [
      {
        key: 'puuid:ally-puuid',
        isSelf: true,
        summaryLine: allySummaryLine
      }
    ]
  }
  const opponentSnapshot: GamingAiTeamSnapshot = {
    schemaVersion: 'gaming_ai_team_snapshot.v1',
    side: 'enemy',
    text: `当前snapshot时间：${base.generatedAt}。模式：${base.queueName}。用户ID：Self#CN1。阵营：敌方。\n\n${enemySummaryLine}`,
    players: [
      {
        key: 'name:Hidden#CN1',
        isSelf: false,
        summaryLine: enemySummaryLine
      }
    ]
  }

  return {
    schemaVersion: 'gaming_ai_input_snapshot.v2',
    mode: 'teammate',
    ...base,
    teammateSnapshot,
    opponentSnapshot
  }
}

function installStoredGamingAuthSession(accessToken = 'stream-access-token'): () => void {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })
  storeRankPeekAuthSession({
    user: {
      id: 1,
      email: 'player@rankpeek.local',
      displayName: 'Player',
      role: 'USER',
      status: 'ACTIVE'
    },
    accessToken,
    refreshToken: 'stream-refresh-token',
    expiresInSeconds: 3600
  })

  return () => {
    clearStoredRankPeekAuthSession()
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
}

test('flattens gaming AI snapshot player tags into one-line natural language strings', () => {
  const flattened = flattenGamingAiSnapshotTags(createSnapshot())

  assert.equal(flattened.allyTeamTags.length, 1)
  assert.equal(flattened.enemyTeamTags.length, 0)
  assert.match(flattened.allyTeamTags[0] ?? '', /^当前snapshot时间：2026-05-13T00:00:00.000Z。模式：Ranked Solo。用户ID：Self#CN1。阵营：我方。/)
  assert.match(flattened.allyTeamTags[0] ?? '', /W#1234 当前位置：打野/)
  assert.doesNotMatch(flattened.allyTeamTags[0] ?? '', /position=|status=|sample=|champion=|段位|样本中使用/)
})

test('flattens opponent mode to enemy snapshot only', () => {
  const snapshot = { ...createSnapshot(), mode: 'opponent' as const }
  const flattened = flattenGamingAiSnapshotTags(snapshot)

  assert.equal(flattened.allyTeamTags.length, 0)
  assert.equal(flattened.enemyTeamTags.length, 1)
  assert.match(flattened.enemyTeamTags[0] ?? '', /^当前snapshot时间：2026-05-13T00:00:00.000Z。模式：Ranked Solo。用户ID：Self#CN1。阵营：敌方。/)
  assert.match(flattened.enemyTeamTags[0] ?? '', /Hidden#CN1 当前位置：上路/)
})

test('creates a stream request that carries mode, schema, two compact snapshots, and flattened tags', () => {
  const snapshot = createSnapshot()
  const request = createGamingAiStreamRequest(snapshot)

  assert.equal(request.mode, 'teammate')
  assert.equal(request.snapshotSchemaVersion, 'gaming_ai_input_snapshot.v2')
  assert.equal(request.snapshot, snapshot)
  assert.equal(request.snapshot.teammateSnapshot.side, 'ally')
  assert.equal(request.snapshot.opponentSnapshot.side, 'enemy')
  assert.deepEqual(request.allyTeamTags, flattenGamingAiSnapshotTags(snapshot).allyTeamTags)
  assert.deepEqual(request.enemyTeamTags, flattenGamingAiSnapshotTags(snapshot).enemyTeamTags)
})

test('streams gaming AI analysis delta and player verdict events from an SSE response', async () => {
  const restoreAuthSession = installStoredGamingAuthSession()
  const request = createGamingAiStreamRequest(createSnapshot())
  const events: string[] = []
  const deltas: string[] = []
  const verdicts: Array<{ playerKey: string; label: string; tone?: string; reason?: string }> = []
  const originalFetch = globalThis.fetch

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    assert.equal(String(url), `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_GAMING_STREAM_ENDPOINT}`)
    assert.equal(init?.method, 'POST')
    assert.deepEqual(init?.headers, {
      'Content-Type': 'application/json',
      Authorization: 'Bearer stream-access-token'
    })
    assert.equal(JSON.parse(String(init?.body)).snapshotSchemaVersion, 'gaming_ai_input_snapshot.v2')

    const encoder = new TextEncoder()
    return new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('event: start\ndata: {"title":"mock"}\n\n'))
        controller.enqueue(encoder.encode('event: delta\ndata: first\n\n'))
        controller.enqueue(encoder.encode('event: player_verdict\ndata: {"playerKey":"puuid:ally-puuid","label":"stable teammate","tone":"stable","reason":"from server stream"}\n\n'))
        controller.enqueue(encoder.encode('event: delta\ndata: second\n\n'))
        controller.enqueue(encoder.encode('event: done\ndata: done\n\n'))
        controller.close()
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' }
    })
  }) as typeof fetch

  try {
    const result = await streamGamingAiAnalysis(request, {
      onEvent: event => {
        events.push(event.type)
        if (event.type === 'player_verdict') {
          verdicts.push(event)
        }
      },
      onDelta: text => deltas.push(text)
    })

    assert.deepEqual(result, { ok: true })
    assert.deepEqual(events, ['start', 'delta', 'player_verdict', 'delta', 'done'])
    assert.deepEqual(deltas, ['first', 'second'])
    assert.deepEqual(verdicts, [{
      playerKey: 'puuid:ally-puuid',
      label: 'stable teammate',
      tone: 'stable',
      reason: 'from server stream'
    }])
  } finally {
    globalThis.fetch = originalFetch
    restoreAuthSession()
  }
})

test('streams gaming AI analysis with the stored RankPeek auth token', async () => {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })
  storeRankPeekAuthSession({
    user: {
      id: 1,
      email: 'player@rankpeek.local',
      displayName: 'Player',
      role: 'USER',
      status: 'ACTIVE'
    },
    accessToken: 'stream-access-token',
    refreshToken: 'stream-refresh-token',
    expiresInSeconds: 3600
  })

  const request = createGamingAiStreamRequest(createSnapshot())
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async (_url: string | URL | Request, init?: RequestInit) => {
    assert.deepEqual(init?.headers, {
      'Content-Type': 'application/json',
      Authorization: 'Bearer stream-access-token'
    })

    const encoder = new TextEncoder()
    return new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('event: done\ndata: done\n\n'))
        controller.close()
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' }
    })
  }) as typeof fetch

  try {
    const result = await streamGamingAiAnalysis(request, {})

    assert.deepEqual(result, { ok: true })
  } finally {
    globalThis.fetch = originalFetch
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
})

test('refreshes the stored RankPeek auth token and retries gaming AI stream once after 401', async () => {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })
  storeRankPeekAuthSession({
    user: {
      id: 1,
      email: 'player@rankpeek.local',
      displayName: 'Player',
      role: 'USER',
      status: 'ACTIVE'
    },
    accessToken: 'expired-stream-access-token',
    refreshToken: 'stream-refresh-token',
    expiresInSeconds: 3600
  })

  const request = createGamingAiStreamRequest(createSnapshot())
  const originalFetch = globalThis.fetch
  const calls: Array<{ url: string; init?: RequestInit }> = []
  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), init })

    if (calls.length === 1) {
      return new Response(JSON.stringify({
        success: false,
        error: { code: 'ACCESS_TOKEN_INVALID' }
      }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      })
    }

    if (String(url).endsWith('/api/auth/refresh')) {
      return new Response(JSON.stringify({
        success: true,
        data: {
          accessToken: 'rotated-stream-access-token',
          refreshToken: 'rotated-stream-refresh-token',
          expiresInSeconds: 3600
        },
        error: null
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    }

    const encoder = new TextEncoder()
    return new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('event: done\ndata: done\n\n'))
        controller.close()
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' }
    })
  }) as typeof fetch

  try {
    const result = await streamGamingAiAnalysis(request, {})

    assert.deepEqual(result, { ok: true })
    assert.equal(calls[0]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_GAMING_STREAM_ENDPOINT}`)
    assert.equal(calls[0]?.init?.headers?.['Authorization' as keyof HeadersInit], 'Bearer expired-stream-access-token')
    assert.match(calls[1]?.url || '', /\/api\/auth\/refresh$/)
    assert.equal(calls[2]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_GAMING_STREAM_ENDPOINT}`)
    assert.equal(calls[2]?.init?.headers?.['Authorization' as keyof HeadersInit], 'Bearer rotated-stream-access-token')
    assert.equal(getStoredRankPeekAuthSession()?.accessToken, 'rotated-stream-access-token')
  } finally {
    globalThis.fetch = originalFetch
    clearStoredRankPeekAuthSession()
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
})

test('returns a login prompt without calling rankpeek-server when no gaming auth session is stored', async () => {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })

  const originalFetch = globalThis.fetch
  let fetchCalled = false
  const errors: string[] = []
  globalThis.fetch = (async () => {
    fetchCalled = true
    throw new Error('should not fetch without auth')
  }) as typeof fetch

  try {
    const result = await streamGamingAiAnalysis(createGamingAiStreamRequest(createSnapshot()), {
      onError: message => errors.push(message)
    })

    assert.equal(result.ok, false)
    assert.equal(result.ok ? '' : result.message, '请先登录 RankPeek 账号后再使用 AI 分析。')
    assert.deepEqual(errors, ['请先登录 RankPeek 账号后再使用 AI 分析。'])
    assert.equal(fetchCalled, false)
  } finally {
    globalThis.fetch = originalFetch
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
})

test('returns a friendly expired-login message when gaming auth refresh fails', async () => {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })
  storeRankPeekAuthSession({
    user: {
      id: 1,
      email: 'player@rankpeek.local',
      displayName: 'Player',
      role: 'USER',
      status: 'ACTIVE'
    },
    accessToken: 'expired-stream-access-token',
    refreshToken: 'invalid-stream-refresh-token',
    expiresInSeconds: 3600
  })

  const originalFetch = globalThis.fetch
  const errors: string[] = []
  globalThis.fetch = (async (url: string | URL | Request) => {
    if (String(url).endsWith('/api/auth/refresh')) {
      return new Response(JSON.stringify({
        success: false,
        error: {
          code: 'REFRESH_TOKEN_INVALID',
          message: 'Invalid or expired refresh token'
        }
      }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      })
    }

    return new Response(JSON.stringify({
      success: false,
      error: { code: 'ACCESS_TOKEN_INVALID' }
    }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const result = await streamGamingAiAnalysis(createGamingAiStreamRequest(createSnapshot()), {
      onError: message => errors.push(message)
    })

    assert.equal(result.ok, false)
    assert.equal(result.ok ? '' : result.message, '登录状态已失效，请重新登录后再试。')
    assert.deepEqual(errors, ['登录状态已失效，请重新登录后再试。'])
    assert.equal(getStoredRankPeekAuthSession(), null)
  } finally {
    globalThis.fetch = originalFetch
    clearStoredRankPeekAuthSession()
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
})

test('maps gaming credit errors to user-facing text instead of HTTP codes', async () => {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })
  storeRankPeekAuthSession({
    user: {
      id: 1,
      email: 'player@rankpeek.local',
      displayName: 'Player',
      role: 'USER',
      status: 'ACTIVE'
    },
    accessToken: 'stream-access-token',
    refreshToken: 'stream-refresh-token',
    expiresInSeconds: 3600
  })

  const originalFetch = globalThis.fetch
  const errors: string[] = []
  globalThis.fetch = (async () => new Response(JSON.stringify({
    success: false,
    error: {
      code: 'INSUFFICIENT_CREDITS',
      message: 'Credit balance is insufficient'
    }
  }), {
    status: 402,
    headers: { 'Content-Type': 'application/json' }
  })) as typeof fetch

  try {
    const result = await streamGamingAiAnalysis(createGamingAiStreamRequest(createSnapshot()), {
      onError: message => errors.push(message)
    })

    assert.equal(result.ok, false)
    assert.equal(result.ok ? '' : result.message, 'AI 分析次数不足，请充值后再试。')
    assert.deepEqual(errors, ['AI 分析次数不足，请充值后再试。'])
  } finally {
    globalThis.fetch = originalFetch
    clearStoredRankPeekAuthSession()
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
})

test('streams structured player insight events from an SSE response', async () => {
  const restoreAuthSession = installStoredGamingAuthSession()
  const request = createGamingAiStreamRequest(createSnapshot())
  const events: string[] = []
  const insights: Array<{ playerKey: string; label: string; tone?: string; text: string }> = []
  const originalFetch = globalThis.fetch

  globalThis.fetch = (async () => {
    const encoder = new TextEncoder()
    return new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('event: start\ndata: {"title":"mock"}\n\n'))
        controller.enqueue(encoder.encode('event: player_insight\ndata: {"playerKey":"puuid:ally-puuid","label":"稳定队友","tone":"stable","text":"这名队友近期节奏稳定，可以围绕他打第一波资源。"}\n\n'))
        controller.enqueue(encoder.encode('event: done\ndata: done\n\n'))
        controller.close()
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' }
    })
  }) as typeof fetch

  try {
    const result = await streamGamingAiAnalysis(request, {
      onEvent: event => {
        events.push(event.type)
        if (event.type === 'player_insight') {
          insights.push(event)
        }
      }
    })

    assert.deepEqual(result, { ok: true })
    assert.deepEqual(events, ['start', 'player_insight', 'done'])
    assert.deepEqual(insights, [{
      playerKey: 'puuid:ally-puuid',
      label: '稳定队友',
      tone: 'stable',
      text: '这名队友近期节奏稳定，可以围绕他打第一波资源。'
    }])
  } finally {
    globalThis.fetch = originalFetch
    restoreAuthSession()
  }
})

test('streams gaming AI analysis player verdict events from an NDJSON response', async () => {
  const restoreAuthSession = installStoredGamingAuthSession()
  const request = createGamingAiStreamRequest(createSnapshot())
  const events: string[] = []
  const originalFetch = globalThis.fetch

  globalThis.fetch = (async () => {
    const encoder = new TextEncoder()
    return new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('{"type":"player_verdict","playerKey":"name:Hidden#CN1","label":"punishable","tone":"weak","reason":"from NDJSON"}\n'))
        controller.enqueue(encoder.encode('{"type":"done"}\n'))
        controller.close()
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/x-ndjson' }
    })
  }) as typeof fetch

  try {
    const result = await streamGamingAiAnalysis(request, {
      onEvent: event => {
        if (event.type === 'player_verdict') {
          events.push(`${event.playerKey}:${event.label}:${event.tone}:${event.reason}`)
          return
        }
        events.push(event.type)
      }
    })

    assert.deepEqual(result, { ok: true })
    assert.deepEqual(events, ['name:Hidden#CN1:punishable:weak:from NDJSON', 'done'])
  } finally {
    globalThis.fetch = originalFetch
    restoreAuthSession()
  }
})

test('streams structured player insight events from an NDJSON response', async () => {
  const restoreAuthSession = installStoredGamingAuthSession()
  const request = createGamingAiStreamRequest(createSnapshot())
  const events: string[] = []
  const originalFetch = globalThis.fetch

  globalThis.fetch = (async () => {
    const encoder = new TextEncoder()
    return new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('{"type":"player_insight","playerKey":"name:Hidden#CN1","label":"可突破","tone":"weak","text":"这个对手近期死亡偏多，前期可以试探他一波。"}\n'))
        controller.enqueue(encoder.encode('{"type":"done"}\n'))
        controller.close()
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/x-ndjson' }
    })
  }) as typeof fetch

  try {
    const result = await streamGamingAiAnalysis(request, {
      onEvent: event => {
        if (event.type === 'player_insight') {
          events.push(`${event.playerKey}:${event.label}:${event.tone}:${event.text}`)
          return
        }
        events.push(event.type)
      }
    })

    assert.deepEqual(result, { ok: true })
    assert.deepEqual(events, ['name:Hidden#CN1:可突破:weak:这个对手近期死亡偏多，前期可以试探他一波。', 'done'])
  } finally {
    globalThis.fetch = originalFetch
    restoreAuthSession()
  }
})

test('returns failed result instead of throwing when rankpeek-server is unavailable', async () => {
  const restoreAuthSession = installStoredGamingAuthSession()
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async () => {
    throw new TypeError('fetch failed')
  }) as typeof fetch

  try {
    const result = await streamGamingAiAnalysis(createGamingAiStreamRequest(createSnapshot()), {})

    assert.equal(result.ok, false)
    assert.equal(result.ok ? '' : result.message, 'AI 服务暂时不可用，请稍后再试。')
  } finally {
    globalThis.fetch = originalFetch
    restoreAuthSession()
  }
})
