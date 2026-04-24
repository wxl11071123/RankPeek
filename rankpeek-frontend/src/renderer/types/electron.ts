export interface OpenExternalResult {
  success: boolean
  error?: string
}

export interface ElectronAPI {
  minimizeWindow: () => Promise<void>
  maximizeWindow: () => Promise<void>
  closeWindow: () => Promise<void>
  openExternal: (url: string) => Promise<OpenExternalResult>
  getVersion: () => Promise<string>
  platform: string
  onBackendReady: (callback: () => void) => () => void
  onTrayNavigate: (callback: (path: string) => void) => () => void
}
