import type { QueueInfo, Rank } from '@/types/api'

export type RankedQueueKey = 'RANKED_SOLO_5x5' | 'RANKED_FLEX_SR'
export type RankLoadStatus = 'loading' | 'loaded' | 'error'
export type RankDisplayState = 'loading' | 'error' | 'unranked' | 'ranked'

export interface RankDisplayText {
  loading: string
  error: string
  unranked: string
  noData: string
  wins: (count: number) => string
}

export interface RankDisplay {
  state: RankDisplayState
  tierText: string
  recordText: string
  iconTier: string
}

export const defaultRankDisplayText: RankDisplayText = {
  loading: '段位加载中',
  error: '段位获取失败',
  unranked: '未定级',
  noData: '暂无排位数据',
  wins: count => `${count}胜`
}

const TIER_LABELS: Record<string, string> = {
  IRON: '黑铁',
  BRONZE: '青铜',
  SILVER: '白银',
  GOLD: '黄金',
  PLATINUM: '铂金',
  EMERALD: '翡翠',
  DIAMOND: '钻石',
  MASTER: '超凡大师',
  GRANDMASTER: '傲世宗师',
  CHALLENGER: '最强王者'
}

const DIVISION_LABELS: Record<string, string> = {
  I: 'I',
  II: 'II',
  III: 'III',
  IV: 'IV'
}

const UNRANKED_TIER_VALUES = new Set(['', 'UNRANKED', 'NONE', 'NULL', 'UNDEFINED'])
const APEX_TIERS = new Set(['MASTER', 'GRANDMASTER', 'CHALLENGER'])

export function getRankQueueInfo(rank: Rank | null | undefined, queueKey: RankedQueueKey): QueueInfo | null {
  return rank?.queueMap?.[queueKey] ?? null
}

export function buildRankDisplay(
  queueInfo: QueueInfo | null | undefined,
  status: RankLoadStatus = 'loaded',
  text: RankDisplayText = defaultRankDisplayText
): RankDisplay {
  if (status === 'loading') {
    return {
      state: 'loading',
      tierText: text.loading,
      recordText: '',
      iconTier: 'unranked'
    }
  }

  if (status === 'error') {
    return {
      state: 'error',
      tierText: text.error,
      recordText: '',
      iconTier: 'unranked'
    }
  }

  if (!isRankedQueueInfo(queueInfo)) {
    return {
      state: 'unranked',
      tierText: text.unranked,
      recordText: text.noData,
      iconTier: 'unranked'
    }
  }

  return {
    state: 'ranked',
    tierText: formatRankTier(queueInfo),
    recordText: formatRankRecord(queueInfo, text),
    iconTier: normalizeTier(queueInfo.tier).toLowerCase()
  }
}

export function isRankedQueueInfo(queueInfo: QueueInfo | null | undefined): queueInfo is QueueInfo {
  const tier = normalizeTier(queueInfo?.tier)
  return Boolean(queueInfo) && !UNRANKED_TIER_VALUES.has(tier)
}

function formatRankTier(queueInfo: QueueInfo): string {
  const tier = normalizeTier(queueInfo.tier)
  const tierLabel = TIER_LABELS[tier] ?? queueInfo.tier
  const points = readFiniteNumber(queueInfo.leaguePoints)

  if (APEX_TIERS.has(tier)) {
    return `${tierLabel} ${points}LP`
  }

  const division = normalizeDivision(queueInfo.division)
  return division ? `${tierLabel} ${division} ${points}LP` : `${tierLabel} ${points}LP`
}

function formatRankRecord(queueInfo: QueueInfo, text: RankDisplayText): string {
  const wins = Math.floor(readFiniteNumber(queueInfo.wins))
  if (wins <= 0) {
    return ''
  }

  return text.wins(wins)
}

function normalizeTier(tier?: string): string {
  return (tier ?? '').trim().toUpperCase()
}

function normalizeDivision(division?: string): string {
  const key = (division ?? '').trim().toUpperCase()
  if (!key || key === 'NA') {
    return ''
  }
  return DIVISION_LABELS[key] ?? key
}

function readFiniteNumber(value?: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0
}
