<script setup lang="ts">
import { computed } from 'vue'
import type { Stats, UserTagSummary } from '@/types/api'
import { buildMatchDetailItems, getTeamKdaLeaders } from './matchRosterDisplay'

export interface MatchRosterPlayer {
  participantId: number
  championId: number
  puuid: string
  gameName: string
  tagLine: string
  summonerName?: string
  stats?: Stats
}

const props = withDefaults(defineProps<{
  players: MatchRosterPlayer[]
  summaries?: Record<string, UserTagSummary>
  currentPuuid?: string
}>(), {
  summaries: () => ({}),
  currentPuuid: ''
})

const emit = defineEmits<{
  navigateToPlayer: [gameName: string, tagLine: string]
}>()

const teamLeaders = computed(() => getTeamKdaLeaders(props.players))

function getChampionUrl(championId: number): string {
  return championId > 0 ? `http://127.0.0.1:8080/api/v1/asset/champion/${championId}` : ''
}

function displayName(player: MatchRosterPlayer): string {
  return player.gameName || player.summonerName || '未知玩家'
}

function fullName(player: MatchRosterPlayer): string {
  const name = displayName(player)
  return player.tagLine ? `${name}#${player.tagLine}` : name
}

function getMatchDetails(player: MatchRosterPlayer) {
  return buildMatchDetailItems(player.stats)
}

function isLeader(player: MatchRosterPlayer, metric: 'kills' | 'deaths' | 'assists'): boolean {
  const value = player.stats?.[metric]
  return typeof value === 'number' && value > 0 && value === teamLeaders.value[metric]
}

function handleClick(player: MatchRosterPlayer) {
  if (!player.gameName || player.puuid === props.currentPuuid) {
    return
  }

  emit('navigateToPlayer', player.gameName, player.tagLine)
}
</script>

<template>
  <div class="roster">
    <button
      v-for="player in players"
      :key="`${player.participantId}-${player.puuid}`"
      class="roster-item"
      :class="{ 'is-me': player.puuid === currentPuuid, clickable: !!player.gameName && player.puuid !== currentPuuid }"
      :disabled="!player.gameName || player.puuid === currentPuuid"
      :title="fullName(player)"
      @click.stop="handleClick(player)"
    >
      <div class="roster-main">
        <img
          class="champion-avatar"
          :src="getChampionUrl(player.championId)"
          alt=""
        />

        <span class="player-name">{{ displayName(player) }}</span>
      </div>

      <div v-if="player.stats" class="roster-panel">
        <div class="kda-row">
          <span class="kda-number" :class="{ 'leader-kill': isLeader(player, 'kills') }">{{ player.stats.kills || 0 }}</span>
          <span class="kda-separator">&#92;</span>
          <span class="kda-number" :class="{ 'leader-death': isLeader(player, 'deaths') }">{{ player.stats.deaths || 0 }}</span>
          <span class="kda-separator">&#92;</span>
          <span class="kda-number" :class="{ 'leader-assist': isLeader(player, 'assists') }">{{ player.stats.assists || 0 }}</span>
        </div>

        <div class="detail-row">
          <span
            v-for="item in getMatchDetails(player)"
            :key="`${player.participantId}-${item.label}`"
            class="detail-pill"
          >
            <span class="detail-label">{{ item.label }}</span>
            <span class="detail-value">{{ item.value }}</span>
          </span>
        </div>
      </div>
    </button>
  </div>
</template>

<style scoped>
.roster {
  display: grid;
  gap: 4px;
}

.roster-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 48%);
  align-items: center;
  gap: 14px;
  width: 100%;
  min-height: 92px;
  padding: 12px 14px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.02);
  color: inherit;
  text-align: left;
}

.roster-item.clickable {
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}

.roster-item.clickable:hover {
  transform: translateY(-1px);
  border-color: rgba(92, 163, 234, 0.35);
  background: rgba(92, 163, 234, 0.08);
}

.roster-item.is-me {
  border-color: rgba(92, 163, 234, 0.45);
  background: rgba(92, 163, 234, 0.1);
}

.roster-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14px;
}

.champion-avatar {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  object-fit: cover;
  flex-shrink: 0;
  background: var(--bg-tertiary);
}

.player-name {
  min-width: 0;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.15;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.roster-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}

.kda-row {
  display: flex;
  align-items: center;
  gap: 3px;
}

.kda-number {
  font-size: 22px;
  font-weight: 800;
  line-height: 1;
  color: #ffffff;
}

.kda-number.leader-kill {
  color: #55d187;
}

.kda-number.leader-death {
  color: #ff6b6b;
}

.kda-number.leader-assist {
  color: #f0c44f;
}

.kda-separator {
  font-size: 14px;
  color: var(--text-tertiary);
}

.detail-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.detail-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1;
}

.detail-label {
  color: var(--text-secondary);
}

.detail-value {
  color: var(--text-primary);
  font-weight: 700;
}

@media (max-width: 980px) {
  .roster-item {
    grid-template-columns: 1fr;
  }

  .roster-panel {
    align-items: flex-start;
  }

  .detail-row {
    justify-content: flex-start;
  }
}
</style>
