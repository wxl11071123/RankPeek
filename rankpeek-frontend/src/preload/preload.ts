import { contextBridge, ipcRenderer } from 'electron'
import type {
  AiAnalysisListOptions,
  AiMemoryExportResult,
  AiMemoryStats,
  AiAnalysisResult,
  AiAnalysisResultInput,
  DatabaseResult,
  LocalDatabaseAPI,
  MatchDetail,
  MatchDetailInput,
  MatchRecord,
  MatchRecordInput,
  MatchRecordListOptions,
  LocalStorageRetentionResult,
  SummonerAccount,
  SummonerAccountInput
} from '../renderer/types/localDatabase'

contextBridge.exposeInMainWorld('electronAPI', {
  minimizeWindow: () => ipcRenderer.invoke('window:minimize'),
  maximizeWindow: () => ipcRenderer.invoke('window:maximize'),
  closeWindow: () => ipcRenderer.invoke('window:close'),
  openExternal: (url: string) => ipcRenderer.invoke('shell:openExternal', url) as Promise<{ success: boolean; error?: string }>,
  getVersion: () => ipcRenderer.invoke('app:getVersion'),
  clearChromiumCache: () => ipcRenderer.invoke('app:clearChromiumCache'),
  platform: process.platform,
  onBackendReady: (callback: () => void) => {
    ipcRenderer.on('backend:ready', callback)
    return () => ipcRenderer.removeListener('backend:ready', callback)
  },
  onTrayNavigate: (callback: (path: string) => void) => {
    const listener = (_event: Electron.IpcRendererEvent, path: string) => callback(path)
    ipcRenderer.on('tray:navigate', listener)
    return () => ipcRenderer.removeListener('tray:navigate', listener)
  },
  database: {
    upsertAccount: (account: SummonerAccountInput) => (
      ipcRenderer.invoke('db:account:upsert', account) as Promise<DatabaseResult<SummonerAccount>>
    ),
    listAccounts: () => (
      ipcRenderer.invoke('db:account:list') as Promise<DatabaseResult<SummonerAccount[]>>
    ),
    getLastSelectedAccount: () => (
      ipcRenderer.invoke('db:account:getLastSelected') as Promise<DatabaseResult<SummonerAccount | null>>
    ),
    setLastSelectedAccount: (region: string, puuid: string) => (
      ipcRenderer.invoke('db:account:setLastSelected', { region, puuid }) as Promise<DatabaseResult<SummonerAccount>>
    ),
    upsertMatchRecords: (records: MatchRecordInput[]) => (
      ipcRenderer.invoke('db:match:upsertRecords', records) as Promise<DatabaseResult<MatchRecord[]>>
    ),
    listMatchRecordsByAccount: (accountPuuid: string, options?: MatchRecordListOptions) => (
      ipcRenderer.invoke('db:match:listByAccount', { accountPuuid, options }) as Promise<DatabaseResult<MatchRecord[]>>
    ),
    getMatchDetail: (region: string, matchId: string) => (
      ipcRenderer.invoke('db:match:getDetail', { region, matchId }) as Promise<DatabaseResult<MatchDetail | null>>
    ),
    upsertMatchDetail: (detail: MatchDetailInput) => (
      ipcRenderer.invoke('db:match:upsertDetail', detail) as Promise<DatabaseResult<MatchDetail>>
    ),
    saveAnalysisResult: (result: AiAnalysisResultInput) => (
      ipcRenderer.invoke('db:ai:saveResult', result) as Promise<DatabaseResult<AiAnalysisResult>>
    ),
    listAnalysisResultsByAccount: (accountPuuid: string, options?: AiAnalysisListOptions) => (
      ipcRenderer.invoke('db:ai:listByAccount', { accountPuuid, options }) as Promise<DatabaseResult<AiAnalysisResult[]>>
    ),
    getAnalysisResultById: (id: number) => (
      ipcRenderer.invoke('db:ai:getById', id) as Promise<DatabaseResult<AiAnalysisResult | null>>
    ),
    findAnalysisByInputHash: (inputHash: string) => (
      ipcRenderer.invoke('db:ai:findByInputHash', inputHash) as Promise<DatabaseResult<AiAnalysisResult | null>>
    ),
    getAiMemoryStats: (accountPuuid: string) => (
      ipcRenderer.invoke('db:ai:getMemoryStats', accountPuuid) as Promise<DatabaseResult<AiMemoryStats>>
    ),
    exportAiMemory: (accountPuuid: string) => (
      ipcRenderer.invoke('db:ai:exportMemory', accountPuuid) as Promise<DatabaseResult<AiMemoryExportResult>>
    ),
    runStorageRetention: () => (
      ipcRenderer.invoke('db:storage:runRetention') as Promise<DatabaseResult<LocalStorageRetentionResult>>
    )
  }
} satisfies { database: LocalDatabaseAPI } & Record<string, unknown>)
