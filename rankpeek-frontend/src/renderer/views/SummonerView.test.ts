import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('summoner lookup delegates the match analysis body to the shared panel', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')

  assert.match(source, /import SummonerMatchHistoryPanel from '@\/components\/summoner\/SummonerMatchHistoryPanel\.vue'/)
  assert.match(source, /<SummonerMatchHistoryPanel[\s\S]*:summoner="resolvedSearchResult"[\s\S]*variant="lookup"[\s\S]*:connected="gameStore\.connected"[\s\S]*:local-cache-enabled="lookupUsesLocalCache"[\s\S]*:lookup-query="searchName"[\s\S]*:lookup-loading="loading"[\s\S]*:lookup-error="error"[\s\S]*@update:lookup-query="searchName = \$event"[\s\S]*@lookup="searchSummoner\(\)"[\s\S]*\/>/)
  assert.doesNotMatch(source, /class="search-shell"/)
  assert.doesNotMatch(source, /class="search-bar"/)
  assert.doesNotMatch(source, /class="search-input"/)
  assert.doesNotMatch(source, /class="search-btn"/)
  assert.doesNotMatch(source, /import MatchDetailModal/)
  assert.doesNotMatch(source, /import MatchRosterCompact/)
  assert.doesNotMatch(source, /import \{ apiClient \}/)
  assert.doesNotMatch(source, /class="lookup-account-strip"/)
  assert.doesNotMatch(source, /class="lookup-filter-bar"/)
  assert.doesNotMatch(source, /class="match-card-main"/)
  assert.doesNotMatch(source, /class="roster-grid"/)
})

test('current-account lookup reuses the connected summoner context instead of refetching by name', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')
  const searchFunction = source.match(/async function searchSummoner\(nameOverride\?: string\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(source, /import \{ computed, onMounted, ref, watch \} from 'vue'/)
  assert.match(source, /const resolvedSearchResult = computed\(\(\) => searchResult\.value\)/)
  assert.match(source, /const lookupUsesLocalCache = computed\(\(\) =>[\s\S]*gameStore\.currentSummoner\?\.puuid[\s\S]*searchResult\.value\?\.puuid/)
  assert.match(source, /function resolveCurrentSummonerLookup\(keyword: string\): Summoner \| null \{[\s\S]*gameStore\.currentSummoner[\s\S]*summonerMatchesLookup\(currentSummoner, keyword\)/)
  assert.match(searchFunction, /const currentSummoner = resolveCurrentSummonerLookup\(keyword\)/)
  assert.match(searchFunction, /if \(currentSummoner\) \{[\s\S]*searchResult\.value = currentSummoner[\s\S]*return[\s\S]*\}/)
  assert.match(searchFunction, /const summoner = await gameStore\.fetchSummonerByName\(keyword\)/)
})

test('summoner lookup search controls live in the shared history panel header', () => {
  const panel = readFileSync(new URL('../components/summoner/SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')
  const zh = readFileSync(new URL('../i18n/locales/zh-CN.ts', import.meta.url), 'utf8')
  const en = readFileSync(new URL('../i18n/locales/en-US.ts', import.meta.url), 'utf8')

  assert.match(panel, /v-if="isLookup \|\| currentSummoner"/)
  assert.match(panel, /<h1>\{\{ panelTitle \}\}<\/h1>[\s\S]*<div v-if="isLookup" class="lookup-search">/)
  assert.match(panel, /class="lookup-search-input-wrap control-glow"[\s\S]*:style="\{ width: lookupInputWidth \}"[\s\S]*<input[\s\S]*class="lookup-search-input"[\s\S]*:value="lookupQueryValue"[\s\S]*:aria-label="t\('summoner\.placeholder'\)"[\s\S]*@input="handleLookupQueryInput"[\s\S]*@keyup\.enter="handleLookupSubmit"/)
  assert.doesNotMatch(panel, /placeholder=/)
  assert.match(panel, /const lookupInputWidth = computed\(\(\) => \{[\s\S]*clamp\(180px, \$\{length \+ 4\}ch, 420px\)/)
  assert.match(panel, /class="lookup-search-icon-btn control-glow"[\s\S]*aria-label="[^"]+"[\s\S]*:disabled="lookupSearchDisabled"[\s\S]*@click="handleLookupSubmit"[\s\S]*<svg[\s\S]*class="lookup-search-icon"[\s\S]*viewBox="0 0 24 24"[\s\S]*<path d=/)
  assert.match(panel, /<RefreshIconButton[\s\S]*:aria-label="refreshing \? t\('common\.refreshing'\) : t\('common\.refresh'\)"[\s\S]*:loading="refreshing"[\s\S]*:disabled="!currentSummoner"/)
  assert.doesNotMatch(panel, /<RefreshIconButton[\s\S]*class="control-glow"/)
  assert.doesNotMatch(panel, /:deep\(\.refresh-icon-btn/)
  assert.match(panel, /class="filter-control limit-select-control control-glow"[\s\S]*<select[\s\S]*class="filter-select limit-select"/)
  assert.match(panel, /class="filter-control champion-select-control control-glow"[\s\S]*<select[\s\S]*class="filter-select champion-select"/)
  assert.match(panel, /class="filter-control control-glow"[\s\S]*<select[\s\S]*class="filter-select"/)
  assert.match(panel, /\.lookup-search-input-wrap \{[\s\S]*border: 1px solid var\(--match-control-border\)[\s\S]*background:[\s\S]*var\(--match-control-bg\)/)
  assert.match(panel, /\.lookup-search-input \{[\s\S]*border: 0[\s\S]*background: transparent/)
  assert.match(panel, /\.lookup-search-input-wrap:hover,[\s\S]*\.lookup-search-input-wrap:focus-within \{[\s\S]*border-color: var\(--match-control-border\)[\s\S]*var\(--match-control-edge-shadow\)/)
  assert.match(panel, /\.lookup-search-input-wrap\.control-glow\[data-near-glow='true'\]:not\(:hover\):not\(:focus-within\) \{[\s\S]*border-color: var\(--match-control-border\)[\s\S]*var\(--match-control-edge-shadow\)/)
  assert.match(panel, /:global\(\[data-theme="light"\] \.match-history-view \.lookup-search-input-wrap\) \{[\s\S]*background:[\s\S]*rgba\(255, 255, 255, 0\.92\)[\s\S]*color: #101722/)
  assert.match(panel, /:global\(\[data-theme="light"\] \.match-history-view \.lookup-search-icon-btn\) \{[\s\S]*color: #000/)
  assert.match(panel, /:global\(\[data-theme="light"\] \.match-history-view \.lookup-search-icon-btn:hover\),[\s\S]*color: #000/)
  assert.match(panel, /:global\(\[data-theme="light"\] \.match-history-view \.lookup-search-icon\),[\s\S]*:global\(\[data-theme="light"\] \.match-history-view \.lookup-search-icon path\) \{[\s\S]*stroke: #000/)
  assert.doesNotMatch(panel, /:global\(\[data-theme="light"\] \.match-history-view \.lookup-search-input:hover\)/)
  assert.match(panel, /\.lookup-search-icon-btn \{[\s\S]*width: var\(--lookup-control-height\)[\s\S]*height: var\(--lookup-control-height\)[\s\S]*border: 1px solid rgba\(92, 163, 234, 0\)[\s\S]*color: #fff/)
  assert.match(panel, /\.lookup-search-icon-btn:hover,[\s\S]*\.lookup-search-icon-btn:focus-visible \{[\s\S]*border-color: rgba\(92, 163, 234, 0\)[\s\S]*var\(--match-control-edge-shadow\)/)
  assert.match(panel, /\.lookup-search-icon-btn\.control-glow\[data-near-glow='true'\]:not\(:hover\):not\(:focus\) \{[\s\S]*border-color: rgba\(92, 163, 234, 0\)[\s\S]*var\(--match-control-edge-shadow\)/)
  assert.match(panel, /\.control-glow:hover::before,[\s\S]*\.control-glow:focus-within::before,[\s\S]*\.control-glow:focus-visible::before/)
  assert.match(panel, /\.filter-control:hover,[\s\S]*\.filter-control:focus-within \{[\s\S]*border-color: var\(--match-control-border\)[\s\S]*var\(--match-control-edge-shadow\)/)
  assert.match(panel, /\.filter-control\.control-glow\[data-near-glow='true'\]:not\(:hover\):not\(:focus-within\) \{[\s\S]*border-color: var\(--match-control-border\)[\s\S]*var\(--match-control-edge-shadow\)/)
  assert.match(panel, /\.lookup-search-icon-btn:disabled \{[\s\S]*opacity: 0\.45[\s\S]*cursor: not-allowed/)
  assert.doesNotMatch(panel.match(/\.lookup-search-icon-btn:disabled \{[\s\S]*?\}/)?.[0] || '', /pointer-events:\s*none/)
  assert.doesNotMatch(panel, /\.lookup-search-input:hover/)
  assert.doesNotMatch(panel, /\.match-history-view\[data-variant='lookup'\] \.filter-select:hover/)
  assert.doesNotMatch(panel, /\{\{ lookupLoading \? t\('summoner\.searching'\) : t\('summoner\.search'\) \}\}/)
  assert.match(panel, /<div class="page-title-row">[\s\S]*<div v-if="currentSummoner" class="page-controls">/)
  assert.match(panel, /<section v-if="currentSummoner" class="content-stack">/)
  assert.match(zh, /'matchHistory\.lookupTitle': '战绩查询'/)
  assert.match(en, /'matchHistory\.lookupTitle': 'Match Lookup'/)
})

test('summoner lookup keeps only search state and leaves match state to the panel', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')

  assert.match(source, /const searchName = ref\(''\)/)
  assert.match(source, /const searchResult = ref<Summoner \| null>\(null\)/)
  assert.match(source, /const loading = ref\(false\)/)
  assert.match(source, /const error = ref\(''\)/)
  assert.doesNotMatch(source, /searchRank|searchMatchHistory|searchUserTag|searchRankedWinRates/)
  assert.doesNotMatch(source, /userTagSummaries|championOptions|modeOptions|filterChampionId|filterQueueId/)
  assert.doesNotMatch(source, /currentPage|reachedEnd|showDetailModal|selectedGameDetail|selectedMatchHistory/)
  assert.doesNotMatch(source, /async function loadMatchHistory|async function loadVisibleUserTagSummaries/)
  assert.doesNotMatch(source, /async function refreshCurrentSummoner|async function handleFilterChange/)
})

test('searchSummoner only resolves the summoner and clears stale lookup panels on failure', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')
  const searchFunction = source.match(/async function searchSummoner\(nameOverride\?: string\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(searchFunction, /const keyword = \(nameOverride \?\? searchName\.value\)\.trim\(\)/)
  assert.match(searchFunction, /error\.value = ''/)
  assert.match(searchFunction, /searchResult\.value = null/)
  assert.match(searchFunction, /loading\.value = true/)
  assert.match(searchFunction, /const summoner = await gameStore\.fetchSummonerByName\(keyword\)/)
  assert.match(searchFunction, /searchResult\.value = summoner/)
  assert.match(searchFunction, /error\.value = t\('summoner\.notFound'\)/)
  assert.match(searchFunction, /error\.value = t\('summoner\.searchFailed'\)/)
  assert.doesNotMatch(searchFunction, /apiClient\./)
  assert.doesNotMatch(searchFunction, /loadMatchHistory|ensurePageSettingsLoaded|applyDefaultFilters/)
})

test('searchSummoner ignores stale lookup responses from earlier searches', () => {
  const source = readFileSync(new URL('./SummonerView.vue', import.meta.url), 'utf8')
  const searchFunction = source.match(/async function searchSummoner\(nameOverride\?: string\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(source, /let searchRequestId = 0/)
  assert.match(searchFunction, /const requestId = \+\+searchRequestId/)
  assert.match(searchFunction, /if \(requestId !== searchRequestId\) \{[\s\S]*return[\s\S]*\}/)
  assert.match(searchFunction, /if \(requestId === searchRequestId\) \{[\s\S]*loading\.value = false[\s\S]*\}/)
})
