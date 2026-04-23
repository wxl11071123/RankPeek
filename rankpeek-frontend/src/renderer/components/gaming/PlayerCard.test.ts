import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('shows all user tags instead of collapsing them into a +N chip', () => {
  const source = readFileSync(new URL('./PlayerCard.vue', import.meta.url), 'utf8')

  assert.match(source, /v-if="!recordStatusMeta && userTags\.length"/)
  assert.match(source, /v-for="tag in userTags"/)
  assert.doesNotMatch(source, /INLINE_TAG_LIMIT/)
  assert.doesNotMatch(source, /hiddenUserTagCount/)
  assert.doesNotMatch(source, /more-chip/)
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
