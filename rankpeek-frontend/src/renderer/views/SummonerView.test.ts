import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('uses the tuned match-history roster layout for lookup results', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')

  assert.match(source, /class="lookup-account-strip"/)
  assert.match(source, /class="lookup-account-profile"/)
  assert.match(source, /class="lookup-filter-bar"/)
  assert.match(source, /import MatchRosterCompact/)
  assert.match(source, /class="match-card-main"/)
  assert.match(source, /class="match-outcome"/)
  assert.match(source, /class="champion-pill"/)
  assert.match(source, /class="roster-grid"/)
  assert.match(source, /class="roster-column"/)
  assert.match(source, /<MatchRosterCompact/)
  assert.match(source, /function formatRankText/)
  assert.doesNotMatch(source, /class="overview-embed"/)
  assert.doesNotMatch(source, /class="stat-strip"/)
  assert.doesNotMatch(source, /class="match-metrics"/)
  assert.doesNotMatch(source, /class="team-player-chip"/)
})

test('refresh button force-refreshes only the visible summoner match history', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')

  assert.match(source, /@click="refreshCurrentSummoner"/)
  assert.match(source, /async function refreshCurrentSummoner\(\)/)
  assert.match(source, /await loadMatchHistory\(\{ forceRefresh: true \}\)/)
  assert.match(source, /async function loadMatchHistory\(options\?: \{ forceRefresh\?: boolean; throwOnError\?: boolean \}\)/)
  assert.match(source, /forceRefresh: options\?\.forceRefresh === true/)
  assert.match(source, /await loadMatchHistory\(\)/)
})

test('manual refresh loads fresh matches before recalculating tags and win rates', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')
  const refreshFunction = source.match(/async function refreshCurrentSummoner\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  const forceRefreshIndex = refreshFunction.indexOf('await loadMatchHistory({ forceRefresh: true })')
  const rankIndex = refreshFunction.indexOf('apiClient.getRank(searchResult.value.puuid)')
  const tagIndex = refreshFunction.indexOf('apiClient.getUserTagByPuuid')
  const winRateIndex = refreshFunction.indexOf('apiClient.getRankedWinRates')

  assert.ok(forceRefreshIndex >= 0)
  assert.ok(rankIndex > forceRefreshIndex)
  assert.ok(tagIndex > forceRefreshIndex)
  assert.ok(winRateIndex > forceRefreshIndex)
})

test('pagination next button is driven by reachedEnd state instead of visible page capacity', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')

  assert.match(source, /const reachedEnd = ref\(false\)/)
  assert.match(source, /const hasNextPage = computed\(\(\) =>[\s\S]*!loading\.value[\s\S]*!reachedEnd\.value[\s\S]*currentPage\.value < totalPages\.value/)
  assert.match(source, /:disabled="!hasNextPage"/)
})

test('nextPage rolls back the page when loading the next slice fails', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')
  const nextPageFunction = source.match(/async function nextPage\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(nextPageFunction, /if \(!hasNextPage\.value\)/)
  assert.match(nextPageFunction, /const previousPage = currentPage\.value/)
  assert.match(nextPageFunction, /currentPage\.value \+= 1/)
  assert.match(nextPageFunction, /try \{[\s\S]*await loadMatchHistory\(\{ throwOnError: true \}\)[\s\S]*\} catch \(err\) \{[\s\S]*currentPage\.value = previousPage[\s\S]*reachedEnd\.value = false/)
  assert.doesNotMatch(nextPageFunction, /throw err/)
})

test('search and filters reset reachedEnd before loading the first page', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')
  const applyDefaultFiltersFunction = source.match(/function applyDefaultFilters\(\) \{[\s\S]*?\n\}/)?.[0] || ''
  const searchFunction = source.match(/async function searchSummoner\(nameOverride\?: string\) \{[\s\S]*?\n\}/)?.[0] || ''
  const filterFunction = source.match(/async function handleFilterChange\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(applyDefaultFiltersFunction, /reachedEnd\.value = false/)
  assert.match(searchFunction, /applyDefaultFilters\(\)/)
  assert.match(filterFunction, /reachedEnd\.value = false[\s\S]*currentPage\.value = 1/)
})

test('short pages mark the end and previous page clears the end marker', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')
  const loadFunction = source.match(/async function loadMatchHistory\(options\?: \{ forceRefresh\?: boolean; throwOnError\?: boolean \}\) \{[\s\S]*?\n\}/)?.[0] || ''
  const prevPageFunction = source.match(/async function prevPage\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(loadFunction, /reachedEnd\.value = matches\.length < pageSize \|\| currentPage\.value >= totalPages\.value/)
  assert.match(prevPageFunction, /currentPage\.value -= 1[\s\S]*reachedEnd\.value = false[\s\S]*await loadMatchHistory\(\)/)
})

test('lookup sample count label describes the current page instead of total samples', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')

  assert.match(source, /t\('summoner\.currentPageCount'\)/)
  assert.doesNotMatch(source, /t\('summoner\.matchSamples'\)/)
})
