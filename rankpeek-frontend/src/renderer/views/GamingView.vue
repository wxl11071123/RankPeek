<template>
  <div class="gaming-view">
    <header class="gaming-header surface-glow">
      <div class="phase-info">
        <span class="phase-badge" :class="phaseClass">{{ phaseLabel }}</span>
        <span class="queue-name" :class="{ unknown: !queueName }">{{ queueName || 'No active session' }}</span>
      </div>
      <div class="header-actions">
        <button
          class="opgg-action-btn control-glow"
          type="button"
          :title="opggButtonTitle"
          :disabled="!opggQuery.enabled"
          @click="openOpggWindow"
        >
          OP.GG
        </button>
        <RefreshIconButton
          :aria-label="loading ? 'Refreshing' : 'Refresh'"
          :title="loading ? 'Refreshing' : 'Refresh'"
          :loading="loading"
          :disabled="loading"
          @click="() => fetchSessionData({ force: true })"
        />
      </div>
    </header>

    <p v-if="lastError" class="connection-bar" role="status">{{ lastError }}</p>

    <main class="teams-container" :class="{ empty: !hasActiveSession }">
      <section class="team-panel team-blue surface-glow">
        <div class="team-header">
          <h2>Blue Team {{ blueTeamPlayers.length }}/5</h2>
        </div>
        <div class="team-players">
          <PlayerCard
            v-for="(player, index) in blueTeamPlayers"
            :key="getParticipantKey(player) || `blue-${index}`"
            class="gaming-player-card surface-glow"
            :session-summoner="player"
            team="blue"
          />
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
        <div class="team-header">
          <h2>Red Team {{ redTeamPlayers.length }}/5</h2>
        </div>
        <div class="team-players">
          <PlayerCard
            v-for="(player, index) in redTeamPlayers"
            :key="getParticipantKey(player) || `red-${index}`"
            class="gaming-player-card surface-glow"
            :session-summoner="player"
            team="red"
          />
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
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getGamingSessionData } from '@/api/sessionDataAdapter'
import RefreshIconButton from '@/components/common/RefreshIconButton.vue'
import PlayerCard from '@/components/gaming/PlayerCard.vue'
import { listenGameflowPhase } from '@/services/gameflowPhaseListener'
import { buildOpggChampionQuery } from '@/services/opggChampionQuery'
import {
  createEmptyGamingSessionData,
  isGameflowSessionClearPhase,
  isGameflowSessionRefreshPhase
} from '@/services/gamingSessionFlow'
import type { SessionData, SessionSummoner } from '@/types/api'

const sessionData = ref<SessionData>(createEmptyGamingSessionData())
const currentPhase = ref('')
const loading = ref(false)
const lastError = ref('')

let refreshInterval: ReturnType<typeof setInterval> | null = null
let unsubscribeGameflowPhase: (() => void) | null = null
let fetchRequestId = 0

const blueTeamPlayers = computed(() => sessionData.value.teamOne ?? [])
const redTeamPlayers = computed(() => sessionData.value.teamTwo ?? [])
const hasActiveSession = computed(() => blueTeamPlayers.value.length > 0 || redTeamPlayers.value.length > 0)
const phaseLabel = computed(() => currentPhase.value || sessionData.value.phase || 'None')
const phaseClass = computed(() => `phase-${phaseLabel.value.toLowerCase()}`)
const queueName = computed(() => sessionData.value.typeCn || sessionData.value.queueType || '')
const opggQuery = computed(() => buildOpggChampionQuery(sessionData.value))
const opggButtonTitle = computed(() => opggQuery.value.reason || 'OP.GG')
const blueEmptySlots = computed(() => Array.from({ length: Math.max(0, 5 - blueTeamPlayers.value.length) }, (_, index) => index))
const redEmptySlots = computed(() => Array.from({ length: Math.max(0, 5 - redTeamPlayers.value.length) }, (_, index) => index))

async function fetchSessionData(options: { force?: boolean } = {}): Promise<void> {
  const requestId = ++fetchRequestId
  loading.value = true
  lastError.value = ''

  try {
    const data = await getGamingSessionData({ forceRefresh: options.force === true })
    if (requestId !== fetchRequestId) {
      return
    }
    sessionData.value = data
    currentPhase.value = data.phase || currentPhase.value
  } catch (error) {
    if (requestId !== fetchRequestId) {
      return
    }
    sessionData.value = createEmptyGamingSessionData(currentPhase.value)
    lastError.value = error instanceof Error ? error.message : 'Failed to load session data'
  } finally {
    if (requestId === fetchRequestId) {
      loading.value = false
    }
  }
}

function getParticipantKey(player: SessionSummoner | null | undefined): string {
  const puuid = player?.summoner?.puuid?.trim()
  if (puuid) {
    return `puuid:${puuid}`
  }

  const gameName = player?.summoner?.gameName?.trim()
  const tagLine = player?.summoner?.tagLine?.trim()
  if (gameName) {
    return `riot:${gameName}#${tagLine || ''}`
  }

  const summonerId = player?.summoner?.summonerId
  return Number.isFinite(summonerId) ? `summoner:${summonerId}` : ''
}

async function openOpggWindow(): Promise<void> {
  if (!opggQuery.value.enabled) {
    return
  }
  await window.electronAPI?.openOpggWindow?.(opggQuery.value)
}

function handleGameflowPhase(phase: string): void {
  currentPhase.value = phase
  if (isGameflowSessionClearPhase(phase)) {
    sessionData.value = createEmptyGamingSessionData(phase)
    return
  }
  if (isGameflowSessionRefreshPhase(phase)) {
    void fetchSessionData()
  }
}

onMounted(() => {
  void fetchSessionData()
  refreshInterval = setInterval(() => {
    void fetchSessionData()
  }, 10000)
  unsubscribeGameflowPhase = listenGameflowPhase(handleGameflowPhase)
})

onUnmounted(() => {
  fetchRequestId += 1
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
  unsubscribeGameflowPhase?.()
  unsubscribeGameflowPhase = null
})
</script>

<style scoped>
.gaming-view {
  display: grid;
  gap: 16px;
  padding: 20px;
  min-height: 100%;
}

.gaming-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.phase-info,
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.phase-badge {
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 6px 10px;
  font-weight: 700;
  color: var(--text-primary);
  background: var(--bg-primary);
}

.queue-name {
  color: var(--text-secondary);
}

.queue-name.unknown {
  color: var(--text-tertiary, var(--text-secondary));
}

.opgg-action-btn {
  height: 34px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 0 12px;
  background: var(--bg-primary);
  color: var(--text-primary);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.opgg-action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.connection-bar {
  margin: 0;
  border: 1px solid rgba(224, 82, 82, 0.35);
  border-radius: 8px;
  padding: 10px 12px;
  background: rgba(224, 82, 82, 0.12);
  color: var(--danger-color, #e05252);
}

.teams-container {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.team-panel {
  display: grid;
  gap: 12px;
  min-width: 0;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
  padding: 14px;
}

.team-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 36px;
}

.team-header h2 {
  margin: 0;
  font-size: 16px;
}

.team-players {
  display: grid;
  gap: 10px;
}

.idle-player-slot {
  display: flex;
  gap: 10px;
  align-items: center;
  min-height: 96px;
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  padding: 12px;
  opacity: 0.5;
}

.idle-slot-avatar,
.idle-slot-title,
.idle-slot-subtitle {
  display: block;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 6px;
}

.idle-slot-avatar {
  width: 44px;
  height: 44px;
}

.idle-slot-copy {
  display: grid;
  gap: 8px;
  width: 100%;
}

.idle-slot-title {
  width: 60%;
  height: 12px;
}

.idle-slot-subtitle {
  width: 40%;
  height: 10px;
}

@media (max-width: 1100px) {
  .teams-container {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .gaming-view {
    padding: 12px;
  }

  .gaming-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
