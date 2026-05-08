<script setup lang="ts">
import { computed, ref } from 'vue'
import AssetHoverTooltip from '@/components/common/AssetHoverTooltip.vue'
import { useI18n } from '@/i18n'
import type {
  DragonType,
  GameDetail,
  GameParticipant,
  GameParticipantIdentity,
  GameStats,
  GameTimeline,
  MatchHistory,
  TeamBanSummary,
  TeamObjectiveEvent,
  TeamObjectiveSummary
} from '@/types/api'
import {
  getAugmentAssetDetails,
  getAugmentIconUrl,
  getAugmentRarityClass,
  getAugmentTooltipDetails,
  getChampionIconUrl,
  getItemAssetDetails,
  getItemIconSlots,
  getItemTooltipDetails,
  getObjectiveIconUrl,
  getPerkAssetDetails,
  getPerkIconUrl,
  getPerkTooltipDetails,
  getSummonerSpellIconUrl,
  getSummonerSpellTooltipDetails,
  markAssetLoadFailed,
  normalizeRiotTooltipText,
  type GameAssetTooltipDetails,
  type ItemIconSlot,
  type ObjectiveIconKind
} from '@/utils/gameAssetUrls'
import {
  formatNumber,
  getCreepScore,
  getTeamParticipants,
  sumTeamStats,
  type MatchDetailParticipant,
  type TeamStatsSummary
} from '@/utils/matchDetailMetrics'

export type InlineDetailTabKey = 'overview' | 'runes' | 'chart'

type DetailLoadStatus = 'idle' | 'loading' | 'loaded' | 'error'
type TeamTone = 'blue' | 'red'
type TraitKind = 'perk' | 'augment'

interface SpellSlot {
  key: string
  id: number | null
  url: string
  empty: boolean
}

interface TraitSlot {
  key: string
  kind: TraitKind
  id: number | null
  url: string
  empty: boolean
  label: string
  rarityClass?: string
}

interface TeamSection {
  key: TeamTone
  teamId: number
  label: string
  result: string
  won: boolean
  players: MatchDetailParticipant[]
  totals: TeamStatsSummary
}

interface ObjectiveDisplayIcon {
  key: string
  kind: ObjectiveIconKind
  label: string
  iconUrl: string
  timestamp: number | null
}

interface ObjectiveDisplayItem {
  key: string
  kind: ObjectiveIconKind
  label: string
  count: number | null
  showCount: boolean
  title: string
  iconUrl: string
  icons?: ObjectiveDisplayIcon[]
  tooltipGroups: ObjectiveTooltipGroup[]
}

interface ObjectiveTooltipGroup {
  key: string
  championId: number
  count: number
  label: string
}

interface ObjectiveEventDescriptor {
  kind: TeamObjectiveEvent['kind']
  dragonType?: DragonType
}

type StructureObjectiveSourceKey = 'turret' | 'inhibitor' | 'turretPlate'
type StructureObjectiveSummaryKey = 'turretKills' | 'inhibitorKills' | 'turretPlateKills' | 'turretPlatesTaken'

interface StructureObjectiveSource {
  summaryKey: StructureObjectiveSummaryKey
  summaryKeys?: StructureObjectiveSummaryKey[]
  eventKind: TeamObjectiveEvent['kind']
  directStatKeys: string[]
  lastFallbackStatKeys: string[]
}

interface TimestampedDragonObjectiveEvent {
  event: TeamObjectiveEvent
  timestamp: number
  index: number
  dragonType: DragonType
}

const LANE_BASED_QUEUE_IDS = new Set([400, 420, 430, 440, 490, 700])
const NON_LANE_BASED_QUEUE_IDS = new Set([450, 900, 1020, 1700, 1710])
const LANE_BASED_GAME_MODES = new Set(['CLASSIC'])
const NON_LANE_BASED_GAME_MODES = new Set(['ARAM', 'CHERRY'])
const RANKED_QUEUE_IDS = new Set([420, 440])
const LANE_BASED_QUEUE_KEYWORDS = [
  '召唤师峡谷',
  '单排',
  '双排',
  '灵活',
  '匹配',
  'RANKED_SOLO_5x5',
  'RANKED_FLEX_SR',
  'CLASSIC',
  'SUMMONER'
]
const NON_LANE_BASED_QUEUE_KEYWORDS = [
  '大乱斗',
  '极地',
  '海克斯大乱斗',
  '斗魂',
  '竞技场',
  '无限火力',
  '克隆',
  'ARAM',
  'CHERRY',
  'ARENA',
  'URF',
  'ONE FOR ALL'
]
const RANKED_QUEUE_KEYWORDS = [
  '排位',
  '单排',
  '双排',
  '灵活',
  'RANKED',
  'SOLO',
  'FLEX'
]
const DRAGON_GROUP_LABEL = '小龙'
const DRAGON_TYPE_ORDER: DragonType[] = ['infernal', 'mountain', 'ocean', 'cloud', 'hextech', 'chemtech']
const DRAGON_TYPE_ALIASES: Record<Exclude<DragonType, 'unknown'>, string[]> = {
  infernal: ['infernal', 'fire', 'fire_dragon', 'FIRE_DRAGON', 'INFERNAL_DRAGON'],
  ocean: ['ocean', 'water', 'water_dragon', 'WATER_DRAGON'],
  mountain: ['mountain', 'earth', 'earth_dragon', 'EARTH_DRAGON', 'MOUNTAIN_DRAGON'],
  cloud: ['cloud', 'air', 'air_dragon', 'AIR_DRAGON', 'CLOUD_DRAGON'],
  hextech: ['hextech', 'hextech_dragon', 'HEXTECH_DRAGON'],
  chemtech: ['chemtech', 'chemtech_dragon', 'CHEMTECH_DRAGON']
}
const DRAGON_TYPE_LABELS: Record<DragonType, string> = {
  infernal: '炼狱龙',
  mountain: '山脉龙',
  ocean: '海洋龙',
  cloud: '云端龙',
  hextech: '海克斯龙',
  chemtech: '炼金龙',
  unknown: '小龙'
}
const STRUCTURE_OBJECTIVE_SOURCES: Record<StructureObjectiveSourceKey, StructureObjectiveSource> = {
  turret: {
    summaryKey: 'turretKills',
    eventKind: 'turret',
    directStatKeys: ['turretKills'],
    lastFallbackStatKeys: ['turretTakedowns']
  },
  inhibitor: {
    summaryKey: 'inhibitorKills',
    eventKind: 'inhibitor',
    directStatKeys: ['inhibitorKills'],
    lastFallbackStatKeys: ['inhibitorTakedowns']
  },
  turretPlate: {
    summaryKey: 'turretPlateKills',
    summaryKeys: ['turretPlateKills', 'turretPlatesTaken'],
    eventKind: 'turretPlate',
    directStatKeys: ['turretPlatesTaken'],
    lastFallbackStatKeys: []
  }
}

const props = withDefaults(defineProps<{
  matchHistory: MatchHistory
  gameDetail: GameDetail | null
  currentPuuid: string
  currentSummonerName: string
  detailStatus?: DetailLoadStatus
  activeTab?: InlineDetailTabKey
}>(), {
  detailStatus: 'idle',
  activeTab: 'overview'
})

const emit = defineEmits<{
  'update:activeTab': [tab: InlineDetailTabKey]
  navigateToPlayer: [gameName: string, tagLine: string]
}>()

const { t } = useI18n()

const activeTabValue = computed<InlineDetailTabKey>({
  get: () => props.activeTab,
  set: tab => emit('update:activeTab', tab)
})

const fallbackGameDetail = computed<GameDetail | null>(() => toGameDetailFromMatchHistory(props.matchHistory))
const displayGameDetail = computed<GameDetail | null>(() => {
  const detail = isRenderableGameDetail(props.gameDetail) ? props.gameDetail : null
  return detail ? mergeGameDetailWithSummary(detail, fallbackGameDetail.value) : fallbackGameDetail.value
})
const blueTeamPlayers = computed(() => getTeamParticipants(displayGameDetail.value, 100, props.currentPuuid))
const redTeamPlayers = computed(() => getTeamParticipants(displayGameDetail.value, 200, props.currentPuuid))
const allPlayers = computed(() => [...blueTeamPlayers.value, ...redTeamPlayers.value])
const blueTeamTotals = computed(() => sumTeamStats(blueTeamPlayers.value))
const redTeamTotals = computed(() => sumTeamStats(redTeamPlayers.value))
const maxChampionDamage = computed(() => maxPlayerMetric(player => readStatNumber(player, 'totalDamageDealtToChampions')))
const maxDamageTaken = computed(() => maxPlayerMetric(player => readStatNumber(player, 'totalDamageTaken')))
const topKillValue = computed(() => getTopMetricValue(allPlayers.value, player => readStatNumber(player, 'kills')))
const topDeathValue = computed(() => getTopMetricValue(allPlayers.value, player => readStatNumber(player, 'deaths')))
const topAssistValue = computed(() => getTopMetricValue(allPlayers.value, player => readStatNumber(player, 'assists')))
const topDamageValue = computed(() => getTopMetricValue(allPlayers.value, player => readStatNumber(player, 'totalDamageDealtToChampions')))
const topTakenValue = computed(() => getTopMetricValue(allPlayers.value, player => readStatNumber(player, 'totalDamageTaken')))
const topGoldValue = computed(() => getTopMetricValue(allPlayers.value, player => readStatNumber(player, 'goldEarned')))
const showVisionScoreColumn = computed(() => isRankedMode(props.matchHistory) || isRankedMode(displayGameDetail.value))
const showDraftAndObjectiveSummary = computed(() => isRankedMode(props.matchHistory) || isRankedMode(displayGameDetail.value))
const hasTimelineData = computed(() => false)
const staticTeamGoldDiff = computed(() => blueTeamTotals.value.goldEarned - redTeamTotals.value.goldEarned)
const failedObjectiveIconKeys = ref(new Set<string>())

const detailTabs = computed<Array<{ key: InlineDetailTabKey; label: string }>>(() => [
  { key: 'overview', label: t('matchDetail.overviewTab') },
  { key: 'runes', label: t('matchDetail.runesTab') },
  { key: 'chart', label: t('matchDetail.chartTab') }
])

const teamSections = computed<TeamSection[]>(() => [
  createTeamSection('blue', 100, t('common.blueTeam'), blueTeamPlayers.value, blueTeamTotals.value),
  createTeamSection('red', 200, t('common.redTeam'), redTeamPlayers.value, redTeamTotals.value)
])

const detailNotice = computed(() => {
  if (props.detailStatus === 'loading' && !isRenderableGameDetail(props.gameDetail)) {
    return t('matchDetail.loadingFallback')
  }
  if (props.detailStatus === 'error') {
    return t('matchDetail.failedFallback')
  }
  return ''
})

function selectTab(tab: InlineDetailTabKey): void {
  activeTabValue.value = tab
}

function createTeamSection(
  key: TeamTone,
  teamId: number,
  label: string,
  players: MatchDetailParticipant[],
  totals: TeamStatsSummary
): TeamSection {
  const won = Boolean(players[0]?.stats?.win)
  return {
    key,
    teamId,
    label,
    result: won ? t('common.win') : t('common.loss'),
    won,
    players,
    totals
  }
}

function getPlayerName(player: MatchDetailParticipant): string {
  return player.displayName || `${t('common.unknownPlayer')} ${player.participantId}`
}

function canNavigatePlayer(player: MatchDetailParticipant): boolean {
  return Boolean(player.gameName && player.tagLine)
}

function handlePlayerClick(player: MatchDetailParticipant): void {
  if (canNavigatePlayer(player)) {
    emit('navigateToPlayer', player.gameName, player.tagLine)
  }
}

function getPlayerSpellSlots(player: MatchDetailParticipant): SpellSlot[] {
  return [player.spell1Id, player.spell2Id].map((spellId, index) => {
    const id = normalizePositiveInteger(spellId)
    const url = getSummonerSpellIconUrl(id)
    return {
      key: `spell-${index}-${id || 'empty'}`,
      id,
      url,
      empty: id === null || !url
    }
  })
}

function getPlayerItemSlots(player: MatchDetailParticipant): ItemIconSlot[] {
  return getItemIconSlots(player.stats)
}

function getItemSlotLabel(slot: ItemIconSlot): string {
  if (slot.empty || slot.itemId === null) {
    return t('matchDetail.emptyItemSlot')
  }
  const details = getItemAssetDetails(slot.itemId)
  return details?.name ? `${details.name} (${slot.itemId})` : `${t('matchDetail.itemLabel')} ${slot.itemId}`
}

function getPlayerTraitSlots(player: MatchDetailParticipant): TraitSlot[] {
  return hasValidAugment(player) ? getAugmentTraitSlots(player) : getPerkTraitSlots(player)
}

function getPerkTraitSlots(player: MatchDetailParticipant): TraitSlot[] {
  const primaryId = readTraitId(player, 'perk0')
  const secondaryId = readTraitId(player, 'perkSubStyle') ||
    readTraitId(player, 'perkPrimaryStyle') ||
    readTraitId(player, 'perk5')
  const coreSlots = [
    createTraitSlot('perk', 'perk0', primaryId),
    createTraitSlot('perk', 'perkSubStyle', secondaryId)
  ]
  const minorSlots = ['perk1', 'perk2', 'perk3', 'perk4', 'perk5']
    .map(key => createTraitSlot('perk', key, readTraitId(player, key)))
    .filter(slot => slot.id !== null)

  return [...coreSlots, ...minorSlots]
}

function getAugmentTraitSlots(player: MatchDetailParticipant): TraitSlot[] {
  return ['playerAugment1', 'playerAugment2', 'playerAugment3', 'playerAugment4', 'playerAugment5', 'playerAugment6']
    .map(key => createTraitSlot('augment', key, readTraitId(player, key)))
}

function getPlayerOverviewAugmentSlots(player: MatchDetailParticipant): TraitSlot[] {
  return getAugmentTraitSlots(player).filter(slot => !slot.empty && slot.id !== null)
}

function createTraitSlot(kind: TraitKind, key: string, id: number | null): TraitSlot {
  const url = id === null
    ? ''
    : kind === 'augment'
      ? getAugmentIconUrl(id)
      : getPerkIconUrl(id)
  return {
    key: `${kind}-${key}-${id || 'empty'}`,
    kind,
    id,
    url,
    empty: id === null || !url,
    label: getTraitSlotLabel(kind, id),
    rarityClass: getTraitRarityClass(kind, id)
  }
}

function getTraitRarityClass(kind: TraitKind, id: number | null): string {
  if (kind !== 'augment' || id === null) {
    return ''
  }

  return getAugmentRarityClass(getAugmentAssetDetails(id)?.rarity)
}

function hasValidAugment(player: MatchDetailParticipant): boolean {
  return ['playerAugment1', 'playerAugment2', 'playerAugment3', 'playerAugment4', 'playerAugment5', 'playerAugment6']
    .some(key => readTraitId(player, key) !== null)
}

function readTraitId(player: MatchDetailParticipant, key: string): number | null {
  return normalizePositiveInteger(readStatNumber(player, key))
}

function getTraitSlotLabel(kind: TraitKind, id: number | null): string {
  if (id === null) {
    return kind === 'augment' ? t('matchDetail.emptyAugmentSlot') : t('matchDetail.emptyRuneSlot')
  }

  const details = kind === 'augment' ? getAugmentAssetDetails(id) : getPerkAssetDetails(id)
  const fallback = kind === 'augment' ? t('matchDetail.augmentLabel') : t('matchDetail.runeLabel')
  const name = details?.name || `${fallback} ${id}`
  const description = normalizeRiotTooltipText(details?.description || details?.tooltip || details?.shortDesc || details?.longDesc || details?.plaintext || '')
  return description ? `${name} (${id}) - ${description}` : `${name} (${id})`
}

function getTraitTooltipDetails(slot: TraitSlot): GameAssetTooltipDetails | null {
  if (slot.empty || slot.id === null) {
    return null
  }

  return slot.kind === 'augment'
    ? getAugmentTooltipDetails(slot.id)
    : getPerkTooltipDetails(slot.id)
}

function isLaneBasedMode(match: MatchHistory | GameDetail | null | undefined): boolean {
  if (!match) {
    return false
  }

  const queueId = normalizeFiniteNumber(match.queueId)
  const gameMode = normalizeModeText(match.gameMode)
  const queueName = normalizeModeText(getQueueName(match))
  const modeText = `${queueName} ${gameMode}`.trim()

  if (queueId !== null && NON_LANE_BASED_QUEUE_IDS.has(queueId)) {
    return false
  }
  if (NON_LANE_BASED_GAME_MODES.has(gameMode)) {
    return false
  }
  if (containsModeKeyword(modeText, NON_LANE_BASED_QUEUE_KEYWORDS)) {
    return false
  }
  if (queueId !== null && LANE_BASED_QUEUE_IDS.has(queueId)) {
    return true
  }
  if (LANE_BASED_GAME_MODES.has(gameMode)) {
    return true
  }
  return containsModeKeyword(modeText, LANE_BASED_QUEUE_KEYWORDS)
}

function isRankedMode(match: MatchHistory | GameDetail | null | undefined): boolean {
  if (!match) {
    return false
  }

  const queueId = normalizeFiniteNumber(match.queueId)
  const queueName = normalizeModeText(getQueueName(match))
  if (queueId !== null && RANKED_QUEUE_IDS.has(queueId)) {
    return true
  }
  if (containsModeKeyword(queueName, RANKED_QUEUE_KEYWORDS)) {
    return true
  }
  return false
}

function getQueueName(match: MatchHistory | GameDetail): string {
  const queueName = 'queueName' in match ? match.queueName : ''
  return typeof queueName === 'string' ? queueName : ''
}

function normalizeModeText(value: unknown): string {
  return typeof value === 'string' ? value.trim().toUpperCase() : ''
}

function containsModeKeyword(value: string, keywords: string[]): boolean {
  return keywords.some(keyword => value.includes(keyword.toUpperCase()))
}

function getDisplayPosition(player: MatchDetailParticipant): string {
  if (!isLaneBasedMode(displayGameDetail.value || props.matchHistory)) {
    return ''
  }
  return getPositionLabel(player)
}

function getPositionLabel(player: MatchDetailParticipant): string {
  return player.timeline?.positionCn ||
    player.teamPosition ||
    player.timeline?.teamPosition ||
    player.individualPosition ||
    player.selectedPosition ||
    ''
}

function getChampionLevel(player: MatchDetailParticipant): number | null {
  return normalizePositiveInteger(
    readStatNumber(player, 'champLevel') ??
    readStatNumber(player, 'championLevel') ??
    readStatNumber(player, 'level')
  )
}

function getPlayerKills(player: MatchDetailParticipant): number {
  return readStatNumber(player, 'kills') ?? 0
}

function getPlayerDeaths(player: MatchDetailParticipant): number {
  return readStatNumber(player, 'deaths') ?? 0
}

function getPlayerAssists(player: MatchDetailParticipant): number {
  return readStatNumber(player, 'assists') ?? 0
}

function getTeamKda(totals: TeamStatsSummary): string {
  return `${totals.kills}/${totals.deaths}/${totals.assists}`
}

function getTeamBans(teamId: number): number[] {
  const summaryBans = getTeamObjectiveSummary(teamId)?.bans || []
  const standaloneBans = getTeamBanSummary(teamId)?.bans || []
  const source = summaryBans.length ? summaryBans : standaloneBans
  return source
    .map(championId => normalizePositiveInteger(championId))
    .filter((championId): championId is number => championId !== null)
    .slice(0, 5)
}

function getTeamObjectiveItems(teamId: number): ObjectiveDisplayItem[] {
  const summary = getTeamObjectiveSummary(teamId)
  if (!summary) {
    return []
  }
  return buildObjectiveDisplayItems(teamId, summary)
}

function buildObjectiveDisplayItems(teamId: number, summary: TeamObjectiveSummary): ObjectiveDisplayItem[] {
  const items: ObjectiveDisplayItem[] = []
  addStructureObjectiveItem(items, teamId, 'turret', 'turret', '塔', readStructureObjectiveCount(teamId, summary, 'turret'))
  addStructureObjectiveItem(items, teamId, 'inhibitor', 'inhibitor', '水晶', readStructureObjectiveCount(teamId, summary, 'inhibitor'))
  addStructureObjectiveItem(items, teamId, 'turret-plate', 'turretPlate', '镀层', readStructureObjectiveCount(teamId, summary, 'turretPlate'))
  addObjectiveItem(items, teamId, 'baron', 'baron', '男爵', readObjectiveCount(summary.baronKills))
  addObjectiveItem(items, teamId, 'elder', 'elder', '远古龙', readObjectiveCount(summary.elderDragonKills))
  addDragonObjectiveItems(items, teamId, summary)
  addObjectiveItem(items, teamId, 'herald', 'herald', '先锋', readObjectiveCount(summary.heraldKills))
  addObjectiveItem(items, teamId, 'voidgrub', 'voidgrub', '虚空巢虫', readObjectiveCount(summary.voidGrubKills))
  return items
}

function getObjectiveItemIcons(item: ObjectiveDisplayItem): ObjectiveDisplayIcon[] {
  if (item.icons?.length) {
    return item.icons
  }
  return [createObjectiveDisplayIcon(item.key, item.kind, item.label, null)]
}

function shouldUseObjectiveIconImage(icon: ObjectiveDisplayIcon): boolean {
  return Boolean(icon.iconUrl) && !failedObjectiveIconKeys.value.has(icon.key)
}

function getObjectiveCountText(item: ObjectiveDisplayItem): string {
  return item.count === null ? '--' : String(item.count)
}

function formatObjectiveTitle(label: string, count: number | null): string {
  return count === null ? `${label} --` : `${label} x${count}`
}

function handleObjectiveIconLoadFailed(event: Event, key: string): void {
  markAssetLoadFailed(event)
  failedObjectiveIconKeys.value = new Set([...failedObjectiveIconKeys.value, key])
}

function getTeamObjectiveSummary(teamId: number): TeamObjectiveSummary | null {
  return (displayGameDetail.value?.teamObjectives || [])
    .find(summary => normalizeTeamId(summary?.teamId) === teamId) || null
}

function getTeamBanSummary(teamId: number): TeamBanSummary | null {
  return (displayGameDetail.value?.teamBans || [])
    .find(summary => normalizeTeamId(summary?.teamId) === teamId) || null
}

function readStructureObjectiveCount(teamId: number, summary: TeamObjectiveSummary, sourceKey: StructureObjectiveSourceKey): number | null {
  const source = STRUCTURE_OBJECTIVE_SOURCES[sourceKey]
  const summaryCount = readStructureSummaryObjectiveCount(summary, source)
  const directStatCount = sumTeamParticipantObjectiveStats(teamId, source.directStatKeys)
  const lastFallbackStatCount = sumTeamParticipantObjectiveStats(teamId, source.lastFallbackStatKeys)
  const eventCount = countObjectiveEvents(summary, teamId, source.eventKind)

  if (sourceKey === 'turretPlate') {
    if (summaryCount !== null && summaryCount > 0) {
      return summaryCount
    }
    if (directStatCount !== null && directStatCount > 0) {
      return directStatCount
    }
    if (eventCount !== null && eventCount > 0) {
      return eventCount
    }
    if (lastFallbackStatCount !== null && lastFallbackStatCount > 0) {
      return lastFallbackStatCount
    }
    if (summaryCount !== null) {
      return summaryCount
    }
    if (directStatCount !== null) {
      return directStatCount
    }
    if (eventCount !== null) {
      return eventCount
    }
    if (lastFallbackStatCount !== null) {
      return lastFallbackStatCount
    }
    return null
  }

  if (eventCount !== null && eventCount > 0) {
    return eventCount
  }

  if (directStatCount !== null && directStatCount > 0) {
    return directStatCount
  }

  if (lastFallbackStatCount !== null && lastFallbackStatCount > 0) {
    return lastFallbackStatCount
  }

  if (summaryCount !== null && summaryCount > 0) {
    return summaryCount
  }
  if (directStatCount !== null) {
    return directStatCount
  }
  if (eventCount !== null && summaryCount !== null) {
    return eventCount
  }
  if (lastFallbackStatCount !== null) {
    return lastFallbackStatCount
  }
  if (sourceKey !== 'turretPlate' && summaryCount !== null) {
    return summaryCount
  }
  return null
}

function readStructureSummaryObjectiveCount(summary: TeamObjectiveSummary, source: StructureObjectiveSource): number | null {
  const keys = source.summaryKeys ?? [source.summaryKey]
  let knownZeroCount: number | null = null
  for (const key of keys) {
    const count = readNullableObjectiveCount(summary[key])
    if (count !== null && count > 0) {
      return count
    }
    if (count !== null && knownZeroCount === null) {
      knownZeroCount = count
    }
  }
  return knownZeroCount
}

function countObjectiveEvents(summary: TeamObjectiveSummary, teamId: number, kind: TeamObjectiveEvent['kind']): number | null {
  if (!Array.isArray(summary.objectiveEvents)) {
    return null
  }
  return summary.objectiveEvents
    .filter(event => matchesObjectiveEvent(event, { kind }, teamId))
    .length
}

function sumTeamParticipantObjectiveStats(teamId: number, fieldKeys: string[]): number | null {
  if (!fieldKeys.length) {
    return null
  }
  let total = 0
  let hasKnownValue = false
  for (const player of allPlayers.value) {
    if (normalizeTeamId(player.teamId) !== teamId) {
      continue
    }
    const value = readParticipantObjectiveStat(player, fieldKeys)
    if (value === null) {
      continue
    }
    hasKnownValue = true
    total += value
  }
  return hasKnownValue ? total : null
}

function readParticipantObjectiveStat(player: MatchDetailParticipant, fieldKeys: string[]): number | null {
  for (const key of fieldKeys) {
    const value = readParticipantObjectiveField(player, key)
    if (value !== null) {
      return Math.max(0, Math.floor(value))
    }
  }
  return null
}

function readParticipantObjectiveField(player: MatchDetailParticipant, key: string): number | null {
  const statsValue = readStatNumber(player, key)
  if (statsValue !== null && statsValue > 0) {
    return statsValue
  }
  const challenges = player.stats?.challenges as Record<string, unknown> | null | undefined
  const challengeValue = normalizeFiniteNumber(challenges?.[key])
  return challengeValue !== null ? challengeValue : statsValue
}

function addStructureObjectiveItem(
  items: ObjectiveDisplayItem[],
  teamId: number,
  key: string,
  kind: ObjectiveIconKind,
  label: string,
  count: number | null
): void {
  items.push({
    key: `${teamId}-${key}`,
    kind,
    label,
    count,
    showCount: true,
    title: formatObjectiveTitle(label, count),
    iconUrl: getObjectiveIconUrl(kind),
    tooltipGroups: getObjectiveTooltipGroups(teamId, key, label)
  })
}

function addDragonObjectiveItems(items: ObjectiveDisplayItem[], teamId: number, summary: TeamObjectiveSummary): void {
  const icons = getDragonTimelineObjectiveIcons(teamId, summary)
  const dragonIcons = icons.length ? icons : getFallbackDragonObjectiveIcons(teamId, summary)
  addDragonObjectiveItem(items, teamId, dragonIcons)
}

function getDragonTimelineObjectiveIcons(teamId: number, summary: TeamObjectiveSummary): ObjectiveDisplayIcon[] {
  const events = getSortedTimestampedDragonEvents(summary, teamId)
  const totalDragonKills = readObjectiveCount(summary.dragonKills)
  if (totalDragonKills > 0 && events.length < totalDragonKills) {
    return []
  }
  return events.map((entry, timelineIndex) => {
    const kind = getObjectiveIconKindForDragonType(entry.dragonType)
    const label = DRAGON_TYPE_LABELS[entry.dragonType]
    return createObjectiveDisplayIcon(
      `${teamId}-dragon-timeline-${timelineIndex}-${entry.dragonType}-${entry.timestamp}`,
      kind,
      label,
      entry.timestamp
    )
  })
}

function getFallbackDragonObjectiveIcons(teamId: number, summary: TeamObjectiveSummary): ObjectiveDisplayIcon[] {
  const icons: ObjectiveDisplayIcon[] = []
  const dragonKillsByType = normalizeDragonKillsByType(summary.dragonKillsByType)
  let fallbackIndex = 0
  for (const dragonType of DRAGON_TYPE_ORDER) {
    const count = dragonKillsByType[dragonType] || 0
    for (let repeatIndex = 0; repeatIndex < count; repeatIndex += 1) {
      const kind = getObjectiveIconKindForDragonType(dragonType)
      icons.push(createObjectiveDisplayIcon(
        `${teamId}-dragon-fallback-${fallbackIndex}-${dragonType}`,
        kind,
        DRAGON_TYPE_LABELS[dragonType],
        null
      ))
      fallbackIndex += 1
    }
  }
  if (icons.length) {
    return icons
  }

  const totalDragonKills = readObjectiveCount(summary.dragonKills)
  for (let index = 0; index < totalDragonKills; index += 1) {
    icons.push(createObjectiveDisplayIcon(
      `${teamId}-dragon-fallback-${index}`,
      'dragon',
      DRAGON_GROUP_LABEL,
      null
    ))
  }
  return icons
}

function addDragonObjectiveItem(items: ObjectiveDisplayItem[], teamId: number, icons: ObjectiveDisplayIcon[]): void {
  if (!icons.length) {
    return
  }
  items.push({
    key: `${teamId}-dragon`,
    kind: 'dragon',
    label: DRAGON_GROUP_LABEL,
    count: null,
    showCount: false,
    title: `${DRAGON_GROUP_LABEL}：${icons.map(icon => icon.label).join(' / ')}`,
    iconUrl: getObjectiveIconUrl('dragon'),
    icons,
    tooltipGroups: getObjectiveTooltipGroups(teamId, 'dragon', DRAGON_GROUP_LABEL)
  })
}

function createObjectiveDisplayIcon(
  key: string,
  kind: ObjectiveIconKind,
  label: string,
  timestamp: number | null
): ObjectiveDisplayIcon {
  return {
    key,
    kind,
    label,
    iconUrl: getObjectiveIconUrl(kind),
    timestamp
  }
}

function getSortedTimestampedDragonEvents(summary: TeamObjectiveSummary, teamId: number): TimestampedDragonObjectiveEvent[] {
  return (summary.objectiveEvents || [])
    .map((event, index): TimestampedDragonObjectiveEvent | null => {
      if (event.kind !== 'dragon') {
        return null
      }
      const timestamp = normalizeFiniteNumber(event.timestamp)
      if (timestamp === null || !matchesObjectiveEvent(event, { kind: 'dragon' }, teamId)) {
        return null
      }
      return {
        event,
        timestamp,
        index,
        dragonType: normalizeDragonTypeKey(event.subType)
      }
    })
    .filter((entry): entry is TimestampedDragonObjectiveEvent => entry !== null)
    .sort((left, right) => left.timestamp - right.timestamp || left.index - right.index)
}

function addObjectiveItem(
  items: ObjectiveDisplayItem[],
  teamId: number,
  key: string,
  kind: ObjectiveIconKind,
  label: string,
  count: number,
  title = formatObjectiveTitle(label, count)
): void {
  if (count <= 0) {
    return
  }
  items.push({
    key: `${teamId}-${key}`,
    kind,
    label,
    count,
    showCount: true,
    title,
    iconUrl: getObjectiveIconUrl(kind),
    tooltipGroups: getObjectiveTooltipGroups(teamId, key, label)
  })
}

function getObjectiveTooltipGroups(teamId: number, itemKey: string, itemLabel: string): ObjectiveTooltipGroup[] {
  const summary = getTeamObjectiveSummary(teamId)
  const descriptor = getObjectiveEventDescriptor(itemKey)
  const groups = new Map<number, ObjectiveTooltipGroup>()
  if (summary && descriptor && summary.objectiveEvents?.length) {
    for (const event of summary.objectiveEvents) {
      if (!matchesObjectiveEvent(event, descriptor, teamId)) {
        continue
      }
      const championId = getObjectiveEventChampionId(event)
      if (championId === null) {
        continue
      }
      const existing = groups.get(championId)
      if (existing) {
        existing.count += 1
        continue
      }
      groups.set(championId, {
        key: `${teamId}-${itemKey}-${championId}`,
        championId,
        count: 1,
        label: itemLabel
      })
    }
  }
  const eventGroups = Array.from(groups.values())
  return eventGroups.length ? eventGroups : getParticipantObjectiveTooltipGroups(teamId, itemKey, itemLabel)
}

function getParticipantObjectiveTooltipGroups(teamId: number, itemKey: string, itemLabel: string): ObjectiveTooltipGroup[] {
  const fieldKeys = getObjectiveParticipantStatKeys(itemKey)
  if (!fieldKeys.length) {
    return []
  }
  return allPlayers.value
    .filter(player => normalizeTeamId(player.teamId) === teamId)
    .map(player => {
      const count = readParticipantObjectiveStat(player, fieldKeys)
      const championId = normalizePositiveInteger(player.championId)
      if (count === null || count <= 0 || championId === null) {
        return null
      }
      return {
        key: `${teamId}-${itemKey}-${championId}`,
        championId,
        count,
        label: itemLabel
      }
    })
    .filter((group): group is ObjectiveTooltipGroup => group !== null)
}

function getObjectiveParticipantStatKeys(itemKey: string): string[] {
  const sourceKey = getStructureObjectiveSourceKey(itemKey)
  if (!sourceKey) {
    return []
  }
  const source = STRUCTURE_OBJECTIVE_SOURCES[sourceKey]
  return [...source.directStatKeys, ...source.lastFallbackStatKeys]
}

function getStructureObjectiveSourceKey(itemKey: string): StructureObjectiveSourceKey | null {
  if (itemKey === 'turret') {
    return 'turret'
  }
  if (itemKey === 'inhibitor') {
    return 'inhibitor'
  }
  if (itemKey === 'turret-plate') {
    return 'turretPlate'
  }
  return null
}

function getObjectiveEventDescriptor(itemKey: string): ObjectiveEventDescriptor | null {
  if (itemKey === 'turret') {
    return { kind: 'turret' }
  }
  if (itemKey === 'turret-plate') {
    return { kind: 'turretPlate' }
  }
  if (itemKey === 'inhibitor') {
    return { kind: 'inhibitor' }
  }
  if (itemKey === 'baron') {
    return { kind: 'baron' }
  }
  if (itemKey === 'herald') {
    return { kind: 'herald' }
  }
  if (itemKey === 'voidgrub') {
    return { kind: 'voidGrub' }
  }
  if (itemKey === 'elder') {
    return { kind: 'elderDragon' }
  }
  if (itemKey === 'dragon') {
    return { kind: 'dragon' }
  }
  if (itemKey.startsWith('dragon-')) {
    const dragonType = normalizeDragonTypeKey(itemKey.slice('dragon-'.length))
    return dragonType === 'unknown' ? null : { kind: 'dragon', dragonType }
  }
  return null
}

function matchesObjectiveEvent(
  event: TeamObjectiveEvent,
  descriptor: ObjectiveEventDescriptor,
  teamId: number
): boolean {
  if (event.kind !== descriptor.kind) {
    return false
  }
  const ownerTeamId = getObjectiveEventOwnerTeamId(event)
  if (ownerTeamId !== null && ownerTeamId !== teamId) {
    return false
  }
  if (descriptor.kind === 'dragon' && descriptor.dragonType) {
    return normalizeDragonTypeKey(event.subType) === descriptor.dragonType
  }
  return true
}

function getObjectiveEventOwnerTeamId(event: TeamObjectiveEvent): number | null {
  const participantId = normalizePositiveInteger(event.participantId)
  if (participantId !== null) {
    const participant = displayGameDetail.value?.participants?.find(candidate => candidate.participantId === participantId)
    const participantTeamId = normalizeTeamId(participant?.teamId)
    if (participantTeamId !== null) {
      return participantTeamId
    }
  }
  return normalizeTeamId(event.teamId)
}

function getObjectiveEventChampionId(event: TeamObjectiveEvent): number | null {
  const directChampionId = normalizePositiveInteger(event.championId)
  if (directChampionId !== null) {
    return directChampionId
  }
  const participantId = normalizePositiveInteger(event.participantId)
  if (participantId === null) {
    return null
  }
  const participant = displayGameDetail.value?.participants?.find(candidate => candidate.participantId === participantId)
  return normalizePositiveInteger(participant?.championId)
}

function normalizeDragonKillsByType(
  source: Partial<Record<DragonType, number>> | Record<string, unknown> | null | undefined
): Partial<Record<DragonType, number>> {
  const result: Partial<Record<DragonType, number>> = {}
  if (!source || typeof source !== 'object') {
    return result
  }
  for (const [rawType, rawCount] of Object.entries(source)) {
    const dragonType = normalizeDragonTypeKey(rawType)
    if (dragonType === 'unknown') {
      continue
    }
    const count = readObjectiveCount(rawCount)
    if (count > 0) {
      result[dragonType] = (result[dragonType] || 0) + count
    }
  }
  return result
}

function normalizeDragonTypeKey(value: unknown): DragonType | 'unknown' {
  if (typeof value !== 'string') {
    return 'unknown'
  }
  const normalized = normalizeDragonToken(value)
  if (!normalized) {
    return 'unknown'
  }
  for (const [dragonType, aliases] of Object.entries(DRAGON_TYPE_ALIASES) as Array<[Exclude<DragonType, 'unknown'>, string[]]>) {
    if (aliases.some(alias => normalizeDragonToken(alias) === normalized)) {
      return dragonType
    }
  }
  return 'unknown'
}

function normalizeDragonToken(value: string): string {
  return value.trim().toLowerCase().replace(/[\s-]+/g, '_')
}

function getObjectiveIconKindForDragonType(type: DragonType): ObjectiveIconKind {
  switch (type) {
    case 'infernal':
      return 'infernal'
    case 'mountain':
      return 'mountain'
    case 'ocean':
      return 'ocean'
    case 'cloud':
      return 'cloud'
    case 'hextech':
      return 'hextech'
    case 'chemtech':
      return 'chemtech'
    case 'unknown':
      return 'unknownDragon'
  }
}

function readObjectiveCount(value: unknown): number {
  const numberValue = normalizeFiniteNumber(value)
  return numberValue !== null && Number.isInteger(numberValue) && numberValue > 0 ? numberValue : 0
}

function readNullableObjectiveCount(value: unknown): number | null {
  const numberValue = normalizeFiniteNumber(value)
  if (numberValue === null || !Number.isInteger(numberValue)) {
    return null
  }
  return Math.max(0, numberValue)
}

function getKillParticipation(player: MatchDetailParticipant, team: TeamSection): string {
  if (!team.totals.kills) {
    return '--'
  }
  const kills = getPlayerKills(player)
  const assists = getPlayerAssists(player)
  return `${Math.round(((kills + assists) / team.totals.kills) * 100)}%`
}

function getVisionScoreText(player: MatchDetailParticipant): string {
  const value = readStatNumber(player, 'visionScore')
  return value === null ? '--' : formatNumber(value)
}

function getTopMetricValue(
  players: MatchDetailParticipant[],
  read: (player: MatchDetailParticipant) => number | null
): number | null {
  const values = players
    .map(read)
    .filter((value): value is number => typeof value === 'number' && Number.isFinite(value) && value > 0)

  return values.length ? Math.max(...values) : null
}

function isTopMetricPlayer(
  player: MatchDetailParticipant,
  topValue: number | null,
  read: (player: MatchDetailParticipant) => number | null
): boolean {
  const value = read(player)
  return topValue !== null && value !== null && Number.isFinite(value) && value > 0 && value === topValue
}

function isTopKillPlayer(player: MatchDetailParticipant): boolean {
  return isTopMetricPlayer(player, topKillValue.value, target => readStatNumber(target, 'kills'))
}

function isTopDeathPlayer(player: MatchDetailParticipant): boolean {
  return isTopMetricPlayer(player, topDeathValue.value, target => readStatNumber(target, 'deaths'))
}

function isTopAssistPlayer(player: MatchDetailParticipant): boolean {
  return isTopMetricPlayer(player, topAssistValue.value, target => readStatNumber(target, 'assists'))
}

function isTopDamagePlayer(player: MatchDetailParticipant): boolean {
  return isTopMetricPlayer(player, topDamageValue.value, target => readStatNumber(target, 'totalDamageDealtToChampions'))
}

function isTopTakenPlayer(player: MatchDetailParticipant): boolean {
  return isTopMetricPlayer(player, topTakenValue.value, target => readStatNumber(target, 'totalDamageTaken'))
}

function isTopGoldPlayer(player: MatchDetailParticipant): boolean {
  return isTopMetricPlayer(player, topGoldValue.value, target => readStatNumber(target, 'goldEarned'))
}

function getDamageRatio(player: MatchDetailParticipant): number {
  return getMetricRatio(readStatNumber(player, 'totalDamageDealtToChampions'), maxChampionDamage.value)
}

function getTakenRatio(player: MatchDetailParticipant): number {
  return getMetricRatio(readStatNumber(player, 'totalDamageTaken'), maxDamageTaken.value)
}

function getMetricRatio(value: number | null, maxValue: number): number {
  if (!value || maxValue <= 0) {
    return 0
  }
  return Math.max(4, Math.min(100, Math.round((value / maxValue) * 100)))
}

function getMetricBarStyle(ratio: number): Record<string, string> {
  return { width: `${ratio}%` }
}

function maxPlayerMetric(read: (player: MatchDetailParticipant) => number | null): number {
  return allPlayers.value.reduce((maxValue, player) => Math.max(maxValue, read(player) ?? 0), 0)
}

function readStatNumber(player: MatchDetailParticipant, key: string): number | null {
  const statsRecord = player.stats as unknown as Record<string, unknown> | null | undefined
  const playerRecord = player as unknown as Record<string, unknown>
  const extraFields = statsRecord?.extraFields as Record<string, unknown> | null | undefined
  return normalizeFiniteNumber(statsRecord?.[key] ?? playerRecord?.[key] ?? extraFields?.[key])
}

function normalizePositiveInteger(value: unknown): number | null {
  const numberValue = normalizeFiniteNumber(value)
  return numberValue !== null && Number.isInteger(numberValue) && numberValue > 0 ? numberValue : null
}

function normalizeTeamId(value: unknown): number | null {
  const numberValue = normalizeFiniteNumber(value)
  return numberValue !== null && Number.isInteger(numberValue) ? numberValue : null
}

function normalizeFiniteNumber(value: unknown): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (typeof value === 'string') {
    const parsed = Number(value.trim())
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function formatSignedNumber(value: number): string {
  if (value === 0) {
    return '0'
  }
  return `${value > 0 ? '+' : '-'}${formatNumber(Math.abs(value))}`
}

function toGameDetailFromMatchHistory(match: MatchHistory | null | undefined): GameDetail | null {
  if (!match) {
    return null
  }

  return {
    gameId: match.gameId,
    gameMode: match.gameMode,
    gameType: match.gameType,
    mapId: 0,
    queueId: match.queueId,
    gameDuration: match.gameDuration,
    gameCreation: match.gameCreation,
    participantIdentities: (match.participantIdentities || []).map(toGameParticipantIdentity),
    participants: (match.participants || []).map(toGameParticipant),
    teamObjectives: match.teamObjectives,
    teamBans: match.teamBans
  }
}

function toGameParticipant(participant: MatchHistory['participants'][number]): GameParticipant {
  return {
    participantId: participant.participantId,
    teamId: participant.teamId,
    championId: participant.championId,
    spell1Id: participant.spell1Id,
    spell2Id: participant.spell2Id,
    teamPosition: participant.teamPosition,
    individualPosition: participant.individualPosition,
    selectedPosition: participant.selectedPosition,
    stats: toGameStats(participant.stats),
    timeline: toGameTimeline(participant)
  }
}

function toGameParticipantIdentity(identity: MatchHistory['participantIdentities'][number]): GameParticipantIdentity {
  return {
    participantId: identity.participantId,
    player: {
      accountId: identity.player?.accountId ?? 0,
      puuid: identity.player?.puuid ?? '',
      platformId: identity.player?.platformId ?? '',
      summonerName: identity.player?.summonerName ?? '',
      gameName: identity.player?.gameName ?? '',
      tagLine: identity.player?.tagLine ?? '',
      summonerId: identity.player?.summonerId ?? 0
    }
  }
}

function toGameTimeline(participant: MatchHistory['participants'][number]): GameTimeline {
  return {
    lane: participant.teamPosition || participant.lane || participant.individualPosition || '',
    role: participant.role || '',
    teamPosition: participant.teamPosition,
    rawLane: participant.lane,
    rawRole: participant.role
  }
}

function toGameStats(stats: MatchHistory['participants'][number]['stats'] | undefined): GameStats {
  const statsRecord = stats as unknown as Record<string, unknown> | null | undefined
  const extraFields = {
    ...(stats?.extraFields || {}),
    turretKills: statsRecord?.turretKills,
    inhibitorKills: statsRecord?.inhibitorKills,
    turretPlatesTaken: statsRecord?.turretPlatesTaken,
    turretTakedowns: statsRecord?.turretTakedowns,
    inhibitorTakedowns: statsRecord?.inhibitorTakedowns,
    playerAugment5: stats?.playerAugment5,
    playerAugment6: stats?.playerAugment6
  }

  return {
    win: stats?.win ?? false,
    kills: stats?.kills ?? 0,
    deaths: stats?.deaths ?? 0,
    assists: stats?.assists ?? 0,
    totalMinionsKilled: stats?.totalMinionsKilled ?? 0,
    neutralMinionsKilled: stats?.neutralMinionsKilled ?? 0,
    goldEarned: stats?.goldEarned ?? 0,
    totalDamageDealtToChampions: stats?.totalDamageDealtToChampions ?? 0,
    totalDamageTaken: stats?.totalDamageTaken ?? 0,
    totalHeal: stats?.totalHeal ?? 0,
    visionScore: stats?.visionScore,
    visionWardsBoughtInGame: 0,
    wardsPlaced: 0,
    wardsKilled: 0,
    largestMultiKill: 0,
    doubleKills: stats?.doubleKills ?? 0,
    tripleKills: stats?.tripleKills ?? 0,
    quadraKills: stats?.quadraKills ?? 0,
    pentaKills: stats?.pentaKills ?? 0,
    largestKillingSpree: stats?.largestKillingSpree,
    legendaryCount: stats?.legendaryCount,
    item0: stats?.item0 ?? 0,
    item1: stats?.item1 ?? 0,
    item2: stats?.item2 ?? 0,
    item3: stats?.item3 ?? 0,
    item4: stats?.item4 ?? 0,
    item5: stats?.item5 ?? 0,
    item6: stats?.item6 ?? 0,
    damageDealtToChampionsRate: stats?.damageDealtToChampionsRate,
    damageTakenRate: stats?.damageTakenRate,
    healRate: stats?.healRate,
    mvp: stats?.mvp,
    perk0: stats?.perk0,
    perk1: stats?.perk1,
    perk2: stats?.perk2,
    perk3: stats?.perk3,
    perk4: stats?.perk4,
    perk5: stats?.perk5,
    perkPrimaryStyle: stats?.perkPrimaryStyle,
    perkSubStyle: stats?.perkSubStyle,
    perks: stats?.perks,
    minionsKilled: stats?.minionsKilled,
    damageDealtToTurrets: stats?.damageDealtToTurrets,
    turretKills: stats?.turretKills,
    inhibitorKills: stats?.inhibitorKills,
    turretPlatesTaken: stats?.turretPlatesTaken,
    turretTakedowns: stats?.turretTakedowns,
    inhibitorTakedowns: stats?.inhibitorTakedowns,
    playerAugment1: stats?.playerAugment1,
    playerAugment2: stats?.playerAugment2,
    playerAugment3: stats?.playerAugment3,
    playerAugment4: stats?.playerAugment4,
    challenges: stats?.challenges,
    extraFields
  }
}

function mergeGameDetailWithSummary(detail: GameDetail, summary: GameDetail | null): GameDetail {
  if (!summary?.participants?.length) {
    return detail
  }

  const summaryByParticipantId = new Map(summary.participants.map(participant => [participant.participantId, participant]))
  return {
    ...detail,
    teamObjectives: detail.teamObjectives?.length ? detail.teamObjectives : summary.teamObjectives,
    teamBans: detail.teamBans?.length ? detail.teamBans : summary.teamBans,
    participants: (detail.participants || []).map(participant =>
      mergeGameParticipantWithSummary(participant, summaryByParticipantId.get(participant.participantId))
    )
  }
}

function mergeGameParticipantWithSummary(participant: GameParticipant, summaryParticipant?: GameParticipant): GameParticipant {
  if (!summaryParticipant) {
    return participant
  }

  return {
    ...participant,
    teamPosition: participant.teamPosition || summaryParticipant.teamPosition,
    individualPosition: participant.individualPosition || summaryParticipant.individualPosition,
    selectedPosition: participant.selectedPosition || summaryParticipant.selectedPosition,
    stats: mergeGameStatsWithSummary(participant.stats, summaryParticipant.stats),
    timeline: {
      ...participant.timeline,
      lane: participant.timeline?.lane || summaryParticipant.timeline?.lane || '',
      role: participant.timeline?.role || summaryParticipant.timeline?.role || '',
      teamPosition: participant.timeline?.teamPosition || summaryParticipant.timeline?.teamPosition,
      positionCn: participant.timeline?.positionCn || summaryParticipant.timeline?.positionCn,
      rawLane: participant.timeline?.rawLane || summaryParticipant.timeline?.rawLane,
      rawRole: participant.timeline?.rawRole || summaryParticipant.timeline?.rawRole
    }
  }
}

function mergeGameStatsWithSummary(stats: GameStats, summaryStats?: GameStats): GameStats {
  if (!summaryStats) {
    return stats
  }

  const merged: GameStats = {
    ...stats,
    perks: stats.perks || summaryStats.perks,
    challenges: stats.challenges || summaryStats.challenges,
    extraFields: {
      ...(summaryStats.extraFields || {}),
      ...(stats.extraFields || {})
    }
  }

  for (const key of [
    'perk0',
    'perk1',
    'perk2',
    'perk3',
    'perk4',
    'perk5',
    'perkPrimaryStyle',
    'perkSubStyle',
    'playerAugment1',
    'playerAugment2',
    'playerAugment3',
    'playerAugment4',
    'item0',
    'item1',
    'item2',
    'item3',
    'item4',
    'item5',
    'item6',
    'totalDamageDealtToChampions',
    'goldEarned',
    'totalDamageTaken',
    'visionScore',
    'totalHeal'
  ] as Array<keyof GameStats>) {
    if (normalizePositiveInteger(summaryStats[key]) && !normalizePositiveInteger(merged[key])) {
      (merged as unknown as Record<string, unknown>)[key] = summaryStats[key]
    }
  }

  return merged
}

function isRenderableGameDetail(detail: GameDetail | null): detail is GameDetail {
  return Boolean(detail?.participants?.some(participant =>
    participant?.teamId != null &&
    participant.championId != null &&
    participant.stats?.kills != null &&
    participant.stats.deaths != null &&
    participant.stats.assists != null
  ))
}
</script>

<template>
  <section class="inline-match-detail" aria-label="match detail">
    <div v-if="detailNotice" class="detail-load-state" role="status">
      {{ detailNotice }}
    </div>

    <nav class="inline-detail-tabs" aria-label="match detail tabs">
      <button
        v-for="tab in detailTabs"
        :key="tab.key"
        class="inline-detail-tab"
        :class="{ active: activeTabValue === tab.key }"
        type="button"
        @click="selectTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <section class="inline-detail-body">
      <div v-if="activeTabValue === 'overview'" class="overview-tab">
        <article
          v-for="team in teamSections"
          :key="team.key"
          class="team-detail-table"
          :class="[team.key, { win: team.won, loss: !team.won, 'with-vision-score': showVisionScoreColumn }]"
        >
          <header class="team-detail-header">
            <div class="team-header-main">
              <strong>{{ team.result }} · {{ team.label }}</strong>
              <div class="team-header-summary">
                <span
                  class="team-kda-summary"
                  aria-label="team score"
                >
                  {{ getTeamKda(team.totals) }}
                </span>
                <div
                  v-if="showDraftAndObjectiveSummary && getTeamObjectiveItems(team.teamId).length"
                  class="team-header-resources"
                >
                  <div
                    class="team-objective-icons"
                    aria-label="objectives"
                  >
                    <span
                      v-for="item in getTeamObjectiveItems(team.teamId)"
                      :key="item.key"
                      class="objective-pill compact-objective-pill"
                      :class="`objective-${item.kind}`"
                      :title="item.title"
                      :aria-label="item.title"
                      tabindex="0"
                    >
                      <template
                        v-for="icon in getObjectiveItemIcons(item)"
                        :key="icon.key"
                      >
                        <img
                          v-if="shouldUseObjectiveIconImage(icon)"
                          class="objective-icon objective-icon-img"
                          :src="icon.iconUrl"
                          alt=""
                          @error="handleObjectiveIconLoadFailed($event, icon.key)"
                        />
                        <span
                          v-else
                          class="objective-icon objective-fallback-icon"
                          :class="`objective-fallback-${icon.kind}`"
                          aria-hidden="true"
                        >
                          <svg
                            viewBox="0 0 16 16"
                            aria-hidden="true"
                            focusable="false"
                          >
                            <path d="M8 1.3c2.6 1.5 4.2 3.7 4.2 6.3 0 3.1-1.8 5.6-4.2 7.1-2.4-1.5-4.2-4-4.2-7.1 0-2.6 1.6-4.8 4.2-6.3Zm0 3.1C6.5 5.5 5.7 6.6 5.7 8c0 1.5.9 2.7 2.3 3.6 1.4-.9 2.3-2.1 2.3-3.6 0-1.4-.8-2.5-2.3-3.6Z" />
                          </svg>
                        </span>
                      </template>
                      <strong
                        v-if="item.showCount"
                        class="objective-count"
                      >
                        {{ getObjectiveCountText(item) }}
                      </strong>
                      <span
                        v-if="item.tooltipGroups.length || item.title"
                        class="objective-tooltip"
                        role="tooltip"
                      >
                        <span class="objective-tooltip-title">{{ item.title }}</span>
                        <span
                          v-if="item.tooltipGroups.length"
                          class="objective-tooltip-actors"
                        >
                          <span
                            v-for="group in item.tooltipGroups"
                            :key="group.key"
                            class="objective-tooltip-actor"
                            :aria-label="`${group.label} x${group.count}`"
                          >
                            <img
                              v-if="getChampionIconUrl(group.championId)"
                              class="objective-tooltip-avatar"
                              :src="getChampionIconUrl(group.championId)"
                              alt=""
                              @error="markAssetLoadFailed"
                            />
                            <span class="objective-tooltip-count">x{{ group.count }}</span>
                          </span>
                        </span>
                      </span>
                    </span>
                  </div>
                </div>
                <div
                  v-if="showDraftAndObjectiveSummary && getTeamBans(team.teamId).length"
                  class="team-draft-row"
                >
                  <span class="draft-objective-label">禁用</span>
                  <div
                    v-if="getTeamBans(team.teamId).length"
                    class="team-ban-icons"
                    aria-label="banned champions"
                  >
                    <span
                      v-for="championId in getTeamBans(team.teamId)"
                      :key="`${team.teamId}-ban-${championId}`"
                      class="ban-champion-icon"
                    >
                      <img
                        v-if="getChampionIconUrl(championId)"
                        :src="getChampionIconUrl(championId)"
                        alt=""
                        @error="markAssetLoadFailed"
                      />
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </header>

          <div class="team-row-labels" aria-hidden="true">
            <span>Player</span>
            <span>KDA</span>
            <span>{{ t('common.damage') }}</span>
            <span>{{ t('matchDetail.damageTaken') }}</span>
            <span>{{ t('common.cs') }}</span>
            <span>{{ t('common.gold') }}</span>
            <span v-if="showVisionScoreColumn" class="vision-score-head">{{ t('matchDetail.visionScore') }}</span>
            <span class="items-head">{{ t('matchDetail.itemsTab') }}</span>
          </div>

          <div
            v-for="player in team.players"
            :key="player.participantId"
            class="participant-row"
            :class="{ me: player.isCurrentPlayer, clickable: canNavigatePlayer(player) }"
            @click="handlePlayerClick(player)"
          >
            <div class="player-cell">
              <span class="champion-wrap">
                <img
                  v-if="getChampionIconUrl(player.championId)"
                  :src="getChampionIconUrl(player.championId)"
                  alt=""
                  @error="markAssetLoadFailed"
                />
                <span v-if="getChampionLevel(player)" class="champion-level">{{ getChampionLevel(player) }}</span>
              </span>
              <span class="spell-stack">
                <span
                  v-for="slot in getPlayerSpellSlots(player)"
                  :key="slot.key"
                  class="mini-slot spell-slot"
                  :class="{ empty: slot.empty }"
                >
                  <AssetHoverTooltip
                    v-if="slot.url && !slot.empty && getSummonerSpellTooltipDetails(slot.id)"
                    :details="getSummonerSpellTooltipDetails(slot.id)!"
                  >
                    <img v-if="slot.url" :src="slot.url" alt="" @error="markAssetLoadFailed" />
                  </AssetHoverTooltip>
                  <img v-else-if="slot.url" :src="slot.url" alt="" @error="markAssetLoadFailed" />
                </span>
              </span>
              <span
                class="trait-pair"
                v-if="!hasValidAugment(player)"
              >
                <span
                  v-for="slot in getPlayerTraitSlots(player).slice(0, 2)"
                  :key="slot.key"
                  class="mini-slot trait-slot"
                  :class="[`trait-${slot.kind}`, slot.rarityClass, { empty: slot.empty }]"
                  :aria-label="slot.label"
                >
                  <AssetHoverTooltip
                    v-if="slot.url && !slot.empty && getTraitTooltipDetails(slot)"
                    :details="getTraitTooltipDetails(slot)!"
                  >
                    <img v-if="slot.url" :src="slot.url" alt="" @error="markAssetLoadFailed" />
                  </AssetHoverTooltip>
                  <img v-else-if="slot.url" :src="slot.url" alt="" @error="markAssetLoadFailed" />
                </span>
              </span>
              <span
                class="player-identity-main"
                :class="{ 'with-augments': getPlayerOverviewAugmentSlots(player).length }"
              >
                <span class="player-copy player-name-wrap">
                  <strong>{{ getPlayerName(player) }}</strong>
                  <span v-if="getDisplayPosition(player)">{{ getDisplayPosition(player) }}</span>
                </span>
                <span
                  class="overview-augment-strip"
                  v-if="getPlayerOverviewAugmentSlots(player).length"
                  aria-label="augments"
                >
                  <span
                    v-for="slot in getPlayerOverviewAugmentSlots(player)"
                    :key="`overview-${slot.key}`"
                    class="overview-augment-slot"
                    :class="slot.rarityClass"
                    :aria-label="slot.label"
                  >
                    <AssetHoverTooltip
                      v-if="slot.url && !slot.empty && getTraitTooltipDetails(slot)"
                      :details="getTraitTooltipDetails(slot)!"
                    >
                      <img v-if="slot.url" :src="slot.url" alt="" @error="markAssetLoadFailed" />
                    </AssetHoverTooltip>
                    <img v-else-if="slot.url" :src="slot.url" alt="" @error="markAssetLoadFailed" />
                  </span>
                </span>
              </span>
            </div>

            <div class="kda-cell">
              <span class="player-kda-score">
                <span class="kda-kills" :class="{ 'top-kills': isTopKillPlayer(player) }">
                  {{ getPlayerKills(player) }}
                </span>
                <span class="kda-separator">/</span>
                <span class="kda-deaths" :class="{ 'top-deaths': isTopDeathPlayer(player) }">
                  {{ getPlayerDeaths(player) }}
                </span>
                <span class="kda-separator">/</span>
                <span class="kda-assists" :class="{ 'top-assists': isTopAssistPlayer(player) }">
                  {{ getPlayerAssists(player) }}
                </span>
              </span>
              <span>{{ t('matchDetail.killParticipation') }} {{ getKillParticipation(player, team) }}</span>
            </div>

            <div class="metric-cell">
              <span class="metric-value-with-icon">
                {{ formatNumber(readStatNumber(player, 'totalDamageDealtToChampions')) }}
                <span
                  class="top-metric-icon top-damage-icon"
                  v-if="isTopDamagePlayer(player)"
                  title="全场最高伤害"
                  aria-label="全场最高伤害"
                >
                  <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false">
                    <path d="M8.8 1.2c.4 2.2-.7 3.2-1.8 4.3-.9.9-1.8 1.9-1.8 3.4 0 1.6 1.1 2.8 2.8 2.8 1.9 0 3.1-1.4 3.1-3.1 0-1-.4-2-1.1-2.9 1.9 1.1 3.1 2.9 3.1 5 0 2.6-2.1 4.4-5.1 4.4s-5.1-1.8-5.1-4.6c0-2 .9-3.4 2-4.7 1.4-1.6 3-2.8 3.9-4.6Z" />
                  </svg>
                </span>
              </span>
              <span class="metric-track"><span class="metric-bar damage-bar" :style="getMetricBarStyle(getDamageRatio(player))"></span></span>
            </div>

            <div class="metric-cell secondary">
              <span class="metric-value-with-icon">
                {{ formatNumber(readStatNumber(player, 'totalDamageTaken')) }}
                <span
                  class="top-metric-icon top-taken-icon"
                  v-if="isTopTakenPlayer(player)"
                  title="全场最高承伤"
                  aria-label="全场最高承伤"
                >
                  <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false">
                    <path d="M8 1.2 13 3v4.1c0 3.3-2 6.2-5 7.7-3-1.5-5-4.4-5-7.7V3l5-1.8Zm0 2.1L4.8 4.5v2.7c0 2.1 1.2 4.1 3.2 5.3 2-1.2 3.2-3.2 3.2-5.3V4.5L8 3.3Z" />
                  </svg>
                </span>
              </span>
              <span class="metric-track"><span class="metric-bar taken-bar" :style="getMetricBarStyle(getTakenRatio(player))"></span></span>
            </div>

            <span class="number-cell">{{ formatNumber(getCreepScore(player.stats)) }}</span>
            <span class="number-cell gold-cell">
              <span class="metric-value-with-icon">
                {{ formatNumber(player.stats?.goldEarned) }}
                <span
                  class="top-metric-icon top-gold-icon"
                  v-if="isTopGoldPlayer(player)"
                  title="全场最高金币"
                  aria-label="全场最高金币"
                >
                  <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false">
                    <circle cx="8" cy="8" r="6.4" />
                    <path d="M5.1 6.1h5.8M5.1 9.9h5.8" fill="none" stroke="rgba(42, 30, 0, 0.45)" stroke-width="1.2" stroke-linecap="round" />
                  </svg>
                </span>
              </span>
            </span>

            <span v-if="showVisionScoreColumn" class="number-cell vision-score-cell">{{ getVisionScoreText(player) }}</span>

            <div class="item-row compact" aria-label="items">
              <span
                v-for="slot in getPlayerItemSlots(player)"
                :key="`${player.participantId}-overview-item-${slot.index}`"
                class="item-slot"
                :class="{ empty: slot.empty }"
                :aria-label="getItemSlotLabel(slot)"
              >
                <AssetHoverTooltip
                  v-if="slot.url && !slot.empty && slot.itemId !== null"
                  :details="getItemTooltipDetails(slot.itemId)!"
                >
                  <img v-if="slot.url" :src="slot.url" alt="" @error="markAssetLoadFailed" />
                </AssetHoverTooltip>
                <img v-else-if="slot.url" :src="slot.url" alt="" @error="markAssetLoadFailed" />
              </span>
            </div>
          </div>
        </article>
      </div>

      <div v-else-if="activeTabValue === 'runes'" class="runes-tab">
        <div
          v-for="player in allPlayers"
          :key="`runes-${player.participantId}`"
          class="rune-player-row"
          :class="{ me: player.isCurrentPlayer, clickable: canNavigatePlayer(player) }"
          @click="handlePlayerClick(player)"
        >
          <div class="player-cell">
            <span class="champion-wrap">
              <img
                v-if="getChampionIconUrl(player.championId)"
                :src="getChampionIconUrl(player.championId)"
                alt=""
                @error="markAssetLoadFailed"
              />
            </span>
            <span class="spell-stack">
              <span
                v-for="slot in getPlayerSpellSlots(player)"
                :key="`runes-${slot.key}`"
                class="mini-slot spell-slot"
                :class="{ empty: slot.empty }"
              >
                <AssetHoverTooltip
                  v-if="slot.url && !slot.empty && getSummonerSpellTooltipDetails(slot.id)"
                  :details="getSummonerSpellTooltipDetails(slot.id)!"
                >
                  <img v-if="slot.url" :src="slot.url" alt="" @error="markAssetLoadFailed" />
                </AssetHoverTooltip>
                <img v-else-if="slot.url" :src="slot.url" alt="" @error="markAssetLoadFailed" />
              </span>
            </span>
            <span class="player-copy">
              <strong>{{ getPlayerName(player) }}</strong>
              <span v-if="getDisplayPosition(player)">{{ getDisplayPosition(player) }}</span>
            </span>
          </div>

          <div class="trait-list">
            <span
              v-for="slot in getPlayerTraitSlots(player)"
              :key="`runes-${slot.key}`"
              class="trait-detail-slot"
              :class="[`trait-${slot.kind}`, slot.rarityClass, { empty: slot.empty }]"
              :aria-label="slot.label"
            >
              <AssetHoverTooltip
                v-if="slot.url && !slot.empty && getTraitTooltipDetails(slot)"
                :details="getTraitTooltipDetails(slot)!"
              >
                <img v-if="slot.url" :src="slot.url" alt="" @error="markAssetLoadFailed" />
              </AssetHoverTooltip>
              <img v-else-if="slot.url" :src="slot.url" alt="" @error="markAssetLoadFailed" />
            </span>
          </div>
        </div>
      </div>

      <div v-else-if="activeTabValue === 'chart'" class="chart-tab">
        <div v-if="hasTimelineData" class="timeline-host"></div>
        <div v-else class="timeline-empty">
          <strong>{{ t('matchDetail.timelineEmptyTitle') }}</strong>
          <span>{{ t('matchDetail.timelineEmptyBody') }}</span>
          <small>{{ t('matchDetail.staticGoldDiff') }} {{ formatSignedNumber(staticTeamGoldDiff) }}</small>
        </div>
      </div>
    </section>
  </section>
</template>

<style scoped>
.inline-match-detail {
  --metric-bar-width: 74%;
  --overview-augment-slot-size: 16px;
  --overview-augment-strip-width: 106px;
  width: 100%;
  min-width: 0;
  margin-top: 8px;
  padding: 10px;
  border: 1px solid rgba(124, 139, 164, 0.14);
  border-radius: 8px;
  background: rgba(12, 18, 28, 0.68);
  color: var(--text-primary);
  box-sizing: border-box;
  overflow: hidden;
}

:global([data-theme="light"] .inline-match-detail) {
  background: rgba(246, 249, 253, 0.94);
  border-color: rgba(24, 35, 54, 0.12);
}

.detail-load-state {
  margin-bottom: 8px;
  padding: 7px 9px;
  border: 1px solid rgba(240, 196, 79, 0.22);
  border-radius: 6px;
  background: rgba(240, 196, 79, 0.08);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.inline-detail-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}

.inline-detail-tab {
  height: 28px;
  padding: 0 12px;
  border: 1px solid rgba(124, 139, 164, 0.16);
  border-radius: 6px;
  background: rgba(124, 139, 164, 0.08);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.inline-detail-tab.active {
  border-color: rgba(var(--accent-rgb), 0.38);
  background: rgba(var(--accent-rgb), 0.14);
  color: var(--accent-color);
}

.inline-detail-body,
.overview-tab,
.runes-tab,
.chart-tab {
  min-width: 0;
}

.overview-tab {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
}

.team-detail-table {
  min-width: 0;
  overflow: hidden;
  border: 1px solid rgba(124, 139, 164, 0.14);
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.035);
}

:global([data-theme="light"] .team-detail-table) {
  background: rgba(255, 255, 255, 0.74);
}

.team-detail-header {
  display: flex;
  min-width: 0;
  align-items: center;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(124, 139, 164, 0.12);
  color: var(--text-secondary);
  font-size: 12px;
}

.team-detail-header strong {
  min-width: 0;
  color: var(--text-primary);
}

.team-header-main {
  display: flex;
  min-width: 0;
  width: 100%;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  flex-wrap: wrap;
}

.team-header-summary {
  display: inline-flex;
  flex: 1 1 auto;
  min-width: 0;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  flex-wrap: wrap;
}

.team-header-resources {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-start;
}

.team-kda-summary {
  color: var(--text-primary);
  font-weight: 800;
  white-space: nowrap;
}

.team-draft-row,
.team-draft-objective-row {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  flex-wrap: wrap;
}

.draft-objective-label {
  color: var(--text-tertiary);
  font-size: 10px;
  font-weight: 800;
  white-space: nowrap;
}

.team-ban-icons,
.team-objective-icons {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 4px;
  min-width: 0;
  flex-wrap: wrap;
}

.ban-champion-icon {
  position: relative;
  width: 20px;
  height: 20px;
  flex: 0 0 20px;
  overflow: hidden;
  border: 1px solid rgba(124, 139, 164, 0.35);
  border-radius: 4px;
  background: rgba(124, 139, 164, 0.12);
}

.ban-champion-icon::after {
  content: '';
  position: absolute;
  left: -4px;
  top: 9px;
  width: 28px;
  height: 2px;
  transform: rotate(-45deg);
  background: rgba(230, 230, 230, 0.85);
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.45);
}

.objective-pill {
  position: relative;
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 3px;
  height: 20px;
  padding: 0 5px;
  border: 1px solid rgba(124, 139, 164, 0.18);
  border-radius: 999px;
  background: rgba(124, 139, 164, 0.08);
  color: var(--text-secondary);
  font-size: 10px;
  font-weight: 800;
  white-space: nowrap;
  outline: none;
}

.objective-count {
  color: var(--text-primary);
  font-size: 10px;
  font-weight: 900;
  line-height: 1;
}

.objective-icon {
  width: 14px;
  height: 14px;
  flex: 0 0 14px;
}

.objective-icon-img,
.objective-fallback-icon svg {
  display: block;
  width: 100%;
  height: 100%;
}

.objective-fallback-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}

.objective-fallback-icon svg {
  fill: currentColor;
}

.objective-pill:focus {
  border-color: rgba(var(--accent-rgb), 0.42);
  box-shadow: 0 0 0 2px rgba(var(--accent-rgb), 0.14);
}

.objective-tooltip {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 30;
  display: none;
  min-width: max-content;
  max-width: 220px;
  flex-direction: column;
  gap: 6px;
  padding: 7px;
  border: 1px solid rgba(124, 139, 164, 0.24);
  border-radius: 6px;
  background: rgba(12, 18, 28, 0.96);
  box-shadow: 0 10px 26px rgba(0, 0, 0, 0.28);
  color: var(--text-primary);
  pointer-events: none;
}

:global([data-theme="light"] .objective-tooltip) {
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 10px 24px rgba(24, 35, 54, 0.16);
}

.objective-pill:hover .objective-tooltip,
.objective-pill:focus .objective-tooltip,
.objective-pill:focus-within .objective-tooltip {
  display: flex;
}

.objective-tooltip-title {
  color: var(--text-secondary);
  font-size: 10px;
  font-weight: 800;
  line-height: 1.1;
}

.objective-tooltip-actors {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
}

.objective-tooltip-actor {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.objective-tooltip-avatar {
  display: block;
  width: 24px;
  height: 24px;
  border: 1px solid rgba(124, 139, 164, 0.28);
  border-radius: 5px;
  object-fit: cover;
}

.objective-tooltip-count {
  color: var(--text-primary);
  font-size: 10px;
  font-weight: 900;
}

.objective-soul-infernal {
  border-color: rgba(239, 111, 122, 0.42);
  color: #ef6f7a;
}

.objective-soul-mountain {
  border-color: rgba(214, 171, 86, 0.42);
  color: #d6ab56;
}

.objective-soul-ocean {
  border-color: rgba(82, 190, 166, 0.42);
  color: #52bea6;
}

.objective-soul-cloud {
  border-color: rgba(148, 202, 255, 0.42);
  color: #94caff;
}

.objective-soul-hextech {
  border-color: rgba(180, 139, 255, 0.42);
  color: #b48bff;
}

.objective-soul-chemtech {
  border-color: rgba(125, 214, 92, 0.42);
  color: #7dd65c;
}

.team-detail-table.blue.win .team-detail-header strong,
.team-detail-table.red.win .team-detail-header strong {
  color: var(--win-color);
}

.team-detail-table.blue.loss .team-detail-header strong,
.team-detail-table.red.loss .team-detail-header strong {
  color: var(--loss-color);
}

.team-row-labels,
.participant-row {
  display: grid;
  grid-template-columns:
    minmax(190px, 1.55fr)
    minmax(70px, 0.62fr)
    minmax(78px, 0.7fr)
    minmax(78px, 0.7fr)
    minmax(40px, 0.32fr)
    minmax(52px, 0.38fr)
    minmax(154px, 0.9fr);
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.team-detail-table.with-vision-score .team-row-labels,
.team-detail-table.with-vision-score .participant-row {
  grid-template-columns:
    minmax(190px, 1.55fr)
    minmax(70px, 0.62fr)
    minmax(78px, 0.7fr)
    minmax(78px, 0.7fr)
    minmax(40px, 0.32fr)
    minmax(52px, 0.38fr)
    minmax(58px, 0.38fr)
    minmax(154px, 0.9fr);
}

.team-row-labels {
  padding: 5px 10px;
  border-bottom: 1px solid rgba(124, 139, 164, 0.08);
  color: var(--text-tertiary);
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
}

.items-head,
.vision-score-head {
  text-transform: none;
}

.participant-row,
.rune-player-row {
  padding: 6px 10px;
  border-bottom: 1px solid rgba(124, 139, 164, 0.08);
  color: var(--text-secondary);
  font-size: 11px;
}

.participant-row:last-child,
.rune-player-row:last-child {
  border-bottom: 0;
}

.participant-row.clickable,
.rune-player-row.clickable {
  cursor: pointer;
}

.participant-row.clickable:hover,
.rune-player-row.clickable:hover {
  background: rgba(var(--accent-rgb), 0.08);
}

.participant-row.me,
.rune-player-row.me {
  background: rgba(var(--accent-rgb), 0.11);
  box-shadow: inset 2px 0 0 var(--accent-color);
}

.player-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.champion-wrap {
  position: relative;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 6px;
  background: rgba(124, 139, 164, 0.12);
  overflow: hidden;
}

.champion-wrap img,
.mini-slot img,
.overview-augment-slot img,
.trait-detail-slot img,
.ban-champion-icon img,
.item-slot img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.champion-wrap img[data-asset-failed='true'],
.mini-slot img[data-asset-failed='true'],
.overview-augment-slot img[data-asset-failed='true'],
.trait-detail-slot img[data-asset-failed='true'],
.ban-champion-icon img[data-asset-failed='true'],
.objective-tooltip-avatar[data-asset-failed='true'],
.item-slot img[data-asset-failed='true'] {
  display: none;
}

.champion-level {
  position: absolute;
  right: 0;
  bottom: 0;
  min-width: 14px;
  padding: 1px 3px;
  border-radius: 4px 0 0 0;
  background: rgba(0, 0, 0, 0.72);
  color: #fff;
  font-size: 9px;
  line-height: 1.1;
  text-align: center;
}

.spell-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 0 0 auto;
}

.trait-pair {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 0 0 auto;
}

.player-identity-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: center;
  gap: 4px;
  flex: 1 1 0;
  min-width: 0;
}

.player-identity-main.with-augments {
  grid-template-columns: minmax(0, 1fr) var(--overview-augment-strip-width);
}

.overview-augment-strip {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  width: var(--overview-augment-strip-width);
  min-width: var(--overview-augment-strip-width);
  flex: 0 0 var(--overview-augment-strip-width);
  overflow: hidden;
  position: relative;
  z-index: 2;
  white-space: nowrap;
}

.mini-slot,
.overview-augment-slot,
.trait-detail-slot,
.item-slot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid var(--augment-rarity-border, rgba(124, 139, 164, 0.14));
  border-radius: 4px;
  background: var(--augment-rarity-bg, rgba(124, 139, 164, 0.1));
  box-shadow: inset 0 0 0 1px var(--augment-rarity-inner, transparent);
}

.mini-slot {
  width: 15px;
  height: 15px;
  flex: 0 0 15px;
}

.overview-augment-slot {
  width: var(--overview-augment-slot-size);
  height: var(--overview-augment-slot-size);
  flex: 0 0 var(--overview-augment-slot-size);
}

.overview-augment-slot :deep(.asset-tooltip-trigger) {
  width: 100%;
  height: 100%;
}

.trait-detail-slot {
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
}

.item-slot {
  width: 20px;
  height: 20px;
  flex: 0 0 20px;
}

.mini-slot.empty,
.trait-detail-slot.empty,
.item-slot.empty {
  opacity: 0.62;
}

.player-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.player-name-wrap {
  overflow: hidden;
  position: relative;
  z-index: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-copy strong,
.player-copy span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-copy strong {
  color: var(--text-primary);
  font-size: 12px;
}

.kda-cell,
.metric-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.player-kda-score {
  display: inline-flex;
  align-items: baseline;
  gap: 1px;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 800;
}

.kda-separator {
  color: var(--text-secondary);
  opacity: 0.75;
}

.top-kills {
  color: #ef6f7a;
  text-shadow: 0 0 8px rgba(239, 111, 122, 0.22);
}

.top-deaths {
  color: #f0c05a;
  text-shadow: 0 0 8px rgba(240, 192, 90, 0.22);
}

.top-assists {
  color: #62d49e;
  text-shadow: 0 0 8px rgba(98, 212, 158, 0.22);
}

.metric-value-with-icon {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.top-metric-icon {
  display: inline-flex;
  width: 12px;
  height: 12px;
  flex: 0 0 12px;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.top-metric-icon svg {
  display: block;
  width: 100%;
  height: 100%;
  fill: currentColor;
}

.top-damage-icon {
  color: #ff7a45;
  filter: drop-shadow(0 0 4px rgba(255, 122, 69, 0.28));
}

.top-taken-icon {
  color: #7bb7ff;
  filter: drop-shadow(0 0 4px rgba(123, 183, 255, 0.24));
}

.top-gold-icon {
  color: #f0c05a;
  filter: drop-shadow(0 0 4px rgba(240, 192, 90, 0.28));
}

.metric-track {
  width: var(--metric-bar-width);
  max-width: 100%;
  height: 4px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(124, 139, 164, 0.12);
}

.metric-bar {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.damage-bar {
  background: rgba(210, 87, 99, 0.78);
}

.taken-bar {
  background: rgba(92, 163, 234, 0.72);
}

.number-cell {
  min-width: 0;
  color: var(--text-secondary);
  font-weight: 700;
}

.gold-cell {
  color: #d7a64b;
}

.vision-score-cell {
  color: var(--text-secondary);
}

.item-row.compact {
  display: flex;
  gap: 3px;
  min-width: 0;
}

.runes-tab {
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(124, 139, 164, 0.14);
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.035);
  overflow: hidden;
}

:global([data-theme="light"] .runes-tab) {
  background: rgba(255, 255, 255, 0.74);
}

.rune-player-row {
  display: grid;
  grid-template-columns: minmax(180px, 0.9fr) minmax(0, 1.4fr);
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.trait-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-width: 0;
}

.chart-tab {
  min-height: 136px;
}

.timeline-empty {
  display: flex;
  min-height: 136px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 18px;
  border: 1px dashed rgba(124, 139, 164, 0.24);
  border-radius: 7px;
  background: rgba(124, 139, 164, 0.07);
  color: var(--text-secondary);
  text-align: center;
}

.timeline-empty strong {
  color: var(--text-primary);
  font-size: 14px;
}

.timeline-empty small {
  color: #d7a64b;
  font-weight: 700;
}

.timeline-host {
  min-height: 136px;
}

@media (max-width: 1080px) {
  .team-row-labels,
  .participant-row {
    grid-template-columns:
      minmax(170px, 1.4fr)
      minmax(70px, 0.6fr)
      minmax(78px, 0.7fr)
      minmax(40px, 0.32fr)
      minmax(150px, 0.9fr);
  }

  .team-row-labels span:nth-child(4),
  .participant-row .metric-cell.secondary,
  .team-row-labels span:nth-child(6),
  .participant-row .gold-cell,
  .team-row-labels .vision-score-head,
  .participant-row .vision-score-cell {
    display: none;
  }
}

@media (max-width: 760px) {
  .inline-match-detail {
    padding: 8px;
  }

  .team-detail-header {
    gap: 3px;
  }

  .team-header-main {
    align-items: flex-start;
  }

  .team-header-summary {
    margin-left: 0;
    justify-content: flex-start;
  }

  .team-row-labels {
    display: none;
  }

  .participant-row,
  .rune-player-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 7px;
  }

  .metric-cell,
  .number-cell {
    display: none;
  }

  .item-row.compact {
    flex-wrap: wrap;
  }
}
</style>
