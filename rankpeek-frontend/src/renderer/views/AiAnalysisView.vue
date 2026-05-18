<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { t } from '@/i18n'
import { useGameStore } from '@/stores/game'
import {
  loadLocalAiAnalysisResults,
  type LocalAiAnalysisDisplayResult
} from '@/services/localAiAnalysis'
import type { AiMemoryStats } from '@/types/localDatabase'
import {
  buildAccountAnalysisInputSnapshot,
  type AiAnalysisInputSnapshot
} from '@/services/aiAnalysisInputSnapshot'
import { getProfileIconUrl, markAssetLoadFailed } from '@/utils/gameAssetUrls'

type ReportTypeFilter = 'all' | 'pregame' | 'postgame' | 'praise' | 'coach'
type ReportCategory = Exclude<ReportTypeFilter, 'all'> | 'fun' | 'other'

interface FeatureCard {
  key: string
  title: string
  positioning: string
  tags: string[]
}

const gameStore = useGameStore()
// Future server AI final results should be saved through saveServerAiFinalResultToLocal()
// and will appear in the existing local history list.

const analysisResults = ref<LocalAiAnalysisDisplayResult[]>([])
const loadingResults = ref(false)
const historyUnavailable = ref(false)
const historyError = ref<string | null>(null)
const placeholderNotice = ref('')
const preparedSnapshot = ref<AiAnalysisInputSnapshot | null>(null)
const preparingInput = ref(false)
const preparationUnavailable = ref(false)
const preparationError = ref<string | null>(null)
const selectedReportType = ref<ReportTypeFilter>('all')
const aiMemoryStats = ref<AiMemoryStats | null>(null)
const aiMemoryLoading = ref(false)
const aiMemoryUnavailable = ref(false)
const aiMemoryError = ref<string | null>(null)
const exportingAiMemory = ref(false)
let loadRequestId = 0
let prepareRequestId = 0
let memoryStatsRequestId = 0

const currentSummoner = computed(() => gameStore.currentSummoner)
const accountPuuid = computed(() => currentSummoner.value?.puuid ?? '')
const currentSummonerName = computed(() => {
  const summoner = currentSummoner.value
  if (!summoner) {
    return ''
  }

  return summoner.tagLine ? `${summoner.gameName}#${summoner.tagLine}` : summoner.gameName
})
const hasResults = computed(() => analysisResults.value.length > 0)
const filteredAnalysisResults = computed(() => {
  if (selectedReportType.value === 'all') {
    return analysisResults.value
  }

  return analysisResults.value.filter(result => getReportCategory(result) === selectedReportType.value)
})
const hasFilteredResults = computed(() => filteredAnalysisResults.value.length > 0)
const accountStatusLabel = computed(() => currentSummonerName.value || t('aiAnalysis.noAccountStatus'))
const rankpeekAccountLabel = computed(() => t('aiAnalysis.rankpeekAccountGuest'))
const rankpeekBalanceLabel = computed(() => '￥0.00')
const currentSummonerProfileIconUrl = computed(() => {
  const summoner = currentSummoner.value
  return summoner?.profileIconId ? getProfileIconUrl(summoner.profileIconId) : ''
})
const memoryTypeDistribution = computed(() => {
  const stats = aiMemoryStats.value
  if (!stats || stats.analysisTypeCounts.length === 0) {
    return t('common.none')
  }

  return stats.analysisTypeCounts
    .map(item => `${getAnalysisTypeLabel(item.analysisType)} ${item.count}`)
    .join(' / ')
})
const aiMemoryDateRange = computed(() => {
  const stats = aiMemoryStats.value
  if (!stats || !stats.earliestCreatedAt || !stats.latestCreatedAt) {
    return t('common.none')
  }

  return `${formatMemoryDate(stats.earliestCreatedAt)} - ${formatMemoryDate(stats.latestCreatedAt)}`
})
const accountInitial = computed(() => {
  const name = currentSummoner.value?.gameName || currentSummonerName.value
  return name ? name.slice(0, 1).toUpperCase() : '?'
})

const reportTypeTabs = computed<Array<{ key: ReportTypeFilter; label: string; count: number }>>(() => [
  {
    key: 'all',
    label: t('aiAnalysis.filterAll'),
    count: analysisResults.value.length
  },
  {
    key: 'pregame',
    label: t('aiAnalysis.featurePreGame'),
    count: countReportsByCategory('pregame')
  },
  {
    key: 'postgame',
    label: t('aiAnalysis.featurePostGame'),
    count: countReportsByCategory('postgame')
  },
  {
    key: 'praise',
    label: t('aiAnalysis.featurePraise'),
    count: countReportsByCategory('praise')
  },
  {
    key: 'coach',
    label: t('aiAnalysis.featureCoach'),
    count: countReportsByCategory('coach')
  }
])

const featureCards = computed<FeatureCard[]>(() => [
  {
    key: 'pre-game',
    title: t('aiAnalysis.featurePreGame'),
    positioning: t('aiAnalysis.preGamePositioning'),
    tags: [
      t('aiAnalysis.tagTeammates'),
      t('aiAnalysis.tagRiskTips'),
      t('aiAnalysis.tagOpponentThreat')
    ]
  },
  {
    key: 'post-game',
    title: t('aiAnalysis.featurePostGame'),
    positioning: t('aiAnalysis.postGamePositioning'),
    tags: [
      t('aiAnalysis.tagFromStrongToWeak'),
      t('aiAnalysis.tagSingleGameReview')
    ]
  },
  {
    key: 'praise',
    title: t('aiAnalysis.featurePraise'),
    positioning: t('aiAnalysis.praisePositioning'),
    tags: [
      t('aiAnalysis.tagPraise'),
      t('aiAnalysis.tagEmotionalValue'),
      t('aiAnalysis.tagEntertainment')
    ]
  },
  {
    key: 'coach',
    title: t('aiAnalysis.featureCoach'),
    positioning: t('aiAnalysis.coachPositioning'),
    tags: [
      t('aiAnalysis.tagRecent20'),
      t('aiAnalysis.tagChampionPool')
    ]
  }
])

watch(
  () => accountPuuid.value,
  () => {
    prepareRequestId += 1
    resetPreparedSnapshot()
    void loadAiMemoryStats()
    void refreshLocalAnalysisResults()
  },
  { immediate: true }
)

function resetPreparedSnapshot() {
  preparedSnapshot.value = null
  preparationUnavailable.value = false
  preparationError.value = null
  preparingInput.value = false
}

function countReportsByCategory(category: ReportCategory) {
  return analysisResults.value.filter(result => getReportCategory(result) === category).length
}

function getReportCategory(result: LocalAiAnalysisDisplayResult): ReportCategory {
  const normalized = result.analysisType.trim().toLowerCase()
  if (!normalized) {
    return 'other'
  }

  if (normalized.includes('postgame_praise') || normalized.includes('praise') || normalized.includes('compliment')) {
    return 'praise'
  }

  if (normalized.includes('pregame') || normalized.includes('pre_game') || normalized.includes('before')) {
    return 'pregame'
  }

  if (normalized.includes('postgame') || normalized.includes('post_game') || normalized.includes('review') || normalized.includes('after')) {
    return 'postgame'
  }

  if (normalized.includes('coach') || normalized.includes('weekly') || normalized.includes('monthly')) {
    return 'coach'
  }

  if (normalized.includes('entertainment') || normalized.includes('fun')) {
    return 'fun'
  }

  return 'other'
}

function getReportCategoryLabel(result: LocalAiAnalysisDisplayResult) {
  switch (getReportCategory(result)) {
    case 'pregame':
      return t('aiAnalysis.featurePreGame')
    case 'postgame':
      return t('aiAnalysis.featurePostGame')
    case 'praise':
      return t('aiAnalysis.featurePraise')
    case 'coach':
      return t('aiAnalysis.featureCoach')
    case 'fun':
      return t('aiAnalysis.featureFun')
    case 'other':
      return result.analysisTypeLabel
  }
}

function getReportTitle(result: LocalAiAnalysisDisplayResult) {
  return result.output.title || result.analysisTypeLabel
}

function getReportScopeLabel(result: LocalAiAnalysisDisplayResult) {
  return result.matchId ? t('aiAnalysis.singleMatchReport') : t('aiAnalysis.accountReport')
}

function getAnalysisTypeLabel(analysisType: string) {
  return analysisType
    .split(/[_-]+/)
    .filter(Boolean)
    .map(part => part.slice(0, 1).toUpperCase() + part.slice(1))
    .join(' ')
}

function formatMemoryDate(value: string) {
  const timestamp = Date.parse(value)
  if (Number.isNaN(timestamp)) {
    return value
  }

  return new Date(timestamp).toLocaleDateString()
}

async function loadAiMemoryStats() {
  const puuid = accountPuuid.value
  const requestId = ++memoryStatsRequestId
  aiMemoryError.value = null

  if (!puuid) {
    aiMemoryStats.value = null
    aiMemoryUnavailable.value = false
    aiMemoryLoading.value = false
    return
  }

  const database = window.electronAPI?.database
  if (!database?.getAiMemoryStats) {
    aiMemoryStats.value = null
    aiMemoryUnavailable.value = true
    aiMemoryLoading.value = false
    aiMemoryError.value = t('aiAnalysis.memoryUnavailable')
    return
  }

  aiMemoryLoading.value = true

  try {
    const result = await database.getAiMemoryStats(puuid)
    if (requestId !== memoryStatsRequestId) {
      return
    }

    if (!result.success) {
      throw new Error(result.error)
    }

    aiMemoryStats.value = result.data
    aiMemoryUnavailable.value = false
  } catch (error) {
    if (requestId !== memoryStatsRequestId) {
      return
    }

    aiMemoryStats.value = null
    aiMemoryUnavailable.value = true
    aiMemoryError.value = error instanceof Error ? error.message : String(error)
  } finally {
    if (requestId === memoryStatsRequestId) {
      aiMemoryLoading.value = false
    }
  }
}

async function exportAiMemory() {
  const puuid = accountPuuid.value
  const database = window.electronAPI?.database
  if (!puuid || !database?.exportAiMemory) {
    window.alert(t('aiAnalysis.memoryUnavailable'))
    return
  }

  exportingAiMemory.value = true

  try {
    const result = await database.exportAiMemory(puuid)
    if (!result.success) {
      throw new Error(result.error)
    }

    if (!result.data.canceled) {
      window.alert(t('aiAnalysis.memoryExported', { count: result.data.exportedCount }))
    }
  } catch (error) {
    console.error('Failed to export AI memory', error)
    window.alert(t('aiAnalysis.memoryExportFailed'))
  } finally {
    exportingAiMemory.value = false
  }
}

async function prepareAnalysisInputSnapshot() {
  const puuid = accountPuuid.value
  const requestId = ++prepareRequestId
  preparationUnavailable.value = false
  preparationError.value = null
  placeholderNotice.value = ''

  if (!puuid) {
    resetPreparedSnapshot()
    placeholderNotice.value = t('aiAnalysis.prepareSelectAccount')
    return
  }

  preparingInput.value = true

  try {
    const snapshot = await buildAccountAnalysisInputSnapshot({
      accountPuuid: puuid,
      accountDisplayName: currentSummonerName.value,
      limit: 20,
      offset: 0
    })

    if (requestId !== prepareRequestId) {
      return
    }

    preparedSnapshot.value = snapshot
    placeholderNotice.value = t('aiAnalysis.prepareSuccess', {
      hash: snapshot.inputHash
    })
  } catch (error) {
    if (requestId !== prepareRequestId) {
      return
    }

    console.warn('Failed to prepare AI analysis input snapshot:', error)
    preparedSnapshot.value = null
    preparationUnavailable.value = true
    preparationError.value = error instanceof Error ? error.message : String(error)
    placeholderNotice.value = t('aiAnalysis.prepareUnavailable')
  } finally {
    if (requestId === prepareRequestId) {
      preparingInput.value = false
    }
  }
}

async function refreshLocalAnalysisResults() {
  const puuid = accountPuuid.value
  const requestId = ++loadRequestId
  placeholderNotice.value = ''
  historyError.value = null

  if (!puuid) {
    analysisResults.value = []
    historyUnavailable.value = false
    loadingResults.value = false
    return
  }

  loadingResults.value = true

  const result = await loadLocalAiAnalysisResults(puuid, {
    limit: 20,
    offset: 0
  })

  if (requestId !== loadRequestId) {
    return
  }

  analysisResults.value = result.results
  historyUnavailable.value = result.unavailable
  historyError.value = result.error
  loadingResults.value = false
}
</script>

<template>
  <div class="ai-analysis-view">
    <section class="hero-panel">
      <div class="hero-copy">
        <h1>{{ t('aiAnalysis.title') }}</h1>
        <p>{{ t('aiAnalysis.subtitle') }}</p>
      </div>

      <div class="status-card account-showcase-card">
        <div class="account-showcase-item rankpeek-account-value">
          {{ rankpeekAccountLabel }}
        </div>
        <div class="account-showcase-item league-account-showcase">
          <img
            v-if="currentSummonerProfileIconUrl"
            class="account-avatar"
            :src="currentSummonerProfileIconUrl"
            alt=""
            @error="markAssetLoadFailed"
          >
          <span v-else class="account-avatar-placeholder">{{ accountInitial }}</span>
          <span class="league-account-name">{{ accountStatusLabel }}</span>
        </div>
        <div class="account-showcase-item balance-row">
          <span class="balance-showcase">{{ rankpeekBalanceLabel }}</span>
          <button class="balance-recharge-button" type="button">{{ t('aiAnalysis.recharge') }}</button>
        </div>
      </div>
    </section>

    <p v-if="placeholderNotice" class="notice-line">{{ placeholderNotice }}</p>

    <section class="feature-section">
      <div class="feature-grid">
        <article
          v-for="card in featureCards"
          :key="card.key"
          class="feature-card"
        >
          <div class="feature-copy">
            <div class="feature-title-row">
              <h2>{{ card.title }}</h2>
            </div>
            <p class="feature-positioning">{{ card.positioning }}</p>
          </div>
          <div class="tag-list" aria-label="feature tags">
            <span v-for="tag in card.tags" :key="tag">{{ tag }}</span>
          </div>
        </article>
      </div>
    </section>

    <section class="ai-memory-section">
      <div class="section-heading memory-heading">
        <span>{{ t('aiAnalysis.memoryTitle') }}</span>
        <button
          class="memory-export-button"
          type="button"
          :disabled="!accountPuuid || exportingAiMemory || aiMemoryLoading"
          @click="exportAiMemory"
        >
          {{ exportingAiMemory ? t('aiAnalysis.memoryExporting') : t('aiAnalysis.memoryExport') }}
        </button>
      </div>

      <div class="memory-card">
        <p class="memory-description">{{ t('aiAnalysis.memoryDescription') }}</p>

        <div v-if="!currentSummoner" class="memory-state">{{ t('aiAnalysis.noAccountHistoryBody') }}</div>
        <div v-else-if="aiMemoryLoading" class="memory-state">{{ t('aiAnalysis.memoryLoading') }}</div>
        <div v-else-if="aiMemoryUnavailable" class="memory-state warning">
          {{ aiMemoryError || t('aiAnalysis.memoryUnavailable') }}
        </div>
        <div v-else class="memory-stats-grid">
          <div class="memory-stat">
            <span>{{ t('aiAnalysis.memoryTotal') }}</span>
            <strong>{{ aiMemoryStats?.totalCount ?? 0 }}</strong>
          </div>
          <div class="memory-stat">
            <span>{{ t('aiAnalysis.memoryLinkedMatches') }}</span>
            <strong>{{ aiMemoryStats?.linkedMatchCount ?? 0 }}</strong>
          </div>
          <div class="memory-stat wide">
            <span>{{ t('aiAnalysis.memoryTypes') }}</span>
            <strong>{{ memoryTypeDistribution }}</strong>
          </div>
          <div class="memory-stat wide">
            <span>{{ t('aiAnalysis.memoryDateRange') }}</span>
            <strong>{{ aiMemoryDateRange }}</strong>
          </div>
        </div>
      </div>
    </section>

    <section class="history-section">
      <div class="section-heading history-heading">
        <span>{{ t('aiAnalysis.historyTitle') }}</span>
      </div>

      <div class="report-type-tabs" role="tablist" aria-label="AI report type filters">
        <button
          v-for="tab in reportTypeTabs"
          :key="tab.key"
          class="report-type-tab"
          :class="{ active: selectedReportType === tab.key }"
          type="button"
          @click="selectedReportType = tab.key"
        >
          <span>{{ tab.label }}</span>
          <strong>{{ tab.count }}</strong>
        </button>
      </div>

      <div v-if="!currentSummoner" class="empty-card">
        <h2>{{ t('aiAnalysis.noAccountTitle') }}</h2>
        <p>{{ t('aiAnalysis.noAccountHistoryBody') }}</p>
      </div>

      <div v-else-if="loadingResults" class="empty-card">
        <h2>{{ t('aiAnalysis.loadingHistory') }}</h2>
        <p>{{ t('aiAnalysis.loadingHistoryBody') }}</p>
      </div>

      <div v-else-if="historyUnavailable" class="empty-card warning">
        <h2>{{ t('aiAnalysis.unavailableTitle') }}</h2>
        <p>{{ historyError || t('aiAnalysis.unavailableBody') }}</p>
      </div>

      <div v-else-if="!hasResults" class="empty-card">
        <h2>{{ t('aiAnalysis.emptyHistoryTitle') }}</h2>
        <p>{{ t('aiAnalysis.emptyHistoryBody') }}</p>
      </div>

      <div v-else-if="!hasFilteredResults" class="empty-card">
        <h2>{{ t('aiAnalysis.emptyFilteredTitle') }}</h2>
        <p>{{ t('aiAnalysis.emptyFilteredBody') }}</p>
      </div>

      <div v-else class="history-list">
        <article
          v-for="result in filteredAnalysisResults"
          :key="result.id"
          class="report-card"
          :class="{ invalid: result.output.status === 'invalid' }"
        >
          <div class="report-main">
            <div class="report-topline">
              <span class="report-type-pill">{{ getReportCategoryLabel(result) }}</span>
              <time>{{ result.createdAtLabel }}</time>
            </div>
            <h3>{{ getReportTitle(result) }}</h3>
            <p>{{ result.output.summary }}</p>
          </div>

          <ul v-if="result.output.highlights.length" class="report-highlights">
            <li v-for="highlight in result.output.highlights.slice(0, 3)" :key="highlight">{{ highlight }}</li>
          </ul>

          <div class="report-context">
            <span>{{ currentSummonerName || t('aiAnalysis.currentAccountFallback') }}</span>
            <span>{{ getReportScopeLabel(result) }}</span>
          </div>

          <div class="report-meta">
            <span>{{ t('aiAnalysis.subjectKey') }}: {{ result.subjectKey || t('common.none') }}</span>
            <span>{{ t('aiAnalysis.gameVersion') }}: {{ result.gameVersion || t('common.none') }}</span>
            <span>{{ t('aiAnalysis.modelName') }}: {{ result.modelName || t('common.none') }}</span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.ai-analysis-view {
  --ai-analysis-module-hover-rgb: 212, 175, 55;
  --ai-analysis-module-hover-border: rgba(var(--ai-analysis-module-hover-rgb), 0.48);
  --ai-analysis-module-hover-shadow:
    0 0 0 1px rgba(var(--ai-analysis-module-hover-rgb), 0.16),
    0 0 18px rgba(var(--ai-analysis-module-hover-rgb), 0.18),
    0 12px 28px rgba(var(--ai-analysis-module-hover-rgb), 0.08);
  --ai-analysis-control-hover-rgb: 212, 175, 55;
  --ai-analysis-control-hover-border: rgba(var(--ai-analysis-control-hover-rgb), 0.46);
  --ai-analysis-control-hover-bg: rgba(var(--ai-analysis-control-hover-rgb), 0.08);
  --ai-analysis-control-hover-shadow:
    0 0 0 1px rgba(var(--ai-analysis-control-hover-rgb), 0.13),
    0 0 14px rgba(var(--ai-analysis-control-hover-rgb), 0.18);
  --ai-analysis-accent-rgb: 212, 175, 55;
  max-width: 1120px;
  margin: 0 auto;
  padding-bottom: 36px;
}

:global([data-theme="light"] .ai-analysis-view) {
  --ai-analysis-module-hover-rgb: 86, 109, 134;
  --ai-analysis-module-hover-border: rgba(var(--ai-analysis-module-hover-rgb), 0.42);
  --ai-analysis-module-hover-shadow:
    0 0 0 1px rgba(var(--ai-analysis-module-hover-rgb), 0.14),
    0 0 18px rgba(var(--ai-analysis-module-hover-rgb), 0.14),
    0 12px 28px rgba(var(--ai-analysis-module-hover-rgb), 0.07);
  --ai-analysis-control-hover-rgb: 226, 179, 34;
  --ai-analysis-control-hover-border: rgba(var(--ai-analysis-control-hover-rgb), 0.42);
  --ai-analysis-control-hover-bg: rgba(var(--ai-analysis-control-hover-rgb), 0.1);
  --ai-analysis-control-hover-shadow:
    0 0 0 1px rgba(var(--ai-analysis-control-hover-rgb), 0.13),
    0 0 12px rgba(var(--ai-analysis-control-hover-rgb), 0.2);
  --ai-analysis-accent-rgb: 166, 133, 32;
}

.hero-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  align-items: stretch;
  padding: 24px;
}

.hero-copy {
  min-width: 0;
}

.hero-copy h1 {
  margin: 0 0 8px;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 760;
  letter-spacing: 0;
}

.hero-copy p {
  max-width: 560px;
  margin: 0 0 20px;
  color: var(--text-secondary);
  font-size: 16px;
  line-height: 1.6;
}

.hero-panel,
.status-card,
.feature-card,
.empty-card,
.memory-card,
.report-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.hero-panel:hover,
.hero-panel:focus-within,
.feature-card:hover,
.feature-card:focus-within,
.empty-card:hover,
.empty-card:focus-within,
.report-card:hover,
.report-card:focus-within {
  border-color: var(--ai-analysis-module-hover-border);
  box-shadow: var(--ai-analysis-module-hover-shadow);
}

.status-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 14px;
  padding: 18px;
}

.account-showcase-card {
  align-items: stretch;
}

.account-showcase-item {
  min-width: 0;
  overflow-wrap: anywhere;
}

.rankpeek-account-value {
  color: var(--text-secondary);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.45;
}

.league-account-showcase {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-top: 1px solid var(--border-subtle);
  border-bottom: 1px solid var(--border-subtle);
}

.league-account-name {
  min-width: 0;
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 780;
  line-height: 1.35;
}

.balance-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.balance-showcase {
  color: rgb(var(--ai-analysis-accent-rgb));
  font-size: 21px;
  font-weight: 820;
  line-height: 1.2;
}

.balance-recharge-button {
  min-height: 28px;
  flex: 0 0 auto;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 0 10px;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 750;
  letter-spacing: 0;
  transition:
    border-color 0.18s ease,
    background 0.18s ease,
    color 0.18s ease,
    box-shadow 0.2s ease;
}

.balance-recharge-button:hover,
.balance-recharge-button:focus-visible {
  border-color: var(--ai-analysis-control-hover-border);
  background: var(--ai-analysis-control-hover-bg);
  box-shadow: var(--ai-analysis-control-hover-shadow);
  color: var(--text-primary);
  outline: none;
}

.report-meta span {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.account-avatar,
.account-avatar-placeholder {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  border-radius: 50%;
}

.account-avatar {
  display: block;
  object-fit: cover;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-subtle);
}

.account-avatar-placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-tertiary);
  border: 1px solid rgba(var(--accent-rgb), 0.22);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.notice-line {
  margin: 16px 0 0;
  padding: 10px 14px;
  border: 1px solid rgba(var(--ai-analysis-accent-rgb), 0.22);
  border-radius: var(--radius-md);
  background: rgba(var(--ai-analysis-accent-rgb), 0.08);
  color: rgb(var(--ai-analysis-accent-rgb));
  font-size: 13px;
  font-weight: 600;
}

.feature-section,
.ai-memory-section,
.history-section {
  margin-top: 24px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: var(--text-secondary);
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.section-heading small {
  color: var(--text-tertiary);
  font-family: var(--font-text);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
  text-transform: none;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.feature-card {
  min-height: 132px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px;
}

.feature-copy h2,
.empty-card h2,
.report-card h3 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-weight: 700;
  letter-spacing: 0;
}

.feature-copy h2 {
  font-size: 17px;
}

.feature-positioning {
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.feature-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.empty-card p,
.report-main p,
.report-highlights li {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-list span,
.report-type-pill {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 9px;
  border: 1px solid rgba(var(--ai-analysis-accent-rgb), 0.2);
  border-radius: var(--radius-sm);
  background: rgba(var(--ai-analysis-accent-rgb), 0.08);
  color: rgb(var(--ai-analysis-accent-rgb));
  font-size: 12px;
  font-weight: 650;
}

.empty-card {
  padding: 22px;
}

.empty-card h2 {
  font-size: 17px;
}

.empty-card p {
  margin: 6px 0 0;
}

.empty-card.warning {
  border-color: rgba(255, 159, 10, 0.22);
  background: linear-gradient(135deg, var(--bg-secondary), rgba(255, 159, 10, 0.08));
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.report-type-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.report-type-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 0 11px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.2s ease, color 0.18s ease;
}

.report-type-tab:hover,
.report-type-tab:focus-visible {
  border-color: var(--ai-analysis-control-hover-border);
  background: var(--ai-analysis-control-hover-bg);
  box-shadow: var(--ai-analysis-control-hover-shadow);
  color: var(--text-primary);
  outline: none;
}

.report-type-tab strong {
  color: var(--text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.report-type-tab.active {
  border-color: rgba(var(--accent-rgb), 0.32);
  background: rgba(var(--accent-rgb), 0.1);
  color: var(--text-primary);
}

.report-type-tab.active strong {
  color: var(--accent-color);
}

.memory-card {
  padding: 18px;
}

.memory-heading {
  align-items: center;
}

.memory-export-button {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.2s ease, color 0.18s ease;
}

.memory-export-button:hover:not(:disabled),
.memory-export-button:focus-visible {
  border-color: var(--ai-analysis-control-hover-border);
  background: var(--ai-analysis-control-hover-bg);
  box-shadow: var(--ai-analysis-control-hover-shadow);
  color: var(--text-primary);
  outline: none;
}

.memory-export-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.memory-description,
.memory-state {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.memory-state {
  margin-top: 12px;
}

.memory-state.warning {
  color: rgb(var(--ai-analysis-accent-rgb));
}

.memory-stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.memory-stat {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-tertiary);
}

.memory-stat span,
.memory-stat strong {
  display: block;
  overflow-wrap: anywhere;
}

.memory-stat span {
  color: var(--text-tertiary);
  font-size: 12px;
  font-weight: 650;
}

.memory-stat strong {
  margin-top: 6px;
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 780;
}

.report-card {
  padding: 18px;
}

.report-card.invalid {
  border-color: rgba(255, 159, 10, 0.22);
}

.report-topline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.report-topline time {
  flex: 0 0 auto;
  color: var(--text-tertiary);
  font-size: 12px;
}

.report-card h3 {
  font-size: 17px;
}

.report-main p {
  margin: 8px 0 0;
  overflow-wrap: anywhere;
}

.report-highlights {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.report-highlights li {
  padding: 10px 12px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-tertiary);
}

.report-context {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.report-context span {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 9px;
  border-radius: var(--radius-sm);
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 650;
}

.report-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

.report-meta span {
  color: var(--text-tertiary);
  overflow-wrap: anywhere;
}

@media (max-width: 1120px) {
  .feature-grid,
  .memory-stats-grid,
  .report-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 840px) {
  .hero-panel {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .hero-panel {
    padding: 20px;
  }

  .hero-copy h1 {
    font-size: 28px;
  }

  .report-topline {
    align-items: stretch;
    flex-direction: column;
    gap: 6px;
  }

  .feature-grid,
  .memory-stats-grid,
  .report-highlights,
  .report-meta {
    grid-template-columns: 1fr;
  }
}
</style>
