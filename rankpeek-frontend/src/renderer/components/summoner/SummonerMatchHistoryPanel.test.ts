import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('match history list hydrates loadout fields from cached and opened game details', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /hydrateMatchHistoryFromLocalDetailIfAvailable\(match, puuid\) \?\? match/)
  assert.match(source, /function applyGameDetailToVisibleMatchHistory\(match: MatchHistory, detail: GameDetail\)/)
  assert.match(source, /mergeGameDetailIntoMatchHistory\(existingMatch, detail\)/)
  assert.match(source, /selectedMatchHistory\.value = mergedMatch/)
  assert.match(source, /applyGameDetailToVisibleMatchHistory\(match, cachedDetail\)/)
  assert.match(source, /applyGameDetailToVisibleMatchHistory\(match, gameDetail\)/)
})
