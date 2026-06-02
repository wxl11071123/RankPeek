<template>
  <div class="home-view">
    <section class="home-hero surface-glow">
      <div class="account-block">
        <img
          v-if="profileIconUrl"
          class="account-avatar"
          :src="profileIconUrl"
          alt=""
          @error="markAssetLoadFailed"
        />
        <span v-else class="account-avatar avatar-fallback"></span>
        <div class="account-copy">
          <span class="eyebrow">{{ connectionLabel }}</span>
          <h1>{{ summonerLabel }}</h1>
          <p>{{ gamePhaseLabel }}</p>
        </div>
      </div>

      <RefreshIconButton
        :aria-label="refreshing ? 'Refreshing' : 'Refresh account'"
        :title="refreshing ? 'Refreshing' : 'Refresh account'"
        :loading="refreshing"
        :disabled="refreshing"
        @click="refreshAccount"
      />
    </section>

    <section class="rank-grid">
      <article class="rank-card surface-glow">
        <span class="eyebrow">Solo/Duo</span>
        <strong>{{ formatRank(soloRank) }}</strong>
        <small>{{ formatRankStats(soloRank) }}</small>
      </article>

      <article class="rank-card surface-glow">
        <span class="eyebrow">Flex</span>
        <strong>{{ formatRank(flexRank) }}</strong>
        <small>{{ formatRankStats(flexRank) }}</small>
      </article>

      <article class="rank-card surface-glow">
        <span class="eyebrow">Local Status</span>
        <strong>{{ connected ? 'Connected' : 'Disconnected' }}</strong>
        <small>{{ rankLoading ? 'Rank data loading' : 'Local League client data' }}</small>
      </article>
    </section>

    <HomeChart
      class="home-chart-panel surface-glow"
      :summoner="currentSummoner"
      :connected="connected"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import HomeChart from '@/components/HomeChart.vue'
import RefreshIconButton from '@/components/common/RefreshIconButton.vue'
import { useGameStore } from '@/stores/game'
import type { QueueInfo } from '@/types/api'
import { getProfileIconUrl, markAssetLoadFailed } from '@/utils/gameAssetUrls'

const gameStore = useGameStore()
const { connected, currentSummoner, gamePhase, rankLoading, soloRank, flexRank } = storeToRefs(gameStore)
const refreshing = ref(false)

const profileIconUrl = computed(() => getProfileIconUrl(currentSummoner.value?.profileIconId))
const summonerLabel = computed(() => {
  const summoner = currentSummoner.value
  if (!summoner) {
    return 'RankPeek'
  }
  return summoner.tagLine ? `${summoner.gameName}#${summoner.tagLine}` : summoner.gameName
})
const connectionLabel = computed(() => connected.value ? 'League client connected' : 'Waiting for League client')
const gamePhaseLabel = computed(() => gamePhase.value ? `Current phase: ${gamePhase.value}` : 'Open League of Legends to load your account.')

onMounted(() => {
  void gameStore.checkConnection()
})

async function refreshAccount(): Promise<void> {
  refreshing.value = true
  try {
    await gameStore.checkConnection()
    if (connected.value) {
      await gameStore.refreshSummoner()
    }
  } finally {
    refreshing.value = false
  }
}

function formatRank(rank: QueueInfo | null): string {
  if (!rank) {
    return 'Unranked'
  }
  const tier = rank.tierCn || rank.tier || 'Unranked'
  const division = rank.displayRank || rank.division || ''
  if (!tier || tier.toUpperCase() === 'UNRANKED') {
    return 'Unranked'
  }
  return `${tier} ${division}`.trim()
}

function formatRankStats(rank: QueueInfo | null): string {
  if (!rank) {
    return 'No ranked data'
  }
  const wins = rank.wins ?? 0
  const losses = rank.losses ?? 0
  const games = wins + losses
  const winRate = games > 0 ? Math.round((wins / games) * 100) : 0
  return `${rank.leaguePoints ?? 0} LP · ${wins}W ${losses}L · ${winRate}%`
}
</script>

<style scoped>
.home-view {
  display: grid;
  gap: 18px;
  padding: 22px;
}

.home-hero,
.rank-card,
.home-chart-panel {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.home-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
  padding: 22px;
}

.account-block {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.account-avatar {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  object-fit: cover;
  background: rgba(255, 255, 255, 0.08);
}

.avatar-fallback {
  display: block;
}

.account-copy {
  min-width: 0;
}

.eyebrow {
  display: block;
  color: var(--text-secondary);
  font-size: 12px;
  letter-spacing: 0;
  text-transform: uppercase;
}

h1 {
  margin: 4px 0;
  color: var(--text-primary);
  font-size: 28px;
  line-height: 1.15;
}

p,
small {
  margin: 0;
  color: var(--text-secondary);
}

.rank-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.rank-card {
  display: grid;
  gap: 8px;
  padding: 16px;
}

.rank-card strong {
  color: var(--text-primary);
  font-size: 20px;
}

.home-chart-panel {
  padding: 16px;
  min-width: 0;
}

@media (max-width: 900px) {
  .rank-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .home-view {
    padding: 12px;
  }

  .home-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
