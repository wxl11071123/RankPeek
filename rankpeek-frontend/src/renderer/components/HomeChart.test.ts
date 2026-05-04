import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('home chart uses reliable match history instead of the legacy filtered history chain', () => {
  const source = readFileSync(new URL('./HomeChart.vue', import.meta.url), 'utf8')
  const homeView = readFileSync(new URL('../views/HomeView.vue', import.meta.url), 'utf8')

  assert.match(source, /loadReliableMatchHistory/)
  assert.match(source, /createHomeChartEntries/)
  assert.match(source, /runWithConcurrencyLimit/)
  assert.doesNotMatch(source, /getFilteredMatchHistory/)
  assert.doesNotMatch(source, /Promise\.allSettled/)
  assert.doesNotMatch(source, /stats\.kills\s*\|\|\s*0/)
  assert.match(homeView, /<HomeChart[^>]*:summoner="currentSummoner"/)
})
