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
