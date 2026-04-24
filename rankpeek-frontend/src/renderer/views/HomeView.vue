<script setup lang="ts">
import { onMounted } from 'vue'
import { useGameStore } from '@/stores/game'
import { getTierCn } from '@/utils/constants'
import StatusCard from '@/components/home/StatusCard.vue'
import QuickActions from '@/components/home/QuickActions.vue'

const gameStore = useGameStore()

onMounted(() => {
  gameStore.checkConnection()
})
</script>

<template>
  <div class="home-view">
    <div class="page-header">
      <h1>首页</h1>
      <p>欢迎回来，{{ gameStore.summonerName || '召唤师' }}</p>
    </div>

    <div class="dashboard-grid">
      <!-- 连接状态 -->
      <StatusCard
        title="连接状态"
        :status="gameStore.connected ? 'connected' : 'disconnected'"
        :value="gameStore.connected ? '已连接' : '未连接'"
        icon="🔌"
      />

      <!-- 游戏阶段 -->
      <StatusCard
        title="游戏阶段"
        status="info"
        :value="gameStore.gamePhase || '无'"
        icon="🎮"
      />

      <!-- 段位信息 -->
      <StatusCard
        title="单排段位"
        status="rank"
        :value="getTierCn(gameStore.soloRank?.tier || '')"
        :subtitle="gameStore.soloRank ? `${gameStore.soloRank.leaguePoints} LP` : undefined"
        icon="🏆"
      />

      <!-- 灵活组排 -->
      <StatusCard
        title="灵活组排"
        status="rank"
        :value="getTierCn(gameStore.flexRank?.tier || '')"
        :subtitle="gameStore.flexRank ? `${gameStore.flexRank.leaguePoints} LP` : undefined"
        icon="🏅"
      />
    </div>

    <!-- 快捷操作 -->
    <QuickActions />

    <!-- 使用提示 -->
    <div v-if="!gameStore.connected" class="connection-hint">
      <div class="hint-icon">⚠️</div>
      <div class="hint-content">
        <h3>未检测到游戏客户端</h3>
        <p>请先启动英雄联盟客户端，然后刷新连接状态。</p>
        <button class="refresh-btn" @click="gameStore.checkConnection">
          刷新连接
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-view {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px 0;
  color: var(--text-primary);
}

.page-header p {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.connection-hint {
  display: flex;
  gap: 16px;
  padding: 20px;
  background: var(--bg-secondary);
  border: 1px solid var(--warning-color);
  border-radius: 12px;
  margin-top: 24px;
}

.hint-icon {
  font-size: 32px;
}

.hint-content h3 {
  font-size: 16px;
  margin: 0 0 8px 0;
  color: var(--text-primary);
}

.hint-content p {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0 0 12px 0;
}

.refresh-btn {
  padding: 8px 16px;
  background: var(--accent-color);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.15s;
}

.refresh-btn:hover {
  opacity: 0.9;
}
</style>
