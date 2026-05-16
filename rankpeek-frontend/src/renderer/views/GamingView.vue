<template>
  <div ref="gamingViewRef" class="gaming-view">
    <div class="gaming-content" :class="{ 'is-idle': !hasActiveSession }">
      <div v-if="isRefreshPaused" class="connection-bar">
        <span class="error-icon" aria-hidden="true"></span>
        <span>{{ t('gaming.connectionPaused') }}</span>
        <button class="resume-btn-small control-glow" @click="resumeRefresh">{{ t('common.reconnect') }}</button>
      </div>
      <div class="gaming-header surface-glow">
        <div class="phase-info">
          <span class="phase-badge" :class="phaseClass">{{ phaseCn }}</span>
          <span class="queue-name" :class="{ unknown: queueUnknown }">{{ queueName }}</span>
        </div>
        <div class="header-actions">
          <RefreshIconButton
            :aria-label="refreshButtonLabel"
            :title="refreshButtonLabel"
            :loading="loading"
            :disabled="loading"
            @click="() => fetchSessionData()"
          />
        </div>
      </div>

      <div class="teams-container">
        <section class="team-panel team-blue surface-glow">
          <div class="team-header team-header-blue">
            <div class="team-title">
              <div>
                <h2>{{ t('gaming.blueTeam') }}</h2>
                <span>{{ blueTeamCount }} / 5</span>
              </div>
            </div>
            <button
              class="team-analysis-btn team-analysis-btn-blue control-glow"
              type="button"
              title="打开队友成分分析"
              @click="openGamingAiAnalysis('teammate')"
            >
              队友成分
            </button>
          </div>
          <div class="team-players">
            <template
              v-for="(player, idx) in blueTeamPlayers"
              :key="getParticipantKey(player) || `blue-${idx}`"
            >
              <PlayerCard
                class="gaming-player-card surface-glow"
                :session-summoner="player"
                :selected="isParticipantExpanded(player)"
                team="blue"
                @select-player="toggleParticipantRecentMatches(player)"
              />
              <ParticipantRecentMatchesPanel
                v-if="isParticipantExpanded(player)"
                class="participant-recent-inline-panel surface-glow"
                :player="player"
              />
            </template>
            <span
              v-for="slot in blueEmptySlots"
              :key="`blue-empty-${slot}`"
              class="idle-player-slot idle-player-slot-blue"
              aria-hidden="true"
            >
              <span class="idle-slot-avatar"></span>
              <span class="idle-slot-copy">
                <span class="idle-slot-title"></span>
                <span class="idle-slot-subtitle"></span>
              </span>
            </span>
          </div>
        </section>

        <section class="team-panel team-red surface-glow">
          <div class="team-header team-header-red">
            <div class="team-title">
              <div>
                <h2>{{ t('gaming.redTeam') }}</h2>
                <span>{{ redTeamCount }} / 5</span>
              </div>
            </div>
            <button
              class="team-analysis-btn team-analysis-btn-red control-glow"
              type="button"
              title="打开赛前对手分析"
              @click="openGamingAiAnalysis('opponent')"
            >
              赛前分析
            </button>
          </div>
          <div class="team-players">
            <template
              v-for="(player, idx) in redTeamPlayers"
              :key="getParticipantKey(player) || `red-${idx}`"
            >
              <PlayerCard
                class="gaming-player-card surface-glow"
                :session-summoner="player"
                :selected="isParticipantExpanded(player)"
                team="red"
                @select-player="toggleParticipantRecentMatches(player)"
              />
              <ParticipantRecentMatchesPanel
                v-if="isParticipantExpanded(player)"
                class="participant-recent-inline-panel surface-glow"
                :player="player"
              />
            </template>
            <span
              v-for="slot in redEmptySlots"
              :key="`red-empty-${slot}`"
              class="idle-player-slot idle-player-slot-red"
              aria-hidden="true"
            >
              <span class="idle-slot-avatar"></span>
              <span class="idle-slot-copy">
                <span class="idle-slot-title"></span>
                <span class="idle-slot-subtitle"></span>
              </span>
            </span>
          </div>
        </section>
      </div>
    </div>
    <GamingAiAnalysisModal
      :open="gamingAiModalOpen"
      :mode="gamingAiModalMode"
      :preview="gamingAiPreview"
      :queue-label="gamingAiQueueLabel"
      :analysis-enabled="gamingAiAnalysisEnabled"
      :stream-state="gamingAiStreamState"
      :stream-text="gamingAiStreamText"
      :stream-error="gamingAiStreamError"
      :player-verdicts="gamingAiPlayerVerdicts"
      @start-analysis="startGamingAiServerAnalysis"
      @cancel-analysis="cancelGamingAiServerAnalysis"
      @close="closeGamingAiAnalysis"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { getGamingSessionData } from '@/api/sessionDataAdapter'
import { apiClient } from '@/api/httpClient'
import { wsClient } from '@/api/websocketClient'
import { listenGameflowPhase } from '@/services/gameflowPhaseListener'
import RefreshIconButton from '@/components/common/RefreshIconButton.vue'
import GamingAiAnalysisModal from '@/components/gaming/GamingAiAnalysisModal.vue'
import ParticipantRecentMatchesPanel from '@/components/gaming/ParticipantRecentMatchesPanel.vue'
import type { CacheUpdateEvent, Lobby, SessionData, SessionSummoner, Summoner } from '@/types/api'
import PlayerCard from '@/components/gaming/PlayerCard.vue'
import {
  createGamingAiAnalysisPreview,
  type GamingAiAnalysisMode,
  type GamingAiAnalysisPreview
} from '@/services/gamingAiAnalysisPreview'
import { buildGamingAiInputSnapshot } from '@/services/gamingAiInputSnapshot'
import {
  createGamingAiStreamRequest,
  streamGamingAiAnalysis,
  type GamingAiPlayerStreamVerdict,
  type GamingAiStreamState
} from '@/services/gamingAiServerStream'
import {
  isGamingAiAnalysisEnabledQueue,
  normalizeGamingQueueLabel
} from '@/services/gamingAiQueue'
import {
  buildLobbyDisplaySessionSummoners,
  createGameflowPhaseTransitionTracker,
  createGamingSessionDataState,
  formatLobbyQueueName,
  isGameflowLobbyDisplayPhase,
  isGameflowSessionClearPhase,
  isGameflowSessionRefreshPhase,
  isPostgameNavigationLogPhase
} from '@/services/gamingSessionFlow'
import { useI18n, type MessageKey } from '@/i18n'

const { t } = useI18n()

const CONTROL_GLOW_RANGE = 96
const SURFACE_GLOW_RANGE = 220
const EDGE_GLOW_MIN = 0.03
const PAGE_GLOW_SELECTOR = '.surface-glow, .control-glow'

const gamingViewRef = ref<HTMLElement | null>(null)

const sessionState = createGamingSessionDataState()
const gameflowPhaseTransitions = createGameflowPhaseTransitionTracker()
const sessionData = ref<SessionData>(sessionState.sessionData)
const currentGameflowPhase = ref('')
const lobbyData = ref<Lobby | null>(null)
const currentSummoner = ref<Summoner | null>(null)
const lobbyLoading = ref(false)
const lobbyError = ref('')

const initialLoading = ref(false)
const refreshing = ref(false)
const lastError = ref('')
const loading = computed(() => initialLoading.value || refreshing.value)
let refreshInterval: ReturnType<typeof setInterval> | null = null
let unsubscribeGameflowPhase: (() => void) | null = null
let unsubscribeCacheUpdate: (() => void) | null = null
let unsubscribeLobby: (() => void) | null = null
let cacheUpdateRefreshTimer: ReturnType<typeof setTimeout> | null = null
let lastCacheUpdateRefreshAt = 0
let sessionFetchInFlight = false
let lobbyFetchInFlight = false
let lobbyRequestId = 0
let hasCompletedInitialSessionFetch = false
const cacheUpdateRefreshDelay = 800
const minCacheUpdateRefreshInterval = 2500

let retryCount = 0
const maxRetries = 3

const failCount = ref(0)
const maxFailCount = 10
const isRefreshPaused = ref(false)
let autoResumeTimer: ReturnType<typeof setTimeout> | null = null

const hasActiveSession = computed(() => {
  const phase = sessionData.value.phase
  return Boolean(phase && !sessionData.value.stale && !sessionData.value.empty && !isGameflowSessionClearPhase(phase))
})
const displayedGameflowPhase = computed(() => currentGameflowPhase.value || sessionData.value.phase)
const hasLobbyPhase = computed(() => isGameflowLobbyDisplayPhase(displayedGameflowPhase.value))
const lobbyQueueLabel = computed(() => formatLobbyQueueName(lobbyData.value))
const lobbyTeamPlayers = computed<SessionSummoner[]>(() =>
  hasLobbyPhase.value ? buildLobbyDisplaySessionSummoners(lobbyData.value, currentSummoner.value, sessionData.value) : []
)

const blueTeamPlayers = computed(() => hasActiveSession.value ? (sessionData.value.teamOne || []) : lobbyTeamPlayers.value)
const redTeamPlayers = computed(() => hasActiveSession.value ? (sessionData.value.teamTwo || []) : [])
const blueTeamCount = computed(() => blueTeamPlayers.value.length)
const redTeamCount = computed(() => redTeamPlayers.value.length)
const blueEmptySlots = computed(() => Math.max(0, 5 - blueTeamCount.value))
const redEmptySlots = computed(() => Math.max(0, 5 - redTeamCount.value))
const allSessionPlayers = computed<SessionSummoner[]>(() => [
  ...blueTeamPlayers.value,
  ...redTeamPlayers.value
])
const expandedParticipantKeys = ref<Set<string>>(new Set())
const gamingAiModalOpen = ref(false)
const gamingAiModalMode = ref<GamingAiAnalysisMode>('teammate')
const gamingAiPreview = ref<GamingAiAnalysisPreview | null>(null)
const gamingAiStreamState = ref<GamingAiStreamState>('idle')
const gamingAiStreamText = ref('')
const gamingAiStreamError = ref('')
const gamingAiPlayerVerdicts = ref<Record<string, GamingAiPlayerStreamVerdict>>({})
let gamingAiStreamAbortController: AbortController | null = null

const phaseCn = computed(() => {
  const phaseMap: Record<string, MessageKey> = {
    ChampSelect: 'gaming.phase.ChampSelect',
    GameStart: 'gaming.phase.GameStart',
    InProgress: 'gaming.phase.InProgress',
    PreEndOfGame: 'gaming.phase.PreEndOfGame',
    EndOfGame: 'gaming.phase.EndOfGame',
    PostGame: 'gaming.phase.EndOfGame',
    POST_GAME: 'gaming.phase.EndOfGame',
    Lobby: 'gaming.phase.Lobby',
    Matchmaking: 'gaming.phase.Matchmaking',
    ReadyCheck: 'gaming.phase.ReadyCheck',
    Reconnect: 'gaming.phase.Reconnect'
  }
  const phase = sessionData.value.phase
  if (!phase || phase === 'None') {
    return '等待对局'
  }
  const key = phaseMap[sessionData.value.phase]
  return key ? t(key) : '未进入对局'
})

const phaseClass = computed(() => {
  const phase = sessionData.value.phase
  if (!phase || phase === 'None') return 'phase-idle'
  if (phase === 'InProgress' || phase === 'GameStart') return 'phase-playing'
  if (phase === 'ChampSelect') return 'phase-select'
  if (phase === 'EndOfGame' || phase === 'PreEndOfGame' || phase === 'PostGame' || phase === 'POST_GAME') return 'phase-ended'
  return ''
})

const queueName = computed(() => {
  if (hasActiveSession.value) {
    return sessionData.value.typeCn || t('common.unknownMode')
  }
  if (hasLobbyPhase.value) {
    if (lobbyQueueLabel.value) {
      return lobbyQueueLabel.value
    }
    if (isGameflowLobbyDisplayPhase(sessionData.value.phase) && sessionData.value.typeCn) {
      return sessionData.value.typeCn
    }
    if (lobbyLoading.value) {
      return '正在读取大厅'
    }
    if (lobbyError.value) {
      return lobbyError.value
    }
    return '大厅'
  }
  if (lastError.value) {
    return lastError.value
  }
  return '未进入房间'
})
const queueUnknown = computed(() => {
  if (hasActiveSession.value) {
    return !sessionData.value.typeCn
  }
  if (hasLobbyPhase.value) {
    return !(lobbyQueueLabel.value || sessionData.value.typeCn)
  }
  return true
})
const gamingAiQueueLabel = computed(() => normalizeGamingQueueLabel(sessionData.value))
const gamingAiAnalysisEnabled = computed(() => isGamingAiAnalysisEnabledQueue(sessionData.value))
const refreshButtonLabel = computed(() => {
  if (loading.value) return t('common.refreshing')
  return hasActiveSession.value ? t('common.refresh') : t('common.refreshStatus')
})

function isDisabledControl(target: HTMLElement) {
  return target instanceof HTMLButtonElement && target.disabled
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
  if (!rect.width || !rect.height) {
    resetGlowElement(target)
    return
  }

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

function updatePageGlow(event: PointerEvent) {
  gamingViewRef.value?.querySelectorAll<HTMLElement>(PAGE_GLOW_SELECTOR).forEach(element => {
    applyGlowElement(element, event.clientX, event.clientY)
  })
}

function resetPageGlow() {
  gamingViewRef.value?.querySelectorAll<HTMLElement>(PAGE_GLOW_SELECTOR).forEach(resetGlowElement)
}

function getParticipantKey(player: SessionSummoner | null | undefined): string {
  const puuid = player?.summoner?.puuid?.trim()
  if (puuid) {
    return `puuid:${puuid}`
  }

  const summonerId = normalizeKeyPart(player?.summoner?.summonerId)
  if (summonerId) {
    return `summoner:${summonerId}`
  }

  const gameName = player?.summoner?.gameName?.trim()
  const tagLine = player?.summoner?.tagLine?.trim()
  if (gameName) {
    return `riot:${gameName}#${tagLine || ''}`
  }

  return ''
}

function normalizeKeyPart(value: unknown): string {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return String(value)
  }
  if (typeof value === 'string') {
    return value.trim()
  }
  return ''
}

function isParticipantExpanded(player: SessionSummoner): boolean {
  const key = getParticipantKey(player)
  return Boolean(key && expandedParticipantKeys.value.has(key))
}

function toggleParticipantRecentMatches(player: SessionSummoner) {
  const key = getParticipantKey(player)
  if (!key) {
    return
  }
  const nextKeys = new Set(expandedParticipantKeys.value)
  if (nextKeys.has(key)) {
    nextKeys.delete(key)
  } else {
    nextKeys.add(key)
  }
  expandedParticipantKeys.value = nextKeys
}

function openGamingAiAnalysis(mode: GamingAiAnalysisMode) {
  cancelGamingAiServerAnalysis()
  gamingAiModalMode.value = mode
  refreshGamingAiPreview(mode)
  resetGamingAiStreamState()
  gamingAiModalOpen.value = true
}

function closeGamingAiAnalysis() {
  cancelGamingAiServerAnalysis()
  resetGamingAiStreamState()
  gamingAiModalOpen.value = false
}

function refreshGamingAiPreview(mode: GamingAiAnalysisMode = gamingAiModalMode.value) {
  const players = mode === 'teammate' ? blueTeamPlayers.value : redTeamPlayers.value
  gamingAiPreview.value = createGamingAiAnalysisPreview({
    mode,
    players,
    sessionData: sessionData.value,
    currentSummonerPuuid: sessionData.value.currentSummoner?.puuid
  })
}

async function startGamingAiServerAnalysis() {
  if (gamingAiStreamState.value === 'preparing' || gamingAiStreamState.value === 'streaming') {
    return
  }
  if (!isGamingAiAnalysisEnabledQueue(sessionData.value)) {
    return
  }

  const players = gamingAiModalMode.value === 'teammate' ? blueTeamPlayers.value : redTeamPlayers.value
  const snapshot = buildGamingAiInputSnapshot({
    mode: gamingAiModalMode.value,
    sessionData: sessionData.value,
    selectedPlayers: players,
    currentSummonerPuuid: sessionData.value.currentSummoner?.puuid
  })
  const request = createGamingAiStreamRequest(snapshot)
  const controller = new AbortController()
  gamingAiStreamAbortController = controller
  gamingAiStreamState.value = 'preparing'
  gamingAiStreamText.value = ''
  gamingAiStreamError.value = ''
  gamingAiPlayerVerdicts.value = {}

  const result = await streamGamingAiAnalysis(request, {
    onEvent: (event) => {
      if (controller.signal.aborted) {
        return
      }
      if (event.type === 'player_verdict') {
        gamingAiStreamState.value = 'streaming'
        gamingAiPlayerVerdicts.value = {
          ...gamingAiPlayerVerdicts.value,
          [event.playerKey]: event
        }
        return
      }
      if (event.type === 'start') {
        gamingAiStreamState.value = 'streaming'
      }
      if (event.type === 'section') {
        gamingAiStreamState.value = 'streaming'
        gamingAiStreamText.value += `${gamingAiStreamText.value ? '\n\n' : ''}${event.title}\n`
      }
    },
    onDelta: (text) => {
      if (controller.signal.aborted) {
        return
      }
      gamingAiStreamState.value = 'streaming'
      gamingAiStreamText.value += text
    },
    onError: (message) => {
      if (controller.signal.aborted) {
        return
      }
      gamingAiStreamError.value = message
      gamingAiStreamState.value = 'failed'
    },
    onDone: () => {
      if (controller.signal.aborted) {
        return
      }
      gamingAiStreamState.value = 'completed'
    }
  }, { signal: controller.signal })

  if (gamingAiStreamAbortController !== controller) {
    return
  }

  gamingAiStreamAbortController = null
  if (!result.ok) {
    if (controller.signal.aborted) {
      gamingAiStreamState.value = 'idle'
      return
    }
    gamingAiStreamError.value = result.message
    gamingAiStreamState.value = 'failed'
    return
  }

  if (gamingAiStreamState.value !== 'completed') {
    gamingAiStreamState.value = 'completed'
  }
}

function cancelGamingAiServerAnalysis() {
  if (gamingAiStreamAbortController) {
    gamingAiStreamAbortController.abort()
    gamingAiStreamAbortController = null
  }
  if (gamingAiStreamState.value === 'preparing' || gamingAiStreamState.value === 'streaming') {
    gamingAiStreamState.value = 'idle'
  }
}

function resetGamingAiStreamState() {
  gamingAiStreamState.value = 'idle'
  gamingAiStreamText.value = ''
  gamingAiStreamError.value = ''
  gamingAiPlayerVerdicts.value = {}
}

function syncSessionDataFromState() {
  sessionData.value = sessionState.sessionData
}

function applyLobbyData(lobby: Lobby | null | undefined) {
  lobbyData.value = lobby || null
  lobbyError.value = ''
}

function clearLobbyStatus() {
  lobbyRequestId += 1
  lobbyData.value = null
  currentSummoner.value = null
  lobbyLoading.value = false
  lobbyError.value = ''
  lobbyFetchInFlight = false
}

async function fetchLobbyData(options: { force?: boolean } = {}) {
  if (lobbyFetchInFlight || (!options.force && lobbyData.value)) return

  const requestId = ++lobbyRequestId
  lobbyFetchInFlight = true
  lobbyLoading.value = true
  lobbyError.value = ''

  try {
    const lobby = await apiClient.getLobby()
    const state = await apiClient.getGameState().catch(() => null)
    if (requestId !== lobbyRequestId) return
    currentSummoner.value = state?.summoner || currentSummoner.value
    applyLobbyData(lobby)
  } catch (error) {
    if (requestId !== lobbyRequestId) return
    lobbyData.value = null
    lobbyError.value = extractFetchErrorMessage(error)
    console.warn('Failed to fetch lobby status', error)
  } finally {
    if (requestId === lobbyRequestId) {
      lobbyLoading.value = false
      lobbyFetchInFlight = false
    }
  }
}

function clearSessionDataForPhase(phase: string) {
  sessionState.clearForPhase(phase)
  syncSessionDataFromState()
  sessionFetchInFlight = false
  initialLoading.value = false
  refreshing.value = false
  lastError.value = ''
  retryCount = 0
}

function handleGameflowPhaseChange(phase: string) {
  currentGameflowPhase.value = phase
  console.debug(`[gameflow] phase=${phase}`)
  if (!gameflowPhaseTransitions.shouldHandlePhase(phase)) {
    return
  }

  if (isGameflowSessionRefreshPhase(phase)) {
    clearLobbyStatus()
    console.debug('未来这里可跳转到对战信息')
    retryCount = 0
    void fetchSessionData({ force: true })
    return
  }

  if (isGameflowSessionClearPhase(phase)) {
    clearSessionDataForPhase(phase)
    if (isGameflowLobbyDisplayPhase(phase)) {
      void fetchLobbyData({ force: true })
      void fetchSessionData({ showLoading: false, force: true })
    } else {
      clearLobbyStatus()
    }
    if (isPostgameNavigationLogPhase(phase)) {
      console.debug('未来这里可跳转到我的战绩')
    }
  }
}

async function fetchSessionData(options: { showLoading?: boolean; force?: boolean } = {}) {
  if (isRefreshPaused.value || (sessionFetchInFlight && !options.force)) return

  const shouldShowFetchState = options.showLoading !== false
  const showInitialLoading = shouldShowFetchState && !hasCompletedInitialSessionFetch
  const requestId = sessionState.beginFetch()
  sessionFetchInFlight = true
  lastError.value = ''
  if (showInitialLoading) {
    initialLoading.value = true
  } else if (shouldShowFetchState) {
    refreshing.value = true
  }
  try {
    const data = await getGamingSessionData({ forceRefresh: options.force === true })
    if (sessionState.applyFetchedData(requestId, data)) {
      syncSessionDataFromState()
      if (isGameflowLobbyDisplayPhase(sessionData.value.phase)) {
        void fetchLobbyData()
      } else {
        clearLobbyStatus()
      }
      failCount.value = 0
    }
  } catch (e) {
    if (sessionState.applyFetchFailure(requestId, currentGameflowPhase.value || sessionData.value.phase)) {
      syncSessionDataFromState()
      lastError.value = extractFetchErrorMessage(e)
      console.error('Failed to fetch session data', e)
      failCount.value++
      if (failCount.value >= maxFailCount) {
        pauseRefresh()
      }
    }
  } finally {
    if (sessionState.isCurrentRequest(requestId)) {
      hasCompletedInitialSessionFetch = true
      initialLoading.value = false
      refreshing.value = false
      sessionFetchInFlight = false
    }
  }
}

function extractFetchErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message.trim()
  }
  return '对战信息刷新失败'
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
  lastError.value = ''
  if (autoResumeTimer) {
    clearTimeout(autoResumeTimer)
    autoResumeTimer = null
  }
  fetchSessionData({ showLoading: false })
  if (!refreshInterval) {
    refreshInterval = setInterval(() => fetchSessionData({ showLoading: false }), 5000)
  }
}

onMounted(() => {
  fetchSessionData()
  refreshInterval = setInterval(() => fetchSessionData({ showLoading: false }), 5000)
  window.addEventListener('pointermove', updatePageGlow)
  window.addEventListener('blur', resetPageGlow)
  document.addEventListener('mouseleave', resetPageGlow)
  unsubscribeGameflowPhase = listenGameflowPhase(handleGameflowPhaseChange)
  unsubscribeCacheUpdate = wsClient.onCacheUpdate((event: CacheUpdateEvent) => {
    if (isCacheUpdateRelevant(event)) {
      scheduleCacheUpdateRefresh()
    }
  })
  unsubscribeLobby = wsClient.onLobby((lobby) => {
    if (hasLobbyPhase.value) {
      applyLobbyData(lobby as Lobby)
    }
  })
})

watch(
  () => allSessionPlayers.value.map(getParticipantKey).join('|'),
  () => {
    const currentKeys = new Set(allSessionPlayers.value.map(getParticipantKey).filter(Boolean))
    const nextKeys = new Set([...expandedParticipantKeys.value].filter(key => currentKeys.has(key)))
    if (nextKeys.size !== expandedParticipantKeys.value.size) {
      expandedParticipantKeys.value = nextKeys
    }
  }
)

watch(sessionData, () => {
  if (gamingAiModalOpen.value) {
    refreshGamingAiPreview()
  }
})

watch(() => sessionData.value.phase, (newVal, oldVal) => {
  if (!hasActiveSession.value) {
    return
  }
  if (newVal === 'ChampSelect' && oldVal !== 'ChampSelect') {
    retryCount = 0
    setTimeout(() => fetchSessionData({ showLoading: false }), 1000)
  }
  if (newVal === 'InProgress' && oldVal !== 'InProgress') {
    retryCount = 0
    setTimeout(() => checkAndRetryFetch(), 2000)
  }
  if (newVal === 'GameStart' && oldVal !== 'GameStart') {
    setTimeout(() => fetchSessionData({ showLoading: false }), 1500)
  }
})

function checkAndRetryFetch() {
  if (!hasActiveSession.value) return
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
        fetchSessionData({ showLoading: false })
        setTimeout(checkAndRetryFetch, 4000)
      }, 3000)
    }
  }
}

onUnmounted(() => {
  cancelGamingAiServerAnalysis()
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
  if (unsubscribeGameflowPhase) {
    unsubscribeGameflowPhase()
    unsubscribeGameflowPhase = null
  }
  if (unsubscribeLobby) {
    unsubscribeLobby()
    unsubscribeLobby = null
  }
  if (cacheUpdateRefreshTimer) {
    clearTimeout(cacheUpdateRefreshTimer)
    cacheUpdateRefreshTimer = null
  }
  window.removeEventListener('pointermove', updatePageGlow)
  window.removeEventListener('blur', resetPageGlow)
  document.removeEventListener('mouseleave', resetPageGlow)
})
</script>

<style scoped>
.gaming-view {
  --gaming-module-hover-rgb: 212, 175, 55;
  --gaming-module-hover-border: rgba(var(--gaming-module-hover-rgb), 0.48);
  --gaming-module-hover-shadow:
    0 0 0 1px rgba(var(--gaming-module-hover-rgb), 0.16),
    0 0 18px rgba(var(--gaming-module-hover-rgb), 0.18),
    0 12px 28px rgba(var(--gaming-module-hover-rgb), 0.08);
  --module-edge-color: var(--gaming-module-hover-border);
  --module-edge-glow: var(--gaming-module-hover-shadow);
  --control-glow-x: 50%;
  --control-glow-y: 50%;
  --control-edge-width: 1px;
  --control-edge-offset: -1px;
  --edge-glow-size: 82px;
  --gaming-control-border-local-glow: rgba(148, 211, 255, 0.98);
  --gaming-control-border-local-glow-fade: rgba(96, 176, 255, 0.4);
  --gaming-control-edge-rgb: 148, 211, 255;
  --gaming-control-edge-shadow:
    inset 0 1px 0 rgba(var(--gaming-control-edge-rgb), calc(var(--edge-top-alpha) * 0.82)),
    inset -1px 0 0 rgba(var(--gaming-control-edge-rgb), calc(var(--edge-right-alpha) * 0.82)),
    inset 0 -1px 0 rgba(var(--gaming-control-edge-rgb), calc(var(--edge-bottom-alpha) * 0.82)),
    inset 1px 0 0 rgba(var(--gaming-control-edge-rgb), calc(var(--edge-left-alpha) * 0.82)),
    0 -3px 11px -6px rgba(var(--gaming-control-edge-rgb), calc(var(--edge-top-alpha) * 0.48)),
    3px 0 11px -6px rgba(var(--gaming-control-edge-rgb), calc(var(--edge-right-alpha) * 0.48)),
    0 3px 11px -6px rgba(var(--gaming-control-edge-rgb), calc(var(--edge-bottom-alpha) * 0.48)),
    -3px 0 11px -6px rgba(var(--gaming-control-edge-rgb), calc(var(--edge-left-alpha) * 0.48));
  --gaming-control-bg-hover: rgba(28, 36, 48, 0.96);
  --gaming-control-border: var(--border-color);
  --gaming-control-border-hover: rgba(96, 176, 255, 0.58);
  --gaming-control-bg-hover-local: linear-gradient(var(--gaming-control-bg-hover), var(--gaming-control-bg-hover)) padding-box,
    radial-gradient(
      circle at var(--control-glow-x) var(--control-glow-y),
      var(--gaming-control-border-local-glow) 0%,
      var(--gaming-control-border-local-glow-fade) 36%,
      var(--gaming-control-border-hover) 72%
    ) border-box;
  --gaming-control-hover-shadow: 0 0 0 1px rgba(41, 151, 255, 0.16), 0 0 16px rgba(41, 151, 255, 0.22);
  --gaming-hover-border: var(--gaming-module-hover-border);
  --gaming-hover-shadow: var(--gaming-module-hover-shadow);
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

:global([data-theme="light"] .gaming-view) {
  --gaming-module-hover-rgb: 86, 109, 134;
  --gaming-module-hover-border: rgba(var(--gaming-module-hover-rgb), 0.42);
  --gaming-module-hover-shadow:
    0 0 0 1px rgba(var(--gaming-module-hover-rgb), 0.14),
    0 0 18px rgba(var(--gaming-module-hover-rgb), 0.14),
    0 12px 28px rgba(var(--gaming-module-hover-rgb), 0.07);
  --module-edge-color: var(--gaming-module-hover-border);
  --module-edge-glow: var(--gaming-module-hover-shadow);
  --gaming-control-border-local-glow: rgba(255, 218, 76, 0.94);
  --gaming-control-border-local-glow-fade: rgba(244, 183, 24, 0.52);
  --gaming-control-edge-rgb: 255, 210, 62;
  --gaming-control-bg-hover: rgba(252, 238, 198, 0.98);
  --gaming-control-border: var(--border-color);
  --gaming-control-border-hover: var(--border-color);
  --gaming-control-hover-shadow: 0 0 0 3px rgba(226, 179, 34, 0.2), 0 0 12px rgba(226, 179, 34, 0.34);
  --gaming-hover-border: var(--gaming-module-hover-border);
  --gaming-hover-shadow: var(--gaming-module-hover-shadow);
}

.gaming-content {
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 16px;
}

.gaming-header {
  position: relative;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 9px 10px 9px 12px;
  background: var(--bg-secondary);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  box-shadow: none;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast), background var(--transition-fast);
}

.gaming-header:hover,
.gaming-header:focus-within {
  border-color: var(--gaming-module-hover-border);
  box-shadow: var(--gaming-module-hover-shadow);
}

.phase-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.phase-badge {
  flex: 0 0 auto;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 800;
  line-height: 1;
}

.phase-badge.phase-idle {
  color: var(--text-secondary);
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
  min-width: 0;
  max-width: 180px;
  padding: 6px 10px;
  border: 1px solid rgba(var(--accent-rgb), 0.18);
  border-radius: 999px;
  background: rgba(var(--accent-rgb), 0.055);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-name.unknown {
  border-color: var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  opacity: 0.68;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  justify-content: flex-end;
  margin-left: auto;
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
    var(--gaming-control-border-local-glow) 0%,
    var(--gaming-control-border-local-glow-fade) 42%,
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
  z-index: 0;
}

.control-glow:hover:not(:disabled)::before,
.control-glow:focus-visible::before,
.control-glow[data-near-glow='true']:not(:disabled)::before,
.surface-glow:hover::before,
.surface-glow:focus-visible::before,
.surface-glow[data-near-glow='true']::before {
  opacity: 1;
}

.control-glow:active:not(:disabled)::before,
.surface-glow:active::before {
  opacity: 0.55;
}

/* Teams */
.teams-container {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  align-content: start;
  align-items: stretch;
}

.team-panel {
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--bg-secondary);
  box-shadow: none;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast), background var(--transition-fast);
}

.team-panel:hover,
.team-panel:focus-within {
  border-color: var(--gaming-module-hover-border);
  box-shadow: var(--gaming-module-hover-shadow);
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
  flex: 1 1 auto;
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.team-title::before {
  content: '';
  flex: 0 0 auto;
  width: 3px;
  height: 30px;
  border-radius: 999px;
  background: rgba(var(--team-accent-rgb), 0.74);
  box-shadow: 0 0 12px rgba(var(--team-accent-rgb), 0.18);
}

.team-title > div {
  min-width: 0;
}

.team-title h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 21px;
  line-height: 1.1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.team-title div > span {
  display: block;
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.team-analysis-btn {
  flex: 0 0 auto;
  min-width: 92px;
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid rgba(126, 198, 255, 0.48);
  border-radius: 9px;
  background: rgba(var(--accent-rgb), 0.065);
  color: #c7e6ff;
  box-shadow: none;
  font-size: 13px;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  opacity: 1;
  transition:
    border-color var(--transition-fast),
    background var(--transition-fast),
    box-shadow var(--transition-fast),
    color var(--transition-fast);
}

.team-analysis-btn:hover,
.team-analysis-btn:focus-visible {
  border-color: transparent;
  background: var(--gaming-control-bg-hover-local);
  color: #f7e6ad;
  box-shadow: var(--gaming-control-hover-shadow), var(--gaming-control-edge-shadow);
  outline: none;
}

.team-analysis-btn.control-glow[data-near-glow='true']:not(:hover):not(:focus-visible) {
  box-shadow: var(--gaming-control-edge-shadow);
}

:global([data-theme="light"] .gaming-view .team-analysis-btn) {
  border-color: rgba(86, 109, 134, 0.24);
  background: rgba(255, 255, 255, 0.72);
  color: #2f4d6a;
  box-shadow: none;
}

:global([data-theme="light"] .gaming-view .team-analysis-btn:hover),
:global([data-theme="light"] .gaming-view .team-analysis-btn:focus-visible) {
  border-color: transparent;
  background: var(--gaming-control-bg-hover-local);
  color: #24384d;
  box-shadow: var(--gaming-control-hover-shadow), var(--gaming-control-edge-shadow);
}

.team-header-blue {
  --team-accent-rgb: 96, 165, 250;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.25), rgba(59, 130, 246, 0.1));
  color: #93c5fd;
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.team-header-red {
  --team-accent-rgb: 248, 113, 113;
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.25), rgba(239, 68, 68, 0.1));
  color: #fca5a5;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.team-players {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1 1 auto;
  min-height: 0;
}

.gaming-player-card.surface-glow {
  transition: background var(--transition-fast), border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.gaming-player-card.surface-glow:hover,
.gaming-player-card.surface-glow:focus-within {
  border-color: var(--gaming-hover-border);
  box-shadow: var(--gaming-hover-shadow);
}

.gaming-player-card.surface-glow[data-near-glow='true']:not(:hover):not(:focus-within) {
  box-shadow: var(--gaming-control-edge-shadow);
}

.gaming-player-card.surface-glow.team-blue:hover,
.gaming-player-card.surface-glow.team-blue:focus-within {
  border-left-color: rgba(92, 163, 234, 0.82);
}

.gaming-player-card.surface-glow.team-red:hover,
.gaming-player-card.surface-glow.team-red:focus-within {
  border-left-color: rgba(222, 111, 111, 0.82);
}

.participant-recent-inline-panel.surface-glow {
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast), background var(--transition-fast);
}

.participant-recent-inline-panel.surface-glow:hover,
.participant-recent-inline-panel.surface-glow:focus-within {
  border-color: var(--gaming-hover-border);
  box-shadow: var(--gaming-hover-shadow);
}

.participant-recent-inline-panel.surface-glow[data-near-glow='true']:not(:hover):not(:focus-within) {
  box-shadow: var(--gaming-control-edge-shadow);
}

.idle-player-slot-blue {
  --slot-accent-rgb: 96, 165, 250;
  --slot-bar-color: rgba(92, 163, 234, 0.7);
  --slot-border-color: rgba(92, 163, 234, 0.2);
  --slot-border-hover: rgba(92, 163, 234, 0.28);
  --slot-avatar-bg: rgba(92, 163, 234, 0.14);
  --slot-line-bg: rgba(92, 163, 234, 0.2);
}

.idle-player-slot-red {
  --slot-accent-rgb: 248, 113, 113;
  --slot-bar-color: rgba(222, 111, 111, 0.7);
  --slot-border-color: rgba(222, 111, 111, 0.2);
  --slot-border-hover: rgba(222, 111, 111, 0.28);
  --slot-avatar-bg: rgba(222, 111, 111, 0.13);
  --slot-line-bg: rgba(222, 111, 111, 0.19);
}

.idle-player-slot {
  position: relative;
  min-width: 0;
  min-height: 62px;
  flex: 0 0 62px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 11px;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid var(--slot-border-color);
  border-left: 3px solid var(--slot-bar-color);
  border-radius: 12px;
  background: var(--bg-secondary);
  opacity: 0.84;
  overflow: hidden;
  transition: border-color var(--transition-fast), opacity var(--transition-fast);
}

.idle-player-slot:hover {
  border-color: var(--slot-border-hover);
  border-left-color: var(--slot-bar-color);
  opacity: 0.9;
}

.idle-slot-avatar {
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border-radius: 9px;
  background: var(--slot-avatar-bg);
}

.idle-slot-copy {
  width: 100%;
  height: 38px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.idle-slot-title,
.idle-slot-subtitle {
  display: block;
  border-radius: 4px;
  background: var(--slot-line-bg);
}

.idle-slot-title {
  width: min(118px, 54%);
  height: 16px;
}

.idle-slot-subtitle {
  width: min(72px, 34%);
  height: 14px;
  opacity: 0.72;
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
  flex: 0 0 auto;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #f87171;
  box-shadow: 0 0 0 4px rgba(248, 113, 113, 0.12), 0 0 12px rgba(248, 113, 113, 0.26);
}

.resume-btn-small {
  --gaming-control-border-local-glow: rgba(248, 113, 113, 0.9);
  --gaming-control-border-local-glow-fade: rgba(239, 68, 68, 0.42);
  --gaming-control-edge-rgb: 248, 113, 113;
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

.resume-btn-small:hover,
.resume-btn-small:focus-visible {
  border-color: transparent;
  background: var(--gaming-control-bg-hover-local);
  box-shadow: var(--gaming-control-hover-shadow), var(--gaming-control-edge-shadow);
  outline: none;
}

.resume-btn-small.control-glow[data-near-glow='true']:not(:hover):not(:focus-visible) {
  box-shadow: var(--gaming-control-edge-shadow);
}

@media (min-width: 1180px) {
  .teams-container {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .idle-player-slot {
    flex: 1 1 62px;
  }
}

@media (max-width: 720px) {
  .gaming-content {
    gap: 12px;
  }

  .team-header {
    align-items: stretch;
    flex-direction: column;
  }

  .team-analysis-btn {
    align-self: flex-start;
    min-width: 88px;
    min-height: 30px;
    padding: 0 10px;
    font-size: 12px;
  }

  .team-panel {
    padding: 12px;
  }

  .idle-player-slot {
    min-height: 58px;
    flex-basis: 58px;
    gap: 10px;
    padding: 9px 11px;
  }

  .idle-slot-avatar {
    width: 36px;
    height: 36px;
  }

  .idle-slot-copy {
    height: 36px;
  }

  .idle-slot-title {
    width: min(104px, 58%);
    height: 15px;
  }

  .idle-slot-subtitle {
    width: min(66px, 38%);
    height: 13px;
  }

  .team-title h2 {
    font-size: 20px;
  }
}
</style>
