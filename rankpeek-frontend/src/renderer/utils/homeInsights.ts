import type { GameDetail, MatchHistory, Participant } from '@/types/api'
import { isRanked } from '@/utils/constants'

export const MIN_ANALYSIS_MATCHES = 10
export const MAX_ANALYSIS_MATCHES = 20
export const AUTO_ANALYSIS_INTERVALS = [10, 20] as const

const ANALYSIS_STORAGE_PREFIX = 'rankpeek.home.analysis'
const AUTO_ANALYSIS_STORAGE_PREFIX = 'rankpeek.home.autoAnalysis'
const FORTUNE_STORAGE_PREFIX = 'rankpeek.home.fortune'

export type GrowthRole = 'all' | 'top' | 'jungle' | 'mid' | 'bottom' | 'support' | 'unknown'
export type GrowthMetric = 'score' | 'kda' | 'winRate' | 'damage' | 'gold'
export type FortuneTone = 'good' | 'neutral' | 'bad'

export interface GrowthPoint {
  gameId: number
  matchIndex: number
  gameCreation: number
  role: GrowthRole
  queueId: number
  win: boolean
  kills: number
  deaths: number
  assists: number
  kda: number
  kdaText: string
  score: number
  winRate: number
  damage: number
  gold: number
}

export interface AnalysisSnapshot {
  id: string
  puuid: string
  analyzedAt: number
  matchStartAt: number
  matchEndAt: number
  matchCount: number
  summary: string
  detail: string
  points: GrowthPoint[]
}

export interface AutoAnalysisSettings {
  enabled: boolean
  interval: (typeof AUTO_ANALYSIS_INTERVALS)[number]
}

export interface Fortune {
  id: string
  label: string
  tone: FortuneTone
  text: string
}

export interface FortuneHistoryEntry {
  date: string
  fortuneId: string
}

export interface FortuneRecord {
  history: FortuneHistoryEntry[]
}

export const FORTUNE_POOL: Fortune[] = [
  { id: 'good-1', label: '强运！', tone: 'good', text: '今日签：连胜窗口已打开，但第三把容易上头。宜补位，忌嘴硬。' },
  { id: 'good-2', label: '上大分', tone: 'good', text: '今日签：队友会比较像人。适合打熟练英雄，少贪一波就能收米。' },
  { id: 'good-3', label: '手热', tone: 'good', text: '今日签：操作手感在线，第一局热身后状态会明显变顺。' },
  { id: 'good-4', label: '贵人局', tone: 'good', text: '今日签：容易遇到会沟通的队友。宜打信号，忌孤独 carry。' },
  { id: 'good-5', label: '节奏王', tone: 'good', text: '今日签：中期转线和资源团会有灵感，别被一次小亏打乱节奏。' },
  { id: 'good-6', label: '稳住赢', tone: 'good', text: '今日签：优势局会比较稳。适合先拿目标，再慢慢压视野。' },
  { id: 'good-7', label: '神之一手', tone: 'good', text: '今日签：关键团容易打出漂亮操作，记得留技能给对面核心。' },
  { id: 'good-8', label: '补分日', tone: 'good', text: '今日签：隐藏分心情不错。输一把别急，下一把更像正片。' },
  { id: 'good-9', label: '小顺风', tone: 'good', text: '今日签：开局容易拿到舒服对线。宜控兵线，忌无脑越塔。' },
  { id: 'neutral-1', label: '？？？', tone: 'neutral', text: '今日签：系统正在装作看不懂你。适合打一两把试水，见好就收。' },
  { id: 'neutral-2', label: '五五开', tone: 'neutral', text: '今日签：胜负看心态。你越想证明自己，越容易被兵线教育。' },
  { id: 'neutral-3', label: '玄学局', tone: 'neutral', text: '今日签：对局味道偏怪。先观察队友，再决定今天打几把。' },
  { id: 'neutral-4', label: '别急', tone: 'neutral', text: '今日签：慢热。第一波资源团别硬接，等装备和视野一起到位。' },
  { id: 'bad-1', label: '霉笔', tone: 'bad', text: '今日签：容易撞见嘴硬队友。建议屏蔽早一点，血压低一点。' },
  { id: 'bad-2', label: '红温预警', tone: 'bad', text: '今日签：逆风局概率偏高。输赢先放一边，别把下一把也赔进去。' },
  { id: 'bad-3', label: '别排了？', tone: 'bad', text: '今日签：手感可能离家出走。适合复盘、练补刀，少赌最后一把。' }
]

export function getTodayKey(date = new Date()): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function getFortuneById(id: string): Fortune | null {
  return FORTUNE_POOL.find(fortune => fortune.id === id) ?? null
}

export function getCurrentFortune(record: FortuneRecord, todayKey = getTodayKey()): Fortune | null {
  const entry = record.history.find(item => item.date === todayKey)
  return entry ? getFortuneById(entry.fortuneId) : null
}

export function drawDailyFortune(
  record: FortuneRecord,
  todayKey = getTodayKey(),
  random = Math.random
): { fortune: Fortune; record: FortuneRecord; alreadyDrawn: boolean } {
  const current = getCurrentFortune(record, todayKey)
  if (current) {
    return { fortune: current, record, alreadyDrawn: true }
  }

  const recentIds = new Set(record.history.slice(-7).map(entry => entry.fortuneId))
  const candidates = FORTUNE_POOL.filter(fortune => !recentIds.has(fortune.id))
  const usablePool = candidates.length > 0 ? candidates : FORTUNE_POOL
  const index = Math.floor(random() * usablePool.length) % usablePool.length
  const fortune = usablePool[index]
  const history = [...record.history, { date: todayKey, fortuneId: fortune.id }].slice(-14)

  return {
    fortune,
    record: { history },
    alreadyDrawn: false
  }
}

export function loadFortuneRecord(accountKey: string): FortuneRecord {
  return safeReadJson<FortuneRecord>(`${FORTUNE_STORAGE_PREFIX}.${accountKey}`, { history: [] })
}

export function saveFortuneRecord(accountKey: string, record: FortuneRecord) {
  safeWriteJson(`${FORTUNE_STORAGE_PREFIX}.${accountKey}`, record)
}

export function loadAnalysisSnapshot(puuid: string): AnalysisSnapshot | null {
  return safeReadJson<AnalysisSnapshot | null>(`${ANALYSIS_STORAGE_PREFIX}.${puuid}`, null)
}

export function saveAnalysisSnapshot(snapshot: AnalysisSnapshot) {
  safeWriteJson(`${ANALYSIS_STORAGE_PREFIX}.${snapshot.puuid}`, snapshot)
}

export function loadAutoAnalysisSettings(accountKey: string): AutoAnalysisSettings {
  return safeReadJson<AutoAnalysisSettings>(`${AUTO_ANALYSIS_STORAGE_PREFIX}.${accountKey}`, {
    enabled: false,
    interval: 10
  })
}

export function saveAutoAnalysisSettings(accountKey: string, settings: AutoAnalysisSettings) {
  safeWriteJson(`${AUTO_ANALYSIS_STORAGE_PREFIX}.${accountKey}`, settings)
}

export function createGrowthAnalysis(
  puuid: string,
  matches: MatchHistory[],
  detailsByGameId: Map<number, GameDetail>,
  analyzedAt = Date.now()
): AnalysisSnapshot {
  const rankedMatches = matches
    .filter(match => isRanked(match.queueId))
    .sort((a, b) => (b.gameCreation || 0) - (a.gameCreation || 0))
    .slice(0, MAX_ANALYSIS_MATCHES)

  if (rankedMatches.length < MIN_ANALYSIS_MATCHES) {
    throw new Error(`MIN_ANALYSIS_MATCHES:${MIN_ANALYSIS_MATCHES}`)
  }

  const chronologicalMatches = [...rankedMatches].reverse()
  const points = chronologicalMatches
    .map((match, index) => createGrowthPoint(puuid, match, detailsByGameId.get(match.gameId), index + 1))
    .filter((point): point is GrowthPoint => point !== null)

  if (points.length < MIN_ANALYSIS_MATCHES) {
    throw new Error(`MIN_ANALYSIS_MATCHES:${MIN_ANALYSIS_MATCHES}`)
  }

  const matchStartAt = Math.min(...points.map(point => point.gameCreation))
  const matchEndAt = Math.max(...points.map(point => point.gameCreation))
  const summary = buildSummary(points)

  return {
    id: `${puuid}-${analyzedAt}`,
    puuid,
    analyzedAt,
    matchStartAt,
    matchEndAt,
    matchCount: points.length,
    summary,
    detail: buildDetail(points),
    points
  }
}

export function getMetricValue(point: GrowthPoint, metric: GrowthMetric): number {
  switch (metric) {
    case 'kda':
      return point.kda
    case 'winRate':
      return point.winRate
    case 'damage':
      return point.damage
    case 'gold':
      return point.gold
    case 'score':
    default:
      return point.score
  }
}

export function filterGrowthPoints(points: GrowthPoint[], role: GrowthRole): GrowthPoint[] {
  if (role === 'all') {
    return points
  }
  return points.filter(point => point.role === role)
}

function createGrowthPoint(
  puuid: string,
  match: MatchHistory,
  detail: GameDetail | undefined,
  matchIndex: number
): GrowthPoint | null {
  const participant = findParticipant(match, puuid)
  if (!participant?.stats) {
    return null
  }

  const stats = participant.stats
  const kills = stats.kills || 0
  const deaths = stats.deaths || 0
  const assists = stats.assists || 0
  const kda = Number(((kills + assists) / Math.max(1, deaths)).toFixed(2))
  const damage = stats.totalDamageDealtToChampions || 0
  const gold = stats.goldEarned || 0
  const win = Boolean(stats.win)

  return {
    gameId: match.gameId,
    matchIndex,
    gameCreation: match.gameCreation || 0,
    role: resolveRole(detail, participant.participantId),
    queueId: match.queueId || 0,
    win,
    kills,
    deaths,
    assists,
    kda,
    kdaText: `${kills}/${deaths}/${assists}`,
    score: calculateScore(kda, damage, gold, win),
    winRate: 0,
    damage,
    gold
  }
}

function findParticipant(match: MatchHistory, puuid: string): Participant | null {
  const identity = match.participantIdentities?.find(item => item.player?.puuid === puuid)
  if (!identity) {
    return null
  }

  return match.participants?.find(item => item.participantId === identity.participantId) ?? null
}

function resolveRole(detail: GameDetail | undefined, participantId: number): GrowthRole {
  const participant = detail?.participants?.find(item => item.participantId === participantId)
  const lane = participant?.timeline?.lane?.toUpperCase()
  const role = participant?.timeline?.role?.toUpperCase()

  if (lane === 'TOP') return 'top'
  if (lane === 'JUNGLE') return 'jungle'
  if (lane === 'MIDDLE' || lane === 'MID') return 'mid'
  if (lane === 'BOTTOM' && role === 'DUO_SUPPORT') return 'support'
  if (lane === 'BOTTOM' || role === 'DUO_CARRY') return 'bottom'
  if (role === 'SUPPORT' || role === 'DUO_SUPPORT') return 'support'

  return 'unknown'
}

function calculateScore(kda: number, damage: number, gold: number, win: boolean): number {
  const kdaScore = Math.min(kda, 8) * 7
  const damageScore = Math.min(damage / 1000, 35)
  const goldScore = Math.min(gold / 500, 25)
  return Math.round(Math.min(100, kdaScore + damageScore + goldScore + (win ? 8 : 0)))
}

function buildSummary(points: GrowthPoint[]): string {
  applyRollingWinRate(points)
  const midpoint = Math.floor(points.length / 2)
  const early = average(points.slice(0, midpoint).map(point => point.score))
  const late = average(points.slice(midpoint).map(point => point.score))
  const wins = points.filter(point => point.win).length
  const trend = late - early

  if (trend >= 8) {
    return `最近 ${points.length} 场排位成长曲线明显上升，后半段决策和输出更稳定，建议继续围绕当前节奏复盘。`
  }

  if (trend <= -8) {
    return `最近 ${points.length} 场排位状态有回落，尤其是中后段波动偏大，建议优先复盘失利局的资源团选择。`
  }

  if (wins / points.length >= 0.6) {
    return `最近 ${points.length} 场排位整体稳定，胜率保持在健康区间，建议把优势局的滚雪球方式固定下来。`
  }

  return `最近 ${points.length} 场排位起伏较大，单局表现不差，但连续性还可以提升，建议先控制前两波关键团。`
}

function buildDetail(points: GrowthPoint[]): string {
  const averageKda = average(points.map(point => point.kda)).toFixed(1)
  const averageDamage = Math.round(average(points.map(point => point.damage)))
  const wins = points.filter(point => point.win).length
  return `样本胜率 ${wins}/${points.length}，平均 KDA ${averageKda}，平均英雄伤害 ${averageDamage}。详情图表可按分路和指标切换，优先看最近低谷点对应的对局时间。`
}

function applyRollingWinRate(points: GrowthPoint[]) {
  let wins = 0
  points.forEach((point, index) => {
    if (point.win) {
      wins += 1
    }
    point.winRate = Math.round((wins / (index + 1)) * 100)
  })
}

function average(values: number[]): number {
  if (values.length === 0) {
    return 0
  }
  return values.reduce((sum, value) => sum + value, 0) / values.length
}

function safeReadJson<T>(key: string, fallback: T): T {
  try {
    if (typeof window === 'undefined') {
      return fallback
    }
    const raw = window.localStorage.getItem(key)
    return raw ? JSON.parse(raw) as T : fallback
  } catch {
    return fallback
  }
}

function safeWriteJson(key: string, value: unknown) {
  try {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(key, JSON.stringify(value))
    }
  } catch {
    // Local persistence is helpful but should not block the UI.
  }
}
