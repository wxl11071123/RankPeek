<template>
  <div class="gaming-view">
    <!-- Waiting state -->
    <div v-if="!sessionData.phase" class="not-in-game">
      <div class="not-in-game-icon">🎮</div>
      <h2>{{ t('gaming.waitTitle') }}</h2>
      <p>{{ t('gaming.waitBody') }}</p>
      <div v-if="isRefreshPaused" class="connection-error">
        <span class="error-icon">⚠️</span>
        <span>{{ t('gaming.connectionPaused') }}</span>
        <button class="resume-btn" @click="resumeRefresh">{{ t('common.reconnect') }}</button>
      </div>
      <button v-else class="refresh-btn" @click="fetchSessionData">{{ t('common.refreshStatus') }}</button>
    </div>

    <!-- Active game state -->
    <div v-else class="gaming-content">
      <div v-if="isRefreshPaused" class="connection-bar">
        <span class="error-icon">⚠️</span>
        <span>{{ t('gaming.connectionPaused') }}</span>
        <button class="resume-btn-small" @click="resumeRefresh">{{ t('common.reconnect') }}</button>
      </div>
      <div class="gaming-header">
        <div class="phase-info">
          <span class="phase-badge" :class="phaseClass">{{ phaseCn }}</span>
          <span class="queue-name">{{ sessionData.typeCn || t('common.unknownMode') }}</span>
        </div>
        <div class="header-actions">
          <button class="refresh-btn-small" @click="fetchSessionData" :disabled="loading">
            <span class="refresh-icon" :class="{ 'spinning': loading }">↻</span>
            <span>{{ loading ? t('common.refreshing') : t('common.refresh') }}</span>
            <span v-if="loading" class="loading-bar">
              <span class="loading-progress"></span>
            </span>
          </button>
        </div>
      </div>

      <div :class="['match-analysis-toolbar', { 'has-analysis-output': latestAnalysisSummary }]">
        <svg class="ai-wordmark" viewBox="0 0 1240 220" aria-hidden="true" focusable="false">
          <defs>
            <linearGradient id="rankpeekAiStroke" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stop-color="rgba(84, 177, 255, 0.12)" />
              <stop offset="48%" stop-color="rgba(157, 219, 255, 0.42)" />
              <stop offset="100%" stop-color="rgba(50, 124, 255, 0.16)" />
            </linearGradient>
            <linearGradient id="rankpeekAiFill" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="rgba(255, 255, 255, 0.04)" />
              <stop offset="100%" stop-color="rgba(41, 151, 255, 0.1)" />
            </linearGradient>
            <filter id="rankpeekAiGlow" x="-8%" y="-60%" width="116%" height="220%">
              <feGaussianBlur stdDeviation="4" result="blur" />
              <feMerge>
                <feMergeNode in="blur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </defs>
          <g class="wordmark-skew" filter="url(#rankpeekAiGlow)">
            <text class="wordmark-echo" x="28" y="156">RANKPEEK</text>
            <text class="wordmark-echo wordmark-ai-echo" x="970" y="156">AI</text>
            <text class="wordmark-main" x="20" y="150">RANKPEEK</text>
            <text class="wordmark-main wordmark-ai" x="960" y="150">AI</text>
          </g>
        </svg>
        <div class="analysis-actions">
          <div class="analysis-action">
            <button class="analysis-btn" type="button" disabled>队友成分</button>
            <span class="analysis-help">检查大腿or拖油瓶</span>
          </div>
          <div class="analysis-action">
            <button class="analysis-btn" type="button" disabled>赛前分析</button>
            <span class="analysis-help">分析小代or软柿子</span>
          </div>
        </div>

        <div v-if="latestAnalysisSummary" class="analysis-result">
          <strong>{{ latestAnalysisSummary }}</strong>
          <p v-if="latestAnalysisDetail">{{ latestAnalysisDetail }}</p>
        </div>
      </div>

      <div class="teams-container">
        <section class="team-panel team-blue">
          <div class="team-header team-header-blue">
            <div class="team-title">
              <span class="team-icon">⚔</span>
              <div>
                <h2>{{ t('gaming.blueTeam') }}</h2>
                <span>{{ sessionData.teamOne?.length || 0 }} / 5</span>
              </div>
            </div>
          </div>
          <template v-if="!sessionData.teamOne || sessionData.teamOne.length === 0">
            <div class="team-placeholder team-placeholder-blue">
              <span class="placeholder-icon">👀</span>
              <span>{{ t('gaming.waitingToJoin') }}</span>
            </div>
          </template>
          <template v-else>
            <div class="team-players">
              <PlayerCard
                v-for="(player, idx) in sessionData.teamOne"
                :key="'blue-' + idx"
                :session-summoner="player"
                team="blue"
                @navigate-to-player="handleNavigateToPlayer"
              />
            </div>
          </template>
        </section>

        <section class="team-panel team-red">
          <div class="team-header team-header-red">
            <div class="team-title">
              <span class="team-icon">🛡</span>
              <div>
                <h2>{{ t('gaming.redTeam') }}</h2>
                <span>{{ sessionData.teamTwo?.length || 0 }} / 5</span>
              </div>
            </div>
          </div>
          <template v-if="sessionData.phase === 'ChampSelect' && (!sessionData.teamTwo || sessionData.teamTwo.length === 0)">
            <div class="enemy-loading">
              <div class="loading-dots">
                <span></span><span></span><span></span>
              </div>
              <span class="loading-text">{{ t('gaming.waitingEnemySelect') }}</span>
            </div>
          </template>
          <template v-else-if="!sessionData.teamTwo || sessionData.teamTwo.length === 0">
            <div class="enemy-placeholder">
              <span class="placeholder-icon">👀</span>
              <span>{{ t('gaming.waitingEnemyData') }}</span>
            </div>
          </template>
          <template v-else>
            <div class="team-players">
              <PlayerCard
                v-for="(player, idx) in sessionData.teamTwo"
                :key="'red-' + idx"
                :session-summoner="player"
                team="red"
                @navigate-to-player="handleNavigateToPlayer"
              />
            </div>
          </template>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { apiClient } from '@/api/httpClient'
import { wsClient } from '@/api/websocketClient'
import type { CacheUpdateEvent, SessionData } from '@/types/api'
import PlayerCard from '@/components/gaming/PlayerCard.vue'
import { DEFAULT_ANALYSIS_QUEUE_MODE } from '@/utils/matchPreferences'
import { useI18n, type MessageKey } from '@/i18n'

const router = useRouter()
const { t } = useI18n()

const sessionData = ref<SessionData>({
  phase: '',
  queueType: '',
  typeCn: '',
  queueId: 0,
  teamOne: [],
  teamTwo: []
})

const loading = ref(false)
let refreshInterval: ReturnType<typeof setInterval> | null = null
let unsubscribeCacheUpdate: (() => void) | null = null
let cacheUpdateRefreshTimer: ReturnType<typeof setTimeout> | null = null
let lastCacheUpdateRefreshAt = 0
let sessionFetchInFlight = false
const cacheUpdateRefreshDelay = 800
const minCacheUpdateRefreshInterval = 2500

let retryCount = 0
const maxRetries = 3

const failCount = ref(0)
const maxFailCount = 10
const isRefreshPaused = ref(false)
const latestAnalysisSummary = ref('')
const latestAnalysisDetail = ref('')
let autoResumeTimer: ReturnType<typeof setTimeout> | null = null

const phaseCn = computed(() => {
  const phaseMap: Record<string, MessageKey> = {
    ChampSelect: 'gaming.phase.ChampSelect',
    GameStart: 'gaming.phase.GameStart',
    InProgress: 'gaming.phase.InProgress',
    PreEndOfGame: 'gaming.phase.PreEndOfGame',
    EndOfGame: 'gaming.phase.EndOfGame',
    Lobby: 'gaming.phase.Lobby',
    Matchmaking: 'gaming.phase.Matchmaking',
    ReadyCheck: 'gaming.phase.ReadyCheck',
    Reconnect: 'gaming.phase.Reconnect'
  }
  const key = phaseMap[sessionData.value.phase]
  return key ? t(key) : sessionData.value.phase
})

const phaseClass = computed(() => {
  const phase = sessionData.value.phase
  if (phase === 'InProgress' || phase === 'GameStart') return 'phase-playing'
  if (phase === 'ChampSelect') return 'phase-select'
  if (phase === 'EndOfGame' || phase === 'PreEndOfGame') return 'phase-ended'
  return ''
})

async function fetchSessionData(options: { showLoading?: boolean } = {}) {
  if (isRefreshPaused.value || sessionFetchInFlight) return

  const showLoading = options.showLoading !== false
  sessionFetchInFlight = true
  if (showLoading) loading.value = true
  try {
    const data = await apiClient.getSessionData(DEFAULT_ANALYSIS_QUEUE_MODE)
    sessionData.value = data
    failCount.value = 0
  } catch (e) {
    console.error('Failed to fetch session data', e)
    failCount.value++
    if (failCount.value >= maxFailCount) {
      pauseRefresh()
    }
  } finally {
    if (showLoading) loading.value = false
    sessionFetchInFlight = false
  }
}

function collectCurrentSessionPuuids(): Set<string> {
  const puuids = new Set<string>()

  for (const player of sessionData.value.teamOne || []) {
    const puuid = player?.summoner?.puuid
    if (puuid) puuids.add(puuid)
  }

  for (const player of sessionData.value.teamTwo || []) {
    const puuid = player?.summoner?.puuid
    if (puuid) puuids.add(puuid)
  }

  return puuids
}

function isCacheUpdateRelevant(event: CacheUpdateEvent): boolean {
  if (!event || event.type !== 'PLAYER_CACHE_UPDATED' || !event.puuid) {
    return false
  }

  const currentPuuids = collectCurrentSessionPuuids()
  return currentPuuids.has(event.puuid)
}

function scheduleCacheUpdateRefresh() {
  if (isRefreshPaused.value) return

  const now = Date.now()
  const elapsed = now - lastCacheUpdateRefreshAt

  if (elapsed >= minCacheUpdateRefreshInterval) {
    lastCacheUpdateRefreshAt = now
    fetchSessionData({ showLoading: false })
    return
  }

  if (cacheUpdateRefreshTimer) return

  cacheUpdateRefreshTimer = setTimeout(() => {
    cacheUpdateRefreshTimer = null
    lastCacheUpdateRefreshAt = Date.now()
    fetchSessionData({ showLoading: false })
  }, cacheUpdateRefreshDelay)
}

function pauseRefresh() {
  isRefreshPaused.value = true
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
  if (autoResumeTimer) clearTimeout(autoResumeTimer)
  autoResumeTimer = setTimeout(() => {
    console.log('Auto-resuming session refresh')
    resumeRefresh()
  }, 15000)
}

function resumeRefresh() {
  isRefreshPaused.value = false
  failCount.value = 0
  if (autoResumeTimer) {
    clearTimeout(autoResumeTimer)
    autoResumeTimer = null
  }
  fetchSessionData()
  if (!refreshInterval) {
    refreshInterval = setInterval(fetchSessionData, 5000)
  }
}

function handleNavigateToPlayer(gameName: string, tagLine: string) {
  router.push({
    path: '/summoner',
    query: { name: `${gameName}#${tagLine}` }
  })
}

onMounted(() => {
  fetchSessionData()
  refreshInterval = setInterval(fetchSessionData, 5000)
  unsubscribeCacheUpdate = wsClient.onCacheUpdate((event: CacheUpdateEvent) => {
    if (isCacheUpdateRelevant(event)) {
      scheduleCacheUpdateRefresh()
    }
  })
})

watch(() => sessionData.value.phase, (newVal, oldVal) => {
  if (newVal === 'ChampSelect' && oldVal !== 'ChampSelect') {
    retryCount = 0
    setTimeout(() => fetchSessionData(), 1000)
  }
  if (newVal === 'InProgress' && oldVal !== 'InProgress') {
    retryCount = 0
    setTimeout(() => checkAndRetryFetch(), 2000)
  }
  if (newVal === 'GameStart' && oldVal !== 'GameStart') {
    setTimeout(() => fetchSessionData(), 1500)
  }
})

function checkAndRetryFetch() {
  const phase = sessionData.value.phase
  if (phase === 'InProgress' || phase === 'GameStart' || phase === 'ChampSelect') {
    const enemyMissing =
      !sessionData.value.teamTwo ||
      sessionData.value.teamTwo.length === 0 ||
      sessionData.value.teamTwo.every((p: any) => !p.summoner?.gameName)

    if (enemyMissing && retryCount < maxRetries) {
      retryCount++
      console.log(`Enemy data missing, retry ${retryCount}/${maxRetries} in 3 seconds`)
      setTimeout(() => {
        fetchSessionData()
        setTimeout(checkAndRetryFetch, 4000)
      }, 3000)
    }
  }
}

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
  }
  if (autoResumeTimer) {
    clearTimeout(autoResumeTimer)
  }
  if (unsubscribeCacheUpdate) {
    unsubscribeCacheUpdate()
    unsubscribeCacheUpdate = null
  }
  if (cacheUpdateRefreshTimer) {
    clearTimeout(cacheUpdateRefreshTimer)
    cacheUpdateRefreshTimer = null
  }
})
</script>

<style scoped>
.gaming-view {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

/* Waiting state */
.not-in-game {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 16px;
  text-align: center;
}

.not-in-game-icon {
  font-size: 64px;
  opacity: 0.5;
}

.not-in-game h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
  font-weight: 700;
}

.not-in-game p {
  margin: 0;
  color: var(--text-secondary);
  max-width: 300px;
}

.refresh-btn {
  padding: 10px 24px;
  background: var(--accent-color);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
  font-weight: 600;
}

.refresh-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

/* Active game state */
.gaming-content {
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 16px;
}

.gaming-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--bg-secondary);
  border-radius: 10px;
  border: 1px solid var(--border-color);
}

.phase-info {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex-wrap: wrap;
}

.phase-badge {
  padding: 7px 13px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 700;
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.phase-badge.phase-playing {
  background: rgba(61, 155, 122, 0.2);
  color: #3d9b7a;
}

.phase-badge.phase-select {
  background: rgba(92, 163, 234, 0.2);
  color: #5ca3ea;
}

.phase-badge.phase-ended {
  background: rgba(128, 128, 128, 0.2);
  color: var(--text-secondary);
}

.queue-name {
  font-size: 15px;
  color: var(--text-secondary);
  font-weight: 700;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.refresh-btn-small {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  padding: 6px 16px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
  position: relative;
  overflow: hidden;
  font-weight: 600;
}

.refresh-btn-small:hover:not(:disabled) {
  background: var(--bg-elevated);
}

.refresh-btn-small:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.match-analysis-toolbar {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0;
  min-height: 142px;
  max-height: 142px;
  padding: 28px 22px;
  border: 1px solid rgba(var(--accent-rgb), 0.34);
  border-radius: 16px;
  background:
    radial-gradient(circle at 18% 0%, rgba(var(--accent-rgb), 0.2), transparent 34%),
    radial-gradient(circle at 82% 100%, rgba(77, 210, 255, 0.12), transparent 38%),
    linear-gradient(135deg, rgba(10, 16, 28, 0.96), rgba(22, 28, 42, 0.9));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 0 0 1px rgba(var(--accent-rgb), 0.08),
    0 16px 36px rgba(0, 0, 0, 0.28),
    0 0 34px rgba(var(--accent-rgb), 0.14);
  transition:
    min-height var(--transition-normal),
    max-height var(--transition-normal),
    border-color var(--transition-fast),
    box-shadow var(--transition-fast);
}

.match-analysis-toolbar.has-analysis-output {
  align-items: stretch;
  justify-content: flex-start;
  min-height: 248px;
  max-height: 360px;
  border-color: rgba(var(--accent-rgb), 0.48);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 0 0 1px rgba(var(--accent-rgb), 0.12),
    0 20px 42px rgba(0, 0, 0, 0.32),
    0 0 42px rgba(var(--accent-rgb), 0.2);
}

.ai-wordmark {
  position: absolute;
  right: -28px;
  top: 50%;
  z-index: 0;
  width: auto;
  height: clamp(170px, 24vw, 300px);
  transform: translateY(-47%);
  opacity: 0.78;
  pointer-events: none;
}

.wordmark-skew {
  transform: skewX(-9deg);
  transform-origin: 50% 50%;
}

.wordmark-main,
.wordmark-echo {
  font-family: Arial, Helvetica, sans-serif;
  font-weight: 900;
  font-size: 142px;
  line-height: 1;
  letter-spacing: 0;
  paint-order: stroke fill;
}

.wordmark-main {
  fill: url(#rankpeekAiFill);
  stroke: url(#rankpeekAiStroke);
  stroke-width: 3px;
}

.wordmark-echo {
  fill: transparent;
  stroke: rgba(84, 177, 255, 0.12);
  stroke-width: 8px;
}

.wordmark-ai,
.wordmark-ai-echo {
  font-size: 154px;
}

.match-analysis-toolbar::after {
  content: '';
  position: absolute;
  inset: 1px;
  z-index: -1;
  border-radius: 15px;
  background:
    linear-gradient(90deg, transparent, rgba(var(--accent-rgb), 0.08), transparent),
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.04) 0 1px, transparent 1px 26px);
  opacity: 0.55;
}

.analysis-actions {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 16px;
  width: 100%;
}

.analysis-action {
  position: relative;
  display: inline-flex;
  flex: 0 1 220px;
  min-width: 178px;
  z-index: 1;
}

.analysis-result {
  position: relative;
  z-index: 1;
  margin-top: 18px;
  padding: 16px 18px;
  border: 1px solid rgba(var(--accent-rgb), 0.24);
  border-radius: 14px;
  background: rgba(8, 14, 24, 0.7);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 14px 28px rgba(0, 0, 0, 0.24);
  backdrop-filter: blur(8px);
}

.analysis-result strong {
  display: block;
  color: #f4fbff;
  font-size: 18px;
  line-height: 1.45;
}

.analysis-result p {
  margin: 8px 0 0;
  color: #aacfff;
  font-size: 14px;
  line-height: 1.6;
}

.refresh-icon {
  font-size: 14px;
  transition: transform 0.3s;
}

.refresh-icon.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.loading-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: rgba(255,255,255,0.1);
}

.loading-progress {
  display: block;
  height: 100%;
  background: var(--accent-color);
  animation: loading-progress 1.5s ease-in-out infinite;
}

@keyframes loading-progress {
  0% { width: 0%; }
  50% { width: 70%; }
  100% { width: 100%; }
}

/* Teams */
.teams-container {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  align-content: start;
}

.team-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--bg-secondary);
}

.team-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
}

.team-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.team-title h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 21px;
  line-height: 1.1;
}

.team-title span:not(.team-icon) {
  display: block;
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.team-icon {
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  font-size: 20px;
  background: rgba(255, 255, 255, 0.06);
}

.analysis-btn {
  position: relative;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  width: 100%;
  min-height: 84px;
  padding: 0 30px;
  border-radius: 14px;
  border: 1px solid rgba(126, 198, 255, 0.62);
  background:
    linear-gradient(180deg, rgba(38, 58, 88, 0.98), rgba(10, 16, 28, 0.98)),
    rgba(var(--accent-rgb), 0.16);
  color: #d9efff;
  font-size: 28px;
  font-weight: 900;
  line-height: 1;
  text-align: left;
  text-shadow: 0 0 18px rgba(var(--accent-rgb), 0.32);
  transform: translateY(0);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.16),
    inset 0 -14px 28px rgba(var(--accent-rgb), 0.12),
    0 0 0 1px rgba(var(--accent-rgb), 0.16),
    0 14px 30px rgba(0, 0, 0, 0.28),
    0 0 24px rgba(var(--accent-rgb), 0.24);
  transition:
    transform var(--transition-fast),
    border-color var(--transition-fast),
    box-shadow var(--transition-fast),
    color var(--transition-fast);
}

.analysis-btn::before {
  content: '';
  position: absolute;
  inset: -1px;
  background: linear-gradient(120deg, transparent 0%, rgba(255, 255, 255, 0.34) 45%, transparent 58%);
  transform: translateX(-140%);
  transition: transform 0.62s ease;
}

.analysis-action:hover .analysis-btn,
.analysis-action:focus-within .analysis-btn {
  transform: translateY(-2px);
  border-color: rgba(146, 211, 255, 0.9);
  color: #ffffff;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.18),
    0 0 0 1px rgba(var(--accent-rgb), 0.22),
    0 18px 34px rgba(0, 0, 0, 0.32),
    0 0 34px rgba(var(--accent-rgb), 0.42);
}

.analysis-action:hover .analysis-btn::before,
.analysis-action:focus-within .analysis-btn::before {
  transform: translateX(140%);
}

.analysis-btn:disabled {
  opacity: 1;
  cursor: default;
}

.analysis-help {
  position: absolute;
  left: 50%;
  top: calc(100% + 7px);
  z-index: 5;
  width: max-content;
  max-width: 180px;
  padding: 6px 9px;
  border: 1px solid rgba(var(--accent-rgb), 0.26);
  border-radius: 8px;
  background: rgba(8, 14, 24, 0.96);
  color: #aacfff;
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.32), 0 0 18px rgba(var(--accent-rgb), 0.16);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.25;
  opacity: 0;
  pointer-events: none;
  transform: translate(-50%, -4px);
  transition: opacity var(--transition-fast), transform var(--transition-fast);
}

.analysis-action:hover .analysis-help,
.analysis-action:focus-within .analysis-help {
  opacity: 1;
  transform: translate(-50%, 0);
}

.team-header-blue {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.25), rgba(59, 130, 246, 0.1));
  color: #93c5fd;
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.team-header-red {
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.25), rgba(239, 68, 68, 0.1));
  color: #fca5a5;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.team-players {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}

/* Enemy loading state */
.enemy-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: rgba(239, 68, 68, 0.05);
  border-radius: 10px;
  border: 1px dashed rgba(239, 68, 68, 0.3);
}

.loading-dots {
  display: flex;
  gap: 6px;
}

.loading-dots span {
  width: 8px;
  height: 8px;
  background: rgba(239, 68, 68, 0.5);
  border-radius: 50%;
  animation: dot-bounce 1.4s ease-in-out infinite both;
}

.loading-dots span:nth-child(1) { animation-delay: -0.32s; }
.loading-dots span:nth-child(2) { animation-delay: -0.16s; }
.loading-dots span:nth-child(3) { animation-delay: 0s; }

@keyframes dot-bounce {
  0%, 80%, 100% {
    transform: scale(0);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.loading-text {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
}

/* Enemy placeholder */
.enemy-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(239, 68, 68, 0.05);
  border-radius: 10px;
  border: 1px dashed rgba(239, 68, 68, 0.3);
  color: var(--text-tertiary);
  font-size: 14px;
}

/* Ally placeholder */
.team-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  border-radius: 10px;
  font-size: 14px;
}

.team-placeholder-blue {
  background: rgba(61, 155, 122, 0.08);
  border: 1px dashed rgba(61, 155, 122, 0.4);
  color: var(--text-secondary);
}

.placeholder-icon {
  font-size: 32px;
  opacity: 0.6;
}

/* Connection warning */
.connection-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 12px;
  padding: 12px 16px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 8px;
  color: #fca5a5;
  font-size: 13px;
}

.connection-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 10px 16px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 8px;
  color: #fca5a5;
  font-size: 13px;
  margin-bottom: 12px;
}

.error-icon {
  font-size: 16px;
}

.resume-btn,
.resume-btn-small {
  padding: 6px 14px;
  background: rgba(239, 68, 68, 0.2);
  color: #fca5a5;
  border: 1px solid rgba(239, 68, 68, 0.4);
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  font-weight: 600;
}

.resume-btn:hover,
.resume-btn-small:hover {
  background: rgba(239, 68, 68, 0.3);
}

@media (min-width: 1180px) {
  .teams-container {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .gaming-content {
    gap: 12px;
  }

  .gaming-header,
  .team-header {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions {
    justify-content: stretch;
  }

  .refresh-btn-small {
    flex: 1;
    justify-content: center;
    min-height: 42px;
    font-size: 14px;
  }

  .match-analysis-toolbar {
    align-items: center;
    gap: 10px;
    min-height: 126px;
    max-height: 126px;
    padding: 14px;
  }

  .match-analysis-toolbar.has-analysis-output {
    align-items: stretch;
    min-height: 260px;
    max-height: 420px;
  }

  .analysis-actions {
    gap: 10px;
    justify-content: flex-start;
  }

  .analysis-action {
    flex: 1 1 0;
    min-width: 0;
  }

  .analysis-btn {
    min-height: 76px;
    padding: 0 10px;
    border-radius: 13px;
    font-size: 24px;
  }

  .analysis-help {
    max-width: 138px;
  }

  .team-panel {
    padding: 12px;
  }

  .team-title h2 {
    font-size: 20px;
  }
}
</style>
