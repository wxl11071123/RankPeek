import test from 'node:test'
import assert from 'node:assert/strict'
import type { GamingAiInputSnapshot } from './gamingAiInputSnapshot.ts'
import {
  createPregameMockRequestFromSnapshot,
  flattenGamingAiSnapshotTags,
  RANKPEEK_SERVER_PREGAME_MOCK_ENDPOINT,
  submitGamingAiInputSnapshotToServer
} from './gamingAiServerSync.ts'

function createSnapshot(): GamingAiInputSnapshot {
  return {
    schemaVersion: 'gaming_ai_input_snapshot.v1',
    mode: 'teammate',
    generatedAt: '2026-05-12T00:00:00.000Z',
    phase: 'ChampSelect',
    queueId: 420,
    queueName: 'Ranked Solo',
    currentSummoner: {
      puuid: 'self-puuid',
      gameName: 'Self',
      tagLine: 'CN1'
    },
    allyTeam: [
      {
        side: 'ally',
        isSelf: true,
        puuid: 'ally-puuid',
        gameName: 'W',
        tagLine: '1234',
        displayName: 'W#1234',
        championId: 141,
        championKey: 'Kayn',
        rankText: 'Emerald I 50 LP',
        rank: {
          solo: {
            tier: 'EMERALD',
            division: 'I',
            leaguePoints: 50,
            wins: 40,
            losses: 35,
            totalGames: 75
          },
          flex: null
        },
        recordStatus: 'NORMAL',
        tags: [
          { name: 'high win rate', good: true, desc: 'long description that should not be required' },
          { name: 'stable output', good: true }
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
        isSelf: false,
        gameName: 'Hidden',
        displayName: 'Hidden#CN1',
        championId: 64,
        rankText: 'Unranked',
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
  assert.match(flattened.allyTeamTags[0] ?? '', /self=true/)
  assert.match(flattened.allyTeamTags[0] ?? '', /champion=141/)
  assert.match(flattened.allyTeamTags[0] ?? '', /rank=Emerald I 50 LP/)
  assert.match(flattened.allyTeamTags[0] ?? '', /status=NORMAL/)
  assert.match(flattened.allyTeamTags[0] ?? '', /sample=20/)
  assert.match(flattened.allyTeamTags[0] ?? '', /winRate=55\.0%/)
  assert.match(flattened.allyTeamTags[0] ?? '', /kda=3\.1/)
  assert.match(flattened.allyTeamTags[0] ?? '', /damageRate=152\.3%/)
  assert.match(flattened.allyTeamTags[0] ?? '', /tags=high win rate, stable output/)
  assert.match(flattened.enemyTeamTags[0] ?? '', /status=PRIVATE/)
})

test('creates a pregame mock request that carries both flattened tags and structured snapshot', () => {
  const snapshot = createSnapshot()
  const request = createPregameMockRequestFromSnapshot(snapshot)

  assert.equal(request.queueId, 420)
  assert.equal(request.championId, 141)
  assert.deepEqual(request.allyTeamTags, flattenGamingAiSnapshotTags(snapshot).allyTeamTags)
  assert.deepEqual(request.enemyTeamTags, flattenGamingAiSnapshotTags(snapshot).enemyTeamTags)
  assert.equal(request.snapshotSchemaVersion, 'gaming_ai_input_snapshot.v1')
  assert.equal(request.snapshot, snapshot)
})

test('submits the snapshot to the local rankpeek-server mock endpoint', async () => {
  const snapshot = createSnapshot()
  const calls: Array<{ url: string; init: RequestInit }> = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), init: init ?? {} })
    return new Response(JSON.stringify({
      success: true,
      data: {
        summary: 'mock analysis accepted'
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const result = await submitGamingAiInputSnapshotToServer(snapshot)

    assert.deepEqual(result, { ok: true, summary: 'mock analysis accepted' })
    assert.equal(calls.length, 1)
    assert.equal(calls[0]?.url, `http://127.0.0.1:18080${RANKPEEK_SERVER_PREGAME_MOCK_ENDPOINT}`)
    assert.equal(calls[0]?.init.method, 'POST')
    assert.deepEqual(calls[0]?.init.headers, { 'Content-Type': 'application/json' })
    assert.equal(JSON.parse(String(calls[0]?.init.body)).snapshotSchemaVersion, 'gaming_ai_input_snapshot.v1')
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
    const result = await submitGamingAiInputSnapshotToServer(createSnapshot())

    assert.equal(result.ok, false)
    assert.match(result.ok ? '' : result.message, /rankpeek-server/)
  } finally {
    globalThis.fetch = originalFetch
  }
})
