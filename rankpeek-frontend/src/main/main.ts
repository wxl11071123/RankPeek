import { app, BrowserWindow, ipcMain, shell } from 'electron'
import { join } from 'path'
import { spawn, ChildProcess } from 'child_process'
import * as fs from 'fs'

let mainWindow: BrowserWindow | null = null
let backendProcess: ChildProcess | null = null

const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged

const logDir = app.getPath('logs')
const logFile = join(logDir, 'rankpeek.log')

if (!fs.existsSync(logDir)) {
  fs.mkdirSync(logDir, { recursive: true })
}
const logStream = fs.createWriteStream(logFile, { flags: 'a' })

function log(level: string, message: string) {
  const timestamp = new Date().toISOString()
  const logLine = `[${timestamp}] [${level}] ${message}\n`
  logStream.write(logLine)
  console.log(logLine.trim())
}

const boundsFile = join(app.getPath('userData'), 'window-bounds.json')

function loadWindowBounds(): { width: number; height: number; x: number; y: number } | null {
  try {
    if (fs.existsSync(boundsFile)) {
      const data = fs.readFileSync(boundsFile, 'utf-8')
      return JSON.parse(data)
    }
  } catch {
    log('WARN', 'Failed to load window bounds')
  }
  return null
}

function saveWindowBounds() {
  if (mainWindow) {
    try {
      const bounds = mainWindow.getBounds()
      fs.writeFileSync(boundsFile, JSON.stringify(bounds))
    } catch {
      log('WARN', 'Failed to save window bounds')
    }
  }
}

function createWindow() {
  const storedBounds = loadWindowBounds()
  const iconPath = isDev
    ? join(__dirname, '../../public/icon.ico')
    : join(process.resourcesPath, 'public/icon.ico')

  mainWindow = new BrowserWindow({
    width: storedBounds?.width ?? 1200,
    height: storedBounds?.height ?? 800,
    x: storedBounds?.x,
    y: storedBounds?.y,
    minWidth: 900,
    minHeight: 600,
    frame: false,
    transparent: false,
    backgroundColor: '#1a1a2e',
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: join(__dirname, '../preload/preload.js'),
      webSecurity: true,
      spellcheck: false
    },
    icon: iconPath,
    titleBarStyle: 'hidden',
    thickFrame: true
  })

  mainWindow.on('close', () => {
    saveWindowBounds()
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })

  mainWindow.on('resize', () => {
    saveWindowBounds()
  })

  mainWindow.on('move', () => {
    saveWindowBounds()
  })

  if (isDev) {
    mainWindow.loadURL('http://localhost:5173')
    mainWindow.webContents.openDevTools()
  } else {
    mainWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http')) {
      shell.openExternal(url)
    }
    return { action: 'deny' }
  })
}

/**
 * 启动后端服务 (Native Image)
 */
async function startBackend(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (isDev) {
      // 开发模式：假设后端已在运行
      console.log('Development mode: Backend should be running on port 8080')
      resolve()
      return
    }

    const exePath = join(process.resourcesPath, 'backend', 'rankpeek-backend.exe')

    console.log('Starting backend from:', exePath)

    backendProcess = spawn(exePath, [], {
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true
    })

    backendProcess.stdout?.on('data', (data) => {
      console.log(`Backend: ${data}`)
    })

    backendProcess.stderr?.on('data', (data) => {
      console.error(`Backend Error: ${data}`)
    })

    backendProcess.on('error', (err) => {
      console.error('Failed to start backend:', err)
      reject(err)
    })

    // 等待后端启动
    waitForBackend().then(resolve).catch(reject)
  })
}

/**
 * 等待后端服务就绪
 */
async function waitForBackend(): Promise<void> {
  const maxRetries = 30
  const retryInterval = 500

  for (let i = 0; i < maxRetries; i++) {
    try {
      const response = await fetch('http://127.0.0.1:8080/actuator/health')
      if (response.ok) {
        console.log('Backend is ready!')
        return
      }
    } catch {
      // 继续等待
    }
    await new Promise(resolve => setTimeout(resolve, retryInterval))
  }

  throw new Error('Backend failed to start within timeout')
}

/**
 * 停止后端服务
 */
function stopBackend() {
  if (backendProcess) {
    console.log('Stopping backend...')
    backendProcess.kill()
    backendProcess = null
  }
}

// ========== IPC 处理 ==========

// 窗口控制
ipcMain.handle('window:minimize', () => {
  mainWindow?.minimize()
})

ipcMain.handle('window:maximize', () => {
  if (mainWindow?.isMaximized()) {
    mainWindow.unmaximize()
  } else {
    mainWindow?.maximize()
  }
})

ipcMain.handle('window:close', () => {
  mainWindow?.close()
})

// 打开外部链接（使用系统默认浏览器）
ipcMain.handle('shell:openExternal', async (_, url: string) => {
  try {
    if (!url || !url.startsWith('http')) {
      throw new Error('Invalid URL')
    }
    // 使用系统默认浏览器打开
    await shell.openExternal(url, { activate: true })
    return { success: true }
  } catch (error) {
    console.error('打开外部链接失败:', error)
    return { success: false, error: String(error) }
  }
})

// 获取应用版本
ipcMain.handle('app:getVersion', () => {
  return app.getVersion()
})

// ========== 应用生命周期 ==========

app.whenReady().then(async () => {
  try {
    await startBackend()
    createWindow()
  } catch (error) {
    console.error('Failed to start application:', error)
    app.quit()
  }

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  stopBackend()
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

app.on('before-quit', () => {
  stopBackend()
})

// 处理未捕获的异常
process.on('uncaughtException', (error) => {
  console.error('Uncaught Exception:', error)
})
