import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('match-history API methods accept forceRefresh options and send explicit booleans', () => {
  const source = readFileSync(new URL('./httpClient.ts', import.meta.url), 'utf8')

  assert.match(
    source,
    /async getMatchHistory\(\s*puuid: string,\s*begIndex = 0,\s*endIndex = 9,\s*options: \{ forceRefresh\?: boolean \} = \{\}/
  )
  assert.match(source, /forceRefresh: options\.forceRefresh === true/)
  assert.match(source, /forceRefresh\?: boolean/)
  assert.match(source, /async getFilteredMatchHistory\(/)
})

test('cache diagnostics API methods use status and scoped clear endpoints', () => {
  const source = readFileSync(new URL('./httpClient.ts', import.meta.url), 'utf8')
  const types = readFileSync(new URL('../types/api.ts', import.meta.url), 'utf8')

  assert.match(types, /export interface CacheStatus \{[\s\S]*enabled: boolean/)
  assert.match(types, /databaseSizeBytes: number/)
  assert.match(types, /summonerCount: number/)
  assert.match(types, /rankCount: number/)
  assert.match(types, /matchCount: number/)
  assert.match(types, /gameDetailCount: number/)
  assert.match(types, /participantCount: number/)
  assert.match(types, /trackedPlayerCount: number/)
  assert.match(types, /latestMatchCreation: number \| null/)
  assert.match(types, /export interface CacheClearResult \{[\s\S]*cleared: boolean/)
  assert.match(types, /scope: CacheClearScope/)
  assert.match(types, /deletedRows: number/)
  assert.match(types, /export type CacheClearScope = 'all' \| 'memory' \| 'localDb'/)

  assert.match(source, /CacheClearResult/)
  assert.match(source, /CacheClearScope/)
  assert.match(source, /CacheStatus/)
  assert.match(source, /async getCacheStatus\(\): Promise<CacheStatus>/)
  assert.match(source, /return this\.get<CacheStatus>\('\/cache\/status'\)/)
  assert.match(
    source,
    /async clearCache\(scope: CacheClearScope, confirm = true\): Promise<CacheClearResult>/
  )
  assert.match(source, /return this\.post<CacheClearResult>\('\/cache\/clear', undefined, \{ scope, confirm \}\)/)
})
