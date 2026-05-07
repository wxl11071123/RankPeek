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
  totalGames?: number | null
  games?: number | null
  tierCn?: string
  division: string
  leaguePoints: number
  wins: number
  losses?: number | null
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
  remake?: boolean
  participants: Participant[]
  participantIdentities: ParticipantIdentity[]
  teamObjectives?: TeamObjectiveSummary[]
  teamBans?: TeamBanSummary[]
}

export interface MatchHistoryPageResponse {
  matches: MatchHistory[]
  page: number
  pageSize: number
  hasNext: boolean
  source: string
  recordStatus: RecordStatus
  sgpServerId?: string
  warnings?: string[]
}

export interface Participant {
  participantId: number
  teamId: number
  championId: number
  spell1Id: number
  spell2Id: number
  teamPosition?: string
  individualPosition?: string
  selectedPosition?: string
  lane?: string
  role?: string
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
  visionScore?: number
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
  doubleKills?: number
  tripleKills?: number
  quadraKills?: number
  pentaKills?: number
  largestKillingSpree?: number
  legendaryCount?: number
  // 符文
  perk0?: number
  perk1?: number
  perk2?: number
  perk3?: number
  perk4?: number
  perk5?: number
  perkPrimaryStyle?: number
  perkSubStyle?: number
  perks?: Record<string, unknown>
  // 补兵（别名）
  minionsKilled?: number
  // 对塔伤害
  damageDealtToTurrets?: number
  turretKills?: number
  inhibitorKills?: number
  turretPlatesTaken?: number
  turretTakedowns?: number
  inhibitorTakedowns?: number
  // 海克斯强化
  playerAugment1?: number
  playerAugment2?: number
  playerAugment3?: number
  playerAugment4?: number
  playerAugment5?: number
  playerAugment6?: number
  challenges?: Record<string, unknown>
  extraFields?: Record<string, unknown>
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

export interface CacheUpdateEvent {
  type: 'PLAYER_CACHE_UPDATED'
  puuid: string
  reason?: string
  updatedScopes?: string[]
  timestamp?: number
}

export interface CacheStatus {
  enabled: boolean
  databasePath: string
  databaseSizeBytes: number
  summonerCount: number
  rankCount: number
  matchCount: number
  gameDetailCount: number
  participantCount: number
  playerMatchIndexCount: number
  trackedPlayerCount: number
  latestMatchCreation: number | null
}

export interface UserStoreStatus {
  enabled: boolean
  path: string
  sizeBytes: number
  updatedAt: number | null
  tagConfigCount: number
}

export type CacheClearScope = 'all' | 'memory' | 'localDb'

export interface CacheClearResult {
  cleared: boolean
  scope: CacheClearScope
  message: string
  deletedRows: number
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

// 配置
export interface AppConfig {
  settings: {
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
  good?: boolean | null
  tagName: string
  tagDesc?: string
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

// ========== 对局详情 ==========

export type DragonType = 'infernal' | 'mountain' | 'ocean' | 'cloud' | 'hextech' | 'chemtech' | 'unknown'
export type ObjectiveEventKind =
  | 'turret'
  | 'turretPlate'
  | 'inhibitor'
  | 'baron'
  | 'dragon'
  | 'elderDragon'
  | 'herald'
  | 'voidGrub'

export interface TeamBanSummary {
  teamId: number
  bans: number[]
}

export interface TeamObjectiveEvent {
  kind: ObjectiveEventKind
  subType?: DragonType | string | null
  teamId?: number | null
  participantId?: number | null
  championId?: number | null
  timestamp?: number | null
}

export interface TeamObjectiveSummary {
  teamId: number
  bans?: number[]
  turretKills?: number
  turretPlateKills?: number
  turretPlatesTaken?: number
  inhibitorKills?: number
  baronKills?: number
  dragonKills?: number
  elderDragonKills?: number
  dragonKillsByType?: Partial<Record<DragonType, number>>
  heraldKills?: number
  voidGrubKills?: number
  dragonSoulType?: DragonType | null
  objectiveEvents?: TeamObjectiveEvent[]
}

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
  teamObjectives?: TeamObjectiveSummary[]
  teamBans?: TeamBanSummary[]
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
  teamPosition?: string
  individualPosition?: string
  selectedPosition?: string
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
  goldSpent?: number
  totalDamageDealtToChampions: number
  magicDamageDealtToChampions?: number
  physicalDamageDealtToChampions?: number
  trueDamageDealtToChampions?: number
  totalDamageTaken: number
  totalHeal: number
  visionScore?: number
  detectorWardsPlaced?: number
  visionWardsBoughtInGame: number
  wardsPlaced: number
  wardsKilled: number
  largestMultiKill: number
  perks?: Record<string, unknown>
  challenges?: Record<string, unknown>
  extraFields?: Record<string, unknown>
  doubleKills: number
  tripleKills: number
  quadraKills: number
  pentaKills: number
  largestKillingSpree?: number
  legendaryCount?: number
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
  turretKills?: number
  inhibitorKills?: number
  turretPlatesTaken?: number
  turretTakedowns?: number
  inhibitorTakedowns?: number
  // MVP/SVP
  mvp?: string
  // 伤害占比
  damageDealtToChampionsRate?: number
  damageTakenRate?: number
  healRate?: number
  item0?: number
  item1?: number
  item2?: number
  item3?: number
  item4?: number
  item5?: number
  item6?: number
}

export interface GameTimeline {
  lane: string
  role: string
  teamPosition?: string
  positionCn?: string
  rawLane?: string
  rawRole?: string
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
  userTag?: UserTag | null
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
  source?: string
  simulatorPhase?: string
  roundIndex?: number
  matchId?: string
  step?: number
  currentSummoner?: Summoner
  lobby?: Lobby | null
  teammates?: SessionSummoner[]
  opponents?: SessionSummoner[]
  championSelect?: Record<string, unknown> | null
  loadingScreen?: Record<string, unknown> | null
  endOfGame?: Record<string, unknown> | null
  matchSummary?: Record<string, unknown> | null
}
