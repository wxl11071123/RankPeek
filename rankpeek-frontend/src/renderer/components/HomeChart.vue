<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { apiClient } from '@/api/httpClient'
import type { GameDetail, MatchHistory } from '@/types/api'

type QueueMode = 420 | 440
type MetricKey =
  | 'kda'
  | 'kills'
  | 'deaths'
  | 'assists'
  | 'gold'
  | 'damageRate'
  | 'goldDiff15'
  | 'visionScore'
type LaneKey = 'all' | 'top' | 'jungle' | 'mid' | 'bottom' | 'support' | 'unknown'
type DetailParticipant = GameDetail['participants'][number]
type MatchParticipant = MatchHistory['participants'][number]
type StatsLike = (DetailParticipant['stats'] | MatchParticipant['stats']) & Record<string, unknown>

interface ChartEntry {
  gameId: number
  gameCreation: number
  championId: number
  win: boolean
  lane: LaneKey
  laneLabel: string
  kills: number
  deaths: number
  assists: number
  kdaText: string
  gold: number
  totalDamage: number
  damageRate: number | null
  goldDiff15: number | null
  visionScore: number | null
}

interface ChartPoint {
  entry: ChartEntry
  value: number
  x: number
  y: number
  label: number
}

interface GridLine {
  value: number
  label: string
  y: number
}

interface HoverValueBadge {
  label: string
  x: number
  y: number
  width: number
}

const props = defineProps<{
  puuid?: string
  connected: boolean
}>()

const QUEUE_OPTIONS: Array<{ value: QueueMode; label: string }> = [
  { value: 420, label: '单双排' },
  { value: 440, label: '灵活组排' }
]

const METRIC_OPTIONS: Array<{ value: MetricKey; label: string }> = [
  { value: 'kda', label: 'KDA 比率' },
  { value: 'kills', label: '击杀' },
  { value: 'deaths', label: '死亡' },
  { value: 'assists', label: '助攻' },
  { value: 'gold', label: '经济' },
  { value: 'damageRate', label: '伤害转化率' },
  { value: 'goldDiff15', label: '15分钟对线经济差' },
  { value: 'visionScore', label: '视野得分' }
]

const LANE_OPTIONS: Array<{ value: LaneKey; label: string }> = [
  { value: 'all', label: '全部分路' },
  { value: 'top', label: '上路' },
  { value: 'jungle', label: '打野' },
  { value: 'mid', label: '中路' },
  { value: 'bottom', label: '下路' },
  { value: 'support', label: '辅助' }
]

const CHART_WIDTH = 760
const CHART_HEIGHT = 280
const CHART_LEFT = 74
const CHART_RIGHT = 26
const CHART_TOP = 28
const CHART_BOTTOM = 44
const GRID_LINE_COUNT = 4
const MAX_DISPLAY_MATCHES = 10
const LOOKBACK_MATCH_COUNT = 50

const selectedQueue = ref<QueueMode>(420)
const selectedMetric = ref<MetricKey>('kda')
const selectedLane = ref<LaneKey>('all')
const entries = ref<ChartEntry[]>([])
const loading = ref(false)
const error = ref('')
const activePoint = ref<ChartPoint | null>(null)
let requestId = 0

const filteredEntries = computed(() =>
  selectedLane.value === 'all'
    ? entries.value
    : entries.value.filter(entry => entry.lane === selectedLane.value)
)

const selectedEntries = computed(() => filteredEntries.value.slice(0, MAX_DISPLAY_MATCHES))

const displayEntries = computed(() => selectedEntries.value.slice().reverse())

const metricEntries = computed(() =>
  displayEntries.value
    .map(entry => ({ entry, value: getMetricValue(entry, selectedMetric.value) }))
    .filter((item): item is { entry: ChartEntry; value: number } => item.value != null)
)

const canDrawChart = computed(() => metricEntries.value.length > 0)

const chartDomain = computed(() =>
  createDomain(
    metricEntries.value.map(item => item.value),
    selectedMetric.value
  )
)

const chartGridLines = computed<GridLine[]>(() => {
  const { min, max } = chartDomain.value
  const range = Math.max(1, max - min)

  return Array.from({ length: GRID_LINE_COUNT }, (_item, index) => {
    const ratio = index / (GRID_LINE_COUNT - 1)
    const value = max - ratio * range
    return {
      value,
      label: formatAxisValue(value, selectedMetric.value),
      y: valueToY(value, min, max)
    }
  })
})

const chartPoints = computed<ChartPoint[]>(() => {
  if (!canDrawChart.value) {
    return []
  }

  const { min, max } = chartDomain.value
  const denominator = Math.max(1, metricEntries.value.length - 1)

  return metricEntries.value.map((item, index) => {
    const x = metricEntries.value.length === 1
      ? CHART_LEFT + (CHART_WIDTH - CHART_LEFT - CHART_RIGHT) / 2
      : CHART_LEFT + (index / denominator) * (CHART_WIDTH - CHART_LEFT - CHART_RIGHT)
    return {
      entry: item.entry,
      value: item.value,
      x,
      y: valueToY(item.value, min, max),
      label: index + 1
    }
  })
})

const chartPolyline = computed(() => chartPoints.value.map(point => `${point.x},${point.y}`).join(' '))

const hoverValueBadge = computed<HoverValueBadge | null>(() => {
  const point = activePoint.value
  if (!point) {
    return null
  }

  const label = formatMetricValue(point.value, selectedMetric.value)
  const width = Math.min(128, Math.max(54, label.length * 8 + 22))
  const halfWidth = width / 2
  return {
    label,
    x: Math.min(CHART_WIDTH - CHART_RIGHT - halfWidth, Math.max(CHART_LEFT + halfWidth, point.x)),
    y: Math.max(CHART_TOP + 30, point.y - 10),
    width
  }
})

const metricLabel = computed(() =>
  METRIC_OPTIONS.find(option => option.value === selectedMetric.value)?.label || ''
)

const emptyText = computed(() => {
  if (!props.connected || !props.puuid) {
    return '连接客户端后查看战绩趋势'
  }
  if (loading.value) {
    return '正在读取最近战绩...'
  }
  if (error.value) {
    return error.value
  }
  if (filteredEntries.value.length === 0) {
    return '请先多打排位哦~'
  }
  return '暂无数据'
})

watch(
  [() => props.puuid, () => props.connected, selectedQueue],
  () => {
    void loadChartData()
  },
  { immediate: true }
)

watch([selectedMetric, selectedLane], () => {
  activePoint.value = null
})

async function loadChartData() {
  const puuid = props.puuid
  const currentRequestId = ++requestId
  activePoint.value = null

  if (!props.connected || !puuid) {
    entries.value = []
    error.value = ''
    return
  }

  loading.value = true
  error.value = ''

  try {
    const matches = await apiClient.getFilteredMatchHistory(puuid, {
      begIndex: 0,
      endIndex: 99,
      queueId: selectedQueue.value,
      maxResults: LOOKBACK_MATCH_COUNT
    })
    const orderedMatches = matches
      .slice()
      .sort((a, b) => (b.gameCreation || 0) - (a.gameCreation || 0))
      .slice(0, LOOKBACK_MATCH_COUNT)

    const detailResults = await Promise.allSettled(
      orderedMatches.map(match => apiClient.getGameDetail(match.gameId))
    )

    const nextEntries = orderedMatches
      .map((match, index) => createEntry(match, detailResults[index], puuid))
      .filter((entry): entry is ChartEntry => Boolean(entry))

    if (currentRequestId === requestId) {
      entries.value = nextEntries
    }
  } catch (loadError) {
    console.error('Failed to load home chart data', loadError)
    if (currentRequestId === requestId) {
      entries.value = []
      error.value = '战绩趋势加载失败'
    }
  } finally {
    if (currentRequestId === requestId) {
      loading.value = false
    }
  }
}

function createEntry(
  match: MatchHistory,
  detailResult: PromiseSettledResult<GameDetail>,
  puuid: string
): ChartEntry | null {
  const participantId = match.participantIdentities.find(identity => identity.player.puuid === puuid)?.participantId
  if (!participantId) {
    return null
  }

  const matchParticipant = match.participants.find(participant => participant.participantId === participantId)
  const detail = detailResult.status === 'fulfilled' ? detailResult.value : null
  const detailParticipant = detail?.participants.find(participant => participant.participantId === participantId)
  const stats = (detailParticipant?.stats || matchParticipant?.stats) as StatsLike | undefined

  if (!stats) {
    return null
  }

  const lane = resolveLane(detailParticipant, matchParticipant)
  const kills = stats.kills || 0
  const deaths = stats.deaths || 0
  const assists = stats.assists || 0
  const gold = stats.goldEarned || 0
  const totalDamage = stats.totalDamageDealtToChampions || 0

  return {
    gameId: match.gameId,
    gameCreation: match.gameCreation,
    championId: detailParticipant?.championId || matchParticipant?.championId || 0,
    win: Boolean(stats.win),
    lane,
    laneLabel: LANE_OPTIONS.find(option => option.value === lane)?.label || '未知',
    kills,
    deaths,
    assists,
    kdaText: `${kills}/${deaths}/${assists}`,
    gold,
    totalDamage,
    damageRate: calculateDamageConversion(stats),
    goldDiff15: calculateGoldDiff15(detail, detailParticipant, lane, stats, match.gameDuration),
    visionScore: readVisionScore(stats)
  }
}

function resolveLane(
  detailParticipant?: DetailParticipant,
  matchParticipant?: MatchParticipant
): LaneKey {
  const detailRecord = toRecord(detailParticipant)
  const matchRecord = toRecord(matchParticipant)
  const timeline = toRecord(detailRecord?.timeline) || toRecord(matchRecord?.timeline)

  return normalizeLane(
    readString(timeline, ['lane']) || readString(detailRecord, ['lane']),
    readString(timeline, ['role']) || readString(detailRecord, ['role']),
    readString(detailRecord, ['teamPosition']) || readString(matchRecord, ['teamPosition']),
    readString(detailRecord, ['individualPosition']) || readString(matchRecord, ['individualPosition'])
  )
}

function normalizeLane(
  lane?: string,
  role?: string,
  teamPosition?: string,
  individualPosition?: string
): LaneKey {
  const laneKey = normalizePosition(lane)
  const roleKey = normalizePosition(role)
  const teamPositionKey = normalizePosition(teamPosition)
  const individualPositionKey = normalizePosition(individualPosition)
  const positionCandidates = [laneKey, teamPositionKey, individualPositionKey, roleKey]

  if (positionCandidates.some(value => value === 'JUNGLE' || value === '打野')) {
    return 'jungle'
  }
  if (positionCandidates.some(value => value === 'TOP' || value === '上路')) {
    return 'top'
  }
  if (positionCandidates.some(value => value === 'MIDDLE' || value === 'MID' || value === '中路')) {
    return 'mid'
  }
  if (
    roleKey.includes('SUPPORT') ||
    teamPositionKey === 'UTILITY' ||
    teamPositionKey === 'SUPPORT' ||
    individualPositionKey === 'UTILITY' ||
    individualPositionKey === 'SUPPORT' ||
    laneKey === 'UTILITY' ||
    laneKey === '辅助'
  ) {
    return 'support'
  }
  if (
    laneKey === 'BOTTOM' ||
    laneKey === 'BOT' ||
    laneKey === '下路' ||
    roleKey === 'DUO_CARRY' ||
    teamPositionKey === 'BOTTOM' ||
    teamPositionKey === 'BOT' ||
    individualPositionKey === 'BOTTOM' ||
    individualPositionKey === 'BOT'
  ) {
    return 'bottom'
  }

  return 'unknown'
}

function calculateDamageConversion(stats: StatsLike): number | null {
  const statsRecord = toRecord(stats)
  const challenges = toRecord(statsRecord?.challenges)
  const challengeValue = readNumber(challenges, ['damagePerGold'])
  if (challengeValue != null) {
    return challengeValue * 100
  }

  const damage = stats.totalDamageDealtToChampions || 0
  const gold = stats.goldEarned || 0
  if (damage <= 0 || gold <= 0) {
    return null
  }
  return (damage / gold) * 100
}

function calculateGoldDiff15(
  detail: GameDetail | null,
  detailParticipant: DetailParticipant | undefined,
  lane: LaneKey,
  stats: StatsLike,
  gameDuration: number
): number | null {
  const statsRecord = toRecord(stats)
  const challenges = toRecord(statsRecord?.challenges)
  const directValue = readNumber(statsRecord, [
    'goldDiff15',
    'goldDiffAt15',
    'goldDifferenceAt15',
    'fifteenMinuteGoldDiff'
  ]) ?? readNumber(challenges, [
    'goldDiff15',
    'goldDiffAt15',
    'goldDifferenceAt15',
    'fifteenMinuteGoldDiff'
  ])

  if (directValue != null) {
    return directValue
  }

  if (!detail || !detailParticipant || lane === 'unknown') {
    return null
  }

  const opponent = detail.participants.find(participant =>
    participant.teamId !== detailParticipant.teamId && resolveLane(participant) === lane
  )
  if (!opponent?.stats) {
    return null
  }

  const minutes = Math.max(1, (detail.gameDuration || gameDuration || 0) / 60)
  const ownCs = getCreepScore(stats)
  const opponentCs = getCreepScore(opponent.stats as StatsLike)
  return Math.round(((ownCs - opponentCs) / minutes) * 15 * 21)
}

function readVisionScore(stats: StatsLike): number | null {
  const statsRecord = toRecord(stats)
  if (!statsRecord) {
    return null
  }
  if (Object.prototype.hasOwnProperty.call(statsRecord, 'visionScore')) {
    return readNumber(statsRecord, ['visionScore']) ?? 0
  }

  const challenges = toRecord(statsRecord.challenges)
  if (challenges && Object.prototype.hasOwnProperty.call(challenges, 'visionScore')) {
    return readNumber(challenges, ['visionScore']) ?? 0
  }

  return null
}

function getCreepScore(stats: StatsLike): number {
  return (stats.totalMinionsKilled || 0) + (stats.neutralMinionsKilled || 0)
}

function getMetricValue(entry: ChartEntry, metric: MetricKey): number | null {
  if (metric === 'kda') {
    return (entry.kills + entry.assists) / Math.max(1, entry.deaths)
  }
  if (metric === 'kills') {
    return entry.kills
  }
  if (metric === 'deaths') {
    return entry.deaths
  }
  if (metric === 'assists') {
    return entry.assists
  }
  if (metric === 'gold') {
    return entry.gold
  }
  if (metric === 'damageRate') {
    return entry.damageRate
  }
  if (metric === 'goldDiff15') {
    return entry.goldDiff15
  }
  return entry.visionScore
}

function createDomain(values: number[], metric: MetricKey): { min: number; max: number } {
  if (!values.length) {
    return { min: 0, max: 1 }
  }

  let min = Math.min(...values)
  let max = Math.max(...values)
  const rawMin = min

  if (min === max) {
    const padding = min === 0 ? 1 : Math.abs(min) * 0.15
    min -= padding
    max += padding
  } else {
    const padding = (max - min) * 0.08
    min -= padding
    max += padding
  }

  if (shouldAnchorZero(metric) && rawMin >= 0) {
    min = 0
  }

  return { min, max }
}

function shouldAnchorZero(metric: MetricKey): boolean {
  return (
    metric === 'kda' ||
    metric === 'kills' ||
    metric === 'deaths' ||
    metric === 'assists' ||
    metric === 'gold' ||
    metric === 'visionScore'
  )
}

function valueToY(value: number, min: number, max: number): number {
  const range = Math.max(1, max - min)
  return CHART_TOP + (1 - (value - min) / range) * (CHART_HEIGHT - CHART_TOP - CHART_BOTTOM)
}

function formatMetricValue(value: number | null, metric = selectedMetric.value): string {
  if (value == null) {
    return '暂无数据'
  }
  if (metric === 'kda') {
    return value.toFixed(2)
  }
  if (metric === 'damageRate') {
    return `${value.toFixed(2)}%`
  }
  if (metric === 'goldDiff15') {
    const roundedValue = Math.round(value)
    return `${roundedValue > 0 ? '+' : ''}${roundedValue.toLocaleString()}`
  }
  return Math.round(value).toLocaleString()
}

function formatAxisValue(value: number, metric: MetricKey): string {
  if (metric === 'damageRate') {
    return `${value.toFixed(1)}%`
  }
  if (metric === 'kda') {
    return value.toFixed(1)
  }
  if (metric === 'goldDiff15') {
    const roundedValue = Math.round(value)
    return `${roundedValue > 0 ? '+' : ''}${roundedValue.toLocaleString()}`
  }
  if (metric === 'gold' && Math.abs(value) >= 1000) {
    return `${Math.round(value / 1000)}k`
  }
  return Math.round(value).toLocaleString()
}

function formatMatchTime(timestamp: number): string {
  if (!timestamp) {
    return '--'
  }

  const diff = Date.now() - timestamp
  if (diff < 0) {
    return formatDate(timestamp)
  }

  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  const days = Math.floor(diff / day)
  if (days >= 1 && days < 7) {
    return `${days}天前`
  }
  if (days >= 7) {
    return formatDate(timestamp)
  }

  const hours = Math.floor(diff / hour)
  if (hours >= 1) {
    return `${hours}小时前`
  }

  const minutes = Math.floor(diff / minute)
  return minutes >= 1 ? `${minutes}分钟前` : '刚刚'
}

function formatDate(timestamp: number): string {
  if (!timestamp) {
    return '--'
  }
  const date = new Date(timestamp)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function championIconUrl(championId: number): string {
  return championId > 0
    ? `http://127.0.0.1:8080/api/v1/asset/champion/${championId}`
    : ''
}

function pointAriaLabel(point: ChartPoint): string {
  return `${point.entry.laneLabel} ${point.entry.kdaText} ${metricLabel.value} ${formatMetricValue(point.value)}`
}

function normalizePosition(value?: string): string {
  return (value || '').trim().toUpperCase().replace(/\s+/g, '_')
}

function toRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' ? value as Record<string, unknown> : null
}

function readString(record: Record<string, unknown> | null, keys: string[]): string | undefined {
  for (const key of keys) {
    const value = record?.[key]
    if (typeof value === 'string' && value.trim()) {
      return value
    }
  }
  return undefined
}

function readNumber(record: Record<string, unknown> | null, keys: string[]): number | null {
  for (const key of keys) {
    const value = record?.[key]
    if (value === null) {
      return null
    }
    const numberValue = typeof value === 'number'
      ? value
      : typeof value === 'string'
        ? Number(value)
        : Number.NaN
    if (Number.isFinite(numberValue)) {
      return numberValue
    }
  }
  return null
}
</script>

<template>
  <section class="home-chart-card">
    <div class="chart-card-header">
      <div>
        <h2>战绩趋势</h2>
      </div>
      <div class="chart-toolbar">
        <div class="queue-tabs" aria-label="排位队列">
          <button
            v-for="option in QUEUE_OPTIONS"
            :key="option.value"
            type="button"
            :class="{ active: selectedQueue === option.value }"
            @click="selectedQueue = option.value"
          >
            {{ option.label }}
          </button>
        </div>
        <select v-model="selectedMetric" class="chart-select" aria-label="筛选数据">
          <option v-for="option in METRIC_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
        <select v-model="selectedLane" class="chart-select" aria-label="分路">
          <option v-for="option in LANE_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>
    </div>

    <div class="chart-surface">
      <div v-if="!canDrawChart" class="chart-empty-state">
        {{ emptyText }}
      </div>
      <svg v-else viewBox="0 0 760 280" role="img" :aria-label="`最近 10 局${metricLabel}趋势`">
        <g v-for="line in chartGridLines" :key="`grid-${line.label}-${line.y}`">
          <line
            :x1="CHART_LEFT"
            :y1="line.y"
            :x2="CHART_WIDTH - CHART_RIGHT"
            :y2="line.y"
            class="chart-guide"
          />
          <text class="axis-label y-axis-label" x="8" :y="line.y + 4">
            {{ line.label }}
          </text>
        </g>
        <line :x1="CHART_LEFT" :y1="CHART_TOP" :x2="CHART_LEFT" :y2="CHART_HEIGHT - CHART_BOTTOM" class="chart-axis" />
        <line
          :x1="CHART_LEFT"
          :y1="CHART_HEIGHT - CHART_BOTTOM"
          :x2="CHART_WIDTH - CHART_RIGHT"
          :y2="CHART_HEIGHT - CHART_BOTTOM"
          class="chart-axis"
        />
        <polyline v-if="chartPoints.length > 1" class="trend-line" :points="chartPolyline" />
        <g
          v-for="point in chartPoints"
          :key="point.entry.gameId"
          class="trend-point"
          tabindex="0"
          :aria-label="pointAriaLabel(point)"
          @mouseenter="activePoint = point"
          @mouseleave="activePoint = null"
          @focus="activePoint = point"
          @blur="activePoint = null"
        >
          <circle class="trend-hit" :cx="point.x" :cy="point.y" r="16" />
          <circle class="trend-dot" :class="{ loss: !point.entry.win }" :cx="point.x" :cy="point.y" r="6" />
        </g>
        <text
          v-for="point in chartPoints"
          :key="`label-${point.entry.gameId}`"
          class="x-label"
          :x="point.x"
          y="260"
        >
          {{ point.label }}
        </text>
        <g
          v-if="hoverValueBadge"
          class="metric-hover-badge"
          :transform="`translate(${hoverValueBadge.x} ${hoverValueBadge.y})`"
        >
          <rect :x="-hoverValueBadge.width / 2" y="-28" :width="hoverValueBadge.width" height="24" rx="8" />
          <text y="-11" text-anchor="middle">{{ hoverValueBadge.label }}</text>
        </g>
      </svg>
      <div v-if="activePoint" class="chart-hover-panel">
        <img
          v-if="championIconUrl(activePoint.entry.championId)"
          class="hover-champion"
          :src="championIconUrl(activePoint.entry.championId)"
          alt=""
        />
        <div class="hover-copy">
          <strong>{{ activePoint.entry.kdaText }}</strong>
          <span>{{ formatMatchTime(activePoint.entry.gameCreation) }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.home-chart-card {
  --chart-theme-glow: 0 0 0 1px rgba(212, 175, 55, 0.28), 0 0 18px rgba(212, 175, 55, 0.24);
  --chart-surface-glow: inset 0 -1px 0 rgba(212, 175, 55, 0.34), 0 10px 24px rgba(212, 175, 55, 0.1);
  --chart-glow-border: rgba(212, 175, 55, 0.42);
  padding: 22px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  transition: border-color 0.3s ease, box-shadow 0.3s ease;
}

.home-chart-card:hover {
  border-color: var(--chart-glow-border);
  box-shadow: var(--chart-theme-glow);
}

.chart-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.chart-card-header h2 {
  margin: 0 0 6px;
  color: var(--text-primary);
  font-size: 24px;
}

.chart-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}

.queue-tabs {
  display: inline-flex;
  padding: 4px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--bg-tertiary);
}

.queue-tabs button {
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 800;
}

.queue-tabs button.active {
  background: var(--accent-color);
  color: #ffffff;
}

.chart-select {
  min-height: 38px;
  padding: 0 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 800;
}

.chart-surface {
  position: relative;
  min-height: 318px;
  padding: 18px;
  border-radius: 10px;
  background: var(--bg-tertiary);
  overflow: hidden;
  transition: box-shadow 0.3s ease, background 0.3s ease;
}

.home-chart-card:hover .chart-surface {
  box-shadow: var(--chart-surface-glow);
}

.chart-surface svg {
  display: block;
  width: 100%;
  min-height: 280px;
}

.chart-empty-state {
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  font-size: 18px;
  font-weight: 800;
}

.chart-axis {
  stroke: rgba(var(--accent-rgb), 0.7);
  stroke-width: 2;
}

.chart-guide {
  stroke: rgba(255, 255, 255, 0.1);
  stroke-width: 1;
}

.trend-line {
  fill: none;
  stroke: var(--accent-color);
  stroke-width: 4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.trend-point {
  outline: none;
}

.metric-hover-badge {
  position: relative;
  z-index: 999;
  pointer-events: none;
}

.metric-hover-badge rect {
  fill: rgba(14, 15, 19, 0.86);
  stroke: var(--accent-color);
  stroke-width: 1;
}

.metric-hover-badge text {
  fill: var(--accent-color);
  font-size: 14px;
  font-weight: 900;
}

.trend-hit {
  fill: transparent;
  cursor: pointer;
}

.trend-dot {
  fill: var(--success-color);
  stroke: var(--bg-tertiary);
  stroke-width: 3;
}

.trend-dot.loss {
  fill: var(--error-color);
}

.axis-label,
.x-label {
  fill: var(--text-secondary);
  font-weight: 800;
}

.axis-label {
  font-size: 12px;
}

.x-label {
  font-size: 13px;
  text-anchor: middle;
}

.y-axis-label {
  text-anchor: start;
}

.chart-hover-panel {
  position: absolute;
  right: 18px;
  bottom: 18px;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 158px;
  padding: 10px 12px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 10px;
  background: rgba(14, 15, 19, 0.82);
  color: #ffffff;
  box-shadow: 0 12px 26px rgba(0, 0, 0, 0.24);
  pointer-events: none;
}

.hover-champion {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 8px;
  object-fit: cover;
}

.hover-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.2;
}

.hover-copy strong {
  color: #ffffff;
  font-size: 15px;
  font-weight: 900;
}

.hover-copy span {
  color: rgba(255, 255, 255, 0.72);
  font-size: 12px;
  font-weight: 700;
}

:global([data-theme="light"] .home-chart-card) {
  --chart-theme-glow: 0 0 0 1px rgba(100, 116, 139, 0.18), 0 0 16px rgba(100, 116, 139, 0.18);
  --chart-surface-glow: inset 0 -1px 0 rgba(100, 116, 139, 0.22), 0 10px 22px rgba(100, 116, 139, 0.1);
  --chart-glow-border: rgba(100, 116, 139, 0.28);
}

@media (max-width: 920px) {
  .chart-card-header {
    flex-direction: column;
  }

  .chart-toolbar {
    justify-content: flex-start;
  }
}
</style>
