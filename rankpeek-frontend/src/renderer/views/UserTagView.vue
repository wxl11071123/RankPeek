<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserTagStore } from '@/stores/userTag'
import { useGameStore } from '@/stores/game'
import { GAME_MODE_OPTIONS } from '@/utils/constants'
import { DEFAULT_ANALYSIS_QUEUE_MODE } from '@/utils/matchPreferences'
import UserTagCard from '@/components/userTag/UserTagCard.vue'
import FriendDisputeCard from '@/components/userTag/FriendDisputeCard.vue'

const userTagStore = useUserTagStore()
const gameStore = useGameStore()

const searchName = ref('')
const selectedMode = ref(DEFAULT_ANALYSIS_QUEUE_MODE)
const modeOptions = GAME_MODE_OPTIONS

async function search() {
  if (!searchName.value.trim()) return
  await userTagStore.fetchUserTagByName(searchName.value, selectedMode.value)
}

async function searchCurrent() {
  if (gameStore.currentSummoner?.puuid) {
    searchName.value = gameStore.summonerName
    await userTagStore.fetchUserTagByPuuid(gameStore.currentSummoner.puuid, selectedMode.value)
  }
}

onMounted(() => {
  userTagStore.init()
})
</script>

<template>
  <div class="user-tag-view">
    <div class="page-header">
      <h1>用户标签分析</h1>
      <p>分析玩家的游戏习惯和近期表现</p>
    </div>

    <!-- 搜索区域 -->
    <div class="search-section">
      <div class="search-row">
        <input
          v-model="searchName"
          type="text"
          placeholder="输入召唤师名称..."
          @keyup.enter="search"
        />
        <select v-model="selectedMode">
          <option v-for="opt in modeOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
        <button @click="search" :disabled="userTagStore.loading">
          {{ userTagStore.loading ? '分析中...' : '分析' }}
        </button>
        <button
          v-if="gameStore.currentSummoner"
          @click="searchCurrent"
          class="secondary-btn"
        >
          分析当前账号
        </button>
      </div>
    </div>

    <!-- 错误提示 -->
    <div v-if="userTagStore.error" class="error-msg">
      {{ userTagStore.error }}
    </div>

    <!-- 分析结果 -->
    <div v-if="userTagStore.currentUserTag" class="result-section">
      <!-- 标签展示 -->
      <UserTagCard :tag="userTagStore.currentUserTag" />

      <!-- 近期数据 -->
      <div class="recent-stats">
        <h2>近期数据</h2>
        <div class="stats-grid">
          <div class="stat-item">
            <span class="label">KDA</span>
            <span class="value">{{ userTagStore.currentUserTag.recentData.kda }}</span>
          </div>
          <div class="stat-item">
            <span class="label">场均击杀</span>
            <span class="value">{{ userTagStore.currentUserTag.recentData.kills }}</span>
          </div>
          <div class="stat-item">
            <span class="label">场均死亡</span>
            <span class="value">{{ userTagStore.currentUserTag.recentData.deaths }}</span>
          </div>
          <div class="stat-item">
            <span class="label">场均助攻</span>
            <span class="value">{{ userTagStore.currentUserTag.recentData.assists }}</span>
          </div>
          <div class="stat-item">
            <span class="label">胜场</span>
            <span class="value win">{{ userTagStore.currentUserTag.recentData.selectWins }}</span>
          </div>
          <div class="stat-item">
            <span class="label">败场</span>
            <span class="value loss">{{ userTagStore.currentUserTag.recentData.selectLosses }}</span>
          </div>
          <div class="stat-item">
            <span class="label">场均金币</span>
            <span class="value">{{ userTagStore.currentUserTag.recentData.averageGold }}</span>
          </div>
          <div class="stat-item">
            <span class="label">场均伤害</span>
            <span class="value">{{ userTagStore.currentUserTag.recentData.averageDamageDealtToChampions }}</span>
          </div>
        </div>
      </div>

      <!-- 好友/冤家 -->
      <FriendDisputeCard :data="userTagStore.currentUserTag.recentData.friendAndDispute" />
    </div>

    <!-- 空状态 -->
    <div v-else class="empty-state">
      <div class="empty-icon">🔍</div>
      <p>输入召唤师名称开始分析</p>
    </div>
  </div>
</template>

<style scoped>
.user-tag-view {
  max-width: 900px;
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

.search-section {
  margin-bottom: 24px;
}

.search-row {
  display: flex;
  gap: 12px;
}

.search-row input {
  flex: 1;
  padding: 12px 16px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 14px;
}

.search-row input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.search-row select {
  padding: 12px 16px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 14px;
}

.search-row button {
  padding: 12px 24px;
  background: var(--accent-color);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.15s;
}

.search-row button:hover:not(:disabled) {
  opacity: 0.9;
}

.search-row button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.secondary-btn {
  background: var(--bg-tertiary) !important;
  color: var(--text-primary) !important;
  border: 1px solid var(--border-color) !important;
}

.error-msg {
  padding: 12px 16px;
  background: rgba(231, 76, 60, 0.1);
  border: 1px solid var(--error-color);
  border-radius: 8px;
  color: var(--error-color);
  margin-bottom: 24px;
}

.result-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.recent-stats {
  background: var(--bg-secondary);
  border-radius: 12px;
  padding: 20px;
}

.recent-stats h2 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 16px 0;
  color: var(--text-primary);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-item .label {
  font-size: 12px;
  color: var(--text-tertiary);
}

.stat-item .value {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-item .value.win {
  color: var(--success-color);
}

.stat-item .value.loss {
  color: var(--error-color);
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  background: var(--bg-secondary);
  border-radius: 12px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state p {
  color: var(--text-secondary);
  margin: 0;
}
</style>
