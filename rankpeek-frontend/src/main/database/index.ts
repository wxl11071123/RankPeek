import { join } from 'path'
import { createDatabaseConnection } from './connection.ts'
import { registerDatabaseIpcHandlers } from './ipcHandlers.ts'
import { runMigrations } from './migrations.ts'
import { createAccountRepository } from './repositories/accountRepository.ts'
import { createMatchRepository } from './repositories/matchRepository.ts'
import {
  getStorageHealthStats,
  runStorageRetention
} from './repositories/storageMaintenanceRepository.ts'
import type { CreateLocalDatabaseOptions, InitLocalDatabaseOptions, LocalDatabase } from './types.ts'

let localDatabase: LocalDatabase | null = null

export { registerDatabaseIpcHandlers }
export type * from './types.ts'

export function createLocalDatabase({ databasePath, logger }: CreateLocalDatabaseOptions): LocalDatabase {
  const connection = createDatabaseConnection(databasePath, logger)

  try {
    runMigrations(connection, logger)
  } catch (error) {
    connection.close()
    throw error
  }

  return {
    databasePath,
    connection,
    accounts: createAccountRepository(connection),
    matches: createMatchRepository(connection),
    runStorageRetention() {
      return runStorageRetention(connection)
    },
    getStorageHealthStats() {
      return getStorageHealthStats(connection, databasePath)
    },
    close() {
      connection.close()
    }
  }
}

export function initLocalDatabase(options: InitLocalDatabaseOptions): LocalDatabase {
  const databasePath = join(options.userDataPath, 'rankpeek.db')
  localDatabase = createLocalDatabase({
    databasePath,
    logger: options.logger
  })

  if (options.runSmokeTest) {
    runLocalDatabaseSmokeTest(localDatabase, options.logger)
  }

  try {
    const result = localDatabase.runStorageRetention()
    if (result.matchRecordsDeleted > 0 || result.matchDetailsDeleted > 0) {
      options.logger.info(
        `Local database retention applied at startup: matchRecordsDeleted=${result.matchRecordsDeleted}, `
          + `matchDetailsDeleted=${result.matchDetailsDeleted}`
      )
    }
  } catch (error) {
    options.logger.warn(`Local database startup retention failed: ${String(error)}`)
  }

  return localDatabase
}

export function getLocalDatabase(): LocalDatabase {
  if (!localDatabase) {
    throw new Error('Local database has not been initialized')
  }

  return localDatabase
}

export function closeLocalDatabase() {
  if (!localDatabase) {
    return
  }

  localDatabase.close()
  localDatabase = null
}

export function runLocalDatabaseSmokeTest(database: LocalDatabase, logger: InitLocalDatabaseOptions['logger']) {
  logger.info('Starting local database smoke test')
  const account = database.accounts.upsertAccount({
    region: 'HN1',
    puuid: 'test-puuid',
    gameName: 'RankPeekTest',
    tagLine: '0001',
    displayName: 'RankPeekTest#0001'
  })
  database.accounts.setLastSelectedAccount(account.region, account.puuid)
  database.matches.upsertMatchRecord({
    region: account.region,
    matchId: 'HN1_RANKPEEK_SMOKE',
    accountPuuid: account.puuid,
    queueId: 420,
    queueName: 'Ranked Solo',
    gameCreation: Date.now(),
    rawSummaryJson: {
      smoke: true,
      source: 'rankpeek-local-database'
    }
  })

  const accounts = database.accounts.listAccounts()
  const matches = database.matches.listMatchRecordsByAccount(account.puuid, { limit: 1 })

  if (!accounts.some((candidate) => candidate.puuid === account.puuid) || matches.length === 0) {
    throw new Error('Local database smoke test failed')
  }

  logger.info('Finished local database smoke test')
}
