import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('main process owns a singleton OP.GG window and sender-scoped window controls', () => {
  const source = readFileSync(new URL('./main.ts', import.meta.url), 'utf8')

  assert.match(source, /let opggWindow: BrowserWindow \| null = null/)
  assert.match(source, /const opggBoundsFile = join\(app\.getPath\('userData'\), 'opgg-window-bounds\.json'\)/)
  assert.match(source, /ipcMain\.handle\('opgg:openWindow'[\s\S]*openOpggWindow/)
  assert.match(source, /function getIpcSenderWindow\(event: (Electron\.)?IpcMainInvokeEvent\)/)
  assert.match(source, /BrowserWindow\.fromWebContents\(event\.sender\)/)
  assert.match(source, /ipcMain\.handle\('window:minimize', \(event\) => \{[\s\S]*getIpcSenderWindow\(event\)\?\.minimize\(\)/)
  assert.match(source, /ipcMain\.handle\('window:close', \(event\) => \{[\s\S]*getIpcSenderWindow\(event\)\?\.close\(\)/)
})

test('OP.GG window loads the standalone route and positions near the LCU window before falling back', () => {
  const source = readFileSync(new URL('./main.ts', import.meta.url), 'utf8')

  assert.match(source, /function createOpggWindow/)
  assert.match(source, /function focusOrCreateOpggWindow/)
  assert.match(source, /#\/opgg/)
  assert.match(source, /opggWindow\.webContents\.send\('opgg:initialQuery'/)
  assert.match(source, /async function fetchLcuWindowBounds/)
  assert.match(source, /\/session\/lcu-window-bounds/)
  assert.match(source, /screen\.getDisplayMatching/)
  assert.match(source, /function calculateAttachedWindowBounds/)
  assert.match(source, /function clampBoundsToWorkArea/)
})
