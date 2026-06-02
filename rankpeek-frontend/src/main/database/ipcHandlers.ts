import type {
  DatabaseIpcResult,
  LocalDatabase,
  LocalDatabaseLogger,
  MatchDetailInput,
  MatchRecordInput,
  MatchRecordListOptions,
  SummonerAccountInput
} from './types.ts'

type IpcHandler = (_event: unknown, payload?: unknown) => unknown | Promise<unknown>

export interface DatabaseIpcHandlerOptions {
  onStorageMutation?: () => void
}

export interface IpcMainLike {
  handle(channel: string, handler: IpcHandler): void
}

export function registerDatabaseIpcHandlers(
  ipcMain: IpcMainLike,
  getDatabase: () => LocalDatabase,
  logger: LocalDatabaseLogger,
  options: DatabaseIpcHandlerOptions = {}
) {
  ipcMain.handle('db:account:upsert', (_event, payload) => {
    if (!isAccountInput(payload)) {
      return failure('Invalid account payload')
    }

    return runDatabaseHandler('db:account:upsert', logger, () => (
      getDatabase().accounts.upsertAccount(payload)
    ))
  })

  ipcMain.handle('db:account:list', () => (
    runDatabaseHandler('db:account:list', logger, () => getDatabase().accounts.listAccounts())
  ))

  ipcMain.handle('db:account:getLastSelected', () => (
    runDatabaseHandler('db:account:getLastSelected', logger, () => getDatabase().accounts.getLastSelectedAccount())
  ))

  ipcMain.handle('db:account:setLastSelected', (_event, payload) => {
    if (!isRegionPuuidPayload(payload)) {
      return failure('Invalid account selection payload')
    }

    return runDatabaseHandler('db:account:setLastSelected', logger, () => (
      getDatabase().accounts.setLastSelectedAccount(payload.region, payload.puuid)
    ))
  })

  ipcMain.handle('db:match:upsertRecords', (_event, payload) => {
    if (!Array.isArray(payload) || !payload.every(isMatchRecordInput)) {
      return failure('Invalid match records payload')
    }

    return runDatabaseHandler('db:match:upsertRecords', logger, () => {
      const records = getDatabase().matches.upsertMatchRecords(payload)
      options.onStorageMutation?.()
      return records
    })
  })

  ipcMain.handle('db:match:listByAccount', (_event, payload) => {
    if (!isMatchListPayload(payload)) {
      return failure('Invalid match list payload')
    }

    return runDatabaseHandler('db:match:listByAccount', logger, () => (
      getDatabase().matches.listMatchRecordsByAccount(payload.accountPuuid, payload.options)
    ))
  })

  ipcMain.handle('db:match:getDetail', (_event, payload) => {
    if (!isRegionMatchPayload(payload)) {
      return failure('Invalid match detail lookup payload')
    }

    return runDatabaseHandler('db:match:getDetail', logger, () => (
      getDatabase().matches.getMatchDetail(payload.region, payload.matchId)
    ))
  })

  ipcMain.handle('db:match:upsertDetail', (_event, payload) => {
    if (!isMatchDetailInput(payload)) {
      return failure('Invalid match detail payload')
    }

    return runDatabaseHandler('db:match:upsertDetail', logger, () => {
      const detail = getDatabase().matches.upsertMatchDetail(payload)
      options.onStorageMutation?.()
      return detail
    })
  })

  ipcMain.handle('db:storage:runRetention', () => (
    runDatabaseHandler('db:storage:runRetention', logger, () => getDatabase().runStorageRetention())
  ))

  ipcMain.handle('db:storage:getHealthStats', () => (
    runDatabaseHandler('db:storage:getHealthStats', logger, () => getDatabase().getStorageHealthStats())
  ))
}

function runDatabaseHandler<T>(
  channel: string,
  logger: LocalDatabaseLogger,
  operation: () => T
): DatabaseIpcResult<T> {
  try {
    return {
      success: true,
      data: operation()
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    logger.error(`Database IPC handler failed (${channel}): ${message}`)
    return failure(message)
  }
}

function failure(error: string): DatabaseIpcResult<never> {
  return {
    success: false,
    error
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function hasOwn(value: Record<string, unknown>, key: string) {
  return Object.prototype.hasOwnProperty.call(value, key)
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function isOptionalString(value: unknown): value is string | null | undefined {
  return value === undefined || value === null || typeof value === 'string'
}

function isOptionalNumber(value: unknown): value is number | null | undefined {
  return value === undefined || value === null || (typeof value === 'number' && Number.isFinite(value))
}

function isOptionalBooleanOrNumber(value: unknown): value is boolean | number | null | undefined {
  return value === undefined
    || value === null
    || typeof value === 'boolean'
    || (typeof value === 'number' && Number.isFinite(value))
}

function isAccountInput(value: unknown): value is SummonerAccountInput {
  return isRecord(value)
    && isNonEmptyString(value.region)
    && isNonEmptyString(value.puuid)
    && isOptionalString(value.gameName)
    && isOptionalString(value.tagLine)
    && isOptionalString(value.summonerName)
    && isOptionalString(value.displayName)
    && isOptionalNumber(value.profileIconId)
    && isOptionalNumber(value.summonerLevel)
    && isOptionalBooleanOrNumber(value.lastSelected)
}

function isRegionPuuidPayload(value: unknown): value is { region: string; puuid: string } {
  return isRecord(value) && isNonEmptyString(value.region) && isNonEmptyString(value.puuid)
}

function isRegionMatchPayload(value: unknown): value is { region: string; matchId: string } {
  return isRecord(value) && isNonEmptyString(value.region) && isNonEmptyString(value.matchId)
}

function isMatchRecordInput(value: unknown): value is MatchRecordInput {
  return isRecord(value)
    && isNonEmptyString(value.region)
    && isNonEmptyString(value.matchId)
    && isNonEmptyString(value.accountPuuid)
    && hasOwn(value, 'rawSummaryJson')
    && isOptionalNumber(value.queueId)
    && isOptionalString(value.queueName)
    && isOptionalString(value.gameMode)
    && isOptionalString(value.gameVersion)
    && isOptionalNumber(value.gameCreation)
    && isOptionalNumber(value.gameDuration)
    && isOptionalNumber(value.championId)
    && isOptionalNumber(value.spell1Id)
    && isOptionalNumber(value.spell2Id)
    && isOptionalBooleanOrNumber(value.win)
    && isOptionalNumber(value.kills)
    && isOptionalNumber(value.deaths)
    && isOptionalNumber(value.assists)
    && isOptionalNumber(value.goldEarned)
    && isOptionalNumber(value.totalDamageDealtToChampions)
    && isOptionalNumber(value.doubleKills)
    && isOptionalNumber(value.tripleKills)
    && isOptionalNumber(value.quadraKills)
    && isOptionalNumber(value.pentaKills)
    && isOptionalNumber(value.largestKillingSpree)
    && isOptionalNumber(value.legendaryCount)
    && isOptionalNumber(value.perk0)
    && isOptionalNumber(value.playerAugment1)
    && isOptionalNumber(value.playerAugment2)
    && isOptionalNumber(value.playerAugment3)
    && isOptionalNumber(value.playerAugment4)
    && isOptionalString(value.lane)
    && isOptionalString(value.role)
    && isOptionalString(value.fetchedAt)
    && isOptionalString(value.updatedAt)
}

function isMatchRecordListOptions(value: unknown): value is MatchRecordListOptions {
  return value === undefined || (
    isRecord(value)
    && isOptionalNumber(value.limit)
    && isOptionalNumber(value.offset)
    && isOptionalNumber(value.queueId)
    && isOptionalNumber(value.championId)
  )
}

function isMatchListPayload(
  value: unknown
): value is { accountPuuid: string; options?: MatchRecordListOptions } {
  return isRecord(value)
    && isNonEmptyString(value.accountPuuid)
    && isMatchRecordListOptions(value.options)
}

function isMatchDetailInput(value: unknown): value is MatchDetailInput {
  return isRecord(value)
    && isNonEmptyString(value.region)
    && isNonEmptyString(value.matchId)
    && hasOwn(value, 'rawDetailJson')
    && (value.normalizedDetailJson === undefined || hasOwn(value, 'normalizedDetailJson'))
    && isOptionalString(value.source)
    && isOptionalNumber(value.schemaVersion)
    && isOptionalString(value.fetchedAt)
    && isOptionalString(value.updatedAt)
}

