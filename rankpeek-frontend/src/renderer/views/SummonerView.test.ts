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
  assert.match(source, /async function loadMatchHistory\(options\?: \{ forceRefresh\?: boolean \}\)/)
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

test('pagination next button is driven by visible page capacity', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')

  assert.match(source, /const hasNextPage = computed\(\(\) =>[\s\S]*!loading\.value[\s\S]*searchMatchHistory\.value\.length === pageSize[\s\S]*currentPage\.value < totalPages\.value/)
  assert.match(source, /:disabled="!hasNextPage"/)
})

test('nextPage increments currentPage and reloads match history only when another page is available', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')
  const nextPageFunction = source.match(/async function nextPage\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(nextPageFunction, /if \(!hasNextPage\.value\)/)
  assert.match(nextPageFunction, /currentPage\.value \+= 1/)
  assert.match(nextPageFunction, /await loadMatchHistory\(\)/)
})

test('lookup sample count label describes the current page instead of total samples', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')

  assert.match(source, /t\('summoner\.currentPageCount'\)/)
  assert.doesNotMatch(source, /t\('summoner\.matchSamples'\)/)
})
