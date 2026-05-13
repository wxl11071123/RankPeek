import test from 'node:test'
import assert from 'node:assert/strict'
import type { GamingAiInputSnapshot } from './gamingAiInputSnapshot.ts'
import {
  createGamingAiStreamRequest,
  flattenGamingAiSnapshotTags,
  RANKPEEK_SERVER_GAMING_STREAM_ENDPOINT,
  streamGamingAiAnalysis
} from './gamingAiServerStream.ts'

function createSnapshot(): GamingAiInputSnapshot {
  return {
    schemaVersion: 'gaming_ai_input_snapshot.v1',
    mode: 'teammate',
    generatedAt: '2026-05-13T00:00:00.000Z',
    phase: 'ChampSelect',
    queueId: 420,
    queueName: 'Ranked Solo',
    allyTeam: [
      {
        side: 'ally',
        puuid: 'ally-puuid',
        gameName: 'W',
        tagLine: '1234',
        displayName: 'W#1234',
        championId: 141,
        championKey: 'Kayn',
        rankText: '翡翠一 50 LP',
        recordStatus: 'NORMAL',
        tags: [
          { name: '高胜率', good: true },
          { name: '稳定输出', good: true }
        ],
        metrics: {
          sample: 20,
          wins: 11,
          losses: 9,
          winRate: 55,
          kda: 3.1,
          kills: 7,
          deaths: 3,
          assists: 8,
          averageGold: 10000,
          averageDamageDealtToChampions: 15230,
          damageRate: 152.3,
          goldRate: 21.5,
          groupRate: 12.5,
          friendsRate: 4.5,
          disputeRate: 1.5
        }
      }
    ],
    enemyTeam: [
      {
        side: 'enemy',
        gameName: 'Hidden',
        tagLine: 'CN1',
        displayName: 'Hidden#CN1',
        championId: 64,
        rankText: '未定级',
        recordStatus: 'PRIVATE',
        tags: [],
        metrics: {
          sample: 0,
          wins: 0,
          losses: 0,
          winRate: null,
          kda: null,
          kills: null,
          deaths: null,
          assists: null,
          averageGold: null,
          averageDamageDealtToChampions: null,
          damageRate: null,
          goldRate: null,
          groupRate: null,
          friendsRate: null,
          disputeRate: null
        }
      }
    ],
    selectedPlayers: [],
    dataQuality: {
      allyCount: 1,
      enemyCount: 1,
      selectedCount: 1,
      normalRecordCount: 1,
      hiddenRecordCount: 1,
      emptyRecordCount: 0,
      errorRecordCount: 0
    }
  }
}

test('flattens gaming AI snapshot player tags into compact team strings', () => {
  const flattened = flattenGamingAiSnapshotTags(createSnapshot())

  assert.equal(flattened.allyTeamTags.length, 1)
  assert.equal(flattened.enemyTeamTags.length, 1)
  assert.match(flattened.allyTeamTags[0] ?? '', /ally \| W#1234/)
  assert.match(flattened.allyTeamTags[0] ?? '', /champion=141/)
  assert.match(flattened.allyTeamTags[0] ?? '', /rank=翡翠一 50 LP/)
  assert.match(flattened.allyTeamTags[0] ?? '', /status=NORMAL/)
  assert.match(flattened.allyTeamTags[0] ?? '', /sample=20/)
  assert.match(flattened.allyTeamTags[0] ?? '', /winRate=55\.0%/)
  assert.match(flattened.allyTeamTags[0] ?? '', /kda=3\.1/)
  assert.match(flattened.allyTeamTags[0] ?? '', /damageRate=152\.3%/)
  assert.match(flattened.allyTeamTags[0] ?? '', /tags=高胜率, 稳定输出/)
  assert.match(flattened.enemyTeamTags[0] ?? '', /enemy \| Hidden#CN1/)
  assert.match(flattened.enemyTeamTags[0] ?? '', /status=PRIVATE/)
})

test('creates a stream request that carries mode, schema, snapshot, and flattened tags', () => {
  const snapshot = createSnapshot()
  const request = createGamingAiStreamRequest(snapshot)

  assert.equal(request.mode, 'teammate')
  assert.equal(request.snapshotSchemaVersion, 'gaming_ai_input_snapshot.v1')
  assert.equal(request.snapshot, snapshot)
  assert.deepEqual(request.allyTeamTags, flattenGamingAiSnapshotTags(snapshot).allyTeamTags)
  assert.deepEqual(request.enemyTeamTags, flattenGamingAiSnapshotTags(snapshot).enemyTeamTags)
})

test('streams gaming AI analysis deltas from an SSE response', async () => {
  const request = createGamingAiStreamRequest(createSnapshot())
  const events: string[] = []
  const deltas: string[] = []
  const originalFetch = globalThis.fetch

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    assert.equal(String(url), `http://127.0.0.1:18080${RANKPEEK_SERVER_GAMING_STREAM_ENDPOINT}`)
    assert.equal(init?.method, 'POST')
    assert.deepEqual(init?.headers, { 'Content-Type': 'application/json' })
    assert.equal(JSON.parse(String(init?.body)).snapshotSchemaVersion, 'gaming_ai_input_snapshot.v1')

    const encoder = new TextEncoder()
    return new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('event: start\ndata: {"title":"mock"}\n\n'))
        controller.enqueue(encoder.encode('event: delta\ndata: 第一段\n\n'))
        controller.enqueue(encoder.encode('event: delta\ndata: 第二段\n\n'))
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
      onEvent: event => events.push(event.type),
      onDelta: text => deltas.push(text)
    })

    assert.deepEqual(result, { ok: true })
    assert.deepEqual(events, ['start', 'delta', 'delta', 'done'])
    assert.deepEqual(deltas, ['第一段', '第二段'])
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
    assert.equal(result.ok ? '' : result.message, 'rankpeek-server 暂不可用')
  } finally {
    globalThis.fetch = originalFetch
  }
})
