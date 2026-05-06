<script setup lang="ts">
import { computed } from 'vue'
import type { SessionSummoner } from '@/types/api'
import { getChampionIconUrl, markAssetLoadFailed } from '@/utils/gameAssetUrls'
import {
  buildParticipantRecentMatchItems,
  type ParticipantRecentMatchItem
} from '@/utils/participantRecentMatches'

const props = defineProps<{
  player: SessionSummoner | null
}>()

const recentItems = computed<ParticipantRecentMatchItem[]>(() =>
  buildParticipantRecentMatchItems(props.player?.matchHistory, props.player?.summoner?.puuid)
)

function resultClass(item: ParticipantRecentMatchItem): string {
  if (item.result === 'win') return 'result-win'
  if (item.result === 'loss') return 'result-loss'
  return 'result-unknown'
}
</script>

<template>
  <section
    class="participant-recent-panel"
    aria-label="最近战绩列表"
  >
    <div
      v-if="!recentItems.length"
      class="recent-empty"
    >
      暂无最近战绩数据
    </div>

    <div
      v-else
      class="recent-match-list"
    >
      <article
        v-for="item in recentItems"
        :key="item.key"
        class="recent-match-row"
        :class="resultClass(item)"
      >
        <div class="recent-champion">
          <img
            v-if="getChampionIconUrl(item.championId)"
            class="recent-champion-avatar"
            :src="getChampionIconUrl(item.championId)"
            alt=""
            @error="markAssetLoadFailed"
          >
          <span
            v-else
            class="recent-champion-avatar champion-placeholder"
            aria-hidden="true"
          />
        </div>

        <div class="recent-match-main">
          <div class="recent-match-summary">
            <strong>{{ item.resultText }}</strong>
            <span>{{ item.timeText }} · {{ item.durationText }}</span>
          </div>
          <div class="recent-match-kda">
            {{ item.kdaText }}
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.participant-recent-panel {
  min-width: 0;
  padding: 10px;
  border: 1px solid rgba(var(--accent-rgb), 0.18);
  border-radius: 12px;
  background:
    linear-gradient(180deg, rgba(var(--accent-rgb), 0.06), rgba(255, 255, 255, 0.02)),
    var(--bg-secondary);
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.14);
}

.recent-empty {
  min-height: 54px;
  display: grid;
  place-items: center;
  border: 1px dashed rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.025);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 800;
}

.recent-match-list {
  max-height: 240px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding-right: 2px;
  overscroll-behavior: contain;
}

.recent-match-row {
  min-width: 0;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 7px 8px;
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-left: 3px solid rgba(184, 192, 204, 0.32);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.03);
}

.recent-match-row.result-win {
  border-left-color: rgba(61, 155, 122, 0.72);
}

.recent-match-row.result-loss {
  border-left-color: rgba(196, 92, 92, 0.72);
}

.recent-champion {
  flex: 0 0 auto;
}

.recent-champion-avatar {
  display: block;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  object-fit: cover;
  background: var(--bg-tertiary);
}

.recent-champion-avatar[data-asset-failed='true'] {
  display: none;
}

.champion-placeholder {
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
}

.recent-match-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.recent-match-summary {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.recent-match-summary strong {
  flex: 0 0 auto;
  font-size: 13px;
  line-height: 1;
  font-weight: 900;
  color: var(--text-secondary);
  white-space: nowrap;
}

.recent-match-row.result-win .recent-match-summary strong {
  color: #55d187;
}

.recent-match-row.result-loss .recent-match-summary strong {
  color: #ff6b6b;
}

.recent-match-summary span {
  min-width: 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-match-kda {
  min-width: 0;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1;
  font-weight: 900;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .participant-recent-panel {
    padding: 10px;
  }
}
</style>
