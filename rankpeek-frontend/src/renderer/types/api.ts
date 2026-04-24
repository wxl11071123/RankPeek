// ========== 统一响应格式 ==========

/**
 * 统一 API 响应格式
 */
export interface ApiResponse<T> {
  /** 响应码，200 表示成功 */
  code: number
  /** 响应消息 */
  message: string
  /** 响应数据 */
  data: T
  /** 时间戳 */
  timestamp: number
}

// ========== 数据类型定义 ==========

// 召唤师信息
export interface Summoner {
  gameName: string
  tagLine: string
  summonerLevel: number
  profileIconId: number
  puuid: string
  summonerId: number
}

// 胜率统计
export interface WinRate {
  wins: number
  losses: number
  winRate: number
}

// 英雄选项
export interface ChampionOption {
  value: number
  label: string
  realName: string
  nickname: string
}

// 游戏模式选项
export interface GameModeOption {
  id: number
  name: string
}

// 段位信息
export interface Rank {
  queueMap: QueueMap
}

export interface QueueMap {
  RANKED_SOLO_5x5: QueueInfo
  RANKED_FLEX_SR: QueueInfo
}

export interface QueueInfo {
  queueType: string
  tier: string
  displayRank?: string
  totalGames?: number
  tierCn?: string
  division: string
  leaguePoints: number
  wins: number
  losses: number
  highestTier: string
  highestDivision: string
  isProvisional: boolean
}

// 对局记录
export interface MatchHistory {
  gameId: number
  gameMode: string
  gameType: string
  queueId: number
  queueName?: string // 中文游戏模式名称
  gameDuration: number
  gameCreation: number
  platformId: string
  participants: Participant[]
  participantIdentities: ParticipantIdentity[]
}

export interface Participant {
  participantId: number
  teamId: number
  championId: number
  spell1Id: number
  spell2Id: number
  stats: Stats
}

export interface Stats {
  win: boolean
  kills: number
  deaths: number
  assists: number
  goldEarned: number
  totalMinionsKilled: number
  neutralMinionsKilled: number
  totalDamageDealtToChampions: number
  totalDamageTaken: number
  totalHeal: number
  // 装备
  item0: number
  item1: number
  item2: number
  item3: number
  item4: number
  item5: number
  item6: number
  // 伤害占比
  damageDealtToChampionsRate?: number
  damageTakenRate?: number
  healRate?: number
  // MVP/SVP
  mvp?: string
  // 符文
  perk0?: number
  // 补兵（别名）
  minionsKilled?: number
  // 对塔伤害
  damageDealtToTurrets?: number
  // 海克斯强化
  playerAugment1?: number
  playerAugment2?: number
  playerAugment3?: number
  playerAugment4?: number
}

export interface ParticipantIdentity {
  participantId: number
  player: Player
}

export interface Player {
  accountId: number
  summonerId: number
  summonerName: string
  gameName: string
  tagLine: string
  puuid: string
  platformId: string
}

// 游戏状态
export interface GameState {
  connected: boolean
  phase: string
  summoner: Summoner | null
  timestamp: number
}

// 大厅信息
export interface Lobby {
  lobbyId: string
  queueId: number
  gameConfig: GameConfig
  members: LobbyMember[]
}

export interface GameConfig {
  queueId: number
  gameMode: string
  isCustom: boolean
}

export interface LobbyMember {
  puuid: string
  summonerName: string
  summonerId: number
  isLeader: boolean
  ready: boolean
  teamId: number
}

// 自动化任务状态
export interface AutomationStatus {
  auto_match: boolean
  auto_accept: boolean
  auto_pick: boolean
  auto_ban: boolean
}

// 配置
export interface AppConfig {
  settings: {
    auto: {
      startMatchSwitch: boolean
      startMatchDelay: number
      acceptMatchSwitch: boolean
      acceptMatchDelay: number
      pickChampionSwitch: boolean
      banChampionSwitch: boolean
      pickChampionSlice: number[]
      banChampionSlice: number[]
    }
    match: {
      defaultQueueMode: number
    }
  }
}

// 用户标签
export type RecordStatus = 'NORMAL' | 'PRIVATE' | 'EMPTY' | 'ERROR'

export interface UserTag {
  recordStatus: RecordStatus
  recentData: RecentData
  tag: RankTag[]
}

export interface UserTagSummary {
  recordStatus: RecordStatus
  recentData: RecentData
  tag: RankTag[]
}

export interface RecentData {
  kda: number
  kills: number
  deaths: number
  assists: number
  selectMode: number
  selectModeCn: string
  selectWins: number
  selectLosses: number
  groupRate: number
  averageGold: number
  goldRate: number
  averageDamageDealtToChampions: number
  damageDealtToChampionsRate: number
  friendAndDispute: FriendAndDispute
  oneGamePlayersMap?: Record<string, OneGamePlayer[]>
}

export interface RankTag {
  good: boolean | null
  tagName: string
  tagDesc: string
}

export interface FriendAndDispute {
  friendsRate: number
  disputeRate: number
  friendsSummoner: OneGamePlayerSummoner[]
  disputeSummoner: OneGamePlayerSummoner[]
}

export interface OneGamePlayer {
  index: number
  gameId: number
  puuid: string
  gameCreatedAt: string
  isMyTeam: boolean
  gameName: string
  tagLine?: string
  championId: number
  kills: number
  deaths: number
  assists: number
  win: boolean
  queueIdCn: string
}

export interface OneGamePlayerSummoner {
  winRate: number
  wins: number
  losses: number
  summoner: Summoner
  oneGamePlayer: OneGamePlayer[]
}

// ARAM 平衡数据
export interface AramBalanceData {
  championId: number
  championName?: string
  dmg_dealt?: number
  dmg_taken?: number
  healing?: number
  shielding?: number
  ability_haste?: number
  mana_regen?: number
  energy_regen?: number
  attack_speed?: number
  movement_speed?: number
  tenacity?: number
}

// 游戏资源详情
export interface AssetDetails {
  id: number
  name: string
  description?: string
  type: string
  iconUrl?: string
  extra?: unknown
}

// ========== 标签配置系统 ==========

// 标签配置
export interface TagConfig {
  id: string
  name: string
  desc: string
  good: boolean | null
  enabled: boolean
  isDefault: boolean
  condition: TagCondition
}

// 条件树节点
export type TagCondition =
  | AndCondition
  | OrCondition
  | NotCondition
  | HistoryCondition
  | CurrentQueueCondition
  | CurrentChampionCondition

export interface AndCondition {
  type: 'and'
  conditions: TagCondition[]
}

export interface OrCondition {
  type: 'or'
  conditions: TagCondition[]
}

export interface NotCondition {
  type: 'not'
  condition: TagCondition
}

export interface HistoryCondition {
  type: 'history'
  filters: MatchFilter[]
  refresh: MatchRefresh
}

export interface CurrentQueueCondition {
  type: 'currentQueue'
  ids: number[]
}

export interface CurrentChampionCondition {
  type: 'currentChampion'
  ids: number[]
}

// 过滤器
export type MatchFilter =
  | QueueFilter
  | ChampionFilter
  | StatFilter

export interface QueueFilter {
  type: 'queue'
  ids: number[]
}

export interface ChampionFilter {
  type: 'champion'
  ids: number[]
}

export interface StatFilter {
  type: 'stat'
  metric: string
  op: Operator
  value: number
}

// 刷新器
export type MatchRefresh =
  | CountRefresh
  | AverageRefresh
  | SumRefresh
  | MaxRefresh
  | MinRefresh
  | StreakRefresh

export interface CountRefresh {
  type: 'count'
  op: Operator
  value: number
}

export interface AverageRefresh {
  type: 'average'
  metric: string
  op: Operator
  value: number
}

export interface SumRefresh {
  type: 'sum'
  metric: string
  op: Operator
  value: number
}

export interface MaxRefresh {
  type: 'max'
  metric: string
  op: Operator
  value: number
}

export interface MinRefresh {
  type: 'min'
  metric: string
  op: Operator
  value: number
}

export interface StreakRefresh {
  type: 'streak'
  min: number
  kind: StreakType
}

// 运算符
export type Operator = '>' | '>=' | '<' | '<=' | '==' | '!='

// 连胜/连败类型
export type StreakType = 'WIN' | 'LOSS'

// ========== 对局详情 ==========

// 对局详情
export interface GameDetail {
  gameId: number
  gameMode: string
  gameType: string
  mapId: number
  queueId: number
  gameDuration: number
  gameCreation: number
  participantIdentities: GameParticipantIdentity[]
  participants: GameParticipant[]
}

export interface GameParticipantIdentity {
  participantId: number
  player: GamePlayer
}

export interface GamePlayer {
  accountId: number
  puuid: string
  platformId: string
  summonerName: string
  gameName: string
  tagLine: string
  summonerId: number
}

export interface GameParticipant {
  participantId: number
  teamId: number
  championId: number
  spell1Id: number
  spell2Id: number
  stats: GameStats
  timeline: GameTimeline
}

export interface GameStats {
  win: boolean
  kills: number
  deaths: number
  assists: number
  totalMinionsKilled: number
  neutralMinionsKilled: number
  goldEarned: number
  totalDamageDealtToChampions: number
  totalDamageTaken: number
  totalHeal: number
  visionWardsBoughtInGame: number
  wardsPlaced: number
  wardsKilled: number
  largestMultiKill: number
  doubleKills: number
  tripleKills: number
  quadraKills: number
  pentaKills: number
  // 符文
  perk0?: number
  perk1?: number
  perk2?: number
  perk3?: number
  perk4?: number
  perk5?: number
  perkPrimaryStyle?: number
  perkSubStyle?: number
  // 海克斯强化 (竞技场模式)
  playerAugment1?: number
  playerAugment2?: number
  playerAugment3?: number
  playerAugment4?: number
  // 补兵（别名）
  minionsKilled?: number
  // 对塔伤害
  damageDealtToTurrets?: number
  // MVP/SVP
  mvp?: string
  // 伤害占比
  damageDealtToChampionsRate?: number
  damageTakenRate?: number
  healRate?: number
}

export interface GameTimeline {
  lane: string
  role: string
}

// ========== 会话数据 ==========

// 预组队标记
export interface PreGroupMarker {
  name: string
  type: string
}

// 会话中的召唤师
export interface SessionSummoner {
  championId: number
  championKey: string
  summoner: Summoner
  matchHistory: MatchHistory[]
  userTag: UserTag
  rank: Rank
  meetGames: OneGamePlayer[]
  preGroupMarkers: PreGroupMarker
  isLoading: boolean
}

// 会话数据
export interface SessionData {
  phase: string
  queueType: string
  typeCn: string
  queueId: number
  teamOne: SessionSummoner[]
  teamTwo: SessionSummoner[]
}
