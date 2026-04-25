import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('shows the Riot ID on one line and packs tags beside the rank before folding extras', () => {
  const source = readFileSync(new URL('./PlayerCard.vue', import.meta.url), 'utf8')

  assert.doesNotMatch(source, /const INLINE_TAG_LIMIT = 2/)
  assert.match(source, /ref="tagContainerRef"/)
  assert.match(source, /ref="tagMeasureRef"/)
  assert.match(source, /function updateVisibleTagCount/)
  assert.match(source, /new ResizeObserver/)
  assert.match(source, /data-tag-measure/)
  assert.match(source, /data-overflow-measure/)
  assert.match(source, /visibleUserTags/)
  assert.match(source, /hiddenUserTags/)
  assert.match(source, /hiddenUserTagCount/)
  assert.match(source, /class="more-chip"/)
  assert.match(source, /\.name-tags\s*{[\s\S]*flex-wrap:\s*nowrap;/)
  assert.match(source, /class="meta-row"[\s\S]*class="tier-row"[\s\S]*class="name-tags"/)
  assert.match(source, /{{ sessionSummoner\.summoner\.gameName }}#{{ sessionSummoner\.summoner\.tagLine }}/)
})

test('formats gaming rank labels with Chinese tier names', () => {
  const source = readFileSync(new URL('./PlayerCard.vue', import.meta.url), 'utf8')

  assert.match(source, /PLATINUM:\s*'铂金'/)
  assert.match(source, /DIAMOND:\s*'钻石'/)
  assert.match(source, /MASTER:\s*'超凡大师'/)
  assert.match(source, /GRANDMASTER:\s*'傲世宗师'/)
  assert.match(source, /CHALLENGER:\s*'最强王者'/)
  assert.match(source, /II:\s*'二'/)
  assert.match(source, /return `\$\{tierLabel\} \$\{divisionLabel\} \$\{queueInfo\.leaguePoints\} LP`/)
})
