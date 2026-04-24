import { app, BrowserWindow, ipcMain, Menu, nativeTheme, shell, Tray, type MenuItemConstructorOptions } from 'electron'
import { join } from 'path'
import { spawn, ChildProcess } from 'child_process'
import * as fs from 'fs'
import { pathToFileURL } from 'url'
import { getTrayMenuEntries, getWindowCloseAction, getWindowMinimizeAction, type TrayMenuAction } from './trayBehavior'
import { buildSplashHtml, getSplashPalette } from './splashScreen'

let mainWindow: BrowserWindow | null = null
let splashWindow: BrowserWindow | null = null
let backendProcess: ChildProcess | null = null
let appTray: Tray | null = null
let isQuitting = false

const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged

const logDir = app.getPath('logs')
const logFile = join(logDir, 'rankpeek.log')

if (!fs.existsSync(logDir)) {
  fs.mkdirSync(logDir, { recursive: true })
}

const logStream = fs.createWriteStream(logFile, { flags: 'a' })
const boundsFile = join(app.getPath('userData'), 'window-bounds.json')

function log(level: string, message: string) {
  const timestamp = new Date().toISOString()
  const logLine = `[${timestamp}] [${level}] ${message}\n`
  logStream.write(logLine)
  console.log(logLine.trim())
}

function getMainIconPath() {
  return isDev
    ? join(__dirname, '../../public/icon.ico')
    : join(process.resourcesPath, 'public/icon.ico')
}

function getTrayIconPath() {
  return isDev
    ? join(__dirname, '../../public/tray-icon.ico')
    : join(process.resourcesPath, 'public/tray-icon.ico')
}

function getBrandingAssetPath(fileName: string) {
  return isDev
    ? join(__dirname, '../../public/branding', fileName)
    : join(process.resourcesPath, 'public/branding', fileName)
}

function loadWindowBounds(): { width: number; height: number; x: number; y: number } | null {
  try {
    if (fs.existsSync(boundsFile)) {
      return JSON.parse(fs.readFileSync(boundsFile, 'utf-8'))
    }
  } catch {
    log('WARN', 'Failed to load window bounds')
  }

  return null
}

function saveWindowBounds() {
  if (!mainWindow) {
    return
  }

  try {
    fs.writeFileSync(boundsFile, JSON.stringify(mainWindow.getBounds()))
  } catch {
    log('WARN', 'Failed to save window bounds')
  }
}

function showMainWindow() {
  if (!mainWindow) {
    createWindow()
    return
  }

  if (mainWindow.isMinimized()) {
    mainWindow.restore()
  }

  if (!mainWindow.isVisible()) {
    mainWindow.show()
  }

  mainWindow.focus()
}

function hideWindowToTray() {
  if (!mainWindow) {
    return
  }

  saveWindowBounds()
  mainWindow.hide()
  log('INFO', 'Window hidden to tray')
}

function navigateRenderer(path: string) {
  if (!mainWindow) {
    return
  }

  showMainWindow()
  if (mainWindow.webContents.isLoadingMainFrame()) {
    mainWindow.webContents.once('did-finish-load', () => {
      mainWindow?.webContents.send('tray:navigate', path)
    })
    return
  }

  mainWindow.webContents.send('tray:navigate', path)
}

function toggleDevTools() {
  if (!mainWindow) {
    return
  }

  if (mainWindow.webContents.isDevToolsOpened()) {
    mainWindow.webContents.closeDevTools()
    return
  }

  mainWindow.webContents.openDevTools({ mode: 'detach' })
}

function handleTrayAction(action: TrayMenuAction) {
  switch (action) {
    case 'show-window':
      showMainWindow()
      return
    case 'hide-window':
      hideWindowToTray()
      return
    case 'navigate-home':
      navigateRenderer('/')
      return
    case 'navigate-summoner':
      navigateRenderer('/summoner')
      return
    case 'navigate-match-history':
      navigateRenderer('/match-history')
      return
    case 'toggle-devtools':
      toggleDevTools()
      return
    case 'quit':
      isQuitting = true
      app.quit()
      return
    default:
      return
  }
}

function createTray() {
  if (appTray) {
    return
  }

  appTray = new Tray(getTrayIconPath())
  appTray.setToolTip('RankPeek')

  const menuEntries: MenuItemConstructorOptions[] = getTrayMenuEntries().map((entry) => (
    entry.action === 'separator'
      ? { type: 'separator' }
      : {
          label: entry.label,
          click: () => handleTrayAction(entry.action)
        }
  ))

  appTray.setContextMenu(Menu.buildFromTemplate(menuEntries))

  appTray.on('click', () => {
    if (mainWindow?.isVisible()) {
      hideWindowToTray()
      return
    }

    showMainWindow()
  })
}

function createSplashWindow() {
  if (splashWindow) {
    return
  }

  const palette = getSplashPalette(nativeTheme.shouldUseDarkColors)
  const logoUrl = pathToFileURL(getBrandingAssetPath(palette.logoFile)).toString()

  splashWindow = new BrowserWindow({
    width: 420,
    height: 420,
    show: true,
    frame: false,
    transparent: false,
    backgroundColor: palette.surfaceColor,
    resizable: false,
    movable: false,
    minimizable: false,
    maximizable: false,
    fullscreenable: false,
    alwaysOnTop: true,
    center: true,
    skipTaskbar: true,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true
    }
  })

  splashWindow.removeMenu()
  void splashWindow.loadURL(
    `data:text/html;charset=UTF-8,${encodeURIComponent(
      buildSplashHtml({
        logoUrl,
        surfaceColor: palette.surfaceColor,
        glowColor: palette.glowColor,
        labelColor: palette.labelColor
      })
    )}`
  )

  splashWindow.on('closed', () => {
    splashWindow = null
  })
}

function closeSplashWindow() {
  if (!splashWindow) {
    return
  }

  splashWindow.close()
  splashWindow = null
}

function createWindow() {
  const storedBounds = loadWindowBounds()

  mainWindow = new BrowserWindow({
    width: storedBounds?.width ?? 1200,
    height: storedBounds?.height ?? 800,
    x: storedBounds?.x,
    y: storedBounds?.y,
    minWidth: 900,
    minHeight: 600,
    show: false,
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
    icon: getMainIconPath(),
    titleBarStyle: 'hidden',
    thickFrame: true
  })

  mainWindow.on('close', (event) => {
    const action = getWindowCloseAction({
      isTrayEnabled: Boolean(appTray),
      isQuitting
    })

    if (action === 'hide-to-tray') {
      event.preventDefault()
      hideWindowToTray()
      return
    }

    saveWindowBounds()
  })

  mainWindow.on('minimize', () => {
    const action = getWindowMinimizeAction({
      isTrayEnabled: Boolean(appTray),
      isQuitting
    })

    if (action === 'keep-minimized') {
      saveWindowBounds()
    }
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

  mainWindow.once('ready-to-show', () => {
    closeSplashWindow()
    mainWindow?.show()
    mainWindow?.focus()
  })

  if (isDev) {
    void mainWindow.loadURL('http://localhost:5173')
    mainWindow.webContents.openDevTools()
  } else {
    void mainWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http')) {
      void shell.openExternal(url)
    }

    return { action: 'deny' }
  })
}

async function startBackend(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (isDev) {
      log('INFO', 'Development mode: backend is expected on port 8080')
      resolve()
      return
    }

    const exePath = join(process.resourcesPath, 'backend', 'rankpeek-backend.exe')
    log('INFO', `Starting backend from ${exePath}`)

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

    backendProcess.on('error', (error) => {
      console.error('Failed to start backend:', error)
      reject(error)
    })

    void waitForBackend().then(resolve).catch(reject)
  })
}

async function waitForBackend(): Promise<void> {
  const maxRetries = 30
  const retryInterval = 500

  for (let index = 0; index < maxRetries; index += 1) {
    try {
      const response = await fetch('http://127.0.0.1:8080/actuator/health')
      if (response.ok) {
        log('INFO', 'Backend is ready')
        return
      }
    } catch {
      // Keep waiting until the backend answers.
    }

    await new Promise((resolve) => setTimeout(resolve, retryInterval))
  }

  throw new Error('Backend failed to start within timeout')
}

function stopBackend() {
  if (!backendProcess) {
    return
  }

  log('INFO', 'Stopping backend')
  backendProcess.kill()
  backendProcess = null
}

ipcMain.handle('window:minimize', () => {
  mainWindow?.minimize()
})

ipcMain.handle('window:maximize', () => {
  if (mainWindow?.isMaximized()) {
    mainWindow.unmaximize()
    return
  }

  mainWindow?.maximize()
})

ipcMain.handle('window:close', () => {
  mainWindow?.close()
})

ipcMain.handle('shell:openExternal', async (_, url: string) => {
  try {
    if (!url || !url.startsWith('http')) {
      throw new Error('Invalid URL')
    }

    await shell.openExternal(url, { activate: true })
    return { success: true }
  } catch (error) {
    console.error('Failed to open external link:', error)
    return { success: false, error: String(error) }
  }
})

ipcMain.handle('app:getVersion', () => app.getVersion())

app.whenReady().then(async () => {
  try {
    createSplashWindow()
    createTray()
    await startBackend()
    createWindow()
  } catch (error) {
    console.error('Failed to start application:', error)
    closeSplashWindow()
    app.quit()
  }

  app.on('activate', () => {
    showMainWindow()
  })
})

app.on('window-all-closed', () => {
  if (isQuitting && process.platform !== 'darwin') {
    stopBackend()
    app.quit()
  }
})

app.on('before-quit', () => {
  isQuitting = true
  closeSplashWindow()
  appTray?.destroy()
  appTray = null
  stopBackend()
})

process.on('uncaughtException', (error) => {
  console.error('Uncaught Exception:', error)
})
