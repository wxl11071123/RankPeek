import type { LocalDatabaseAPI } from './localDatabase'

export interface OpenExternalResult {
  success: boolean
  error?: string
}

export interface ElectronCacheClearResult {
  deletedPaths: string[]
  failedPaths: Array<{ path: string; error: string }>
}

export type ElectronOperationResult<T> = {
  success: true
  data: T
} | {
  success: false
  error: string
}

export interface ElectronAPI {
  minimizeWindow: () => Promise<void>
  maximizeWindow: () => Promise<void>
  closeWindow: () => Promise<void>
  openExternal: (url: string) => Promise<OpenExternalResult>
  getVersion: () => Promise<string>
  clearChromiumCache: () => Promise<ElectronOperationResult<ElectronCacheClearResult>>
  platform: string
  onBackendReady: (callback: () => void) => () => void
  onTrayNavigate: (callback: (path: string) => void) => () => void
  database: LocalDatabaseAPI
}
