import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('pagination next button is driven by visible page capacity', () => {
  const source = readFileSync(new URL('./MatchHistoryView.vue', import.meta.url), 'utf8')

  assert.match(source, /const hasNextPage = computed\(\(\) =>[\s\S]*!loading\.value[\s\S]*matchHistory\.value\.length === pageSize[\s\S]*currentPage\.value < totalPages\.value/)
  assert.match(source, /:disabled="!hasNextPage"/)
})

test('nextPage increments currentPage and reloads match history only when another page is available', () => {
  const source = readFileSync(new URL('./MatchHistoryView.vue', import.meta.url), 'utf8')
  const nextPageFunction = source.match(/async function nextPage\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(nextPageFunction, /if \(!hasNextPage\.value\)/)
  assert.match(nextPageFunction, /currentPage\.value \+= 1/)
  assert.match(nextPageFunction, /await loadMatchHistory\(\)/)
})
