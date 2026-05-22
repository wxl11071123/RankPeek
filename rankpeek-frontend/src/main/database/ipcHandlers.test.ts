import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createLocalDatabase } from './index.ts'
import { registerDatabaseIpcHandlers } from './ipcHandlers.ts'

type Handler = (_event: unknown, payload?: unknown) => unknown | Promise<unknown>

class FakeIpcMain {
  readonly handlers = new Map<string, Handler>()

  handle(channel: string, handler: Handler) {
    this.handlers.set(channel, handler)
  }

  invoke(channel: string, payload?: unknown) {
    const handler = this.handlers.get(channel)
    assert.ok(handler, `Expected handler for ${channel}`)
    return handler({}, payload)
  }
}

const silentLogger = {
  info: () => undefined,
  warn: () => undefined,
  error: () => undefined
}

function createRegisteredHandlers(options?: Parameters<typeof registerDatabaseIpcHandlers>[3]) {
  const directory = mkdtempSync(join(tmpdir(), 'rankpeek-ipc-db-'))
  const database = createLocalDatabase({ databasePath: join(directory, 'rankpeek.db'), logger: silentLogger })
  const ipcMain = new FakeIpcMain()
  registerDatabaseIpcHandlers(ipcMain, () => database, silentLogger, options)

  return {
    ipcMain,
    database,
    cleanup: () => {
      database.close()
      rmSync(directory, { recursive: true, force: true })
    }
  }
}

test('database IPC handlers return success envelopes for account writes and reads', async () => {
  const { ipcMain, cleanup } = createRegisteredHandlers()

  try {
    const invalid = await ipcMain.invoke('db:account:upsert', { region: 'HN1' })
    assert.deepEqual(invalid, {
      success: false,
      error: 'Invalid account payload'
    })

    const upserted = await ipcMain.invoke('db:account:upsert', {
      region: 'HN1',
      puuid: 'test-puuid',
      gameName: 'RankPeekTest',
      tagLine: '0001',
      displayName: 'RankPeekTest#0001'
    })
    assert.equal(upserted.success, true)

    const listed = await ipcMain.invoke('db:account:list')
    assert.equal(listed.success, true)
    assert.equal(listed.data.length, 1)
    assert.equal(listed.data[0].puuid, 'test-puuid')
  } finally {
    cleanup()
  }
})

test('database IPC handlers support match record and AI analysis smoke operations', async () => {
  const { ipcMain, cleanup } = createRegisteredHandlers()

  try {
    const matchWrite = await ipcMain.invoke('db:match:upsertRecords', [
      {
        region: 'HN1',
        matchId: 'HN1_1',
        accountPuuid: 'test-puuid',
        gameCreation: 200,
        rawSummaryJson: { matchId: 'HN1_1' }
      }
    ])
    assert.equal(matchWrite.success, true)
    assert.equal(matchWrite.data.length, 1)

    const matches = await ipcMain.invoke('db:match:listByAccount', {
      accountPuuid: 'test-puuid',
      options: { limit: 5 }
    })
    assert.equal(matches.success, true)
    assert.equal(matches.data[0].matchId, 'HN1_1')

    const analysisWrite = await ipcMain.invoke('db:ai:saveResult', {
      accountPuuid: 'test-puuid',
      matchId: 'HN1_1',
      analysisType: 'match_summary',
      inputHash: 'hash-1',
      outputJson: { summary: 'Keep farming.' }
    })
    assert.equal(analysisWrite.success, true)

    const analysis = await ipcMain.invoke('db:ai:findByInputHash', 'hash-1')
    assert.equal(analysis.success, true)
    assert.equal(analysis.data.inputHash, 'hash-1')
  } finally {
    cleanup()
  }
})

test('database IPC handlers expose storage retention and AI memory stats/export without delete APIs', async () => {
  const { ipcMain, database, cleanup } = createRegisteredHandlers({
    exportAiMemory: async ({ payload }) => ({
      filePath: 'C:/RankPeek/ai-memory.json',
      exportedCount: payload.records.length
    })
  })

  try {
    database.aiAnalyses.saveAnalysisResult({
      accountPuuid: 'test-puuid',
      matchId: 'HN1_1',
      analysisType: 'match_summary',
      inputHash: 'hash-1',
      outputJson: { summary: 'Keep farming.' }
    })

    const stats = await ipcMain.invoke('db:ai:getMemoryStats', 'test-puuid')
    assert.equal(stats.success, true)
    assert.equal(stats.data.totalCount, 1)

    const exported = await ipcMain.invoke('db:ai:exportMemory', 'test-puuid')
    assert.equal(exported.success, true)
    assert.equal(exported.data.filePath, 'C:/RankPeek/ai-memory.json')
    assert.equal(exported.data.exportedCount, 1)

    const retention = await ipcMain.invoke('db:storage:runRetention')
    assert.equal(retention.success, true)

    const health = await ipcMain.invoke('db:storage:getHealthStats')
    assert.equal(health.success, true)
    assert.equal(health.data.aiAnalysisCount, 0)
    assert.equal(health.data.orphanSingleMatchAiCount, 0)

    assert.equal(ipcMain.handlers.has('db:ai:deleteMemory'), false)
    assert.equal(ipcMain.handlers.has('db:ai:clearMemory'), false)
  } finally {
    cleanup()
  }
})
