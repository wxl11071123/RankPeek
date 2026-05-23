import test from 'node:test'
import assert from 'node:assert/strict'
import type { QueueInfo, SessionData, SessionSummoner } from '../types/api.ts'
import { buildOpggChampionQuery } from './opggChampionQuery.ts'

function queueInfo(tier: string): QueueInfo {
  return {
    queueType: 'RANKED_SOLO_5x5',
    tier,
    division: 'II',
    leaguePoints: 66,
    wins: 20,
    losses: 10,
    highestTier: tier,
    highestDivision: 'II',
    isProvisional: false
  }
}

function player(overrides: Partial<SessionSummoner> = {}): SessionSummoner {
  return {
    championId: 103,
    championKey: 'Ahri',
    selectedPosition: 'MIDDLE',
    summoner: {
      gameName: 'Self',
      tagLine: '1234',
      summonerLevel: 300,
      profileIconId: 1,
      puuid: 'self-puuid',
      summonerId: 1
    },
    matchHistory: [],
    userTag: null,
    rank: {
      queueMap: {
        RANKED_SOLO_5x5: queueInfo('EMERALD'),
        RANKED_FLEX_SR: queueInfo('PLATINUM')
      }
    },
    meetGames: [],
    preGroupMarkers: { name: '', type: '' },
    isLoading: false,
    ...overrides
  }
}

function session(overrides: Partial<SessionData> = {}, self = player()): SessionData {
  return {
    phase: 'ChampSelect',
    queueType: 'RANKED_SOLO_5x5',
    typeCn: 'ranked',
    queueId: 420,
    currentSummoner: self.summoner,
    teamOne: [self],
    teamTwo: [],
    ...overrides
  }
}

test('ranked OP.GG query uses current champion, lane, exact queue rank bucket, and KR region', () => {
  const self = player({ selectedPosition: 'MIDDLE' })

  const query = buildOpggChampionQuery(session({}, self))

  assert.equal(query.enabled, true)
  assert.equal(query.reason, '')
  assert.equal(query.championId, 103)
  assert.equal(query.mode, 'ranked')
  assert.equal(query.region, 'kr')
  assert.equal(query.tier, 'emerald_plus')
  assert.equal(query.position, 'mid')
  assert.equal(query.filterLabel, 'KR · 排位 · 翡翠+ · 中路')
})

test('ranked OP.GG query maps flex rank, jungle position, and master-plus tiers', () => {
  const self = player({
    selectedPosition: 'JUNGLE',
    rank: {
      queueMap: {
        RANKED_SOLO_5x5: queueInfo('GOLD'),
        RANKED_FLEX_SR: queueInfo('MASTER')
      }
    }
  })

  const query = buildOpggChampionQuery(session({
    queueId: 440,
    queueType: 'RANKED_FLEX_SR'
  }, self))

  assert.equal(query.enabled, true)
  assert.equal(query.mode, 'ranked')
  assert.equal(query.tier, 'master_plus')
  assert.equal(query.position, 'jungle')
})

test('ranked OP.GG query disables with a reason when required champion, lane, or rank is missing', () => {
  assert.equal(buildOpggChampionQuery(session({}, player({ championId: 0 }))).enabled, false)
  assert.match(buildOpggChampionQuery(session({}, player({ championId: 0 }))).reason, /英雄/)

  assert.equal(buildOpggChampionQuery(session({}, player({ selectedPosition: '' }))).enabled, false)
  assert.match(buildOpggChampionQuery(session({}, player({ selectedPosition: '' }))).reason, /位置/)

  assert.equal(buildOpggChampionQuery(session({}, player({
    rank: {
      queueMap: {
        RANKED_SOLO_5x5: { ...queueInfo('UNRANKED'), tier: 'UNRANKED' },
        RANKED_FLEX_SR: queueInfo('PLATINUM')
      }
    }
  }))).enabled, false)
})

test('non-ranked OP.GG query enables by mode and leaves champion detail empty when no champion is selected', () => {
  const query = buildOpggChampionQuery(session({
    queueId: 450,
    queueType: 'ARAM',
    typeCn: 'ARAM'
  }, player({ championId: 0 })))

  assert.equal(query.enabled, true)
  assert.equal(query.championId, null)
  assert.equal(query.mode, 'aram')
  assert.equal(query.region, 'kr')
  assert.equal(query.tier, 'all')
  assert.equal(query.position, 'none')
})

test('OP.GG query maps low and common ranked tiers to OP.GG plus buckets', () => {
  assert.equal(buildOpggChampionQuery(session({}, player({
    rank: { queueMap: { RANKED_SOLO_5x5: queueInfo('SILVER'), RANKED_FLEX_SR: queueInfo('GOLD') } }
  }))).tier, 'ibsg')
  assert.equal(buildOpggChampionQuery(session({}, player({
    rank: { queueMap: { RANKED_SOLO_5x5: queueInfo('GOLD'), RANKED_FLEX_SR: queueInfo('PLATINUM') } }
  }))).tier, 'gold_plus')
  assert.equal(buildOpggChampionQuery(session({}, player({
    rank: { queueMap: { RANKED_SOLO_5x5: queueInfo('DIAMOND'), RANKED_FLEX_SR: queueInfo('PLATINUM') } }
  }))).tier, 'diamond_plus')
})
