import test from 'node:test'
import assert from 'node:assert/strict'
import type { RecordStatus, SessionData, SessionSummoner } from '../types/api.ts'
import { buildGamingAiInputSnapshot } from './gamingAiInputSnapshot.ts'

function createPlayer(overrides: Partial<SessionSummoner> & {
  name?: string
  tagLine?: string
  puuid?: string
  recordStatus?: RecordStatus
  wins?: number
  losses?: number
  kda?: number
  kills?: number
  deaths?: number
  assists?: number
  damage?: number
  gold?: number
  tier?: string
  division?: string
  tags?: Array<{ tagName: string; good?: boolean | null; tagDesc?: string }>
} = {}): SessionSummoner {
  const wins = overrides.wins ?? 11
  const losses = overrides.losses ?? 9
  return {
    championId: overrides.championId ?? 141,
    championKey: overrides.championKey ?? String(overrides.championId ?? 141),
    summoner: {
      gameName: overrides.name ?? 'W',
      tagLine: overrides.tagLine ?? '1234',
      summonerLevel: 300,
      profileIconId: 29,
      puuid: overrides.puuid ?? `${overrides.name ?? 'player'}-puuid`,
      summonerId: 1
    },
    matchHistory: [
      {
        gameId: 998877,
        gameMode: 'CLASSIC',
        gameType: 'MATCHED_GAME',
        queueId: 420,
        gameDuration: 1800,
        gameCreation: 1710000000000,
        platformId: 'HN1',
        participants: [],
        participantIdentities: []
      }
    ],
    userTag: {
      recordStatus: overrides.recordStatus ?? 'NORMAL',
      recentData: {
        kda: overrides.kda ?? 3.1,
        kills: overrides.kills ?? 7,
        deaths: overrides.deaths ?? 3,
        assists: overrides.assists ?? 8,
        selectMode: 420,
        selectModeCn: 'ranked',
        selectWins: wins,
        selectLosses: losses,
        groupRate: 12.5,
        averageGold: overrides.gold ?? 10000,
        goldRate: 21.5,
        averageDamageDealtToChampions: overrides.damage ?? 15230,
        damageDealtToChampionsRate: 0,
        friendAndDispute: {
          friendsRate: 4.5,
          disputeRate: 1.5,
          friendsSummoner: [],
          disputeSummoner: []
        }
      },
      tag: overrides.tags ?? [
        { tagName: 'high win rate', good: true, tagDesc: 'recently winning' },
        { tagName: 'stable output', good: true }
      ]
    },
    rank: {
      queueMap: {
        RANKED_SOLO_5x5: {
          queueType: 'RANKED_SOLO_5x5',
          tier: overrides.tier ?? 'EMERALD',
          division: overrides.division ?? 'I',
          leaguePoints: 50,
          wins: 40,
          losses: 35,
          highestTier: '',
          highestDivision: '',
          isProvisional: false
        },
        RANKED_FLEX_SR: {
          queueType: 'RANKED_FLEX_SR',
          tier: 'PLATINUM',
          division: 'IV',
          leaguePoints: 10,
          wins: 12,
          losses: 11,
          highestTier: '',
          highestDivision: '',
          isProvisional: false
        }
      }
    },
    meetGames: [],
    preGroupMarkers: { name: 'duo-a', type: 'same-team' },
    isLoading: false,
    ...overrides
  }
}

function createSessionData(overrides: Partial<SessionData> = {}): SessionData {
  const ally = createPlayer({ name: 'Ally', puuid: 'ally-puuid' })
  const enemy = createPlayer({ name: 'Enemy', puuid: 'enemy-puuid', recordStatus: 'PRIVATE' })
  return {
    phase: 'ChampSelect',
    queueType: 'RANKED_SOLO_5x5',
    typeCn: 'Ranked Solo',
    queueId: 420,
    matchId: 'mock-match',
    roundIndex: 2,
    currentSummoner: ally.summoner,
    teamOne: [ally],
    teamTwo: [enemy],
    ...overrides
  }
}

test('builds a gaming AI input snapshot from the current session teams', () => {
  const sessionData = createSessionData()
  const snapshot = buildGamingAiInputSnapshot({
    mode: 'teammate',
    sessionData,
    selectedPlayers: sessionData.teamOne,
    currentSummonerPuuid: 'ally-puuid'
  })

  assert.equal(snapshot.schemaVersion, 'gaming_ai_input_snapshot.v1')
  assert.equal(snapshot.mode, 'teammate')
  assert.equal(snapshot.phase, 'ChampSelect')
  assert.equal(snapshot.queueId, 420)
  assert.equal(snapshot.queueName, 'Ranked Solo')
  assert.equal(snapshot.matchId, 'mock-match')
  assert.equal(snapshot.roundIndex, 2)
  assert.equal(snapshot.currentSummoner?.puuid, 'ally-puuid')
  assert.equal(snapshot.allyTeam.length, 1)
  assert.equal(snapshot.enemyTeam.length, 1)
  assert.equal(snapshot.selectedPlayers.length, 1)
  assert.equal(snapshot.allyTeam[0]?.key, 'puuid:ally-puuid')
  assert.equal(snapshot.enemyTeam[0]?.key, 'puuid:enemy-puuid')
  assert.equal(snapshot.selectedPlayers[0]?.key, 'puuid:ally-puuid')
  assert.equal(snapshot.selectedPlayers[0]?.side, 'ally')
})

test('prefers teammates and opponents when session data has hydrated team aliases', () => {
  const teammate = createPlayer({ name: 'HydratedAlly', puuid: 'hydrated-ally' })
  const opponent = createPlayer({ name: 'HydratedEnemy', puuid: 'hydrated-enemy' })
  const sessionData = createSessionData({
    teammates: [teammate],
    opponents: [opponent]
  })

  const snapshot = buildGamingAiInputSnapshot({
    mode: 'opponent',
    sessionData,
    selectedPlayers: sessionData.opponents ?? [],
    currentSummonerPuuid: 'hydrated-ally'
  })

  assert.equal(snapshot.allyTeam[0]?.displayName, 'HydratedAlly#1234')
  assert.equal(snapshot.enemyTeam[0]?.displayName, 'HydratedEnemy#1234')
  assert.deepEqual(snapshot.selectedPlayers.map(player => player.side), ['enemy'])
})

test('reuses user tags, recent metrics, rank, champion, summoner, and record status', () => {
  const player = createPlayer({
    name: 'MetricPlayer',
    championId: 64,
    championKey: 'LeeSin',
    wins: 13,
    losses: 7,
    kda: 3.25,
    kills: 8,
    deaths: 4,
    assists: 5,
    damage: 17750,
    gold: 11000,
    recordStatus: 'ERROR',
    tags: [{ tagName: 'volatile', good: false, tagDesc: 'swings hard' }]
  })
  const snapshot = buildGamingAiInputSnapshot({
    mode: 'teammate',
    sessionData: createSessionData({ teamOne: [player], teamTwo: [] }),
    selectedPlayers: [player]
  })
  const normalized = snapshot.allyTeam[0]

  assert.equal(normalized?.puuid, 'MetricPlayer-puuid')
  assert.equal(normalized?.gameName, 'MetricPlayer')
  assert.equal(normalized?.tagLine, '1234')
  assert.equal(normalized?.displayName, 'MetricPlayer#1234')
  assert.equal(normalized?.championId, 64)
  assert.equal(normalized?.championKey, 'LeeSin')
  assert.equal(normalized?.recordStatus, 'ERROR')
  assert.deepEqual(normalized?.tags, [{ name: 'volatile', good: false, desc: 'swings hard' }])
  assert.equal(normalized?.metrics.sample, 20)
  assert.equal(normalized?.metrics.wins, 13)
  assert.equal(normalized?.metrics.losses, 7)
  assert.equal(normalized?.metrics.winRate, 65)
  assert.equal(normalized?.metrics.kda, 3.25)
  assert.equal(normalized?.metrics.averageGold, 11000)
  assert.equal(normalized?.metrics.averageDamageDealtToChampions, 17750)
  assert.equal(Number(normalized?.metrics.damageRate?.toFixed(2)), 161.36)
  assert.match(normalized?.rankText ?? '', /50/)
})

test('keeps PRIVATE, EMPTY, and ERROR data quality counts without normalizing them away', () => {
  const privatePlayer = createPlayer({ name: 'Private', recordStatus: 'PRIVATE' })
  const emptyPlayer = createPlayer({ name: 'Empty', recordStatus: 'EMPTY' })
  const errorPlayer = createPlayer({ name: 'Error', recordStatus: 'ERROR' })
  const normalPlayer = createPlayer({ name: 'Normal', recordStatus: 'NORMAL' })
  const snapshot = buildGamingAiInputSnapshot({
    mode: 'teammate',
    sessionData: createSessionData({
      teamOne: [privatePlayer, emptyPlayer],
      teamTwo: [errorPlayer, normalPlayer]
    }),
    selectedPlayers: [privatePlayer, emptyPlayer]
  })

  assert.deepEqual(snapshot.allyTeam.map(player => player.recordStatus), ['PRIVATE', 'EMPTY'])
  assert.deepEqual(snapshot.enemyTeam.map(player => player.recordStatus), ['ERROR', 'NORMAL'])
  assert.deepEqual(snapshot.dataQuality, {
    allyCount: 2,
    enemyCount: 2,
    selectedCount: 2,
    normalRecordCount: 1,
    hiddenRecordCount: 1,
    emptyRecordCount: 1,
    errorRecordCount: 1
  })
})

test('does not copy raw match history into the snapshot', () => {
  const snapshot = buildGamingAiInputSnapshot({
    mode: 'teammate',
    sessionData: createSessionData(),
    selectedPlayers: []
  })
  const serialized = JSON.stringify(snapshot)

  assert.doesNotMatch(serialized, /matchHistory/)
  assert.doesNotMatch(serialized, /998877/)
  assert.doesNotMatch(serialized, /participantIdentities/)
})

test('converts NaN and Infinity metrics into null or zero before sending', () => {
  const player = createPlayer({
    name: 'BadNumbers',
    wins: Number.NaN,
    losses: Number.POSITIVE_INFINITY,
    kda: Number.NaN,
    damage: Number.POSITIVE_INFINITY,
    gold: Number.NaN
  })
  const snapshot = buildGamingAiInputSnapshot({
    mode: 'teammate',
    sessionData: createSessionData({ teamOne: [player], teamTwo: [] }),
    selectedPlayers: [player]
  })
  const metrics = snapshot.allyTeam[0]?.metrics

  assert.equal(metrics?.sample, 0)
  assert.equal(metrics?.wins, 0)
  assert.equal(metrics?.losses, 0)
  assert.equal(metrics?.winRate, null)
  assert.equal(metrics?.kda, null)
  assert.equal(metrics?.averageGold, null)
  assert.equal(metrics?.averageDamageDealtToChampions, null)
  assert.equal(metrics?.damageRate, null)
  assertNoNonFiniteNumbers(snapshot)
})

function assertNoNonFiniteNumbers(value: unknown): void {
  if (typeof value === 'number') {
    assert.equal(Number.isFinite(value), true)
    return
  }
  if (Array.isArray(value)) {
    value.forEach(assertNoNonFiniteNumbers)
    return
  }
  if (value && typeof value === 'object') {
    Object.values(value).forEach(assertNoNonFiniteNumbers)
  }
}
