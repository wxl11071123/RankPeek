import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('match history card renders compact spell, rune, augment, and performance tag surfaces', () => {
  const source = readFileSync(new URL('./MatchHistoryCard.vue', import.meta.url), 'utf8')
  const apiTypes = readFileSync(new URL('../../types/api.ts', import.meta.url), 'utf8')

  assert.match(source, /getSummonerSpellIconUrl/)
  assert.match(source, /getPerkIconUrl/)
  assert.match(source, /getAugmentIconUrl/)
  assert.match(source, /getMatchPerformanceTags/)
  assert.match(source, /const currentSpellSlots = computed/)
  assert.match(source, /spell1Id/)
  assert.match(source, /spell2Id/)
  assert.match(source, /const currentPerkSlots = computed\(\(\) => getPerkSlots\(currentPlayer\.value, 1\)\)/)
  assert.match(source, /perk0/)
  assert.match(source, /const currentAugmentSlots = computed\(\(\) => getAugmentSlots\(currentPlayer\.value, 1\)\)/)
  assert.match(source, /playerAugment1/)
  assert.match(source, /participant\?\.stats/)
  assert.match(source, /class="loadout-stack"/)
  assert.match(source, /v-for="slot in currentSpellSlots"/)
  assert.match(source, /v-for="slot in currentPerkSlots"/)
  assert.match(source, /v-for="slot in currentAugmentSlots"/)
  assert.match(source, /class="performance-tags"/)
  assert.match(source, /v-for="tag in performanceTags"/)
  assert.match(source, /\{\{ tag\.label \}\}/)
  assert.match(source, /@error="markAssetLoadFailed"/)
  assert.match(apiTypes, /^\s*playerAugment1\?: number/m)
})
