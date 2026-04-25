<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { apiClient } from '@/api/httpClient'
import HomeChart from '@/components/HomeChart.vue'
import { useGameStore } from '@/stores/game'
import { isRanked } from '@/utils/constants'
import {
  AUTO_ANALYSIS_INTERVALS,
  FORTUNE_POOL,
  MAX_ANALYSIS_MATCHES,
  MIN_ANALYSIS_MATCHES,
  createGrowthAnalysis,
  drawDailyFortune,
  filterGrowthPoints,
  getCurrentFortune,
  getMetricValue,
  loadAnalysisSnapshot,
  loadAutoAnalysisSettings,
  loadFortuneRecord,
  saveAnalysisSnapshot,
  saveAutoAnalysisSettings,
  saveFortuneRecord
} from '@/utils/homeInsights'
import { t } from '@/i18n'
import type { MatchHistory, QueueInfo } from '@/types/api'
import type {
  AnalysisSnapshot,
  AutoAnalysisSettings,
  Fortune,
  FortuneRecord,
  GrowthMetric,
  GrowthPoint,
  GrowthRole
} from '@/utils/homeInsights'

const gameStore = useGameStore()

const TIER_CN_MAP: Record<string, string> = {
  iron: '黑铁',
  bronze: '青铜',
  silver: '白银',
  gold: '黄金',
  platinum: '铂金',
  emerald: '翡翠',
  diamond: '钻石',
  master: '大师',
  grandmaster: '宗师',
  challenger: '王者',
  坚韧黑铁: '黑铁',
  黑铁: '黑铁',
  英勇黄铜: '青铜',
  青铜: '青铜',
  不屈白银: '白银',
  白银: '白银',
  荣耀黄金: '黄金',
  黄金: '黄金',
  华贵铂金: '铂金',
  铂金: '铂金',
  流光翡翠: '翡翠',
  翡翠: '翡翠',
  璀璨钻石: '钻石',
  钻石: '钻石',
  超凡大师: '大师',
  大师: '大师',
  傲世宗师: '宗师',
  宗师: '宗师',
  最强王者: '王者',
  王者: '王者'
}

const DIVISION_CN_MAP: Record<string, string> = {
  i: '1',
  ii: '2',
  iii: '3',
  iv: '4',
  '1': '1',
  '2': '2',
  '3': '3',
  '4': '4'
}

const UNRANKED_TIER_VALUES = new Set(['', 'unranked', 'none', 'null', 'undefined', '无', '未设置', '未定级'])

const analysis = ref<AnalysisSnapshot | null>(null)
const analysisExpanded = ref(true)
const analysisLoading = ref(false)
const analysisError = ref('')
const selectedRole = ref<GrowthRole>('all')
const selectedMetric = ref<GrowthMetric>('score')
const hoveredPoint = ref<GrowthPoint | null>(null)
const autoAnalysis = ref<AutoAnalysisSettings>({ enabled: false, interval: 10 })

const fortuneRecord = ref<FortuneRecord>({ history: [] })
const currentFortune = ref<Fortune | null>(null)
const fortuneRolling = ref(false)
const rollingFortuneLabel = ref('？？？')
let fortuneTimer: number | null = null

const currentSummoner = computed(() => gameStore.currentSummoner)
const accountKey = computed(() => currentSummoner.value?.puuid || 'local')
const accountConnected = computed(() => gameStore.connected && Boolean(currentSummoner.value))
const soloRank = computed(() => gameStore.soloRank)
const flexRank = computed(() => gameStore.flexRank)
const displayName = computed(() => gameStore.summonerName || t('common.summoner'))
const profileIconUrl = computed(() =>
  currentSummoner.value?.profileIconId
    ? `http://127.0.0.1:8080/api/v1/asset/profile/${currentSummoner.value.profileIconId}`
    : ''
)

const roleOptions = computed<Array<{ value: GrowthRole; label: string }>>(() => [
  { value: 'all', label: t('home.role.all') },
  { value: 'top', label: t('home.role.top') },
  { value: 'jungle', label: t('home.role.jungle') },
  { value: 'mid', label: t('home.role.mid') },
  { value: 'bottom', label: t('home.role.bottom') },
  { value: 'support', label: t('home.role.support') }
])

const metricOptions = computed<Array<{ value: GrowthMetric; label: string }>>(() => [
  { value: 'score', label: t('home.metric.score') },
  { value: 'kda', label: t('home.metric.kda') },
  { value: 'winRate', label: t('home.metric.winRate') },
  { value: 'damage', label: t('home.metric.damage') },
  { value: 'gold', label: t('home.metric.gold') }
])

const visiblePoints = computed(() =>
  analysis.value ? filterGrowthPoints(analysis.value.points, selectedRole.value) : []
)

const chartModel = computed(() => {
  const points = visiblePoints.value
  if (!points.length) {
    return []
  }

  const width = 640
  const height = 240
  const left = 46
  const right = 22
  const top = 24
  const bottom = 42
  const values = points.map(point => getMetricValue(point, selectedMetric.value))
  const minValue = selectedMetric.value === 'score' || selectedMetric.value === 'winRate'
    ? 0
    : Math.min(...values)
  const maxValue = selectedMetric.value === 'score' || selectedMetric.value === 'winRate'
    ? 100
    : Math.max(...values)
  const range = Math.max(1, maxValue - minValue)

  return points.map((point, index) => {
    const x = points.length === 1
      ? width / 2
      : left + (index / (points.length - 1)) * (width - left - right)
    const value = getMetricValue(point, selectedMetric.value)
    const y = top + (1 - (value - minValue) / range) * (height - top - bottom)

    return { point, value, x, y }
  })
})

const chartPolyline = computed(() =>
  chartModel.value.map(item => `${item.x},${item.y}`).join(' ')
)

const fortuneLabel = computed(() => currentFortune.value?.label || '？？？')
const fortuneTone = computed(() => currentFortune.value?.tone || 'neutral')
const fortuneButtonText = computed(() => {
  if (fortuneRolling.value) {
    return t('home.fortuneDrawing')
  }
  return currentFortune.value ? t('home.fortuneComeTomorrow') : t('home.drawFortune')
})

const canAnalyze = computed(() => accountConnected.value && !analysisLoading.value)

onMounted(() => {
  void gameStore.checkConnection()
  loadLocalHomeState()
})

onBeforeUnmount(() => {
  clearFortuneTimer()
})

watch(accountKey, () => {
  loadLocalHomeState()
})

function loadLocalHomeState() {
  const key = accountKey.value
  analysis.value = currentSummoner.value?.puuid
    ? loadAnalysisSnapshot(currentSummoner.value.puuid)
    : null
  autoAnalysis.value = loadAutoAnalysisSettings(key)
  fortuneRecord.value = loadFortuneRecord(key)
  currentFortune.value = getCurrentFortune(fortuneRecord.value)
  analysisError.value = ''
}

async function runAnalysis() {
  const puuid = currentSummoner.value?.puuid
  if (!puuid || analysisLoading.value) {
    return
  }

  analysisLoading.value = true
  analysisError.value = ''

  try {
    const matches = await apiClient.getFilteredMatchHistory(puuid, {
      begIndex: 0,
      endIndex: 49,
      maxResults: 50
    })
    const rankedMatches = matches
      .filter(match => isRanked(match.queueId))
      .sort((a, b) => (b.gameCreation || 0) - (a.gameCreation || 0))
      .slice(0, MAX_ANALYSIS_MATCHES)

    if (rankedMatches.length < MIN_ANALYSIS_MATCHES) {
      analysisError.value = t('home.analysisTooFew', {
        min: MIN_ANALYSIS_MATCHES,
        count: rankedMatches.length
      })
      return
    }

    const detailResults = await Promise.allSettled(
      rankedMatches.map(match => apiClient.getGameDetail(match.gameId))
    )
    const detailsByGameId = new Map<number, Awaited<ReturnType<typeof apiClient.getGameDetail>>>()

    detailResults.forEach((result, index) => {
      if (result.status === 'fulfilled') {
        detailsByGameId.set(rankedMatches[index].gameId, result.value)
      }
    })

    const snapshot = createGrowthAnalysis(puuid, rankedMatches as MatchHistory[], detailsByGameId)
    saveAnalysisSnapshot(snapshot)
    analysis.value = snapshot
    analysisExpanded.value = true
    selectedRole.value = 'all'
  } catch (error) {
    console.error('Failed to run home analysis', error)
    analysisError.value = t('home.analysisFailed')
  } finally {
    analysisLoading.value = false
  }
}

function toggleAutoAnalysis() {
  autoAnalysis.value = {
    ...autoAnalysis.value,
    enabled: !autoAnalysis.value.enabled
  }
  saveAutoAnalysisSettings(accountKey.value, autoAnalysis.value)
}

function changeAutoInterval(event: Event) {
  const interval = Number((event.target as HTMLSelectElement).value)
  autoAnalysis.value = {
    ...autoAnalysis.value,
    interval: interval === 20 ? 20 : 10
  }
  saveAutoAnalysisSettings(accountKey.value, autoAnalysis.value)
}

function drawFortune() {
  if (currentFortune.value || fortuneRolling.value) {
    return
  }

  fortuneRolling.value = true
  clearFortuneTimer()
  fortuneTimer = window.setInterval(() => {
    const index = Math.floor(Math.random() * FORTUNE_POOL.length) % FORTUNE_POOL.length
    rollingFortuneLabel.value = FORTUNE_POOL[index].label
  }, 72)

  window.setTimeout(() => {
    clearFortuneTimer()
    const result = drawDailyFortune(fortuneRecord.value)
    fortuneRecord.value = result.record
    currentFortune.value = result.fortune
    rollingFortuneLabel.value = result.fortune.label
    fortuneRolling.value = false
    saveFortuneRecord(accountKey.value, fortuneRecord.value)
  }, 1180)
}

function clearFortuneTimer() {
  if (fortuneTimer) {
    window.clearInterval(fortuneTimer)
    fortuneTimer = null
  }
}

function formatRank(rank: QueueInfo | null): string {
  if (!rank || isUnrankedTier(rank.tier)) {
    return '未定级'
  }

  const tier = formatTierCn(rank)
  const division = formatDivision(rank)
  return division ? `${tier} ${division}` : tier
}

function isUnrankedTier(tier?: string): boolean {
  return UNRANKED_TIER_VALUES.has((tier || '').trim().toLowerCase())
}

function formatTierCn(rank: QueueInfo): string {
  const displayTier = rank.displayRank?.trim().split(/\s+/)[0]
  const candidates = [rank.tier, rank.tierCn, displayTier]
  let fallback = ''

  for (const candidate of candidates) {
    const key = candidate?.trim()
    if (!key) {
      continue
    }
    const mappedTier = TIER_CN_MAP[key.toLowerCase()] || TIER_CN_MAP[key]
    if (mappedTier) {
      return mappedTier
    }
    if (!isUnrankedTier(key)) {
      fallback ||= key
    }
  }

  return fallback || '未定级'
}

function formatDivision(rank: QueueInfo): string {
  const displayDivision = rank.displayRank?.trim().split(/\s+/)[1]
  const candidates = [rank.division, displayDivision]

  for (const candidate of candidates) {
    const key = candidate?.trim()
    if (!key) {
      continue
    }
    return DIVISION_CN_MAP[key.toLowerCase()] || key
  }

  return ''
}

function formatDateTime(timestamp?: number): string {
  if (!timestamp) {
    return '--'
  }
  const date = new Date(timestamp)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}${t('home.month')}${day}${t('home.day')} ${hour}:${minute}`
}

function formatDateRange(snapshot: AnalysisSnapshot): string {
  return `${formatShortDate(snapshot.matchStartAt)} ${t('home.to')} ${formatShortDate(snapshot.matchEndAt)}`
}

function formatShortDate(timestamp?: number): string {
  if (!timestamp) {
    return '--'
  }
  const date = new Date(timestamp)
  return `${date.getMonth() + 1}${t('home.month')}${date.getDate()}${t('home.day')}`
}

function roleLabel(role: GrowthRole): string {
  return roleOptions.value.find(item => item.value === role)?.label || t('home.role.unknown')
}

function formatPointTooltip(point: GrowthPoint): string {
  const result = point.win ? t('common.win') : t('common.loss')
  return `${formatDateTime(point.gameCreation)} · ${roleLabel(point.role)} · ${point.kdaText} · ${result}`
}

function formatMetricValue(value: number): string {
  if (selectedMetric.value === 'winRate') {
    return `${Math.round(value)}%`
  }
  if (selectedMetric.value === 'kda') {
    return value.toFixed(1)
  }
  return Math.round(value).toLocaleString()
}
</script>

<template>
  <div class="home-view">
    <section v-if="accountConnected && currentSummoner" class="account-panel">
      <div class="account-identity">
        <img class="account-avatar" :src="profileIconUrl" alt="" />
        <div class="account-main">
          <div class="summoner-heading">
            <h2>{{ displayName }}</h2>
            <span class="connection-pill connected">{{ t('home.clientConnected') }}</span>
          </div>
          <div class="rank-row">
            <span>{{ t('home.soloQueue') }}：{{ formatRank(soloRank) }}</span>
            <span>{{ t('home.flexQueue') }}：{{ formatRank(flexRank) }}</span>
          </div>
        </div>
      </div>
      <button class="secondary-btn" type="button" @click="gameStore.refreshSummoner">
        {{ t('home.refreshAccount') }}
      </button>
    </section>

    <section v-else class="account-panel disconnected-panel">
      <div class="account-identity">
        <div class="disconnected-mark">!</div>
        <div class="account-main">
          <div class="account-kicker">
            <span class="connection-pill">{{ t('common.disconnected') }}</span>
          </div>
          <h2>{{ t('home.noClientTitle') }}</h2>
          <p>{{ t('home.noClientBody') }}</p>
        </div>
      </div>
      <button class="primary-btn" type="button" @click="gameStore.checkConnection">
        {{ t('common.refreshConnection') }}
      </button>
    </section>

    <section class="feature-grid">
      <article class="ai-analysis-card">
        <div class="card-copy">
          <h2>电子教练</h2>
          <p>{{ t('home.aiAnalysisBody') }}</p>
        </div>

        <div class="action-row">
          <button class="primary-btn" type="button" :disabled="!canAnalyze" @click="runAnalysis">
            {{ analysisLoading ? t('home.analyzing') : t('home.analyzeNow') }}
          </button>
          <button
            class="auto-analysis-switch"
            type="button"
            role="switch"
            :aria-checked="autoAnalysis.enabled"
            :class="{ active: autoAnalysis.enabled }"
            :disabled="!accountConnected"
            @click="toggleAutoAnalysis"
          >
            <span class="switch-track">
              <span class="switch-thumb"></span>
            </span>
            <span class="switch-label">自动分析</span>
          </button>
          <select
            class="interval-select"
            :value="autoAnalysis.interval"
            :disabled="!accountConnected"
            @change="changeAutoInterval"
          >
            <option v-for="interval in AUTO_ANALYSIS_INTERVALS" :key="interval" :value="interval">
              {{ t('home.everyGames', { count: interval }) }}
            </option>
          </select>
        </div>

        <p class="hint-line">
          {{ t('home.autoAnalysisHint', { count: autoAnalysis.interval }) }}
        </p>
        <p v-if="analysisError" class="error-line">{{ analysisError }}</p>
      </article>

      <article class="fortune-card" :class="fortuneTone">
        <div class="panel-eyebrow fortune-eyebrow">抽个签</div>
        <div class="fortune-layout">
          <div class="slot-reel" :class="{ rolling: fortuneRolling }">
            {{ fortuneRolling ? rollingFortuneLabel : fortuneLabel }}
          </div>
          <p class="fortune-text">
            {{ currentFortune?.text || t('home.fortuneIdle') }}
          </p>
          <button
            class="fortune-button"
            type="button"
            :disabled="Boolean(currentFortune) || fortuneRolling"
            @click="drawFortune"
          >
            {{ fortuneButtonText }}
          </button>
          <p class="fortune-disclaimer">
            <span v-if="currentFortune">{{ t('home.fortuneOnceDaily') }}</span>
            {{ t('home.fortuneDisclaimer') }}
          </p>
        </div>
      </article>
    </section>

    <section v-if="analysis" class="analysis-result">
      <div class="analysis-meta">
        <span>{{ t('home.analysisTime') }}：{{ formatDateTime(analysis.analyzedAt) }}</span>
        <span>{{ t('home.matchPeriod') }}：{{ formatDateRange(analysis) }}</span>
        <span>{{ t('home.analyzedMatches', { count: analysis.matchCount }) }}</span>
      </div>
      <p class="analysis-rule">
        {{ t('home.analysisRule', { min: MIN_ANALYSIS_MATCHES, max: MAX_ANALYSIS_MATCHES }) }}
      </p>
      <button class="summary-banner" type="button" @click="analysisExpanded = !analysisExpanded">
        <strong>{{ t('home.oneLineSummary') }}：{{ analysis.summary }}</strong>
        <span>{{ analysisExpanded ? t('home.collapseAnalysis') : t('home.expandAnalysis') }}</span>
      </button>

      <div v-if="analysisExpanded" class="growth-section">
        <div class="chart-header">
          <div>
            <h2>{{ t('home.chartTitle') }}</h2>
            <p>{{ hoveredPoint ? formatPointTooltip(hoveredPoint) : t('home.chartHoverHint') }}</p>
          </div>
          <div class="chart-controls">
            <select v-model="selectedRole" class="analysis-role-select">
              <option v-for="role in roleOptions" :key="role.value" :value="role.value">
                {{ role.label }}
              </option>
            </select>
            <select v-model="selectedMetric" class="analysis-metric-select">
              <option v-for="metric in metricOptions" :key="metric.value" :value="metric.value">
                {{ metric.label }}
              </option>
            </select>
          </div>
        </div>

        <div class="growth-chart">
          <svg v-if="chartModel.length" viewBox="0 0 640 240" role="img" :aria-label="t('home.chartTitle')">
            <line x1="46" y1="24" x2="46" y2="198" class="chart-axis" />
            <line x1="46" y1="198" x2="618" y2="198" class="chart-axis" />
            <line x1="46" y1="82" x2="618" y2="82" class="chart-guide" />
            <line x1="46" y1="140" x2="618" y2="140" class="chart-guide" />
            <polyline class="chart-line" :points="chartPolyline" />
            <g
              v-for="item in chartModel"
              :key="item.point.gameId"
              @mouseenter="hoveredPoint = item.point"
              @mouseleave="hoveredPoint = null"
            >
              <circle class="chart-dot-hit" :cx="item.x" :cy="item.y" r="15" />
              <circle class="chart-dot" :cx="item.x" :cy="item.y" r="5" />
              <text class="chart-dot-value" :x="item.x" :y="item.y - 12">
                {{ formatMetricValue(item.value) }}
              </text>
            </g>
            <text class="axis-label" x="10" y="30">{{ t('home.chartHigh') }}</text>
            <text class="axis-label" x="10" y="202">{{ t('home.chartLow') }}</text>
            <text
              v-for="item in chartModel.filter((_item, index) => index === 0 || index === chartModel.length - 1)"
              :key="`label-${item.point.gameId}`"
              class="x-label"
              :x="item.x"
              y="224"
            >
              {{ t('home.matchIndex', { count: item.point.matchIndex }) }}
            </text>
          </svg>
          <div v-else class="chart-empty">{{ t('home.chartEmpty') }}</div>
        </div>

        <div class="analysis-detail">
          <h3>{{ t('home.analysisDetail') }}</h3>
          <p>{{ analysis.detail }}</p>
        </div>
      </div>
    </section>

    <HomeChart :puuid="currentSummoner?.puuid" :connected="accountConnected" />
  </div>
</template>

<style scoped>
.home-view {
  max-width: 1180px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.account-panel,
.ai-analysis-card,
.fortune-card,
.analysis-result {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.account-main p,
.card-copy p,
.hint-line,
.analysis-rule,
.chart-header p,
.analysis-detail p,
.fortune-text,
.fortune-disclaimer {
  color: var(--text-secondary);
}

.connection-pill {
  flex-shrink: 0;
  padding: 8px 12px;
  border-radius: var(--radius-pill);
  background: var(--error-bg);
  color: var(--error-color);
  font-size: 13px;
  font-weight: 700;
}

.connection-pill.connected {
  background: var(--success-bg);
  color: var(--success-color);
}

.account-panel {
  min-height: 122px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  padding: 22px;
}

.account-identity {
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 18px;
}

.account-avatar,
.disconnected-mark {
  width: 88px;
  height: 88px;
  border-radius: 20px;
  background: var(--bg-tertiary);
}

.account-avatar {
  object-fit: cover;
}

.disconnected-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--warning-color);
  border: 1px solid var(--warning-color);
  font-size: 42px;
  font-weight: 900;
}

.panel-eyebrow {
  color: var(--text-tertiary);
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 6px;
}

.account-main {
  min-width: 0;
}

.summoner-heading {
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 10px;
  margin-bottom: 8px;
  white-space: nowrap;
}

.account-kicker {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 8px;
}

.account-kicker .panel-eyebrow {
  margin-bottom: 0;
}

.account-main h2,
.card-copy h2,
.fortune-card h2,
.chart-header h2 {
  color: var(--text-primary);
  margin: 0;
}

.account-main h2 {
  font-size: 30px;
  line-height: 1.15;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summoner-heading h2 {
  min-width: 0;
}

.rank-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.rank-row span {
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-weight: 700;
}

.feature-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, 0.65fr);
  gap: 16px;
}

.ai-analysis-card,
.fortune-card {
  min-height: 250px;
  padding: 22px;
}

.card-copy h2 {
  font-size: 26px;
  margin-bottom: 8px;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
}

.primary-btn,
.secondary-btn,
.fortune-button,
.interval-select,
.analysis-role-select,
.analysis-metric-select {
  min-height: 46px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 800;
}

.primary-btn,
.fortune-button {
  padding: 0 18px;
  background: var(--accent-color);
  color: #ffffff;
}

.secondary-btn,
.interval-select,
.analysis-role-select,
.analysis-metric-select {
  padding: 0 14px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
}

.secondary-btn.active {
  border-color: rgba(var(--accent-rgb), 0.7);
  color: var(--accent-hover);
}

.auto-analysis-switch {
  min-height: 46px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 800;
}

.switch-track {
  width: 54px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  padding: 3px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  transition: background 0.18s ease;
}

.switch-thumb {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: var(--text-secondary);
  transition: transform 0.18s ease, background 0.18s ease;
}

.auto-analysis-switch.active {
  border-color: rgba(var(--accent-rgb), 0.72);
}

.auto-analysis-switch.active .switch-track {
  background: var(--accent-color);
}

.auto-analysis-switch.active .switch-thumb {
  transform: translateX(24px);
  background: #ffffff;
}

.auto-analysis-switch:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}

.primary-btn:disabled,
.secondary-btn:disabled,
.fortune-button:disabled,
.interval-select:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}

.hint-line {
  margin: 14px 0 0;
  font-size: 14px;
}

.error-line {
  margin-top: 10px;
  color: var(--error-color);
  font-weight: 700;
}

.fortune-card {
  text-align: left;
}

.fortune-eyebrow {
  color: white;
  font-size: 26px;
  font-weight: bold;
}

.fortune-layout {
  display: flex;
  min-height: 198px;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 14px;
}

.slot-reel {
  min-width: 142px;
  min-height: 70px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 18px;
  border-radius: 16px;
  background: rgba(196, 220, 255, 0.94);
  color: #0b64d8;
  font-size: 34px;
  line-height: 1;
  font-weight: 900;
  letter-spacing: 0;
}

.slot-reel.rolling {
  animation: slot-pop 0.16s linear infinite;
}

.fortune-card.bad .slot-reel {
  color: #8a1f17;
  background: rgba(255, 214, 204, 0.96);
}

.fortune-card.good .slot-reel {
  color: #085f2d;
  background: rgba(199, 245, 212, 0.96);
}

.fortune-text {
  margin: 0;
  min-height: 42px;
  font-size: 15px;
}

.fortune-button {
  min-width: 172px;
}

.fortune-disclaimer {
  margin: 0;
  font-size: 13px;
}

.analysis-result {
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.analysis-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  color: var(--text-secondary);
  font-size: 14px;
}

.analysis-rule {
  margin: 0;
  font-size: 14px;
}

.summary-banner {
  width: 100%;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-radius: 10px;
  background: var(--accent-color);
  color: #ffffff;
  text-align: left;
}

.summary-banner strong {
  font-size: 18px;
  line-height: 1.5;
}

.summary-banner span {
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 800;
}

.growth-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.chart-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.chart-header h2 {
  font-size: 22px;
  margin-bottom: 6px;
}

.chart-controls {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.growth-chart {
  min-height: 280px;
  padding: 16px;
  border-radius: 10px;
  background: var(--bg-tertiary);
  overflow: hidden;
}

.growth-chart svg {
  width: 100%;
  height: 100%;
  min-height: 248px;
}

.chart-axis {
  stroke: rgba(var(--accent-rgb), 0.75);
  stroke-width: 2;
}

.chart-guide {
  stroke: var(--border-color);
  stroke-width: 1;
}

.chart-line {
  fill: none;
  stroke: var(--accent-color);
  stroke-width: 4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.chart-dot-hit {
  fill: transparent;
  cursor: pointer;
}

.chart-dot {
  fill: var(--accent-color);
  stroke: var(--bg-tertiary);
  stroke-width: 3;
}

.chart-dot-value,
.axis-label,
.x-label {
  fill: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.chart-dot-value {
  text-anchor: middle;
  opacity: 0;
}

g:hover .chart-dot-value {
  opacity: 1;
}

.x-label {
  text-anchor: middle;
}

.chart-empty {
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.analysis-detail {
  padding-top: 4px;
}

.analysis-detail h3 {
  margin: 0 0 8px;
  color: var(--text-primary);
  font-size: 18px;
}

@keyframes slot-pop {
  0% {
    transform: translateY(-2px);
  }
  100% {
    transform: translateY(2px);
  }
}

@media (max-width: 920px) {
  .feature-grid,
  .account-panel {
    grid-template-columns: 1fr;
  }

  .account-panel {
    align-items: flex-start;
  }

  .summoner-heading h2 {
    font-size: 24px;
  }

  .account-avatar,
  .disconnected-mark {
    width: 76px;
    height: 76px;
  }

  .chart-header,
  .summary-banner {
    flex-direction: column;
    align-items: stretch;
  }

  .chart-controls,
  .action-row {
    justify-content: flex-start;
  }
}
</style>
