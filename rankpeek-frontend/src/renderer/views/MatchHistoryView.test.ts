import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('pagination next button is driven by reachedEnd state instead of visible page capacity', () => {
  const source = readFileSync(new URL('./MatchHistoryView.vue', import.meta.url), 'utf8')

  assert.match(source, /const reachedEnd = ref\(false\)/)
  assert.match(source, /const hasNextPage = computed\(\(\) =>[\s\S]*!loading\.value[\s\S]*!reachedEnd\.value[\s\S]*currentPage\.value < totalPages\.value/)
  assert.match(source, /:disabled="!hasNextPage"/)
})

test('nextPage rolls back the page when loading the next slice fails', () => {
  const source = readFileSync(new URL('./MatchHistoryView.vue', import.meta.url), 'utf8')
  const nextPageFunction = source.match(/async function nextPage\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(nextPageFunction, /if \(!hasNextPage\.value\)/)
  assert.match(nextPageFunction, /const previousPage = currentPage\.value/)
  assert.match(nextPageFunction, /currentPage\.value \+= 1/)
  assert.match(nextPageFunction, /try \{[\s\S]*await loadMatchHistory\(\{ throwOnError: true \}\)[\s\S]*\} catch \(err\) \{[\s\S]*currentPage\.value = previousPage[\s\S]*reachedEnd\.value = false/)
  assert.doesNotMatch(nextPageFunction, /throw err/)
})

test('account changes and filters reset reachedEnd before loading the first page', () => {
  const source = readFileSync(new URL('./MatchHistoryView.vue', import.meta.url), 'utf8')
  const applyDefaultFiltersFunction = source.match(/function applyDefaultFilters\(\) \{[\s\S]*?\n\}/)?.[0] || ''
  const filterFunction = source.match(/async function handleFilterChange\(\) \{[\s\S]*?\n\}/)?.[0] || ''
  const watcher = source.match(/watch\([\s\S]*?currentSummoner\.value\?\.puuid[\s\S]*?\{ immediate: true \}/)?.[0] || ''

  assert.match(applyDefaultFiltersFunction, /reachedEnd\.value = false/)
  assert.match(filterFunction, /reachedEnd\.value = false[\s\S]*currentPage\.value = 1/)
  assert.match(watcher, /applyDefaultFilters\(\)/)
})

test('short pages mark the end and previous page clears the end marker', () => {
  const source = readFileSync(new URL('./MatchHistoryView.vue', import.meta.url), 'utf8')
  const loadFunction = source.match(/async function loadMatchHistory\(options\?: \{ throwOnError\?: boolean \}\) \{[\s\S]*?\n\}/)?.[0] || ''
  const prevPageFunction = source.match(/async function prevPage\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(loadFunction, /reachedEnd\.value = matches\.length < pageSize \|\| currentPage\.value >= totalPages\.value/)
  assert.match(prevPageFunction, /currentPage\.value -= 1[\s\S]*reachedEnd\.value = false[\s\S]*await loadMatchHistory\(\)/)
})
