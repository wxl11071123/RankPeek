import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('champion search aliases cover common Chinese nicknames', () => {
  const source = readFileSync(new URL('./championSearchAliases.ts', import.meta.url), 'utf8')

  assert.match(source, /CHAMPION_SEARCH_ALIASES_BY_ID/)
  assert.match(source, /106:\s*\[[^\]]*'狗熊'/)
  assert.match(source, /127:\s*\[[^\]]*'冰女'/)
  assert.match(source, /89:\s*\[[^\]]*'日女'/)
  assert.match(source, /91:\s*\[[^\]]*'男刀'/)
  assert.match(source, /39:\s*\[[^\]]*'女刀'/)
  assert.match(source, /championOptionMatchesSearch/)
})
