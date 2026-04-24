import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiClient } from '@/api/httpClient'
import { wsClient } from '@/api/websocketClient'
import type { GameState, Summoner, Rank, QueueInfo, MatchHistory } from '@/types/api'

export const useGameStore = defineStore('game', () => {
  // 状态
  const connected = ref(false)
  const gamePhase = ref<string>('')
  const currentSummoner = ref<Summoner | null>(null)
  const currentRank = ref<Rank | null>(null)
  const matchHistory = ref<MatchHistory[]>([])

  // 计算属性
  const isConnected = computed(() => connected.value)
  const isInGame = computed(() =>
    ['InProgress', 'ChampSelect', 'ReadyCheck'].includes(gamePhase.value)
  )
  const summonerName = computed(() => {
    if (!currentSummoner.value) return ''
    const s = currentSummoner.value
    return s.tagLine ? `${s.gameName}#${s.tagLine}` : s.gameName
  })

  // 排位信息
  const soloRank = computed((): QueueInfo | null =>
    currentRank.value?.queueMap?.RANKED_SOLO_5x5 || null
  )
  const flexRank = computed((): QueueInfo | null =>
    currentRank.value?.queueMap?.RANKED_FLEX_SR || null
  )

  /**
   * 初始化连接
   */
  async function initConnection() {
    // 连接 WebSocket
    wsClient.connect()

    // 订阅游戏状态
    wsClient.onGameState((state: GameState) => {
      connected.value = state.connected
      gamePhase.value = state.phase || ''
      currentSummoner.value = state.summoner

      // 获取段位信息
      if (state.summoner?.puuid) {
        fetchRank(state.summoner.puuid)
      }
    })

    // 初始检查
    await checkConnection()
  }

  /**
   * 检查连接状态
   */
  async function checkConnection() {
    try {
      const state = await apiClient.getGameState()
      connected.value = state.connected
      gamePhase.value = state.phase || ''
      currentSummoner.value = state.summoner

      if (state.summoner?.puuid) {
        await fetchRank(state.summoner.puuid)
      }
    } catch {
      connected.value = false
    }
  }

  /**
   * 刷新召唤师信息
   */
  async function refreshSummoner() {
    try {
      const summoner = await apiClient.getMySummoner()
      currentSummoner.value = summoner
      await fetchRank(summoner.puuid)
    } catch (error) {
      console.error('Failed to refresh summoner:', error)
    }
  }

  /**
   * 获取段位信息
   */
  async function fetchRank(puuid: string): Promise<Rank | null> {
    try {
      const rank = await apiClient.getRank(puuid)
      currentRank.value = rank
      return rank
    } catch (error) {
      console.error('Failed to fetch rank:', error)
      return null
    }
  }

  /**
   * 获取战绩
   */
  async function fetchMatchHistory(puuid: string, count = 20) {
    try {
      matchHistory.value = await apiClient.getMatchHistory(puuid, 0, count)
    } catch (error) {
      console.error('Failed to fetch match history:', error)
    }
  }

  /**
   * 根据名称查询召唤师
   */
  async function fetchSummonerByName(name: string): Promise<Summoner | null> {
    try {
      return await apiClient.getSummonerByName(name)
    } catch (error) {
      console.error('Failed to fetch summoner by name:', error)
      return null
    }
  }

  return {
    // 状态
    connected,
    gamePhase,
    currentSummoner,
    currentRank,
    matchHistory,

    // 计算属性
    isConnected,
    isInGame,
    summonerName,
    soloRank,
    flexRank,

    // 方法
    initConnection,
    checkConnection,
    refreshSummoner,
    fetchRank,
    fetchMatchHistory,
    fetchSummonerByName
  }
})
