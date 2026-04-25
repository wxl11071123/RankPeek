import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('shows analysis metrics and runes on the right side of detail rows', () => {
  const source = readFileSync(new URL('./MatchDetailModal.vue', import.meta.url), 'utf8')

  assert.match(source, /class="player-analysis"/)
  assert.match(source, /getDamageConversionText/)
  assert.match(source, /getGoldDiff15Text/)
  assert.match(source, /getVisionControlText/)
  assert.match(source, /getPerkIds/)
  assert.match(source, /getPerkUrl/)
  assert.match(source, /伤害转化/)
  assert.match(source, /15分经济/)
  assert.match(source, /视野控制/)
  assert.match(source, /天赋/)
  assert.doesNotMatch(source, /player\.stats\?\.goldEarned\) }} {{ t\('common\.gold'\) }}/)
})
