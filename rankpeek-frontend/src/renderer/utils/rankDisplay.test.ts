import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildRankDisplay,
  getRankQueueInfo,
  type RankLoadStatus
} from './rankDisplay.ts'
import type { QueueInfo, Rank } from '../types/api.ts'

function queueInfo(overrides: Partial<QueueInfo> = {}): QueueInfo {
  return {
    queueType: 'RANKED_SOLO_5x5',
    tier: 'GOLD',
    division: 'II',
    leaguePoints: 63,
    wins: 28,
    losses: 22,
    highestTier: 'GOLD',
    highestDivision: 'II',
    isProvisional: false,
    ...overrides
  }
}

test('maps ranked queue keys to their queue entries', () => {
  const solo = queueInfo({ queueType: 'RANKED_SOLO_5x5', tier: 'PLATINUM' })
  const flex = queueInfo({ queueType: 'RANKED_FLEX_SR', tier: 'GOLD' })
  const rank: Rank = {
    queueMap: {
      RANKED_SOLO_5x5: solo,
      RANKED_FLEX_SR: flex
    }
  }

  assert.equal(getRankQueueInfo(rank, 'RANKED_SOLO_5x5'), solo)
  assert.equal(getRankQueueInfo(rank, 'RANKED_FLEX_SR'), flex)
})

test('formats ranked tier, division, league points, and win rate from queue info', () => {
  const display = buildRankDisplay(queueInfo(), 'loaded')

  assert.equal(display.tierText, '黄金 II 63LP')
  assert.equal(display.recordText, '56% 胜率 (28W 22L)')
  assert.equal(display.state, 'ranked')
  assert.equal(display.iconTier, 'gold')
})

test('does not show fake unranked records while rank data is loading', () => {
  const display = buildRankDisplay(null, 'loading')

  assert.equal(display.tierText, '段位加载中')
  assert.equal(display.recordText, '')
  assert.equal(display.state, 'loading')
})

test('shows an explicit failed state instead of pretending queues are unranked', () => {
  const display = buildRankDisplay(null, 'error')

  assert.equal(display.tierText, '段位获取失败')
  assert.equal(display.recordText, '')
  assert.equal(display.state, 'error')
})

test('empty queue entries display real unranked copy without fake zero win rate', () => {
  const display = buildRankDisplay(
    queueInfo({
      tier: '',
      division: 'NA',
      leaguePoints: 0,
      wins: 0,
      losses: 0
    }),
    'loaded'
  )

  assert.equal(display.tierText, '未定级')
  assert.equal(display.recordText, '暂无排位数据')
  assert.equal(display.state, 'unranked')
})

test('one missing queue is unranked without hiding the other ranked queue', () => {
  const soloDisplay = buildRankDisplay(queueInfo({ tier: 'EMERALD', division: 'IV', leaguePoints: 35 }), 'loaded')
  const flexDisplay = buildRankDisplay(null, 'loaded')

  assert.equal(soloDisplay.tierText, '翡翠 IV 35LP')
  assert.equal(soloDisplay.recordText, '56% 胜率 (28W 22L)')
  assert.equal(flexDisplay.tierText, '未定级')
  assert.equal(flexDisplay.recordText, '暂无排位数据')
})

test('rank load status values stay intentionally narrow', () => {
  const states: RankLoadStatus[] = ['loading', 'loaded', 'error']

  assert.deepEqual(states, ['loading', 'loaded', 'error'])
})
