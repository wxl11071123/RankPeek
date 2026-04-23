import { contextBridge, ipcRenderer } from 'electron'

/**
 * 暴露给渲染进程的 API
 */
contextBridge.exposeInMainWorld('electronAPI', {
  // 窗口控制
  minimizeWindow: () => ipcRenderer.invoke('window:minimize'),
  maximizeWindow: () => ipcRenderer.invoke('window:maximize'),
  closeWindow: () => ipcRenderer.invoke('window:close'),

  // 外部链接
  openExternal: (url: string) => ipcRenderer.invoke('shell:openExternal', url) as Promise<{ success: boolean; error?: string }>,

  // 应用信息
  getVersion: () => ipcRenderer.invoke('app:getVersion'),

  // 平台信息
  platform: process.platform,

  // 事件监听
  onBackendReady: (callback: () => void) => {
    ipcRenderer.on('backend:ready', callback)
    return () => ipcRenderer.removeListener('backend:ready', callback)
  }
})
