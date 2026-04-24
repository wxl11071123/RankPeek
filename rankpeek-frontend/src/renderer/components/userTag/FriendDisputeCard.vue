<script setup lang="ts">
import type { FriendAndDispute } from '@/types/api'

defineProps<{
  data: FriendAndDispute
}>()
</script>

<template>
  <div class="friend-dispute-card">
    <h2>组队分析</h2>
    <p class="section-desc">基于近期对局识别经常遇到的玩家</p>

    <div class="cards-row">
      <!-- 好友 -->
      <div class="relation-card friends">
        <div class="card-header">
          <span class="icon">👥</span>
          <span class="title">队友</span>
          <span class="rate">{{ data.friendsRate }}% 胜率</span>
        </div>
        <div v-if="data.friendsSummoner && data.friendsSummoner.length > 0" class="player-list">
          <div
            v-for="player in data.friendsSummoner"
            :key="player.summoner.puuid"
            class="player-item"
          >
            <div class="player-info">
              <span class="name">
                {{ player.summoner.gameName }}#{{ player.summoner.tagLine }}
              </span>
              <span class="stats">
                {{ player.wins }}胜{{ player.losses }}负 ({{ player.winRate }}%)
              </span>
            </div>
            <div class="games-count">{{ player.oneGamePlayer?.length || 0 }}场</div>
          </div>
        </div>
        <div v-else class="empty-text">暂无数据</div>
      </div>

      <!-- 冤家 -->
      <div class="relation-card dispute">
        <div class="card-header">
          <span class="icon">⚔️</span>
          <span class="title">对手</span>
          <span class="rate">{{ data.disputeRate }}% 胜率</span>
        </div>
        <div v-if="data.disputeSummoner && data.disputeSummoner.length > 0" class="player-list">
          <div
            v-for="player in data.disputeSummoner"
            :key="player.summoner.puuid"
            class="player-item"
          >
            <div class="player-info">
              <span class="name">
                {{ player.summoner.gameName }}#{{ player.summoner.tagLine }}
              </span>
              <span class="stats">
                {{ player.wins }}胜{{ player.losses }}负 ({{ player.winRate }}%)
              </span>
            </div>
            <div class="games-count">{{ player.oneGamePlayer?.length || 0 }}场</div>
          </div>
        </div>
        <div v-else class="empty-text">暂无数据</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.friend-dispute-card {
  background: var(--bg-secondary);
  border-radius: 12px;
  padding: 20px;
}

.friend-dispute-card h2 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: var(--text-primary);
}

.section-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  margin: 0 0 16px 0;
}

.cards-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.relation-card {
  padding: 16px;
  background: var(--bg-tertiary);
  border-radius: 10px;
}

.relation-card.friends {
  border-left: 3px solid var(--success-color);
}

.relation-card.dispute {
  border-left: 3px solid var(--error-color);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.card-header .icon {
  font-size: 18px;
}

.card-header .title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  flex: 1;
}

.card-header .rate {
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-secondary);
  padding: 2px 8px;
  border-radius: 4px;
}

.player-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.player-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--bg-secondary);
  border-radius: 6px;
}

.player-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.player-info .name {
  font-size: 13px;
  color: var(--text-primary);
}

.player-info .stats {
  font-size: 11px;
  color: var(--text-tertiary);
}

.games-count {
  font-size: 12px;
  color: var(--accent-color);
  font-weight: 500;
}

.empty-text {
  text-align: center;
  padding: 20px;
  color: var(--text-tertiary);
  font-size: 13px;
}
</style>
