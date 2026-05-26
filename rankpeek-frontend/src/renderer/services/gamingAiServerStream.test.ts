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
  const request = createGamingAiStreamRequest(createSnapshot())
  const events: string[] = []
  const deltas: string[] = []
  const verdicts: Array<{ playerKey: string; label: string; tone?: string; reason?: string }> = []
  const originalFetch = globalThis.fetch

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    assert.equal(String(url), `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_GAMING_STREAM_ENDPOINT}`)
    assert.equal(init?.method, 'POST')
    assert.deepEqual(init?.headers, { 'Content-Type': 'application/json' })
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
  }
})

test('streams structured player insight events from an SSE response', async () => {
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
  }
})

test('streams gaming AI analysis player verdict events from an NDJSON response', async () => {
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
  }
})

test('streams structured player insight events from an NDJSON response', async () => {
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
  }
})

test('returns failed result instead of throwing when rankpeek-server is unavailable', async () => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async () => {
    throw new TypeError('fetch failed')
  }) as typeof fetch

  try {
    const result = await streamGamingAiAnalysis(createGamingAiStreamRequest(createSnapshot()), {})

    assert.equal(result.ok, false)
  } finally {
    globalThis.fetch = originalFetch
  }
})
