import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiClient } from '@/api/httpClient'
import type { AppConfig } from '@/types/api'

export const useAutomationStore = defineStore('automation', () => {
  // 状态
  const autoMatch = ref(false)
  const autoMatchDelay = ref(0)
  const autoAccept = ref(false)
  const autoAcceptDelay = ref(0)
  const autoPick = ref(false)
  const autoBan = ref(false)
  const pickChampions = ref<number[]>([])
  const banChampions = ref<number[]>([])

  // 配置
  const config = ref<AppConfig | null>(null)

  // 计算属性
  const anyEnabled = computed(() =>
    autoMatch.value || autoAccept.value || autoPick.value || autoBan.value
  )

  /**
   * 获取自动化状态
   */
  async function fetchStatus() {
    try {
      const status = await apiClient.getAutomationStatus()
      autoMatch.value = status.auto_match
      autoAccept.value = status.auto_accept
      autoPick.value = status.auto_pick
      autoBan.value = status.auto_ban
    } catch (error) {
      console.error('Failed to fetch automation status:', error)
    }
  }

  /**
   * 获取配置
   */
  async function fetchConfig() {
    try {
      config.value = await apiClient.getConfig()
      const auto = config.value.settings.auto
      autoMatchDelay.value = auto.startMatchDelay
      autoAcceptDelay.value = auto.acceptMatchDelay
      pickChampions.value = auto.pickChampionSlice
      banChampions.value = auto.banChampionSlice
    } catch (error) {
      console.error('Failed to fetch config:', error)
    }
  }

  /**
   * 设置自动匹配
   */
  async function setAutoMatch(enabled: boolean) {
    try {
      if (enabled) {
        await apiClient.startAutoMatch()
      } else {
        await apiClient.stopAutoMatch()
      }
      autoMatch.value = enabled
    } catch (error) {
      console.error('Failed to set auto match:', error)
    }
  }

  /**
   * 设置自动接受
   */
  async function setAutoAccept(enabled: boolean) {
    try {
      await apiClient.setAutoAccept(enabled)
      autoAccept.value = enabled
    } catch (error) {
      console.error('Failed to set auto accept:', error)
    }
  }

  /**
   * 设置自动选人
   */
  async function setAutoPick(enabled: boolean) {
    try {
      await apiClient.setAutoPick(enabled)
      autoPick.value = enabled
    } catch (error) {
      console.error('Failed to set auto pick:', error)
    }
  }

  /**
   * 设置自动禁人
   */
  async function setAutoBan(enabled: boolean) {
    try {
      await apiClient.setAutoBan(enabled)
      autoBan.value = enabled
    } catch (error) {
      console.error('Failed to set auto ban:', error)
    }
  }

  /**
   * 更新选择英雄列表
   */
  async function updatePickChampions(champions: number[]) {
    try {
      await apiClient.setConfig('settings.auto.pickChampionSlice', champions)
      pickChampions.value = champions
    } catch (error) {
      console.error('Failed to update pick champions:', error)
    }
  }

  /**
   * 更新禁用英雄列表
   */
  async function updateBanChampions(champions: number[]) {
    try {
      await apiClient.setConfig('settings.auto.banChampionSlice', champions)
      banChampions.value = champions
    } catch (error) {
      console.error('Failed to update ban champions:', error)
    }
  }

  /**
   * 更新自动匹配延迟时间
   */
  async function updateAutoMatchDelay(delay: number) {
    try {
      await apiClient.setConfig('settings.auto.startMatchDelay', delay)
      autoMatchDelay.value = delay
    } catch (error) {
      console.error('Failed to update auto match delay:', error)
    }
  }

  /**
   * 更新自动接受延迟时间
   */
  async function updateAutoAcceptDelay(delay: number) {
    try {
      await apiClient.setConfig('settings.auto.acceptMatchDelay', delay)
      autoAcceptDelay.value = delay
    } catch (error) {
      console.error('Failed to update auto accept delay:', error)
    }
  }

  /**
   * 初始化
   */
  async function init() {
    await Promise.all([fetchStatus(), fetchConfig()])
  }

  return {
    // 状态
    autoMatch,
    autoMatchDelay,
    autoAccept,
    autoAcceptDelay,
    autoPick,
    autoBan,
    pickChampions,
    banChampions,
    config,

    // 计算属性
    anyEnabled,

    // 方法
    fetchStatus,
    fetchConfig,
    setAutoMatch,
    setAutoAccept,
    setAutoPick,
    setAutoBan,
    updatePickChampions,
    updateBanChampions,
    updateAutoMatchDelay,
    updateAutoAcceptDelay,
    init
  }
})
