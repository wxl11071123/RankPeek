<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { apiClient } from '@/api/httpClient'
import {
  createHomeChartEntries,
  mergeHomeChartDetail,
  runWithConcurrencyLimit
} from '@/services/homeChartEntries'
import { loadReliableMatchHistory } from '@/services/reliableMatchHistory'
import type { HomeChartEntry } from '@/services/homeChartEntries'
import type { MatchHistory, Summoner } from '@/types/api'
import { getChampionIconUrl, markAssetLoadFailed } from '@/utils/gameAssetUrls'

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
type LaneKey = HomeChartEntry['lane']
type ChartEntry = HomeChartEntry

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
  summoner?: Summoner | null
  puuid?: string
  connected: boolean
}>()

const CONTROL_GLOW_RANGE = 96
const SURFACE_GLOW_RANGE = 220
const EDGE_GLOW_MIN = 0.03

function isDisabledControl(target: HTMLElement) {
  return (target instanceof HTMLButtonElement || target instanceof HTMLSelectElement) && target.disabled
}

function resetEdgeGlow(target: HTMLElement) {
  target.style.setProperty('--edge-top-alpha', '0')
  target.style.setProperty('--edge-right-alpha', '0')
  target.style.setProperty('--edge-bottom-alpha', '0')
  target.style.setProperty('--edge-left-alpha', '0')
  delete target.dataset.nearGlow
}

function resetGlowElement(target: HTMLElement) {
  target.style.setProperty('--control-glow-x', '50%')
  target.style.setProperty('--control-glow-y', '50%')
  resetEdgeGlow(target)
}

function applyGlowElement(target: HTMLElement, clientX: number, clientY: number) {
  if (isDisabledControl(target)) {
    resetGlowElement(target)
    return
  }

  const rect = target.getBoundingClientRect()
  const range = target.classList.contains('surface-glow') ? SURFACE_GLOW_RANGE : CONTROL_GLOW_RANGE
  const x = clientX - rect.left
  const y = clientY - rect.top
  const clampedX = Math.min(Math.max(x, 0), rect.width)
  const clampedY = Math.min(Math.max(y, 0), rect.height)
  const inRange = x >= -range && x <= rect.width + range && y >= -range && y <= rect.height + range

  target.style.setProperty('--control-glow-x', `${clampedX}px`)
  target.style.setProperty('--control-glow-y', `${clampedY}px`)

  if (!inRange) {
    resetEdgeGlow(target)
    return
  }

  const strength = (distance: number) => {
    const raw = Math.max(0, 1 - Math.min(Math.abs(distance), range) / range)
    return Math.pow(raw, 1.18)
  }

  const top = strength(y)
  const right = strength(rect.width - x)
  const bottom = strength(rect.height - y)
  const left = strength(x)
  const maxStrength = Math.max(top, right, bottom, left)

  target.style.setProperty('--edge-top-alpha', top.toFixed(3))
  target.style.setProperty('--edge-right-alpha', right.toFixed(3))
  target.style.setProperty('--edge-bottom-alpha', bottom.toFixed(3))
  target.style.setProperty('--edge-left-alpha', left.toFixed(3))

  if (maxStrength > EDGE_GLOW_MIN) {
    target.dataset.nearGlow = 'true'
  } else {
    delete target.dataset.nearGlow
  }
}

function updateControlGlow(event: PointerEvent) {
  const target = event.currentTarget as HTMLElement | null
  if (!target) {
    return
  }

  applyGlowElement(target, event.clientX, event.clientY)
  target.querySelectorAll<HTMLElement>('.control-glow').forEach(element => {
    applyGlowElement(element, event.clientX, event.clientY)
  })
}

function resetControlGlow(event: PointerEvent) {
  const target = event.currentTarget as HTMLElement | null
  if (!target) {
    return
  }

  resetGlowElement(target)
  target.querySelectorAll<HTMLElement>('.control-glow').forEach(resetGlowElement)
}

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
const CHART_POINT_SIZE = 20
const CHART_POINT_RADIUS = CHART_POINT_SIZE / 2
const CHART_POINT_CORNER_RADIUS = 5
const CHART_POINT_RING_SIZE = CHART_POINT_SIZE + 2
const CHART_POINT_RING_RADIUS = CHART_POINT_RING_SIZE / 2
const CHART_POINT_RING_CORNER_RADIUS = CHART_POINT_CORNER_RADIUS + 1
const CHART_POINT_ACTIVE_SIZE = CHART_POINT_RING_SIZE + 6
const CHART_POINT_ACTIVE_RADIUS = CHART_POINT_ACTIVE_SIZE / 2
const CHART_POINT_ACTIVE_CORNER_RADIUS = CHART_POINT_RING_CORNER_RADIUS + 2
const CHART_POINT_HIT_RADIUS = 16
const GRID_LINE_COUNT = 4
const MAX_DISPLAY_MATCHES = 10
const LOOKBACK_MATCH_COUNT = 50
const DETAIL_REQUEST_CONCURRENCY = 4
const LOAD_ERROR_TEXT = '战绩趋势加载失败'

const selectedQueue = ref<QueueMode>(420)
const selectedMetric = ref<MetricKey>('kda')
const selectedLane = ref<LaneKey>('all')
const entries = ref<ChartEntry[]>([])
const loading = ref(false)
const error = ref('')
const activePoint = ref<ChartPoint | null>(null)
let requestId = 0
let detailRequestId = 0
let activeDataKey = ''

const currentPuuid = computed(() => props.puuid || props.summoner?.puuid || '')

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
  [currentPuuid, () => props.connected, selectedQueue],
  () => {
    void loadChartData()
  },
  { immediate: true }
)

watch([selectedMetric, selectedLane], () => {
  activePoint.value = null
})

async function loadChartData() {
  const puuid = currentPuuid.value
  const currentRequestId = ++requestId
  const dataKey = `${puuid}:${selectedQueue.value}`
  activePoint.value = null

  if (!props.connected || !puuid) {
    entries.value = []
    error.value = ''
    loading.value = false
    activeDataKey = ''
    return
  }

  if (dataKey !== activeDataKey) {
    entries.value = []
    activeDataKey = dataKey
  }

  loading.value = true
  error.value = ''
  let receivedUsableMatches = false

  const applyMatches = (matches: MatchHistory[]) => {
    if (currentRequestId !== requestId) {
      return
    }

    entries.value = createHomeChartEntries(matches, puuid)
    receivedUsableMatches = entries.value.length > 0
    loading.value = false
    const currentDetailRequestId = ++detailRequestId
    void hydrateEntryDetails(matches, puuid, currentRequestId, currentDetailRequestId)
  }

  try {
    const result = await loadReliableMatchHistory({
      summoner: props.summoner ?? null,
      currentPuuid: puuid,
      queueId: selectedQueue.value,
      limit: LOOKBACK_MATCH_COUNT,
      minQualityMatches: MAX_DISPLAY_MATCHES,
      forceRefresh: true,
      onUpdate: update => applyMatches(update.matches)
    })
    if (currentRequestId === requestId) {
      if (!receivedUsableMatches && result.matches.length > 0) {
        applyMatches(result.matches)
      }
      if (!entries.value.length && result.errors.length > 0) {
        error.value = LOAD_ERROR_TEXT
      }
    }
  } catch (loadError) {
    console.error('Failed to load home chart data', loadError)
    if (currentRequestId === requestId && !entries.value.length) {
      error.value = LOAD_ERROR_TEXT
    }
  } finally {
    if (currentRequestId === requestId) {
      loading.value = false
    }
  }
}

async function hydrateEntryDetails(
  matches: MatchHistory[],
  puuid: string,
  chartRequestId: number,
  currentDetailRequestId: number
) {
  const matchesToHydrate = matches.slice(0, LOOKBACK_MATCH_COUNT)
  await runWithConcurrencyLimit(matchesToHydrate, DETAIL_REQUEST_CONCURRENCY, async match => {
    const detail = await apiClient.getGameDetail(match.gameId)
    if (chartRequestId !== requestId || currentDetailRequestId !== detailRequestId) {
      return
    }

    entries.value = entries.value.map(entry =>
      entry.gameId === match.gameId
        ? mergeHomeChartDetail(entry, match, detail, puuid)
        : entry
    )
  }, (detailError, match) => {
    console.warn(`Failed to hydrate home chart detail for game ${match.gameId}`, detailError)
  })
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

function resolveChampionCdnUrl(championId: number): string {
  return getChampionIconUrl(championId)
}

function pointClipId(point: ChartPoint): string {
  return `home-chart-point-${point.entry.gameId}`
}

function pointAriaLabel(point: ChartPoint): string {
  return `${point.entry.laneLabel} ${point.entry.kdaText} ${metricLabel.value} ${formatMetricValue(point.value)}`
}

</script>

<template>
  <section
    class="home-chart-card surface-glow"
    @pointermove="updateControlGlow"
    @pointerleave="resetControlGlow"
  >
    <div class="chart-card-header">
      <div>
        <h2>战绩趋势</h2>
      </div>
      <div class="chart-toolbar">
        <div
          class="queue-tabs control-glow"
          aria-label="排位队列"
          @pointermove="updateControlGlow"
          @pointerleave="resetControlGlow"
        >
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
        <select
          v-model="selectedMetric"
          class="chart-select control-glow"
          aria-label="选择指标"
          @pointermove="updateControlGlow"
          @pointerleave="resetControlGlow"
        >
          <option v-for="option in METRIC_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
        <select
          v-model="selectedLane"
          class="chart-select control-glow"
          aria-label="选择分路"
          @pointermove="updateControlGlow"
          @pointerleave="resetControlGlow"
        >
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
        <defs>
          <clipPath
            v-for="point in chartPoints"
            :id="pointClipId(point)"
            :key="`clip-${point.entry.gameId}`"
            clipPathUnits="userSpaceOnUse"
          >
            <rect
              :x="point.x - CHART_POINT_RADIUS"
              :y="point.y - CHART_POINT_RADIUS"
              :width="CHART_POINT_SIZE"
              :height="CHART_POINT_SIZE"
              :rx="CHART_POINT_CORNER_RADIUS"
              :ry="CHART_POINT_CORNER_RADIUS"
            />
          </clipPath>
        </defs>
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
          <rect
            v-if="activePoint?.entry.gameId === point.entry.gameId"
            class="trend-active-ring"
            :x="point.x - CHART_POINT_ACTIVE_RADIUS"
            :y="point.y - CHART_POINT_ACTIVE_RADIUS"
            :width="CHART_POINT_ACTIVE_SIZE"
            :height="CHART_POINT_ACTIVE_SIZE"
            :rx="CHART_POINT_ACTIVE_CORNER_RADIUS"
            :ry="CHART_POINT_ACTIVE_CORNER_RADIUS"
          />
          <rect
            class="trend-avatar-ring"
            :x="point.x - CHART_POINT_RING_RADIUS"
            :y="point.y - CHART_POINT_RING_RADIUS"
            :width="CHART_POINT_RING_SIZE"
            :height="CHART_POINT_RING_SIZE"
            :rx="CHART_POINT_RING_CORNER_RADIUS"
            :ry="CHART_POINT_RING_CORNER_RADIUS"
          />
          <image
            v-if="resolveChampionCdnUrl(point.entry.championId)"
            class="trend-avatar"
            :href="resolveChampionCdnUrl(point.entry.championId)"
            :x="point.x - CHART_POINT_RADIUS"
            :y="point.y - CHART_POINT_RADIUS"
            :width="CHART_POINT_SIZE"
            :height="CHART_POINT_SIZE"
            :clip-path="`url(#${pointClipId(point)})`"
            preserveAspectRatio="xMidYMid slice"
          />
          <rect
            v-else
            class="trend-avatar-fallback"
            :x="point.x - CHART_POINT_RADIUS"
            :y="point.y - CHART_POINT_RADIUS"
            :width="CHART_POINT_SIZE"
            :height="CHART_POINT_SIZE"
            :rx="CHART_POINT_CORNER_RADIUS"
            :ry="CHART_POINT_CORNER_RADIUS"
          />
          <circle class="trend-hit" :cx="point.x" :cy="point.y" :r="CHART_POINT_HIT_RADIUS" />
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
          v-if="resolveChampionCdnUrl(activePoint.entry.championId)"
          class="hover-champion"
          :src="resolveChampionCdnUrl(activePoint.entry.championId)"
          alt=""
          @error="markAssetLoadFailed"
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
  --module-edge-color: rgba(232, 221, 186, 0.46);
  --module-edge-soft: rgba(96, 176, 255, 0.14);
  --module-edge-glow: 0 0 0 1px rgba(96, 176, 255, 0.14), 0 10px 24px rgba(41, 151, 255, 0.1);
  --chart-module-hover-border: var(--home-module-hover-border, rgba(96, 176, 255, 0.48));
  --chart-module-hover-shadow: var(
    --home-module-hover-shadow,
    0 0 0 1px rgba(96, 176, 255, 0.16),
    0 0 18px rgba(41, 151, 255, 0.18),
    0 12px 28px rgba(41, 151, 255, 0.08)
  );
  --chart-hover-border: var(--chart-module-hover-border);
  --chart-hover-shadow: var(--chart-module-hover-shadow);
  --chart-bright-blue-rgb: 33, 196, 255;
  --chart-bright-blue: rgb(var(--chart-bright-blue-rgb));
  --control-glow-x: 50%;
  --control-glow-y: 50%;
  --control-edge-width: 1px;
  --control-edge-offset: -1px;
  --edge-glow-size: 82px;
  --chart-control-local-glow: transparent;
  --chart-control-local-glow-fade: transparent;
  --chart-control-border-local-glow: rgba(78, 215, 255, 0.98);
  --chart-control-border-local-glow-fade: rgba(41, 151, 255, 0.48);
  --chart-control-edge-rgb: 78, 215, 255;
  --chart-control-edge-shadow:
    inset 0 1px 0 rgba(var(--chart-control-edge-rgb), calc(var(--edge-top-alpha) * 0.82)),
    inset -1px 0 0 rgba(var(--chart-control-edge-rgb), calc(var(--edge-right-alpha) * 0.82)),
    inset 0 -1px 0 rgba(var(--chart-control-edge-rgb), calc(var(--edge-bottom-alpha) * 0.82)),
    inset 1px 0 0 rgba(var(--chart-control-edge-rgb), calc(var(--edge-left-alpha) * 0.82)),
    0 -3px 11px -6px rgba(var(--chart-control-edge-rgb), calc(var(--edge-top-alpha) * 0.48)),
    3px 0 11px -6px rgba(var(--chart-control-edge-rgb), calc(var(--edge-right-alpha) * 0.48)),
    0 3px 11px -6px rgba(var(--chart-control-edge-rgb), calc(var(--edge-bottom-alpha) * 0.48)),
    -3px 0 11px -6px rgba(var(--chart-control-edge-rgb), calc(var(--edge-left-alpha) * 0.48));
  --chart-control-active-local-glow: transparent;
  --chart-control-active-local-glow-fade: transparent;
  --chart-control-radius: 10px;
  --chart-control-bg: var(--bg-secondary);
  --chart-control-bg-hover: rgba(28, 36, 48, 0.96);
  --chart-control-bg-hover-local: linear-gradient(var(--chart-control-bg-hover), var(--chart-control-bg-hover)) padding-box,
    radial-gradient(
      circle at var(--control-glow-x) var(--control-glow-y),
      var(--chart-control-border-local-glow) 0%,
      var(--chart-control-border-local-glow-fade) 36%,
      var(--chart-control-border-hover) 72%
    ) border-box;
  --chart-control-bg-active: rgba(13, 17, 24, 0.98);
  --chart-control-active-bg: rgba(var(--chart-bright-blue-rgb), 0.78);
  --chart-control-active-bg-hover-local: linear-gradient(var(--chart-control-active-bg), var(--chart-control-active-bg)) padding-box,
    radial-gradient(
      circle at var(--control-glow-x) var(--control-glow-y),
      var(--chart-control-border-local-glow) 0%,
      var(--chart-control-border-local-glow-fade) 36%,
      var(--chart-control-border-hover) 72%
    ) border-box;
  --chart-control-active-bg-pressed: rgba(var(--chart-bright-blue-rgb), 0.58);
  --chart-control-border: var(--border-color);
  --chart-control-border-hover: rgba(96, 176, 255, 0.58);
  --chart-control-text: var(--text-primary);
  --chart-control-active-text: #f5faff;
  --chart-control-muted: var(--text-secondary);
  --chart-control-shadow: none;
  --chart-control-focus: 0 0 0 1px rgba(41, 151, 255, 0.16), 0 0 16px rgba(41, 151, 255, 0.22);
  --chart-control-active-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.34), 0 0 0 1px rgba(41, 151, 255, 0.14);
  --chart-tab-thumb-hover-shadow: var(--chart-control-focus);
  --chart-tab-shell-bg: var(--bg-secondary);
  --chart-select-menu-bg: #161b24;
  --chart-select-menu-text: var(--text-primary);
  --chart-surface-shadow: inset 0 0 0 1px var(--module-edge-soft);
  --chart-avatar-ring: #1d1d1f;
  --chart-badge-bg: rgba(14, 15, 19, 0.86);
  --chart-hover-panel-bg: rgba(14, 15, 19, 0.82);
  --chart-hover-panel-border: rgba(255, 255, 255, 0.14);
  padding: 22px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: none;
  transition: border-color 0.3s ease, box-shadow 0.3s ease;
}

.home-chart-card:hover,
.home-chart-card:focus-within {
  border-color: var(--chart-module-hover-border);
  box-shadow: var(--chart-module-hover-shadow);
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

.control-glow {
  --control-glow-x: 50%;
  --control-glow-y: 50%;
  --control-edge-width: 1px;
  --control-edge-offset: -1px;
  --edge-glow-size: 82px;
  --edge-top-alpha: 0;
  --edge-right-alpha: 0;
  --edge-bottom-alpha: 0;
  --edge-left-alpha: 0;
  position: relative;
  isolation: isolate;
  overflow: visible;
}

.surface-glow {
  --control-glow-x: 50%;
  --control-glow-y: 50%;
  --control-edge-width: 1px;
  --control-edge-offset: -1px;
  --edge-glow-size: 220px;
  --edge-top-alpha: 0;
  --edge-right-alpha: 0;
  --edge-bottom-alpha: 0;
  --edge-left-alpha: 0;
  position: relative;
  isolation: isolate;
  overflow: visible;
}

.control-glow::before,
.surface-glow::before {
  content: '';
  position: absolute;
  inset: var(--control-edge-offset);
  border-radius: inherit;
  background: radial-gradient(
    circle var(--edge-glow-size) at calc(var(--control-glow-x) + 1px) calc(var(--control-glow-y) + 1px),
    var(--chart-control-border-local-glow) 0%,
    var(--chart-control-border-local-glow-fade) 42%,
    transparent 78%
  );
  padding: var(--control-edge-width);
  -webkit-mask:
    linear-gradient(#000 0 0) content-box,
    linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.14s ease;
}

.control-glow:hover::before,
.control-glow:focus-visible::before,
.control-glow[data-near-glow='true']::before,
.surface-glow:hover::before,
.surface-glow:focus-visible::before,
.surface-glow[data-near-glow='true']::before {
  opacity: 1;
}

.control-glow:active::before,
.surface-glow:active::before {
  opacity: 0.55;
}

.queue-tabs.control-glow::before,
.chart-select.control-glow::before {
  content: none;
}

.queue-tabs {
  display: inline-flex;
  padding: 4px;
  border: 1px solid var(--chart-control-border);
  border-radius: 999px;
  background: var(--chart-tab-shell-bg);
  box-shadow: var(--chart-control-shadow);
  transition: border-color 0.3s ease, box-shadow 0.3s ease;
}

.queue-tabs button {
  min-height: 30px;
  padding: 0 12px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--chart-control-muted);
  font-size: 13px;
  font-weight: 800;
  transition: background 0.3s ease, box-shadow 0.3s ease, color 0.3s ease;
}

.queue-tabs:hover {
  border-color: transparent;
  background: var(--chart-control-bg-hover-local);
  box-shadow: var(--chart-control-focus), var(--chart-control-edge-shadow);
}

.queue-tabs.control-glow[data-near-glow='true']:not(:hover):not(:focus-visible) {
  border-color: transparent;
  background: linear-gradient(var(--chart-tab-shell-bg), var(--chart-tab-shell-bg)) padding-box,
    radial-gradient(
      circle at var(--control-glow-x) var(--control-glow-y),
      var(--chart-control-border-local-glow) 0%,
      var(--chart-control-border-local-glow-fade) 36%,
      var(--chart-control-border) 72%
    ) border-box;
  box-shadow: none;
}

.queue-tabs button:hover,
.queue-tabs button:focus-visible {
  color: var(--chart-control-text);
  background: var(--chart-control-bg-hover-local);
  box-shadow: none;
  outline: none;
}

.queue-tabs button:active {
  background: var(--chart-control-bg-active);
}

.queue-tabs button.active {
  background: var(--chart-control-active-bg);
  color: var(--chart-control-active-text);
}

.queue-tabs:hover button.active,
.queue-tabs button.active:hover,
.queue-tabs button.active:focus-visible {
  background: var(--chart-control-active-bg-hover-local);
  box-shadow: var(--chart-tab-thumb-hover-shadow), var(--chart-control-edge-shadow);
}

.queue-tabs button.active:active {
  background: var(--chart-control-active-bg-pressed);
}

.chart-select {
  min-height: 38px;
  padding: 0 12px;
  border: 1px solid var(--chart-control-border);
  border-radius: var(--chart-control-radius);
  background: var(--chart-control-bg);
  color: var(--chart-control-text);
  box-shadow: var(--chart-control-shadow);
  font-size: 13px;
  font-weight: 800;
  transition: border-color 0.3s ease, box-shadow 0.3s ease, background 0.3s ease;
}

.chart-select option {
  background: var(--chart-select-menu-bg);
  background-color: var(--chart-select-menu-bg);
  color: var(--chart-select-menu-text);
}

.chart-select:hover,
.chart-select:focus {
  border-color: transparent;
  background: var(--chart-control-bg-hover-local);
  box-shadow: var(--chart-control-focus), var(--chart-control-edge-shadow);
  outline: none;
}

.chart-select.control-glow[data-near-glow='true']:not(:hover):not(:focus) {
  border-color: transparent;
  background: linear-gradient(var(--chart-control-bg), var(--chart-control-bg)) padding-box,
    radial-gradient(
      circle at var(--control-glow-x) var(--control-glow-y),
      var(--chart-control-border-local-glow) 0%,
      var(--chart-control-border-local-glow-fade) 36%,
      var(--chart-control-border) 72%
    ) border-box;
  box-shadow: none;
}

.chart-select:active {
  border-color: var(--chart-control-border);
  background: var(--chart-control-bg-active);
  box-shadow: var(--chart-control-active-shadow), var(--chart-control-edge-shadow);
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
  box-shadow: var(--chart-surface-shadow);
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
  stroke: var(--chart-bright-blue);
  stroke-width: 2;
}

.chart-guide {
  stroke: rgba(255, 255, 255, 0.1);
  stroke-width: 1;
}

.trend-line {
  fill: none;
  stroke: var(--chart-bright-blue);
  stroke-width: 2.5;
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
  fill: var(--chart-badge-bg);
  stroke: var(--chart-bright-blue);
  stroke-width: 1;
}

.metric-hover-badge text {
  fill: var(--chart-bright-blue);
  font-size: 14px;
  font-weight: 900;
}

.trend-hit {
  fill: transparent;
  cursor: pointer;
}

.trend-avatar-ring {
  fill: none;
  stroke: var(--chart-avatar-ring);
  stroke-width: 1;
  filter: drop-shadow(0 2px 5px rgba(0, 0, 0, 0.24));
}

.trend-active-ring {
  fill: none;
  stroke: var(--module-edge-color);
  stroke-width: 2;
  filter: drop-shadow(0 0 8px var(--module-edge-soft));
}

.trend-avatar {
  pointer-events: none;
}

.trend-avatar-fallback {
  fill: var(--chart-bright-blue);
  stroke: var(--chart-avatar-ring);
  stroke-width: 1;
}

.trend-point:focus-visible .trend-active-ring {
  stroke-width: 3;
}

.axis-label,
.x-label {
  fill: rgba(var(--chart-bright-blue-rgb), 0.82);
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
  border: 1px solid var(--chart-hover-panel-border);
  border-radius: 10px;
  background: var(--chart-hover-panel-bg);
  color: #ffffff;
  box-shadow: 0 12px 26px rgba(0, 0, 0, 0.24);
  pointer-events: none;
}

.hover-champion {
  display: block;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 8px;
  object-fit: cover;
}

.hover-champion[data-asset-failed='true'] {
  display: none;
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
  --rp-light-gold-border: var(--border-color);
  --rp-light-gold-border-hover: rgba(86, 109, 134, 0.42);
  --rp-light-gold-edge-core: rgba(78, 215, 255, 0.98);
  --rp-light-gold-edge-fade: rgba(41, 151, 255, 0.48);
  --rp-light-gold-glow: 0 0 0 1px rgba(41, 151, 255, 0.12), 0 0 12px rgba(41, 151, 255, 0.2);
  --rp-light-gold-glow-active: inset 0 1px 2px rgba(17, 77, 116, 0.14), 0 0 0 1px rgba(41, 151, 255, 0.12), 0 0 8px rgba(41, 151, 255, 0.18);
  --rp-light-global-glow: var(--rp-light-gold-glow);
  --rp-gold-border: var(--rp-light-gold-border);
  --rp-gold-border-hover: var(--rp-light-gold-border-hover);
  --rp-gold-glow-soft: none;
  --rp-gold-glow-hover: var(--rp-light-gold-glow);
  --rp-gold-glow-active: var(--rp-light-gold-glow-active);
  --rp-blue-glow-hover: 0 0 0 2px rgba(41, 151, 255, 0.12), 0 0 6px rgba(41, 151, 255, 0.22);
  --module-edge-color: rgba(86, 109, 134, 0.36);
  --module-edge-soft: rgba(86, 109, 134, 0.1);
  --module-edge-glow: 0 0 0 1px rgba(86, 109, 134, 0.1), 0 10px 22px rgba(86, 109, 134, 0.08);
  --chart-module-hover-border: var(--home-module-hover-border, rgba(86, 109, 134, 0.42));
  --chart-module-hover-shadow: var(
    --home-module-hover-shadow,
    0 0 0 1px rgba(86, 109, 134, 0.14),
    0 0 18px rgba(86, 109, 134, 0.14),
    0 12px 28px rgba(86, 109, 134, 0.07)
  );
  --chart-control-bg: var(--bg-secondary);
  --chart-control-bg-hover: rgba(244, 249, 255, 0.98);
  --chart-control-local-glow: transparent;
  --chart-control-local-glow-fade: transparent;
  --control-edge-width: 2px;
  --control-edge-offset: -2px;
  --chart-control-border-local-glow: var(--rp-light-gold-edge-core);
  --chart-control-border-local-glow-fade: var(--rp-light-gold-edge-fade);
  --chart-control-edge-rgb: 78, 215, 255;
  --chart-control-active-local-glow: transparent;
  --chart-control-active-local-glow-fade: transparent;
  --chart-control-bg-active: rgba(232, 241, 252, 0.98);
  --chart-control-active-bg: rgba(41, 151, 255, 0.18);
  --chart-control-active-bg-pressed: rgba(41, 151, 255, 0.26);
  --chart-control-border: var(--rp-gold-border);
  --chart-control-border-hover: var(--rp-gold-border-hover);
  --chart-control-text: #24384d;
  --chart-control-active-text: #24384d;
  --chart-control-muted: #52697f;
  --chart-control-shadow: var(--rp-gold-glow-soft);
  --chart-control-focus: var(--rp-gold-glow-hover);
  --chart-control-active-shadow: var(--rp-gold-glow-active);
  --chart-tab-thumb-hover-shadow: var(--rp-gold-glow-hover);
  --chart-tab-shell-bg: var(--bg-secondary);
  --chart-select-menu-bg: #f7f3e6;
  --chart-select-menu-text: #4f421e;
  --chart-hover-border: var(--chart-module-hover-border);
  --chart-hover-shadow: var(--chart-module-hover-shadow);
  --chart-surface-shadow: inset 0 0 0 1px var(--module-edge-soft);
  --chart-avatar-ring: #ffffff;
  --chart-badge-bg: rgba(255, 255, 255, 0.94);
  --chart-hover-panel-bg: rgba(255, 255, 255, 0.94);
  --chart-hover-panel-border: rgba(0, 113, 227, 0.18);
}

:global([data-theme="light"] .queue-tabs button:hover),
:global([data-theme="light"] .queue-tabs button:focus-visible) {
  background: var(--chart-control-bg-hover-local);
}

:global([data-theme="light"] .queue-tabs:hover button.active),
:global([data-theme="light"] .queue-tabs button.active:hover),
:global([data-theme="light"] .queue-tabs button.active:focus-visible) {
  background: var(--chart-control-active-bg-hover-local);
  box-shadow: var(--chart-tab-thumb-hover-shadow);
}

:global([data-theme="light"] .metric-hover-badge rect) {
  fill: var(--chart-badge-bg);
}

:global([data-theme="light"] .chart-hover-panel) {
  color: var(--text-primary);
}

:global([data-theme="light"] .hover-copy strong) {
  color: var(--text-primary);
}

:global([data-theme="light"] .hover-copy span) {
  color: var(--text-secondary);
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
