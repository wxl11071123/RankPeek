import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiClient } from '@/api/httpClient'
import type { UserTag, AramBalanceData } from '@/types/api'
import { DEFAULT_ANALYSIS_QUEUE_MODE } from '@/utils/matchPreferences'

export const useUserTagStore = defineStore('userTag', () => {
  // 状态
  const currentUserTag = ref<UserTag | null>(null)
  const aramBalanceData = ref<Record<number, AramBalanceData>>({})
  const fandomHasData = ref(false)
  const loading = ref(false)
  const error = ref('')

  /**
   * 根据名称获取用户标签
   */
  async function fetchUserTagByName(name: string, mode: number = DEFAULT_ANALYSIS_QUEUE_MODE) {
    loading.value = true
    error.value = ''

    try {
      currentUserTag.value = await apiClient.getUserTagByName(name, mode)
    } catch (e) {
      error.value = '获取用户标签失败'
      console.error('Failed to fetch user tag:', e)
    } finally {
      loading.value = false
    }
  }

  /**
   * 根据 PUUID 获取用户标签
   */
  async function fetchUserTagByPuuid(puuid: string, mode: number = DEFAULT_ANALYSIS_QUEUE_MODE) {
    loading.value = true
    error.value = ''

    try {
      currentUserTag.value = await apiClient.getUserTagByPuuid(puuid, mode)
    } catch (e) {
      error.value = '获取用户标签失败'
      console.error('Failed to fetch user tag:', e)
    } finally {
      loading.value = false
    }
  }

  /**
   * 更新 Fandom 数据
   */
  async function updateFandomData() {
    try {
      const result = await apiClient.updateFandomData()
      console.log('Fandom data updated:', result)

      // 刷新状态
      await checkFandomStatus()
      await fetchAllAramBalance()
    } catch (e) {
      console.error('Failed to update Fandom data:', e)
    }
  }

  /**
   * 检查 Fandom 数据状态
   */
  async function checkFandomStatus() {
    try {
      const status = await apiClient.getFandomStatus()
      fandomHasData.value = status.hasData
    } catch (e) {
      fandomHasData.value = false
    }
  }

  /**
   * 获取所有 ARAM 平衡数据
   */
  async function fetchAllAramBalance() {
    try {
      aramBalanceData.value = await apiClient.getAllAramBalance()
    } catch (e) {
      console.error('Failed to fetch ARAM balance data:', e)
    }
  }

  /**
   * 获取英雄 ARAM 平衡数据
   */
  function getAramBalance(championId: number): AramBalanceData | undefined {
    return aramBalanceData.value[championId]
  }

  /**
   * 初始化
   */
  async function init() {
    await checkFandomStatus()
    if (fandomHasData.value) {
      await fetchAllAramBalance()
    }
  }

  return {
    // 状态
    currentUserTag,
    aramBalanceData,
    fandomHasData,
    loading,
    error,

    // 方法
    fetchUserTagByName,
    fetchUserTagByPuuid,
    updateFandomData,
    checkFandomStatus,
    fetchAllAramBalance,
    getAramBalance,
    init
  }
})
