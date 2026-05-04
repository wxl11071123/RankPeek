<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { t } from '@/i18n'
import { useGameStore } from '@/stores/game'
import {
  loadLocalAiAnalysisResults,
  type LocalAiAnalysisDisplayResult
} from '@/services/localAiAnalysis'
import {
  buildAccountAnalysisInputSnapshot,
  type AiAnalysisInputSnapshot
} from '@/services/aiAnalysisInputSnapshot'
import { isServerAiEnabled } from '@/services/serverAiAnalysisClient'

interface FeatureCard {
  key: string
  title: string
  items: string[]
}

const gameStore = useGameStore()
const serverAiEnabled = isServerAiEnabled()
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
let loadRequestId = 0
let prepareRequestId = 0

const currentSummoner = computed(() => gameStore.currentSummoner)
const accountPuuid = computed(() => currentSummoner.value?.puuid ?? '')
const currentSummonerName = computed(() => {
  const summoner = currentSummoner.value
  if (!summoner) {
    return ''
  }

  return summoner.tagLine ? `${summoner.gameName}#${summoner.tagLine}` : summoner.gameName
})
const puuidTail = computed(() => {
  const puuid = accountPuuid.value
  return puuid ? puuid.slice(-8) : ''
})
const hasResults = computed(() => analysisResults.value.length > 0)
const preparationStatusLabel = computed(() => {
  if (!currentSummoner.value) {
    return t('aiAnalysis.prepareSelectAccount')
  }

  const snapshot = preparedSnapshot.value
  if (!snapshot) {
    return t('aiAnalysis.preparePending')
  }

  return snapshot.source.hasEnoughData
    ? t('aiAnalysis.prepareReady')
    : t('aiAnalysis.prepareInsufficient')
})

const featureCards = computed<FeatureCard[]>(() => [
  {
    key: 'pre-game',
    title: t('aiAnalysis.featurePreGame'),
    items: [
      t('aiAnalysis.preGameTeammates'),
      t('aiAnalysis.preGameRisks'),
      t('aiAnalysis.preGamePlan'),
      t('aiAnalysis.preGameDraft')
    ]
  },
  {
    key: 'post-game',
    title: t('aiAnalysis.featurePostGame'),
    items: [
      t('aiAnalysis.postGameSwing'),
      t('aiAnalysis.postGameDeaths'),
      t('aiAnalysis.postGameObjective'),
      t('aiAnalysis.postGameGoldTurn')
    ]
  },
  {
    key: 'coach',
    title: t('aiAnalysis.featureCoach'),
    items: [
      t('aiAnalysis.coachRecent'),
      t('aiAnalysis.coachChampionPool'),
      t('aiAnalysis.coachRole'),
      t('aiAnalysis.coachReport')
    ]
  },
  {
    key: 'fun',
    title: t('aiAnalysis.featureFun'),
    items: [
      t('aiAnalysis.funProfile'),
      t('aiAnalysis.funPraise'),
      t('aiAnalysis.funIndex'),
      t('aiAnalysis.funAram')
    ]
  }
])

watch(
  () => accountPuuid.value,
  () => {
    prepareRequestId += 1
    resetPreparedSnapshot()
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
    <header class="analysis-header">
      <div>
        <h1>{{ t('aiAnalysis.title') }}</h1>
        <p>{{ t('aiAnalysis.subtitle') }}</p>
      </div>
      <button class="primary-action" type="button" :disabled="preparingInput" @click="prepareAnalysisInputSnapshot">
        {{ preparingInput ? t('aiAnalysis.preparingInput') : t('aiAnalysis.prepareInput') }}
      </button>
    </header>

    <p v-if="placeholderNotice" class="notice-line">{{ placeholderNotice }}</p>

    <section class="account-card">
      <div class="section-heading">
        <span>{{ t('aiAnalysis.currentAccount') }}</span>
      </div>

      <div v-if="currentSummoner" class="account-body">
        <div class="account-mark">{{ currentSummoner.gameName.slice(0, 1).toUpperCase() }}</div>
        <div class="account-copy">
          <h2>{{ currentSummonerName }}</h2>
          <p>{{ t('aiAnalysis.puuidTail', { tail: puuidTail }) }}</p>
          <span>{{ t('aiAnalysis.accountHint') }}</span>
        </div>
      </div>

      <div v-else class="empty-card">
        <h2>{{ t('aiAnalysis.noAccountTitle') }}</h2>
        <p>{{ t('aiAnalysis.noAccountBody') }}</p>
      </div>
    </section>

    <section class="data-prep-card">
      <div class="section-heading">
        <span>{{ t('aiAnalysis.dataPrepTitle') }}</span>
        <small>{{ t('aiAnalysis.dataPrepLimit') }}</small>
      </div>

      <div v-if="!currentSummoner" class="prep-empty">
        <p>{{ t('aiAnalysis.prepareSelectAccount') }}</p>
      </div>

      <div v-else class="prep-body">
        <div v-if="preparedSnapshot" class="prep-grid">
          <div>
            <strong>{{ preparedSnapshot.source.matchRecordCount }}</strong>
            <span>{{ t('aiAnalysis.localMatches') }}</span>
          </div>
          <div>
            <strong>{{ preparedSnapshot.source.matchDetailCount }}</strong>
            <span>{{ t('aiAnalysis.localDetails') }}</span>
          </div>
          <div>
            <strong>{{ preparedSnapshot.source.hasEnoughData ? t('aiAnalysis.prepareReady') : t('aiAnalysis.prepareInsufficient') }}</strong>
            <span>{{ t('aiAnalysis.dataPrepStatus') }}</span>
          </div>
        </div>

        <div v-else class="prep-grid">
          <div>
            <strong>0</strong>
            <span>{{ t('aiAnalysis.localMatches') }}</span>
          </div>
          <div>
            <strong>0</strong>
            <span>{{ t('aiAnalysis.localDetails') }}</span>
          </div>
          <div>
            <strong>{{ preparationStatusLabel }}</strong>
            <span>{{ t('aiAnalysis.dataPrepStatus') }}</span>
          </div>
        </div>

        <p v-if="preparedSnapshot" class="hash-line">
          {{ t('aiAnalysis.inputHash') }}: {{ preparedSnapshot.inputHash }}
        </p>
        <p v-else-if="preparationUnavailable" class="prep-warning">
          {{ preparationError || t('aiAnalysis.prepareUnavailable') }}
        </p>

        <button class="secondary-action prep-action" type="button" :disabled="preparingInput" @click="prepareAnalysisInputSnapshot">
          {{ preparingInput ? t('aiAnalysis.preparingInput') : t('aiAnalysis.prepareInput') }}
        </button>
      </div>
    </section>

    <section class="server-ai-card">
      <div class="section-heading">
        <span>{{ t('aiAnalysis.serverAiTitle') }}</span>
        <small>{{ serverAiEnabled ? t('aiAnalysis.serverAiEnabled') : t('aiAnalysis.serverAiDisabled') }}</small>
      </div>

      <p class="server-ai-summary">{{ t('aiAnalysis.serverAiLocalOnly') }}</p>

      <div class="server-ai-mode-grid">
        <div>
          <strong>{{ t('aiAnalysis.serverAiStreamTitle') }}</strong>
          <span>{{ t('aiAnalysis.serverAiStreamPlan') }}</span>
        </div>
        <div>
          <strong>{{ t('aiAnalysis.serverAiAsyncTitle') }}</strong>
          <span>{{ t('aiAnalysis.serverAiAsyncPlan') }}</span>
        </div>
      </div>
    </section>

    <section class="feature-section">
      <div class="section-heading">
        <span>{{ t('aiAnalysis.featureTitle') }}</span>
      </div>

      <div class="feature-grid">
        <article v-for="card in featureCards" :key="card.key" class="feature-card">
          <h2>{{ card.title }}</h2>
          <ul>
            <li v-for="item in card.items" :key="item">{{ item }}</li>
          </ul>
          <button class="secondary-action" type="button" disabled>
            {{ t('aiAnalysis.comingSoon') }}
          </button>
        </article>
      </div>
    </section>

    <section class="history-section">
      <div class="section-heading history-heading">
        <span>{{ t('aiAnalysis.historyTitle') }}</span>
        <small v-if="currentSummoner">{{ t('aiAnalysis.historyLimit') }}</small>
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

      <div v-else class="history-list">
        <article v-for="result in analysisResults" :key="result.id" class="history-card">
          <div class="history-card-header">
            <div>
              <strong>{{ result.analysisTypeLabel }}</strong>
              <span>{{ result.createdAtLabel }}</span>
            </div>
            <span class="type-code">{{ result.analysisType }}</span>
          </div>

          <div class="meta-grid">
            <span>{{ t('aiAnalysis.subjectKey') }}: {{ result.subjectKey || t('common.none') }}</span>
            <span>{{ t('aiAnalysis.gameVersion') }}: {{ result.gameVersion || t('common.none') }}</span>
            <span>{{ t('aiAnalysis.modelName') }}: {{ result.modelName || t('common.none') }}</span>
            <span>{{ t('aiAnalysis.promptVersion') }}: {{ result.promptVersion || t('common.none') }}</span>
          </div>

          <div class="output-preview" :class="{ invalid: result.output.status === 'invalid' }">
            <h3 v-if="result.output.title">{{ result.output.title }}</h3>
            <p>{{ result.output.summary }}</p>
            <ul v-if="result.output.highlights.length">
              <li v-for="highlight in result.output.highlights" :key="highlight">{{ highlight }}</li>
            </ul>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.ai-analysis-view {
  max-width: 1080px;
  margin: 0 auto;
  padding-bottom: 36px;
}

.analysis-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
}

.analysis-header h1 {
  margin: 0 0 6px;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0;
}

.analysis-header p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 15px;
}

.primary-action,
.secondary-action {
  min-height: 40px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  padding: 0 16px;
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0;
  transition:
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.2s ease,
    color 0.18s ease,
    opacity 0.18s ease;
}

.primary-action {
  flex: 0 0 auto;
  background: var(--accent-color);
  color: #fff;
  box-shadow: 0 0 14px rgba(var(--accent-rgb), 0.22);
}

.primary-action:hover {
  box-shadow:
    0 0 0 1px rgba(var(--accent-rgb), 0.16),
    0 0 18px rgba(var(--accent-rgb), 0.3);
}

.secondary-action {
  width: 100%;
  margin-top: auto;
  background: var(--bg-tertiary);
  border-color: var(--border-subtle);
  color: var(--text-secondary);
}

.secondary-action:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.notice-line {
  margin: 0 0 16px;
  padding: 10px 14px;
  border: 1px solid rgba(var(--accent-rgb), 0.22);
  border-radius: var(--radius-md);
  background: rgba(var(--accent-rgb), 0.1);
  color: var(--accent-color);
  font-size: 13px;
  font-weight: 600;
}

.account-card,
.data-prep-card,
.server-ai-card,
.feature-card,
.empty-card,
.history-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow:
    0 12px 28px rgba(0, 0, 0, 0.16),
    0 0 0 1px rgba(var(--accent-rgb), 0.03);
}

.account-card,
.data-prep-card,
.server-ai-card,
.feature-section,
.history-section {
  margin-top: 24px;
}

.account-card,
.data-prep-card,
.server-ai-card {
  padding: 22px 24px;
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

.account-body {
  display: flex;
  align-items: center;
  gap: 16px;
}

.account-mark {
  width: 48px;
  height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  background:
    linear-gradient(135deg, rgba(var(--accent-rgb), 0.22), rgba(212, 167, 44, 0.18)),
    var(--bg-tertiary);
  border: 1px solid rgba(var(--accent-rgb), 0.18);
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 800;
}

.account-copy {
  min-width: 0;
}

.account-copy h2,
.empty-card h2,
.feature-card h2,
.history-card h3 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-weight: 650;
  letter-spacing: 0;
}

.account-copy h2 {
  font-size: 20px;
}

.account-copy p,
.account-copy span,
.empty-card p,
.feature-card li,
.output-preview p,
.output-preview li,
.meta-grid span {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.account-copy p {
  margin: 5px 0 2px;
  color: var(--text-tertiary);
  font-family: var(--font-mono);
}

.prep-empty p,
.hash-line,
.prep-warning {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.prep-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.prep-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.prep-grid div {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-tertiary);
}

.prep-grid strong,
.prep-grid span {
  display: block;
  overflow-wrap: anywhere;
}

.prep-grid strong {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 750;
}

.prep-grid span {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 12px;
}

.hash-line {
  font-family: var(--font-mono);
  color: var(--accent-color);
  overflow-wrap: anywhere;
}

.prep-warning {
  color: var(--warning-color);
  overflow-wrap: anywhere;
}

.prep-action {
  width: auto;
  align-self: flex-start;
  margin-top: 0;
}

.server-ai-summary {
  margin: 0 0 14px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.server-ai-mode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.server-ai-mode-grid div {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-tertiary);
}

.server-ai-mode-grid strong,
.server-ai-mode-grid span {
  display: block;
  overflow-wrap: anywhere;
}

.server-ai-mode-grid strong {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 750;
}

.server-ai-mode-grid span {
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.feature-card {
  min-height: 220px;
  display: flex;
  flex-direction: column;
  padding: 18px;
}

.feature-card h2 {
  font-size: 16px;
}

.feature-card ul,
.output-preview ul {
  margin: 12px 0 0;
  padding-left: 18px;
}

.feature-card li + li,
.output-preview li + li {
  margin-top: 6px;
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

.history-card {
  padding: 18px;
}

.history-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.history-card-header div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-card-header strong {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 700;
}

.history-card-header span {
  color: var(--text-tertiary);
  font-size: 12px;
}

.type-code {
  max-width: 220px;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  background: rgba(var(--accent-rgb), 0.1);
  color: var(--accent-color);
  font-family: var(--font-mono);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 12px;
  margin-bottom: 14px;
}

.meta-grid span {
  overflow-wrap: anywhere;
}

.output-preview {
  padding: 14px;
  border-radius: var(--radius-md);
  background: var(--bg-tertiary);
  border: 1px solid var(--border-subtle);
}

.output-preview.invalid {
  background: var(--warning-bg);
  border-color: rgba(255, 159, 10, 0.22);
}

.output-preview h3 {
  margin-bottom: 6px;
  font-size: 15px;
}

.output-preview p {
  margin: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 1120px) {
  .feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .analysis-header,
  .history-card-header,
  .account-body {
    align-items: stretch;
    flex-direction: column;
  }

  .feature-grid,
  .meta-grid,
  .prep-grid,
  .server-ai-mode-grid {
    grid-template-columns: 1fr;
  }

  .primary-action,
  .prep-action {
    width: 100%;
  }
}
</style>
