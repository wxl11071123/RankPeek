import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createLocalDatabase } from './index.ts'
import type { LocalDatabase } from './types.ts'

const silentLogger = {
  info: () => undefined,
  warn: () => undefined,
  error: () => undefined
}

function createTempLocalDatabase(): { database: LocalDatabase; databasePath: string; cleanup: () => void } {
  const directory = mkdtempSync(join(tmpdir(), 'rankpeek-db-'))
  const databasePath = join(directory, 'rankpeek.db')
  const database = createLocalDatabase({ databasePath, logger: silentLogger })

  return {
    database,
    databasePath,
    cleanup: () => {
      database.close()
      rmSync(directory, { recursive: true, force: true })
    }
  }
}

test('initializes database file and records migration version 1 once', () => {
  const { database, databasePath, cleanup } = createTempLocalDatabase()

  try {
    const migration = database.connection
      .prepare('SELECT version, name FROM schema_migrations WHERE version = 1')
      .get() as { version: number; name: string } | undefined

    assert.deepEqual(migration, {
      version: 1,
      name: '1_init_user_database'
    })

    database.close()
    const reopened = createLocalDatabase({ databasePath, logger: silentLogger })
    const migrationCount = reopened.connection
      .prepare('SELECT COUNT(*) AS count FROM schema_migrations WHERE version = 1')
      .get() as { count: number }

    assert.equal(migrationCount.count, 1)
    reopened.close()
  } finally {
    cleanup()
  }
})

test('account repository upserts accounts and tracks a single last selected account', () => {
  const { database, cleanup } = createTempLocalDatabase()

  try {
    const first = database.accounts.upsertAccount({
      region: 'HN1',
      puuid: 'test-puuid',
      gameName: 'RankPeekTest',
      tagLine: '0001',
      displayName: 'RankPeekTest#0001'
    })

    assert.equal(first.region, 'HN1')
    assert.equal(first.puuid, 'test-puuid')
    assert.equal(first.displayName, 'RankPeekTest#0001')

    const updated = database.accounts.upsertAccount({
      region: 'HN1',
      puuid: 'test-puuid',
      summonerName: 'RankPeekLegacy',
      summonerLevel: 88
    })

    assert.equal(updated.id, first.id)
    assert.equal(updated.summonerName, 'RankPeekLegacy')
    assert.equal(updated.summonerLevel, 88)

    database.accounts.upsertAccount({
      region: 'HN1',
      puuid: 'second-puuid',
      displayName: 'Second#0002'
    })

    database.accounts.setLastSelectedAccount('HN1', 'test-puuid')
    let selected = database.accounts.getLastSelectedAccount()
    assert.equal(selected?.puuid, 'test-puuid')

    database.accounts.setLastSelectedAccount('HN1', 'second-puuid')
    selected = database.accounts.getLastSelectedAccount()
    assert.equal(selected?.puuid, 'second-puuid')

    const accounts = database.accounts.listAccounts()
    assert.equal(accounts.length, 2)
    assert.equal(accounts.filter((account) => account.lastSelected).length, 1)
  } finally {
    cleanup()
  }
})

test('match repository upserts records, supports filters, and caches detail payloads', () => {
  const { database, cleanup } = createTempLocalDatabase()

  try {
    database.matches.upsertMatchRecords([
      {
        region: 'HN1',
        matchId: 'HN1_1',
        accountPuuid: 'test-puuid',
        queueId: 420,
        queueName: 'Ranked Solo',
        gameMode: 'CLASSIC',
        gameVersion: '16.8.1',
        gameCreation: 200,
        gameDuration: 1800,
        championId: 103,
        win: true,
        kills: 10,
        deaths: 2,
        assists: 8,
        lane: 'MIDDLE',
        role: 'SOLO',
        rawSummaryJson: { matchId: 'HN1_1' }
      },
      {
        region: 'HN1',
        matchId: 'HN1_2',
        accountPuuid: 'test-puuid',
        queueId: 430,
        gameCreation: 100,
        championId: 99,
        rawSummaryJson: { matchId: 'HN1_2' }
      }
    ])

    const allRecords = database.matches.listMatchRecordsByAccount('test-puuid', { limit: 10 })
    assert.deepEqual(
      allRecords.map((record) => record.matchId),
      ['HN1_1', 'HN1_2']
    )

    const rankedRecords = database.matches.listMatchRecordsByAccount('test-puuid', { queueId: 420 })
    assert.equal(rankedRecords.length, 1)
    assert.equal(rankedRecords[0]?.championId, 103)

    const championRecords = database.matches.listMatchRecordsByAccount('test-puuid', { championId: 99 })
    assert.equal(championRecords.length, 1)
    assert.equal(championRecords[0]?.matchId, 'HN1_2')

    const detail = database.matches.upsertMatchDetail({
      region: 'HN1',
      matchId: 'HN1_1',
      rawDetailJson: { metadata: { matchId: 'HN1_1' } },
      normalizedDetailJson: { participants: [] },
      source: 'smoke-test'
    })

    assert.equal(detail.matchId, 'HN1_1')
    assert.match(detail.rawDetailJson, /HN1_1/)

    const cachedDetail = database.matches.getMatchDetail('HN1', 'HN1_1')
    assert.equal(cachedDetail?.source, 'smoke-test')
  } finally {
    cleanup()
  }
})

test('match repository keeps complete records when an incomplete upsert arrives and allows complete repair', () => {
  const { database, cleanup } = createTempLocalDatabase()

  try {
    const completeRecord = {
      region: 'HN1',
      matchId: 'HN1_quality',
      accountPuuid: 'test-puuid',
      queueId: 420,
      queueName: 'Ranked Solo',
      gameMode: 'CLASSIC',
      gameCreation: 300,
      gameDuration: 1800,
      championId: 103,
      win: true,
      kills: 10,
      deaths: 2,
      assists: 8,
      rawSummaryJson: { matchId: 'HN1_quality', quality: 'complete' }
    }

    database.matches.upsertMatchRecord(completeRecord)
    database.matches.upsertMatchRecord({
      ...completeRecord,
      championId: null,
      win: null,
      kills: null,
      deaths: null,
      assists: null,
      rawSummaryJson: { matchId: 'HN1_quality', quality: 'incomplete' }
    })

    const protectedRecords = database.matches.listMatchRecordsByAccount('test-puuid', { limit: 10 })
    const protectedRecord = protectedRecords.find((record) => record.matchId === 'HN1_quality')
    assert.equal(protectedRecord?.championId, 103)
    assert.equal(protectedRecord?.win, true)
    assert.equal(protectedRecord?.kills, 10)
    assert.equal(protectedRecord?.deaths, 2)
    assert.equal(protectedRecord?.assists, 8)
    assert.match(protectedRecord?.rawSummaryJson ?? '', /complete/)

    database.matches.upsertMatchRecord({
      region: 'HN1',
      matchId: 'HN1_repair',
      accountPuuid: 'test-puuid',
      queueId: 420,
      gameCreation: 200,
      championId: null,
      win: null,
      kills: null,
      deaths: null,
      assists: null,
      rawSummaryJson: { matchId: 'HN1_repair', quality: 'incomplete' }
    })
    database.matches.upsertMatchRecord({
      region: 'HN1',
      matchId: 'HN1_repair',
      accountPuuid: 'test-puuid',
      queueId: 420,
      gameCreation: 200,
      championId: 99,
      win: false,
      kills: 3,
      deaths: 4,
      assists: 5,
      rawSummaryJson: { matchId: 'HN1_repair', quality: 'complete' }
    })

    const repairedRecords = database.matches.listMatchRecordsByAccount('test-puuid', { limit: 10 })
    const repairedRecord = repairedRecords.find((record) => record.matchId === 'HN1_repair')
    assert.equal(repairedRecord?.championId, 99)
    assert.equal(repairedRecord?.win, false)
    assert.equal(repairedRecord?.kills, 3)
    assert.equal(repairedRecord?.deaths, 4)
    assert.equal(repairedRecord?.assists, 5)
    assert.match(repairedRecord?.rawSummaryJson ?? '', /complete/)
  } finally {
    cleanup()
  }
})

test('match repository round-trips loadout and performance summary columns', () => {
  const { database, cleanup } = createTempLocalDatabase()

  try {
    database.matches.upsertMatchRecord({
      region: 'HN1',
      matchId: 'HN1_enhanced',
      accountPuuid: 'test-puuid',
      queueId: 420,
      gameCreation: 400,
      gameDuration: 1800,
      championId: 103,
      win: true,
      kills: 10,
      deaths: 2,
      assists: 8,
      goldEarned: 12345,
      totalDamageDealtToChampions: 30123,
      doubleKills: 2,
      tripleKills: 1,
      quadraKills: 0,
      pentaKills: 0,
      largestKillingSpree: 9,
      legendaryCount: 1,
      spell1Id: 4,
      spell2Id: 14,
      perk0: 8010,
      playerAugment1: 10001,
      playerAugment2: 10002,
      playerAugment3: 10003,
      playerAugment4: 10004,
      rawSummaryJson: { matchId: 'HN1_enhanced' }
    } as unknown as Parameters<typeof database.matches.upsertMatchRecord>[0])

    const records = database.matches.listMatchRecordsByAccount('test-puuid', { limit: 10 })
    const enhanced = records.find((record) => record.matchId === 'HN1_enhanced') as any

    assert.deepEqual({
      goldEarned: enhanced?.goldEarned,
      totalDamageDealtToChampions: enhanced?.totalDamageDealtToChampions,
      doubleKills: enhanced?.doubleKills,
      tripleKills: enhanced?.tripleKills,
      quadraKills: enhanced?.quadraKills,
      pentaKills: enhanced?.pentaKills,
      largestKillingSpree: enhanced?.largestKillingSpree,
      legendaryCount: enhanced?.legendaryCount,
      spell1Id: enhanced?.spell1Id,
      spell2Id: enhanced?.spell2Id,
      perk0: enhanced?.perk0,
      playerAugment1: enhanced?.playerAugment1,
      playerAugment2: enhanced?.playerAugment2,
      playerAugment3: enhanced?.playerAugment3,
      playerAugment4: enhanced?.playerAugment4
    }, {
      goldEarned: 12345,
      totalDamageDealtToChampions: 30123,
      doubleKills: 2,
      tripleKills: 1,
      quadraKills: 0,
      pentaKills: 0,
      largestKillingSpree: 9,
      legendaryCount: 1,
      spell1Id: 4,
      spell2Id: 14,
      perk0: 8010,
      playerAugment1: 10001,
      playerAugment2: 10002,
      playerAugment3: 10003,
      playerAugment4: 10004
    })
  } finally {
    cleanup()
  }
})

test('AI analysis repository saves results and queries by account, id, and input hash', () => {
  const { database, cleanup } = createTempLocalDatabase()

  try {
    const saved = database.aiAnalyses.saveAnalysisResult({
      accountPuuid: 'test-puuid',
      matchId: 'HN1_1',
      analysisType: 'match_summary',
      subjectKey: 'HN1_1:test-puuid',
      gameVersion: '16.8.1',
      modelName: 'rankpeek-test-model',
      promptVersion: 'v1',
      inputHash: 'hash-1',
      outputJson: { summary: 'Keep farming.' }
    })

    assert.equal(saved.accountPuuid, 'test-puuid')
    assert.equal(saved.analysisType, 'match_summary')
    assert.match(saved.outputJson, /Keep farming/)

    const byAccount = database.aiAnalyses.listAnalysisResultsByAccount('test-puuid', { limit: 10 })
    assert.equal(byAccount.length, 1)
    assert.equal(byAccount[0]?.id, saved.id)

    assert.equal(database.aiAnalyses.getAnalysisResultById(saved.id)?.inputHash, 'hash-1')
    assert.equal(database.aiAnalyses.findAnalysisByInputHash('hash-1')?.id, saved.id)
  } finally {
    cleanup()
  }
})
