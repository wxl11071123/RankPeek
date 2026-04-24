import type {
  ApiResponse,
  AppConfig,
  AramBalanceData,
  AssetDetails,
  AutomationStatus,
  ChampionOption,
  GameDetail,
  GameModeOption,
  GameState,
  Lobby,
  MatchHistory,
  Rank,
  SessionData,
  Summoner,
  TagConfig,
  UserTag,
  UserTagSummary,
  WinRate
} from '@/types/api'
import axios, { AxiosError, AxiosInstance } from 'axios'

const API_BASE_URL = 'http://127.0.0.1:8080/api/v1'

class ApiError extends Error {
  code: number
  timestamp: number

  constructor(message: string, code: number, timestamp: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.timestamp = timestamp
  }
}

class ApiClient {
  private client: AxiosInstance

  constructor() {
    this.client = axios.create({
      baseURL: API_BASE_URL,
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json'
      }
    })

    this.client.interceptors.request.use((config) => {
      config.headers['X-Request-ID'] = crypto.randomUUID()
      config.headers['X-Request-Time'] = Date.now().toString()
      return config
    })

    this.client.interceptors.response.use(
      (response) => response,
      (error: AxiosError) => {
        if (error.response?.status === 503) {
          console.warn('LCU 服务不可用')
        }
        console.error('API Error:', error.message)
        return Promise.reject(error)
      }
    )
  }

  /**
   * 发送 GET 请求并解包响应数据
   */
  private async get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    const { data: response } = await this.client.get<ApiResponse<T>>(url, { params })
    if (response.code !== 200) {
      throw new ApiError(response.message, response.code, response.timestamp)
    }
    return response.data
  }

  /**
   * 发送 POST 请求（无返回数据）
   */
  private async postVoid(url: string, data?: unknown): Promise<void> {
    const { data: response } = await this.client.post<ApiResponse<void>>(url, data)
    if (response.code !== 200) {
      throw new ApiError(response.message, response.code, response.timestamp)
    }
  }

  /**
   * 发送 POST 请求并解包响应数据
   */
  private async post<T>(url: string, data?: unknown): Promise<T> {
    const { data: response } = await this.client.post<ApiResponse<T>>(url, data)
    if (response.code !== 200) {
      throw new ApiError(response.message, response.code, response.timestamp)
    }
    return response.data
  }

  /**
   * 发送 PUT 请求（无返回数据）
   */
  private async putVoid(url: string, data?: unknown): Promise<void> {
    const { data: response } = await this.client.put<ApiResponse<void>>(url, data)
    if (response.code !== 200) {
      throw new ApiError(response.message, response.code, response.timestamp)
    }
  }

  /**
   * 发送 DELETE 请求（无返回数据）
   */
  private async deleteVoid(url: string): Promise<void> {
    const { data: response } = await this.client.delete<ApiResponse<void>>(url)
    if (response.code !== 200) {
      throw new ApiError(response.message, response.code, response.timestamp)
    }
  }

  // ========== 召唤师 API ==========

  /**
   * 获取当前召唤师信息
   */
  async getMySummoner(): Promise<Summoner> {
    return this.get<Summoner>('/summoner/me')
  }

  /**
   * 根据 PUUID 获取召唤师
   */
  async getSummonerByPuuid(puuid: string): Promise<Summoner> {
    return this.get<Summoner>(`/summoner/puuid/${puuid}`)
  }

  /**
   * 根据名称获取召唤师
   */
  async getSummonerByName(name: string): Promise<Summoner> {
    return this.get<Summoner>(`/summoner/name/${encodeURIComponent(name)}`)
  }

  /**
   * 获取段位信息
   */
  async getRank(puuid: string): Promise<Rank> {
    return this.get<Rank>(`/summoner/rank/${puuid}`)
  }

  /**
   * 获取战绩
   * @param puuid 玩家 PUUID
   * @param begIndex 起始索引（inclusive）
   * @param endIndex 结束索引（inclusive）
   */
  async getMatchHistory(puuid: string, begIndex = 0, endIndex = 9): Promise<MatchHistory[]> {
    return this.get<MatchHistory[]>(`/summoner/matches/${puuid}`, { begIndex, endIndex })
  }

  /**
   * 获取筛选后的战绩
   */
  async getFilteredMatchHistory(
    puuid: string,
    options: {
      begIndex?: number
      endIndex?: number
      queueId?: number
      championId?: number
      maxResults?: number
    } = {}
  ): Promise<MatchHistory[]> {
    return this.get<MatchHistory[]>(`/summoner/matches-filtered/${puuid}`, options)
  }

  /**
   * 获取服务器名称
   */
  async getPlatformName(name: string): Promise<string> {
    return this.get<string>(`/summoner/platform/${encodeURIComponent(name)}`)
  }

  /**
   * 获取胜率统计
   */
  async getWinRate(puuid: string, mode?: number): Promise<WinRate> {
    return this.get<WinRate>(`/summoner/win-rate/${puuid}`, { mode })
  }

  /**
   * 获取排位胜率统计（从战绩计算真实胜率）
   */
  async getRankedWinRates(puuid: string): Promise<Record<string, WinRate>> {
    return this.get<Record<string, WinRate>>(`/summoner/ranked-win-rates/${puuid}`)
  }

  // ========== 会话 API ==========

  /**
   * 获取游戏状态
   */
  async getGameState(): Promise<GameState> {
    return this.get<GameState>('/session/game-state')
  }

  /**
   * 获取游戏阶段
   */
  async getGamePhase(): Promise<string> {
    return this.get<string>('/session/phase')
  }

  /**
   * 获取大厅信息
   */
  async getLobby(): Promise<Lobby> {
    return this.get<Lobby>('/session/lobby')
  }

  /**
   * 开始匹配
   */
  async startMatchmaking(): Promise<void> {
    return this.postVoid('/session/matchmaking/start')
  }

  /**
   * 取消匹配
   */
  async cancelMatchmaking(): Promise<void> {
    return this.postVoid('/session/matchmaking/cancel')
  }

  /**
   * 接受对局
   */
  async acceptMatch(): Promise<void> {
    return this.postVoid('/session/accept')
  }

  /**
   * 检查连接状态
   */
  async checkConnection(): Promise<boolean> {
    try {
      return await this.get<boolean>('/session/connected')
    } catch {
      return false
    }
  }

  // ========== 自动化 API ==========

  /**
   * 获取自动化任务状态
   */
  async getAutomationStatus(): Promise<AutomationStatus> {
    return this.get<AutomationStatus>('/automation/status')
  }

  /**
   * 启动自动匹配
   */
  async startAutoMatch(): Promise<void> {
    return this.postVoid('/automation/match/start')
  }

  /**
   * 停止自动匹配
   */
  async stopAutoMatch(): Promise<void> {
    return this.postVoid('/automation/match/stop')
  }

  /**
   * 设置自动接受
   */
  async setAutoAccept(enabled: boolean): Promise<void> {
    return this.postVoid(`/automation/accept/${enabled}`)
  }

  /**
   * 设置自动选人
   */
  async setAutoPick(enabled: boolean): Promise<void> {
    return this.postVoid(`/automation/pick/${enabled}`)
  }

  /**
   * 设置自动禁人
   */
  async setAutoBan(enabled: boolean): Promise<void> {
    return this.postVoid(`/automation/ban/${enabled}`)
  }

  /**
   * 批量设置自动化
   */
  async setBatchAutomation(settings: Record<string, boolean>): Promise<void> {
    return this.postVoid('/automation/batch', settings)
  }

  // ========== 配置 API ==========

  /**
   * 获取所有配置
   */
  async getConfig(): Promise<AppConfig> {
    return this.get<AppConfig>('/config')
  }

  /**
   * 获取指定配置
   */
  async getConfigValue(key: string): Promise<unknown> {
    return this.get<unknown>(`/config/${key}`)
  }

  /**
   * 更新配置
   */
  async setConfig(key: string, value: unknown): Promise<void> {
    return this.putVoid(`/config/${key}`, { value })
  }

  /**
   * 获取英雄选项列表
   */
  async getChampionOptions(): Promise<ChampionOption[]> {
    return this.get<ChampionOption[]>('/config/champions')
  }

  /**
   * 获取游戏模式列表
   */
  async getGameModes(): Promise<GameModeOption[]> {
    return this.get<GameModeOption[]>('/config/game-modes')
  }

  // ========== 用户标签 API ==========

  /**
   * 根据名称获取用户标签
   */
  async getUserTagByName(name: string, mode = 0): Promise<UserTag> {
    return this.get<UserTag>(`/user-tag/name/${encodeURIComponent(name)}`, { mode })
  }

  /**
   * 根据 PUUID 获取用户标签
   */
  async getUserTagByPuuid(puuid: string, mode = 0): Promise<UserTag> {
    return this.get<UserTag>(`/user-tag/puuid/${puuid}`, { mode })
  }

  /**
   * 批量获取用户标签摘要
   */
  async getUserTagSummaryBatch(puuids: string[], mode = 0): Promise<Record<string, UserTagSummary>> {
    if (puuids.length === 0) {
      return {}
    }
    return this.post<Record<string, UserTagSummary>>('/user-tag/batch-summary', { puuids, mode })
  }

  // ========== Fandom API ==========

  /**
   * 更新 Fandom 数据
   */
  async updateFandomData(): Promise<string> {
    return this.post<string>('/fandom/update')
  }

  /**
   * 获取英雄 ARAM 平衡数据
   */
  async getAramBalance(championId: number): Promise<AramBalanceData> {
    return this.get<AramBalanceData>(`/fandom/aram/${championId}`)
  }

  /**
   * 获取所有 ARAM 平衡数据
   */
  async getAllAramBalance(): Promise<Record<number, AramBalanceData>> {
    return this.get<Record<number, AramBalanceData>>('/fandom/aram')
  }

  /**
   * 检查 Fandom 数据状态
   */
  async getFandomStatus(): Promise<{ hasData: boolean; message: string }> {
    return this.get<{ hasData: boolean; message: string }>('/fandom/status')
  }

  // ========== 资源 API ==========

  /**
   * 获取资源详情
   */
  async getAssetDetails(type: string, ids: number[]): Promise<AssetDetails[]> {
    return this.get<AssetDetails[]>('/asset/details', { type, ids: ids.join(',') })
  }

  /**
   * 获取单个资源详情
   */
  async getAssetDetail(type: string, id: number): Promise<AssetDetails> {
    return this.get<AssetDetails>(`/asset/detail/${type}/${id}`)
  }

  // ========== 标签配置 API ==========

  /**
   * 获取所有标签配置
   */
  async getTagConfigs(): Promise<TagConfig[]> {
    return this.get<TagConfig[]>('/tag-config')
  }

  /**
   * 保存标签配置列表
   */
  async saveTagConfigs(configs: TagConfig[]): Promise<void> {
    return this.postVoid('/tag-config', configs)
  }

  /**
   * 添加标签配置
   */
  async addTagConfig(config: TagConfig): Promise<void> {
    return this.postVoid('/tag-config/add', config)
  }

  /**
   * 更新标签配置
   */
  async updateTagConfig(id: string, config: TagConfig): Promise<void> {
    return this.putVoid(`/tag-config/${id}`, config)
  }

  /**
   * 删除标签配置
   */
  async deleteTagConfig(id: string): Promise<void> {
    return this.deleteVoid(`/tag-config/${id}`)
  }

  /**
   * 切换标签启用状态
   */
  async toggleTagConfig(id: string): Promise<void> {
    return this.postVoid(`/tag-config/${id}/toggle`)
  }

  /**
   * 重置为默认配置
   */
  async resetTagConfigs(): Promise<void> {
    return this.postVoid('/tag-config/reset')
  }

  /**
   * 获取默认标签配置
   */
  async getDefaultTagConfigs(): Promise<TagConfig[]> {
    return this.get<TagConfig[]>('/tag-config/defaults')
  }

  // ========== 对局详情 API ==========

  /**
   * 获取单局详情
   */
  async getGameDetail(gameId: number): Promise<GameDetail> {
    return this.get<GameDetail>(`/summoner/game-detail/${gameId}`)
  }

  // ========== 会话数据 API ==========

  /**
   * 获取完整会话数据（包含双方队伍所有玩家信息）
   */
  async getSessionData(mode?: number): Promise<SessionData> {
    return this.get<SessionData>('/session/data', mode != null ? { mode } : undefined)
  }
}

export const apiClient = new ApiClient()
