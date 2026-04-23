import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('electronAPI', {
  minimizeWindow: () => ipcRenderer.invoke('window:minimize'),
  maximizeWindow: () => ipcRenderer.invoke('window:maximize'),
  closeWindow: () => ipcRenderer.invoke('window:close'),
  openExternal: (url: string) => ipcRenderer.invoke('shell:openExternal', url) as Promise<{ success: boolean; error?: string }>,
  getVersion: () => ipcRenderer.invoke('app:getVersion'),
  platform: process.platform,
  onBackendReady: (callback: () => void) => {
    ipcRenderer.on('backend:ready', callback)
    return () => ipcRenderer.removeListener('backend:ready', callback)
  },
  onTrayNavigate: (callback: (path: string) => void) => {
    const listener = (_event: Electron.IpcRendererEvent, path: string) => callback(path)
    ipcRenderer.on('tray:navigate', listener)
    return () => ipcRenderer.removeListener('tray:navigate', listener)
  }
})
