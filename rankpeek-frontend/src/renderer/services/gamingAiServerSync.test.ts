import test from 'node:test'
import assert from 'node:assert/strict'
import type { GamingAiInputSnapshot, GamingAiTeamSnapshot } from './gamingAiInputSnapshot.ts'
import {
  createPregameMockRequestFromSnapshot,
  flattenGamingAiSnapshotTags,
  RANKPEEK_SERVER_PREGAME_MOCK_ENDPOINT,
  submitGamingAiInputSnapshotToServer
} from './gamingAiServerSync.ts'
import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'

function createSnapshot(): GamingAiInputSnapshot {
  const base = {
    generatedAt: '2026-05-12T00:00:00.000Z',
    phase: 'ChampSelect',
    queueId: 420,
    queueName: 'Ranked Solo',
    currentSummoner: {
      puuid: 'self-puuid',
      gameName: 'Self',
      tagLine: 'CN1'
    }
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
  assert.match(flattened.allyTeamTags[0] ?? '', /^当前snapshot时间：2026-05-12T00:00:00.000Z。模式：Ranked Solo。用户ID：Self#CN1。阵营：我方。/)
  assert.match(flattened.allyTeamTags[0] ?? '', /W#1234 当前位置：打野/)
  assert.doesNotMatch(flattened.allyTeamTags[0] ?? '', /position=|status=|sample=|champion=|段位|样本中使用/)
})

test('flattens opponent mode to enemy snapshot only', () => {
  const snapshot = { ...createSnapshot(), mode: 'opponent' as const }
  const flattened = flattenGamingAiSnapshotTags(snapshot)

  assert.equal(flattened.allyTeamTags.length, 0)
  assert.equal(flattened.enemyTeamTags.length, 1)
  assert.match(flattened.enemyTeamTags[0] ?? '', /^当前snapshot时间：2026-05-12T00:00:00.000Z。模式：Ranked Solo。用户ID：Self#CN1。阵营：敌方。/)
  assert.match(flattened.enemyTeamTags[0] ?? '', /Hidden#CN1 当前位置：上路/)
})

test('creates a pregame mock request that carries both flattened tags and compact snapshots', () => {
  const snapshot = createSnapshot()
  const request = createPregameMockRequestFromSnapshot(snapshot)

  assert.equal(request.queueId, 420)
  assert.equal(request.championId, undefined)
  assert.deepEqual(request.allyTeamTags, flattenGamingAiSnapshotTags(snapshot).allyTeamTags)
  assert.deepEqual(request.enemyTeamTags, flattenGamingAiSnapshotTags(snapshot).enemyTeamTags)
  assert.equal(request.snapshotSchemaVersion, 'gaming_ai_input_snapshot.v2')
  assert.equal(request.snapshot, snapshot)
  assert.equal(request.snapshot?.teammateSnapshot.side, 'ally')
  assert.equal(request.snapshot?.opponentSnapshot.side, 'enemy')
})

test('submits the snapshot to the configured rankpeek-server mock endpoint', async () => {
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
    assert.equal(calls[0]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_PREGAME_MOCK_ENDPOINT}`)
    assert.equal(calls[0]?.init.method, 'POST')
    assert.deepEqual(calls[0]?.init.headers, { 'Content-Type': 'application/json' })
    assert.equal(JSON.parse(String(calls[0]?.init.body)).snapshotSchemaVersion, 'gaming_ai_input_snapshot.v2')
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
  } finally {
    globalThis.fetch = originalFetch
  }
})
