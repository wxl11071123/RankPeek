import {
  app,
  BrowserWindow,
  dialog,
  ipcMain,
  Menu,
  screen,
  session,
  shell,
  Tray,
  type IpcMainInvokeEvent,
  type MenuItemConstructorOptions,
  type Rectangle
} from 'electron'
import { join } from 'path'
import { spawn, ChildProcess } from 'child_process'
import * as fs from 'fs'
import {
  closeLocalDatabase,
  getLocalDatabase,
  initLocalDatabase,
  registerDatabaseIpcHandlers,
  type LocalDatabaseLogger
} from './database/index'
import { BackendIdentityMismatchError, createBackendInstanceId, fetchBackendIdentity, waitForBackend } from './backendStartup'
import { getTrayMenuEntries, getWindowCloseAction, getWindowMinimizeAction, type TrayMenuAction } from './trayBehavior'

let mainWindow: BrowserWindow | null = null
let splashWindow: BrowserWindow | null = null
let opggWindow: BrowserWindow | null = null
let backendProcess: ChildProcess | null = null
let appTray: Tray | null = null
let isQuitting = false
let startupFallbackTimer: ReturnType<typeof setTimeout> | null = null
let startupCheckInterval: ReturnType<typeof setInterval> | null = null
let noLcuTimeout: ReturnType<typeof setTimeout> | null = null
let minimumSplashTimer: ReturnType<typeof setTimeout> | null = null
let startupExitStarted = false
let startupStartedAt = 0
let backendShutdownInProgress = false
let backendShutdownCompleted = false
let backendInstanceId: string | null = null

const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged
const API_BASE_URL = 'http://127.0.0.1:8080/api/v1'
const STARTUP_CHECK_INTERVAL_MS = 500
const NO_LCU_TIMEOUT_MS = 6000
const STARTUP_FORCE_TIMEOUT_MS = 10000
const MIN_SPLASH_VISIBLE_MS = 3600
const BACKEND_SHUTDOWN_REQUEST_TIMEOUT_MS = 2000
const BACKEND_GRACEFUL_EXIT_TIMEOUT_MS = 5000
const OPGG_WINDOW_TITLE = 'RP-OPGG'
const LOG_ROTATION_MAX_BYTES = 10 * 1024 * 1024
const LOG_ROTATION_KEEP_COUNT = 5
const CORRUPT_BACKUP_KEEP_COUNT = 3

type StartupExitMode = 'smooth' | 'quick'

const logDir = app.getPath('logs')
const logFile = join(logDir, 'rankpeek.log')

if (!fs.existsSync(logDir)) {
  fs.mkdirSync(logDir, { recursive: true })
}

rotateLogFile(logFile)
const logStream = fs.createWriteStream(logFile, { flags: 'a' })
const boundsFile = join(app.getPath('userData'), 'window-bounds.json')
const opggBoundsFile = join(app.getPath('userData'), 'opgg-window-bounds.json')
let storageRetentionTimer: NodeJS.Immediate | null = null

interface OpggChampionQuery {
  enabled?: boolean
  reason?: string
  championId?: number | null
  mode?: string
  region?: 'kr' | string
  tier?: string
  position?: string
  filterLabel?: string
}

interface LcuWindowBoundsPayload {
  found: boolean
  x?: number | null
  y?: number | null
  width?: number | null
  height?: number | null
}

interface OpggWindowBoundsState {
  bounds: Rectangle
  userMoved: boolean
}

function rotateLogFile(filePath: string) {
  try {
    if (!fs.existsSync(filePath) || fs.statSync(filePath).size < LOG_ROTATION_MAX_BYTES) {
      return
    }

    for (let index = LOG_ROTATION_KEEP_COUNT - 1; index >= 1; index -= 1) {
      const source = `${filePath}.${index}`
      const target = `${filePath}.${index + 1}`
      if (fs.existsSync(source)) {
        fs.renameSync(source, target)
      }
    }

    fs.renameSync(filePath, `${filePath}.1`)
  } catch (error) {
    console.warn(`Failed to rotate RankPeek log: ${String(error)}`)
  }
}

function log(level: string, message: string) {
  const timestamp = new Date().toISOString()
  const logLine = `[${timestamp}] [${level}] ${message}\n`
  logStream.write(logLine)
  console.log(logLine.trim())
}

const databaseLogger: LocalDatabaseLogger = {
  info: (message) => log('INFO', message),
  warn: (message) => log('WARN', message),
  error: (message) => log('ERROR', message)
}

function getMainIconPath() {
  return getPublicAssetPath('icon.ico')
}

function getTrayIconPath() {
  return getPublicAssetPath('tray-icon.ico')
}

function getPublicAssetPath(fileName: string) {
  return isDev
    ? join(__dirname, '../../public', fileName)
    : join(process.resourcesPath, 'public', fileName)
}

function scheduleLocalStorageRetention() {
  if (storageRetentionTimer) {
    return
  }

  storageRetentionTimer = setImmediate(() => {
    storageRetentionTimer = null
    try {
      const result = getLocalDatabase().runStorageRetention()
      if (result.matchRecordsDeleted > 0 || result.matchDetailsDeleted > 0) {
        log(
          'INFO',
          `Local database retention applied: matchRecordsDeleted=${result.matchRecordsDeleted}, `
            + `matchDetailsDeleted=${result.matchDetailsDeleted}`
        )
      }
    } catch (error) {
      log('WARN', `Local database retention failed: ${String(error)}`)
    }
  })
}

async function saveAiMemoryExport({ payload }: Parameters<NonNullable<Parameters<typeof registerDatabaseIpcHandlers>[3]>['exportAiMemory']>[0]) {
  const defaultFileName = `rankpeek-ai-memory-${sanitizeFileToken(payload.accountPuuid)}-${Date.now()}.json`
  const saveResult = await dialog.showSaveDialog(mainWindow ?? undefined, {
    title: '导出 AI 记忆',
    defaultPath: join(app.getPath('documents'), defaultFileName),
    filters: [
      { name: 'JSON', extensions: ['json'] }
    ]
  })

  if (saveResult.canceled || !saveResult.filePath) {
    return {
      filePath: null,
      exportedCount: 0,
      canceled: true
    }
  }

  await fs.promises.writeFile(saveResult.filePath, JSON.stringify(payload, null, 2), 'utf8')
  return {
    filePath: saveResult.filePath,
    exportedCount: payload.records.length,
    canceled: false
  }
}

function sanitizeFileToken(value: string) {
  return value.replace(/[^a-zA-Z0-9_-]+/g, '-').slice(0, 32) || 'account'
}

async function clearElectronCacheArtifacts() {
  const userDataPath = app.getPath('userData')
  const deletedPaths: string[] = []
  const failedPaths: Array<{ path: string; error: string }> = []

  try {
    await session.defaultSession.clearCache()
  } catch (error) {
    failedPaths.push({ path: 'electron-session-cache', error: String(error) })
  }

  for (const directoryName of ['Cache', 'Code Cache', 'GPUCache']) {
    const directoryPath = join(userDataPath, directoryName)
    try {
      await fs.promises.rm(directoryPath, { recursive: true, force: true })
      deletedPaths.push(directoryPath)
    } catch (error) {
      failedPaths.push({ path: directoryPath, error: String(error) })
    }
  }

  for (const directoryPath of [userDataPath, join(userDataPath, 'user-store')]) {
    const deleted = await pruneCorruptUserStoreBackups(directoryPath)
    deletedPaths.push(...deleted)
  }

  return {
    deletedPaths,
    failedPaths
  }
}

async function pruneCorruptUserStoreBackups(directoryPath: string) {
  try {
    const entries = await fs.promises.readdir(directoryPath, { withFileTypes: true })
    const backups = await Promise.all(
      entries
        .filter((entry) => entry.isFile() && /^rankpeek-user-store\.corrupt-.*\.json$/.test(entry.name))
        .map(async (entry) => {
          const filePath = join(directoryPath, entry.name)
          const stat = await fs.promises.stat(filePath)
          return { filePath, mtimeMs: stat.mtimeMs }
        })
    )

    backups.sort((left, right) => left.mtimeMs - right.mtimeMs || left.filePath.localeCompare(right.filePath))
    const deleted: string[] = []
    for (const backup of backups.slice(0, Math.max(0, backups.length - CORRUPT_BACKUP_KEEP_COUNT))) {
      await fs.promises.rm(backup.filePath, { force: true })
      deleted.push(backup.filePath)
    }
    return deleted
  } catch {
    return []
  }
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

function loadOpggWindowBounds(): OpggWindowBoundsState | null {
  try {
    if (!fs.existsSync(opggBoundsFile)) {
      return null
    }

    const parsed = JSON.parse(fs.readFileSync(opggBoundsFile, 'utf-8'))
    const bounds = isWindowBounds(parsed?.bounds) ? parsed.bounds : parsed
    if (!isWindowBounds(bounds)) {
      return null
    }

    return {
      bounds,
      userMoved: parsed?.userMoved !== false
    }
  } catch {
    log('WARN', 'Failed to load OP.GG window bounds')
    return null
  }
}

function saveOpggWindowBounds(bounds: Rectangle, userMoved = true) {
  try {
    fs.writeFileSync(opggBoundsFile, JSON.stringify({ bounds, userMoved }))
  } catch {
    log('WARN', 'Failed to save OP.GG window bounds')
  }
}

function isWindowBounds(value: unknown): value is Rectangle {
  if (!isRecord(value)) {
    return false
  }

  return ['x', 'y', 'width', 'height'].every(key => {
    const numberValue = value[key]
    return typeof numberValue === 'number' && Number.isFinite(numberValue)
  })
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

  splashWindow = new BrowserWindow({
    width: 480,
    height: 520,
    frame: false,
    transparent: false,
    backgroundColor: '#000000',
    resizable: false,
    show: false,
    minimizable: false,
    maximizable: false,
    fullscreenable: false,
    center: true,
    skipTaskbar: true,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true
    }
  })

  splashWindow.removeMenu()
  void splashWindow.loadFile(getPublicAssetPath('loading.html')).catch((error) => {
    log('WARN', `Failed to load splash screen: ${String(error)}`)
  })

  splashWindow.once('ready-to-show', () => {
    if (splashWindow) {
      showSplashWindowOnceOnTop(splashWindow)
    }
  })

  splashWindow.on('closed', () => {
    splashWindow = null
  })
}

function showSplashWindowOnceOnTop(window: BrowserWindow) {
  if (window.isDestroyed()) {
    return
  }

  window.setAlwaysOnTop(true)
  window.show()
  window.focus()
  window.setAlwaysOnTop(false)
}

function closeSplashWindow() {
  if (!splashWindow) {
    return
  }

  if (!splashWindow.isDestroyed()) {
    splashWindow.close()
  }
  splashWindow = null
}

function getOpggWindowUrl() {
  return isDev
    ? 'http://localhost:5173/#/opgg'
    : join(__dirname, '../renderer/index.html')
}

async function openOpggWindow(query?: OpggChampionQuery) {
  try {
    const opened = await focusOrCreateOpggWindow(query)
    return {
      success: true,
      data: { opened }
    }
  } catch (error) {
    log('WARN', `Failed to open OP.GG window: ${String(error)}`)
    return {
      success: false,
      error: String(error)
    }
  }
}

async function focusOrCreateOpggWindow(query?: OpggChampionQuery): Promise<boolean> {
  if (opggWindow && !opggWindow.isDestroyed()) {
    if (opggWindow.isMinimized()) {
      opggWindow.restore()
    }
    if (!opggWindow.isVisible()) {
      opggWindow.show()
    }
    opggWindow.focus()
    sendOpggInitialQuery(query)
    return false
  }

  await createOpggWindow(query)
  return true
}

async function createOpggWindow(query?: OpggChampionQuery) {
  const storedBounds = loadOpggWindowBounds()
  const initialBounds = storedBounds?.userMoved
    ? storedBounds.bounds
    : await resolveInitialOpggWindowBounds()
  let trackingUserBounds = false

  opggWindow = new BrowserWindow({
    width: initialBounds.width,
    height: initialBounds.height,
    x: initialBounds.x,
    y: initialBounds.y,
    minWidth: 720,
    minHeight: 560,
    show: false,
    frame: false,
    transparent: false,
    backgroundColor: '#111827',
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: join(__dirname, '../preload/preload.js'),
      webSecurity: true,
      spellcheck: false
    },
    icon: getMainIconPath(),
    title: OPGG_WINDOW_TITLE,
    titleBarStyle: 'hidden',
    thickFrame: true
  })

  const createdWindow = opggWindow
  const saveMovedBounds = () => {
    if (!trackingUserBounds || createdWindow.isDestroyed()) {
      return
    }
    saveOpggWindowBounds(createdWindow.getBounds(), true)
  }

  createdWindow.removeMenu()
  createdWindow.setTitle(OPGG_WINDOW_TITLE)
  createdWindow.on('page-title-updated', (event) => {
    event.preventDefault()
    createdWindow.setTitle(OPGG_WINDOW_TITLE)
  })
  createdWindow.on('closed', () => {
    if (opggWindow === createdWindow) {
      opggWindow = null
    }
  })
  createdWindow.on('move', saveMovedBounds)
  createdWindow.on('resize', saveMovedBounds)
  createdWindow.once('ready-to-show', () => {
    createdWindow.show()
    createdWindow.focus()
    setTimeout(() => {
      trackingUserBounds = true
    }, 300)
  })
  createdWindow.webContents.once('did-finish-load', () => {
    sendOpggInitialQuery(query)
  })
  createdWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http')) {
      void shell.openExternal(url)
    }
    return { action: 'deny' }
  })

  if (isDev) {
    void createdWindow.loadURL(getOpggWindowUrl())
  } else {
    void createdWindow.loadFile(getOpggWindowUrl(), { hash: '/opgg' })
  }
}

function sendOpggInitialQuery(query?: OpggChampionQuery) {
  if (!opggWindow || opggWindow.isDestroyed() || !query) {
    return
  }

  if (opggWindow.webContents.isLoadingMainFrame()) {
    opggWindow.webContents.once('did-finish-load', () => {
      opggWindow?.webContents.send('opgg:initialQuery', query)
    })
    return
  }

  opggWindow.webContents.send('opgg:initialQuery', query)
}

async function resolveInitialOpggWindowBounds(): Promise<Rectangle> {
  const defaultSize = { width: 980, height: 720 }
  const lcuBounds = await fetchLcuWindowBounds()
  if (lcuBounds) {
    return calculateAttachedWindowBounds(lcuBounds, defaultSize)
  }

  const mainBounds = mainWindow?.getBounds()
  if (mainBounds) {
    return calculateAttachedWindowBounds(mainBounds, defaultSize)
  }

  const workArea = screen.getPrimaryDisplay().workArea
  return clampBoundsToWorkArea({
    x: Math.round(workArea.x + (workArea.width - defaultSize.width) / 2),
    y: Math.round(workArea.y + (workArea.height - defaultSize.height) / 2),
    ...defaultSize
  }, workArea)
}

async function fetchLcuWindowBounds(): Promise<Rectangle | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/session/lcu-window-bounds`)
    if (!response.ok) {
      return null
    }

    const payload = unwrapApiResponse(await response.json())
    if (!isLcuWindowBoundsPayload(payload)) {
      return null
    }

    return {
      x: payload.x,
      y: payload.y,
      width: payload.width,
      height: payload.height
    }
  } catch {
    return null
  }
}

function isLcuWindowBoundsPayload(value: unknown): value is Required<LcuWindowBoundsPayload> {
  if (!isRecord(value) || value.found !== true) {
    return false
  }

  const x = value.x
  const y = value.y
  const width = value.width
  const height = value.height
  return (
    typeof x === 'number' &&
    typeof y === 'number' &&
    typeof width === 'number' &&
    typeof height === 'number' &&
    Number.isFinite(x) &&
    Number.isFinite(y) &&
    Number.isFinite(width) &&
    Number.isFinite(height) &&
    width > 0 &&
    height > 0
  )
}

function calculateAttachedWindowBounds(anchorBounds: Rectangle, size: { width: number; height: number }): Rectangle {
  const display = screen.getDisplayMatching(anchorBounds)
  const workArea = display.workArea
  const margin = 8
  const y = Math.round(anchorBounds.y + Math.max(0, (anchorBounds.height - size.height) / 2))
  const rightBounds = {
    x: anchorBounds.x + anchorBounds.width + margin,
    y,
    width: size.width,
    height: size.height
  }
  if (rightBounds.x + rightBounds.width <= workArea.x + workArea.width) {
    return clampBoundsToWorkArea(rightBounds, workArea)
  }

  const leftBounds = {
    x: anchorBounds.x - size.width - margin,
    y,
    width: size.width,
    height: size.height
  }
  if (leftBounds.x >= workArea.x) {
    return clampBoundsToWorkArea(leftBounds, workArea)
  }

  return clampBoundsToWorkArea(rightBounds, workArea)
}

function clampBoundsToWorkArea(bounds: Rectangle, workArea: Rectangle): Rectangle {
  const width = Math.min(bounds.width, workArea.width)
  const height = Math.min(bounds.height, workArea.height)
  return {
    width,
    height,
    x: Math.min(Math.max(bounds.x, workArea.x), workArea.x + workArea.width - width),
    y: Math.min(Math.max(bounds.y, workArea.y), workArea.y + workArea.height - height)
  }
}

function clearStartupTimers() {
  if (!startupFallbackTimer) {
    // Continue clearing the other startup timers below.
  } else {
    clearTimeout(startupFallbackTimer)
    startupFallbackTimer = null
  }

  if (startupCheckInterval) {
    clearInterval(startupCheckInterval)
    startupCheckInterval = null
  }

  if (noLcuTimeout) {
    clearTimeout(noLcuTimeout)
    noLcuTimeout = null
  }

  if (minimumSplashTimer) {
    clearTimeout(minimumSplashTimer)
    minimumSplashTimer = null
  }
}

function requestStartupExit(mode: StartupExitMode) {
  if (startupExitStarted) {
    return
  }

  if (mode === 'smooth') {
    const remainingVisibleTime = MIN_SPLASH_VISIBLE_MS - (Date.now() - startupStartedAt)
    if (remainingVisibleTime > 0) {
      if (!minimumSplashTimer) {
        minimumSplashTimer = setTimeout(() => {
          minimumSplashTimer = null
          requestStartupExit(mode)
        }, remainingVisibleTime)
      }
      return
    }
  }

  runStartupExit(mode)
}

function runStartupExit(mode: StartupExitMode) {
  if (startupExitStarted || !mainWindow || mainWindow.isDestroyed()) {
    return
  }

  startupExitStarted = true
  clearStartupTimers()

  const showMainWindowAfterSplash = () => {
    closeSplashWindow()
    if (!mainWindow || mainWindow.isDestroyed()) {
      return
    }

    mainWindow.show()
    mainWindow.focus()
  }

  if (splashWindow && !splashWindow.isDestroyed()) {
    const exitScript = mode === 'smooth'
      ? 'typeof window.finishWithLCU === "function" && window.finishWithLCU()'
      : 'typeof window.finishWithoutLCU === "function" && window.finishWithoutLCU()'

    void splashWindow.webContents
      .executeJavaScript(exitScript)
      .catch((error) => {
        log('WARN', `Failed to finish splash animation: ${String(error)}`)
      })

    setTimeout(showMainWindowAfterSplash, mode === 'smooth' ? 2500 : 1700)
    return
  }

  showMainWindowAfterSplash()
}

function startStartupReadinessChecks() {
  clearStartupTimers()
  startupStartedAt = Date.now()

  let lcuReady = false
  let dataReady = false
  let isChecking = false

  const checkReadiness = async () => {
    if (startupExitStarted || dataReady || isChecking) {
      return
    }

    isChecking = true
    try {
      lcuReady = await checkLcuReady()
      if (!lcuReady) {
        return
      }

      dataReady = await checkHomeDataReady()
      if (dataReady) {
        requestStartupExit('smooth')
      }
    } finally {
      isChecking = false
    }
  }

  startupCheckInterval = setInterval(() => {
    void checkReadiness()
  }, STARTUP_CHECK_INTERVAL_MS)

  void checkReadiness()

  noLcuTimeout = setTimeout(() => {
    if (!dataReady && !lcuReady) {
      requestStartupExit('quick')
    }
  }, NO_LCU_TIMEOUT_MS)

  startupFallbackTimer = setTimeout(() => {
    requestStartupExit('quick')
  }, STARTUP_FORCE_TIMEOUT_MS)
}

async function checkLcuReady() {
  const gameStatePayload = await fetchStartupJson(`${API_BASE_URL}/session/game-state`)
  const gameState = unwrapApiResponse(gameStatePayload)
  if (isConnectedPayload(gameState)) {
    return true
  }

  const connectedPayload = await fetchStartupJson(`${API_BASE_URL}/session/connected`)
  const connected = unwrapApiResponse(connectedPayload)
  if (connected === true || isConnectedPayload(connected)) {
    return true
  }

  return false
}

async function checkHomeDataReady() {
  const gameStatePayload = await fetchStartupJson(`${API_BASE_URL}/session/game-state`)
  const gameState = unwrapApiResponse(gameStatePayload)
  const gameStatePuuid = getSummonerPuuid(gameState)

  const summonerPayload = await fetchStartupJson(`${API_BASE_URL}/summoner/me`)
  const summoner = unwrapApiResponse(summonerPayload)
  const puuid = getSummonerPuuid(summoner) ?? gameStatePuuid

  if (!puuid) {
    return false
  }

  const [rankPayload, rankedWinRatesPayload, matchesReady] = await Promise.all([
    fetchStartupJson(`${API_BASE_URL}/summoner/rank/${encodeURIComponent(puuid)}`),
    fetchStartupJson(`${API_BASE_URL}/summoner/ranked-win-rates/${encodeURIComponent(puuid)}`),
    hasAnalyzableMatches(puuid)
  ])
  const rank = unwrapApiResponse(rankPayload)
  const rankedWinRates = unwrapApiResponse(rankedWinRatesPayload)

  return rank !== null && rankedWinRates !== null && matchesReady
}

async function hasAnalyzableMatches(puuid: string) {
  const matchesPayload = await fetchStartupJson(
    `${API_BASE_URL}/summoner/matches/${encodeURIComponent(puuid)}?begIndex=0&endIndex=9`
  )
  const matches = unwrapApiResponse(matchesPayload)

  return Array.isArray(matches) && matches.length > 0
}

async function fetchStartupJson(url: string): Promise<unknown> {
  try {
    const response = await fetch(url)
    if (!response.ok) {
      return null
    }

    return await response.json()
  } catch {
    return null
  }
}

function unwrapApiResponse(payload: unknown): unknown {
  if (!isRecord(payload) || !('data' in payload)) {
    return payload
  }

  return payload.data
}

function isConnectedPayload(payload: unknown): boolean {
  if (payload === true) {
    return true
  }

  if (!isRecord(payload)) {
    return false
  }

  return payload.connected === true
}

function getSummonerPuuid(payload: unknown): string | null {
  if (!isRecord(payload)) {
    return null
  }

  if (typeof payload.puuid === 'string' && payload.puuid.length > 0) {
    return payload.puuid
  }

  return getSummonerPuuid(payload.summoner)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
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
    clearStartupTimers()
    startupExitStarted = false
    mainWindow = null
  })

  mainWindow.on('resize', () => {
    saveWindowBounds()
  })

  mainWindow.on('move', () => {
    saveWindowBounds()
  })

  startStartupReadinessChecks()

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
    const spawnedBackendInstanceId = createBackendInstanceId()
    backendInstanceId = spawnedBackendInstanceId
    log('INFO', `Starting backend from ${exePath}`)

    backendProcess = spawn(exePath, [], {
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true,
      env: {
        ...process.env,
        RANKPEEK_BACKEND_INSTANCE_ID: spawnedBackendInstanceId
      }
    })
    backendShutdownCompleted = false

    const spawnedBackendProcess = backendProcess

    backendProcess.stdout?.on('data', (data) => {
      writeBackendOutput('stdout', data)
    })

    backendProcess.stderr?.on('data', (data) => {
      writeBackendOutput('stderr', data)
    })

    backendProcess.on('error', (error) => {
      log('ERROR', `Failed to start backend process: ${String(error)}`)
      reject(error)
    })

    backendProcess.on('exit', (code, signal) => {
      log('INFO', `Backend process exited: code=${String(code)}, signal=${String(signal)}`)
      if (backendProcess === spawnedBackendProcess) {
        backendProcess = null
        backendInstanceId = null
      }
    })

    void waitForBackend({
      expectedInstanceId: spawnedBackendInstanceId,
      log: (message) => log('INFO', message)
    }).then(resolve).catch((error) => {
      if (error instanceof BackendIdentityMismatchError) {
        log('ERROR', error.message)
      }
      reject(error)
    })
  })
}

function writeBackendOutput(streamName: 'stdout' | 'stderr', data: Buffer | string) {
  const text = data.toString()
  for (const line of text.split(/\r?\n/)) {
    if (line.trim().length === 0) {
      continue
    }

    log(streamName === 'stderr' ? 'ERROR' : 'INFO', `Backend ${streamName}: ${line}`)
  }
}

async function stopBackend(): Promise<void> {
  if (isDev) {
    log('INFO', 'Development mode: leaving manually started backend running')
    return
  }

  const processToStop = backendProcess
  if (!processToStop) {
    return
  }

  log('INFO', 'Requesting backend graceful shutdown')
  const shutdownRequested = await requestBackendShutdown(backendInstanceId)

  const exited = await waitForBackendExit(processToStop, BACKEND_GRACEFUL_EXIT_TIMEOUT_MS)
  if (exited) {
    log('INFO', 'Backend exited after graceful shutdown request')
    if (backendProcess === processToStop) {
      backendProcess = null
      backendInstanceId = null
    }
    return
  }

  log('WARN', shutdownRequested
    ? 'Backend did not exit before timeout; falling back to process kill'
    : 'Backend shutdown request was skipped because port 8080 belongs to another backend; killing spawned process'
  )
  processToStop.kill()
  await waitForBackendExit(processToStop, 2000)
  if (backendProcess === processToStop) {
    backendProcess = null
    backendInstanceId = null
  }
}

async function requestBackendShutdown(expectedInstanceId: string | null): Promise<boolean> {
  if (expectedInstanceId) {
    try {
      const identity = await fetchBackendIdentity()
      if (identity.instanceId !== expectedInstanceId) {
        log('WARN', 'Skipping backend graceful shutdown because port 8080 does not belong to the spawned backend')
        return false
      }
    } catch (error) {
      log('WARN', `Could not verify backend identity before shutdown request: ${String(error)}`)
      return false
    }
  }

  const controller = new AbortController()
  const timeout = setTimeout(() => {
    controller.abort()
  }, BACKEND_SHUTDOWN_REQUEST_TIMEOUT_MS)

  try {
    const response = await fetch(`${API_BASE_URL}/system/shutdown`, {
      method: 'POST',
      signal: controller.signal
    })
    log('INFO', `Backend shutdown request completed: status=${response.status}`)
    return true
  } catch (error) {
    log('WARN', `Backend shutdown request failed; will wait before fallback kill: ${String(error)}`)
    return false
  } finally {
    clearTimeout(timeout)
  }
}

function waitForBackendExit(processToWait: ChildProcess, timeoutMs: number): Promise<boolean> {
  if (processToWait.exitCode !== null || processToWait.signalCode !== null) {
    return Promise.resolve(true)
  }

  return new Promise((resolve) => {
    let settled = false
    const timeout = setTimeout(() => {
      if (settled) {
        return
      }
      settled = true
      processToWait.off('exit', handleExit)
      resolve(false)
    }, timeoutMs)

    const handleExit = () => {
      if (settled) {
        return
      }
      settled = true
      clearTimeout(timeout)
      resolve(true)
    }

    processToWait.once('exit', handleExit)
  })
}

function getIpcSenderWindow(event: IpcMainInvokeEvent) {
  return BrowserWindow.fromWebContents(event.sender) ?? mainWindow
}

ipcMain.handle('window:minimize', (event) => {
  getIpcSenderWindow(event)?.minimize()
})

ipcMain.handle('window:maximize', (event) => {
  const targetWindow = getIpcSenderWindow(event)
  if (targetWindow?.isMaximized()) {
    targetWindow.unmaximize()
    return
  }

  targetWindow?.maximize()
})

ipcMain.handle('window:close', (event) => {
  getIpcSenderWindow(event)?.close()
})

ipcMain.handle('opgg:openWindow', (_event, query?: OpggChampionQuery) => {
  return openOpggWindow(query)
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

ipcMain.handle('app:clearChromiumCache', async () => {
  try {
    return {
      success: true,
      data: await clearElectronCacheArtifacts()
    }
  } catch (error) {
    return {
      success: false,
      error: String(error)
    }
  }
})

app.whenReady().then(async () => {
  try {
    initLocalDatabase({
      userDataPath: app.getPath('userData'),
      logger: databaseLogger,
      runSmokeTest: process.env.RANKPEEK_DB_SMOKE_TEST === '1'
    })
    registerDatabaseIpcHandlers(ipcMain, getLocalDatabase, databaseLogger, {
      exportAiMemory: saveAiMemoryExport,
      onStorageMutation: scheduleLocalStorageRetention
    })
    createSplashWindow()
    createTray()
    await startBackend()
    createWindow()
  } catch (error) {
    log('ERROR', `Failed to start application: ${String(error)}`)
    console.error('Failed to start application:', error)
    closeSplashWindow()
    closeLocalDatabase()
    await stopBackend()
    app.quit()
  }

  app.on('activate', () => {
    showMainWindow()
  })
})

app.on('window-all-closed', () => {
  if (isQuitting && process.platform !== 'darwin') {
    app.quit()
  }
})

app.on('before-quit', (event) => {
  isQuitting = true
  clearStartupTimers()
  closeSplashWindow()
  appTray?.destroy()
  appTray = null
  closeLocalDatabase()

  if (!isDev && backendProcess && !backendShutdownCompleted) {
    event.preventDefault()
    if (backendShutdownInProgress) {
      return
    }

    backendShutdownInProgress = true
    void stopBackend().finally(() => {
      backendShutdownCompleted = true
      backendShutdownInProgress = false
      app.quit()
    })
  }
})

process.on('uncaughtException', (error) => {
  console.error('Uncaught Exception:', error)
})
