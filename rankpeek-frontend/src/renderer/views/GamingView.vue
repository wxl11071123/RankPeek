<template>
  <div class="gaming-view">
    <!-- 未在游戏中 -->
    <div v-if="!sessionData.phase" class="not-in-game">
      <div class="not-in-game-icon">🎮</div>
      <h2>等待加入游戏</h2>
      <p>当您进入选人阶段或游戏开始后，这里将显示双方玩家信息</p>
      <!-- 断线提示 -->
      <div v-if="isRefreshPaused" class="connection-error">
        <span class="error-icon">⚠️</span>
        <span>连接中断，自动刷新已暂停</span>
        <button class="resume-btn" @click="resumeRefresh">重新连接</button>
      </div>
      <button v-else class="refresh-btn" @click="fetchSessionData">刷新状态</button>
    </div>

    <!-- 游戏中 -->
    <div v-else class="gaming-content">
      <!-- 断线提示栏 -->
      <div v-if="isRefreshPaused" class="connection-bar">
        <span class="error-icon">⚠️</span>
        <span>连接中断，自动刷新已暂停</span>
        <button class="resume-btn-small" @click="resumeRefresh">重新连接</button>
      </div>
      <!-- 头部信息 -->
      <div class="gaming-header">
        <div class="phase-info">
          <span class="phase-badge" :class="phaseClass">{{ phaseCn }}</span>
          <span class="queue-name">{{ sessionData.typeCn || '未知模式' }}</span>
        </div>
        <div class="header-actions">
          <button class="refresh-btn-small" @click="fetchSessionData" :disabled="loading">
            <span class="refresh-icon" :class="{ 'spinning': loading }">↻</span>
            <span>{{ loading ? '刷新中...' : '刷新' }}</span>
            <span v-if="loading" class="loading-bar">
              <span class="loading-progress"></span>
            </span>
          </button>
        </div>
      </div>

      <!-- 双方队伍 -->
      <div class="teams-container">
        <!-- 我方 -->
        <div class="team-column team-blue">
          <div class="team-header team-header-blue">
            <span class="team-icon">⚔</span>
            我方队伍
          </div>
          <!-- 无对局时显示占位 -->
          <template v-if="!sessionData.teamOne || sessionData.teamOne.length === 0">
            <div class="team-placeholder team-placeholder-blue">
              <span class="placeholder-icon">👀</span>
              <span>等待加入游戏...</span>
            </div>
          </template>
          <!-- 正常显示我方 -->
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
        </div>

        <!-- 敌方 -->
        <div class="team-column team-red">
          <div class="team-header team-header-red">
            <span class="team-icon">🛡</span>
            敌方队伍
          </div>
          <!-- 选人阶段且敌方无数据时显示等待动画 -->
          <template v-if="sessionData.phase === 'ChampSelect' && (!sessionData.teamTwo || sessionData.teamTwo.length === 0)">
            <div class="enemy-loading">
              <div class="loading-dots">
                <span></span><span></span><span></span>
              </div>
              <span class="loading-text">等待对手选择...</span>
            </div>
          </template>
          <!-- 敌方无数据时显示占位 -->
          <template v-else-if="!sessionData.teamTwo || sessionData.teamTwo.length === 0">
            <div class="enemy-placeholder">
              <span class="placeholder-icon">👀</span>
              <span>等待敌方数据...</span>
            </div>
          </template>
          <!-- 正常显示敌方 -->
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
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { apiClient } from '@/api/httpClient'
import type { SessionData } from '@/types/api'
import PlayerCard from '@/components/gaming/PlayerCard.vue'
import { DEFAULT_ANALYSIS_QUEUE_MODE } from '@/utils/matchPreferences'

const router = useRouter()

// 数据
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

// 重试机制
let retryCount = 0
const maxRetries = 3

// 失败计数与自动恢复
const failCount = ref(0)
const maxFailCount = 10
const isRefreshPaused = ref(false)
let autoResumeTimer: ReturnType<typeof setTimeout> | null = null

// 阶段中文
const phaseCn = computed(() => {
  const phaseMap: Record<string, string> = {
    'ChampSelect': '选人阶段',
    'GameStart': '游戏开始',
    'InProgress': '游戏进行中',
    'PreEndOfGame': '即将结束',
    'EndOfGame': '游戏结束',
    'Lobby': '大厅',
    'Matchmaking': '匹配中',
    'ReadyCheck': '确认阶段',
    'Reconnect': '重新连接'
  }
  return phaseMap[sessionData.value.phase] || sessionData.value.phase
})

// 阶段样式类
const phaseClass = computed(() => {
  const phase = sessionData.value.phase
  if (phase === 'InProgress' || phase === 'GameStart') return 'phase-playing'
  if (phase === 'ChampSelect') return 'phase-select'
  if (phase === 'EndOfGame' || phase === 'PreEndOfGame') return 'phase-ended'
  return ''
})

// 获取会话数据
async function fetchSessionData() {
  // 如果刷新已暂停，直接返回
  if (isRefreshPaused.value) return

  loading.value = true
  try {
    const data = await apiClient.getSessionData(DEFAULT_ANALYSIS_QUEUE_MODE)
    sessionData.value = data
    // 成功时重置失败计数
    failCount.value = 0
  } catch (e) {
    console.error('获取会话数据失败', e)
    // 失败计数增加
    failCount.value++
    // 达到阈值时暂停刷新
    if (failCount.value >= maxFailCount) {
      pauseRefresh()
    }
  } finally {
    loading.value = false
  }
}

// 暂停自动刷新
function pauseRefresh() {
  isRefreshPaused.value = true
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
  // 15秒后自动恢复刷新
  if (autoResumeTimer) clearTimeout(autoResumeTimer)
  autoResumeTimer = setTimeout(() => {
    console.log('自动恢复数据刷新')
    resumeRefresh()
  }, 15000)
}

// 恢复自动刷新
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

// 跳转到玩家详情
function handleNavigateToPlayer(gameName: string, tagLine: string) {
  router.push({
    path: '/summoner',
    query: { name: `${gameName}#${tagLine}` }
  })
}

onMounted(() => {
  fetchSessionData()
  refreshInterval = setInterval(fetchSessionData, 5000)
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
      console.log(`敌方数据缺失，尝试 ${retryCount}/${maxRetries}，3秒后重试...`)
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
})
</script>

<style scoped>
.gaming-view {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 未在游戏中 */
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

/* 游戏中 */
.gaming-content {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 16px;
}

.gaming-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--bg-secondary);
  border-radius: 10px;
  border: 1px solid var(--border-color);
}

.phase-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.phase-badge {
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 13px;
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
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
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
  padding: 6px 16px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  font-size: 12px;
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

/* 队伍 */
.teams-container {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.team-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.team-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 700;
}

.team-icon {
  font-size: 16px;
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
  gap: 8px;
  flex: 1;
  overflow-y: auto;
}

/* 敌方加载状态 */
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

/* 敌方占位 */
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

/* 我方占位 - 绿色底色 */
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

/* 断线提示 */
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
</style>
