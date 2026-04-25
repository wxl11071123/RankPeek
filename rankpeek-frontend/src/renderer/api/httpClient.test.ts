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
