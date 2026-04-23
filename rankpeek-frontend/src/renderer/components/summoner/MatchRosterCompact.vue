<script setup lang="ts">
import type { RankTag, UserTagSummary } from '@/types/api'

export interface MatchRosterPlayer {
  participantId: number
  championId: number
  puuid: string
  gameName: string
  tagLine: string
  summonerName?: string
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

function getChampionUrl(championId: number): string {
  return championId > 0 ? `http://127.0.0.1:8080/api/v1/asset/champion/${championId}` : ''
}

function displayName(player: MatchRosterPlayer): string {
  return player.gameName || player.summonerName || '未知玩家'
}

function getSummary(player: MatchRosterPlayer): UserTagSummary | undefined {
  return player.puuid ? props.summaries[player.puuid] : undefined
}

function getVisibleTags(player: MatchRosterPlayer): RankTag[] {
  return getSummary(player)?.tag?.slice(0, 2) ?? []
}

function getStatusMeta(player: MatchRosterPlayer): { label: string; desc: string; className: string } | null {
  switch (getSummary(player)?.recordStatus) {
    case 'PRIVATE':
      return {
        label: '战绩隐藏',
        desc: 'LCU 内无法看到该玩家的近期对局。',
        className: 'private'
      }
    case 'EMPTY':
      return {
        label: '暂无对局',
        desc: '近期可用数据不足，暂时无法展示。',
        className: 'empty'
      }
    case 'ERROR':
      return {
        label: '加载失败',
        desc: '这次标签数据加载失败。',
        className: 'error'
      }
    default:
      return null
  }
}

function getRecentRecordText(player: MatchRosterPlayer): string {
  const summary = getSummary(player)
  if (!summary || summary.recordStatus !== 'NORMAL') {
    return ''
  }

  const wins = summary.recentData?.selectWins || 0
  const losses = summary.recentData?.selectLosses || 0
  const kda = summary.recentData?.kda

  const recordText = wins + losses > 0 ? `${wins}胜${losses}负` : ''
  const kdaText = typeof kda === 'number' ? `${kda.toFixed(1)} KDA` : ''

  return [recordText, kdaText].filter(Boolean).join(' · ')
}

function fullName(player: MatchRosterPlayer): string {
  const name = displayName(player)
  return player.tagLine ? `${name}#${player.tagLine}` : name
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
      <img
        class="champion-avatar"
        :src="getChampionUrl(player.championId)"
        alt=""
      />
      <div class="roster-copy">
        <span class="player-name">{{ displayName(player) }}</span>
        <div class="roster-meta">
          <span
            v-if="getStatusMeta(player)"
            class="status-chip"
            :class="getStatusMeta(player)?.className"
            :title="getStatusMeta(player)?.desc"
          >
            {{ getStatusMeta(player)?.label }}
          </span>

          <template v-else>
            <span
              v-for="tag in getVisibleTags(player)"
              :key="tag.tagName"
              class="tag-chip"
              :class="tag.good === true ? 'good' : tag.good === false ? 'bad' : 'neutral'"
              :title="tag.tagDesc"
            >
              {{ tag.tagName }}
            </span>
          </template>
        </div>
        <span v-if="getRecentRecordText(player)" class="record-text">{{ getRecentRecordText(player) }}</span>
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
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  padding: 6px 8px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
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

.champion-avatar {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
  background: var(--bg-tertiary);
}

.roster-copy {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.roster-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-height: 18px;
}

.player-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag-chip,
.status-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border-radius: 999px;
  font-size: 10px;
  line-height: 1;
  white-space: nowrap;
}

.tag-chip.good {
  background: rgba(61, 155, 122, 0.14);
  color: #3d9b7a;
}

.tag-chip.bad {
  background: rgba(196, 92, 92, 0.14);
  color: #c45c5c;
}

.tag-chip.neutral {
  background: rgba(128, 128, 128, 0.16);
  color: var(--text-secondary);
}

.status-chip.private {
  background: rgba(198, 154, 66, 0.16);
  color: #d7a64b;
}

.status-chip.empty {
  background: rgba(128, 128, 128, 0.16);
  color: var(--text-secondary);
}

.status-chip.error {
  background: rgba(196, 92, 92, 0.14);
  color: #c45c5c;
}

.record-text {
  font-size: 11px;
  line-height: 1.2;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
