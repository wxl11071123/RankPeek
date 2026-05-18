import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('match history list hydrates loadout fields from cached and opened game details', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /const hydratedMatch = await hydrateMatchHistoryFromLocalDetailIfAvailable\(match, puuid, requestId\)[\s\S]*renderableMatches\.push\(hydratedMatch \?\? match\)/)
  assert.match(source, /function applyGameDetailToVisibleMatchHistory\(match: MatchHistory, detail: GameDetail\)/)
  assert.match(source, /mergeGameDetailIntoMatchHistory\(existingMatch, detail\)/)
  assert.match(source, /selectedMatchHistory\.value = mergedMatch/)
  assert.match(source, /applyGameDetailToVisibleMatchHistory\(match, cachedDetail\)/)
  assert.match(source, /applyGameDetailToVisibleMatchHistory\(match, gameDetail\)/)
})

test('game detail merge preserves summary loadout stats when detail omits them', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')
  const mergeFunction = source.match(/function mergeGameDetailIntoMatchHistory\(match: MatchHistory, detail: GameDetail\): MatchHistory \{[\s\S]*?function toMatchParticipantFromGameDetail/)?.[0] || ''

  assert.match(source, /const LOADOUT_STAT_KEYS = \[/)
  assert.match(source, /'item0'[\s\S]*'item6'/)
  assert.match(source, /'perk0'[\s\S]*'perk4'[\s\S]*'perk5'/)
  assert.match(source, /'perkPrimaryStyle'[\s\S]*'perkSubStyle'/)
  assert.match(source, /'playerAugment5'[\s\S]*'playerAugment6'/)
  assert.match(source, /function mergeParticipantStatsLoadout/)
  assert.match(source, /function mergeParticipantLoadout/)
  assert.match(mergeFunction, /const existingParticipantsById = new Map/)
  assert.match(mergeFunction, /mergeParticipantLoadout\(existingParticipant, nextParticipant\)/)
  assert.doesNotMatch(mergeFunction, /participants: detailParticipants\.length >= participantCount\s*\?\s*detailParticipants\.map\(toMatchParticipantFromGameDetail\)\s*:\s*match\.participants/)
})

test('opened game detail keeps draft and objective summaries on visible match history', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')
  const mergeFunction = source.match(/function mergeGameDetailIntoMatchHistory\(match: MatchHistory, detail: GameDetail\): MatchHistory \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(mergeFunction, /teamObjectives: detail\.teamObjectives\?\.length \? detail\.teamObjectives : match\.teamObjectives/)
  assert.match(mergeFunction, /teamBans: detail\.teamBans\?\.length \? detail\.teamBans : match\.teamBans/)
})

test('match detail opens inline under the selected history card instead of a modal', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /import MatchHistoryInlineDetail from '@\/components\/match-history\/MatchHistoryInlineDetail\.vue'/)
  assert.doesNotMatch(source, /import MatchDetailModal/)
  assert.doesNotMatch(source, /<MatchDetailModal/)
  assert.match(source, /const expandedGameId = ref<number \| null>\(null\)/)
  assert.match(source, /const activeInlineDetailTabByGameId = ref<Record<string, InlineDetailTabKey>>\(\{\}\)/)
  assert.match(source, /class="match-list-item"/)
  assert.match(source, /v-for="match in matchHistory"/)
  assert.match(source, /:expanded="expandedGameId === match\.gameId"/)
  assert.match(source, /@open-detail="toggleInlineDetail"/)
  assert.match(source, /<MatchHistoryInlineDetail[\s\S]*v-if="expandedGameId === match\.gameId"[\s\S]*:match-history="match"[\s\S]*:game-detail="selectedGameDetail"[\s\S]*:detail-status="selectedGameDetailStatus"[\s\S]*@navigate-to-player="handleNavigateToPlayer"/)
  assert.match(source, /function getInlineDetailTab\(gameId: number\): InlineDetailTabKey/)
  assert.match(source, /function setInlineDetailTab\(gameId: number, tab: InlineDetailTabKey\)/)
})

test('inline detail is not passed user tag summaries while history cards keep them', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')
  const cardBlock = source.match(/<MatchHistoryCard[\s\S]*?\/>/)?.[0] || ''
  const inlineBlock = source.match(/<MatchHistoryInlineDetail[\s\S]*?\/>/)?.[0] || ''

  assert.match(cardBlock, /:user-tag-summaries="visibleUserTagSummaries"/)
  assert.doesNotMatch(inlineBlock, /user-tag-summaries/)
})

test('match history page header stays in normal flow above overview and controls', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')
  const templateBlock = source.match(/<template>[\s\S]*?<\/template>/)?.[0] || ''
  const pageShellBlock = templateBlock.match(/<section[\s\S]*class="page-shell surface-glow"[\s\S]*?<\/section>/)?.[0] || ''
  const pageShellRule = source.match(/\.page-shell \{[\s\S]*?\n\}/)?.[0] || ''
  const pageShellSurfaceRule = source.match(/\.page-shell\.surface-glow \{[\s\S]*?\n\}/)?.[0] || ''
  const pageShellStyles = `${pageShellRule}\n${pageShellSurfaceRule}`

  assert.match(pageShellBlock, /<h1>\{\{ panelTitle \}\}<\/h1>/)
  assert.match(source, /: t\('matchHistory\.title'\)/)
  assert.doesNotMatch(pageShellBlock, /v-model\.number="selectedLimit"/)
  assert.doesNotMatch(pageShellBlock, /<select[\s\S]*v-model\.number="filterChampionId"/)
  assert.match(pageShellBlock, /class="filter-select champion-filter-trigger"/)
  assert.match(pageShellBlock, /class="champion-filter-menu"/)
  assert.match(pageShellBlock, /v-for="champion in loadedChampionOptions"/)
  assert.match(pageShellBlock, /class="champion-option-count"/)
  assert.match(pageShellBlock, /@click="selectChampionFilter\(champion\.value\)"/)
  assert.match(pageShellBlock, /v-model\.number="filterQueueId"/)
  assert.match(pageShellBlock, /<RefreshIconButton[\s\S]*@click="handleRefresh"/)
  assert.doesNotMatch(pageShellBlock, /sticky|fixed|floating|is-sticky|match-history-sticky/)
  assert.doesNotMatch(pageShellStyles, /position:\s*(?:sticky|fixed)/)
  assert.doesNotMatch(pageShellStyles, /top:\s*[^;]+;/)
  assert.doesNotMatch(pageShellStyles, /z-index:\s*80/)
  assert.ok(templateBlock.indexOf('class="page-shell surface-glow"') < templateBlock.indexOf('<SummonerOverviewPanel'))
  assert.ok(templateBlock.indexOf('<SummonerOverviewPanel') < templateBlock.indexOf('<MatchHistoryInlineDetail'))
})

test('champion filter renders champion names with separately styled game counts', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /const selectedChampionOption = computed/)
  assert.match(source, /function formatChampionFilterLabel\(/)
  assert.match(source, /function selectChampionFilter\(championId: number\)/)
  assert.match(source, /<span class="champion-option-name">\{\{ champion\.label \}\}<\/span>/)
  assert.match(source, /<span class="champion-option-count">\{\{ champion\.games \}\}<\/span>/)
  assert.match(source, /<span class="champion-option-unit">\{\{ t\('matchHistory\.gamesUnit'\) \}\}<\/span>/)
})

test('champion filter menu matches trigger width and keeps its scrollbar inset', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')
  const menuRule = source.match(/\.champion-filter-menu \{[\s\S]*?\n\}/)?.[0] || ''
  const scrollRule = source.match(/\.champion-filter-scroll \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(source, /class="champion-filter-scroll"/)
  assert.match(menuRule, /left:\s*-1px/)
  assert.match(menuRule, /right:\s*-1px/)
  assert.match(menuRule, /width:\s*auto/)
  assert.doesNotMatch(menuRule, /width:\s*max\(/)
  assert.match(menuRule, /overflow:\s*hidden/)
  assert.match(scrollRule, /max-height:\s*272px/)
  assert.match(scrollRule, /overflow-y:\s*auto/)
  assert.match(scrollRule, /overflow-x:\s*hidden/)
  assert.match(scrollRule, /scrollbar-gutter:\s*stable/)
})

test('match history filter controls receive window-level proximity glow outside the module', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /const WINDOW_PROXIMITY_GLOW_SELECTOR = '\.surface-glow, \.filters \.filter-control\.control-glow'/)
  assert.match(source, /function getWindowProximityGlowElements\(\)/)
  assert.match(source, /querySelectorAll<HTMLElement>\(WINDOW_PROXIMITY_GLOW_SELECTOR\)/)
  assert.match(source, /function updateWindowProximityGlowAtPoint\(clientX: number, clientY: number\)/)
  assert.match(source, /getWindowProximityGlowElements\(\)\.forEach\(element => \{[\s\S]*applyGlowElement\(element, clientX, clientY\)/)
  assert.match(source, /function resetWindowProximityGlow\(\)/)
  assert.match(source, /getWindowProximityGlowElements\(\)\.forEach\(resetGlowElement\)/)
  assert.match(source, /function handleWindowPointerMove\(event: PointerEvent\) \{[\s\S]*scheduleWindowProximityGlow\(event\)/)
})

test('load-more footer includes the number of currently loaded visible matches in every state', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /const loadedMatchCount = computed\(\(\) => matchHistory\.value\.length\)/)
  assert.match(source, /t\('matchHistory\.loadingMoreWithCount', \{ count: loadedMatchCount \}\)/)
  assert.match(source, /t\('matchHistory\.loadMoreWithCount', \{ count: loadedMatchCount \}\)/)
  assert.match(source, /t\('matchHistory\.loadMoreFailedWithCount', \{ count: loadedMatchCount \}\)/)
  assert.match(source, /t\('matchHistory\.noMoreMatchesWithCount', \{ count: loadedMatchCount \}\)/)
})

test('load more continues to remote fetch after a short cached page', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')
  const loadMoreFunction = source.match(/async function loadMoreMatchHistory\(\) \{[\s\S]*?(?=async function loadSelectedMatchUserTagSummaries)/)?.[0] || ''

  assert.match(loadMoreFunction, /await hydrateMatchHistoryFromLocalCache\(requestId, \{ page, append: true \}\)/)
  assert.doesNotMatch(loadMoreFunction, /if \(hydrated && !hasNext\.value\) \{[\s\S]*?return[\s\S]*?\}/)
  assert.match(loadMoreFunction, /await loadMatchHistory\(\{ page, append: true, requestId \}\)/)
})

test('lookup rank summary is requested with the viewed summoner puuid', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')
  const setupBlock = source.match(/<script setup lang="ts">[\s\S]*?<\/script>/)?.[0] || ''
  const watcherBlock = setupBlock.match(/watch\(\s*\(\) => currentSummoner\.value\?\.puuid[\s\S]*?\{ immediate: true \}\s*\)/)?.[0] || ''
  const refreshFunction = setupBlock.match(/async function refreshRemoteMatchHistory\(options: MatchHistoryLoadOptions = \{\}\) \{[\s\S]*?async function handleRemoteMatchHistoryFailure/)?.[0] || ''
  const rankFunction = setupBlock.match(/async function loadRankSummary\(puuid: string, requestId: number\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(setupBlock, /const currentSummoner = computed\(\(\) => props\.summoner\)/)
  assert.doesNotMatch(setupBlock, /useGameStore/)
  assert.match(watcherBlock, /const requestId = matchHistoryRequestId/)
  assert.match(watcherBlock, /void refreshRemoteMatchHistory\(\{ forceRefresh: true, requestId \}\)/)
  assert.match(refreshFunction, /const puuid = currentSummoner\.value\?\.puuid/)
  assert.match(refreshFunction, /void loadRankSummary\(puuid, requestId\)/)
  assert.doesNotMatch(refreshFunction, /loadSgpRankedRecords|getRankedWinRates/)
  assert.match(rankFunction, /apiClient\.getRank\(puuid\)/)
  assert.doesNotMatch(source, /:ranked-records="sgpRankedRecords"/)
  assert.doesNotMatch(setupBlock, /sgpRankedRecords|loadSgpRankedRecords|calculateSgpRankedRecords|getRankedQueueKey/)
  assert.doesNotMatch(setupBlock, /apiClient\.getMatchHistoryPage\(puuid, \{[\s\S]*pageSize:\s*200[\s\S]*source:\s*'sgp'/)
})

test('inline detail toggle folds the same match and keeps only one expanded game active', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')
  const toggleFunction = source.match(/async function toggleInlineDetail\(match: MatchHistory\) \{[\s\S]*?function collapseInlineDetail/)?.[0] || ''
  const collapseFunction = source.match(/function collapseInlineDetail\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(toggleFunction, /if \(expandedGameId\.value === match\.gameId\) \{[\s\S]*collapseInlineDetail\(\)[\s\S]*return/)
  assert.match(toggleFunction, /const previousExpandedGameId = expandedGameId\.value/)
  assert.match(toggleFunction, /clearInlineDetailTab\(previousExpandedGameId\)/)
  assert.match(toggleFunction, /expandedGameId\.value = match\.gameId/)
  assert.match(toggleFunction, /selectedMatchHistory\.value = match/)
  assert.match(toggleFunction, /selectedGameDetail\.value = null/)
  assert.match(toggleFunction, /selectedGameDetailStatus\.value = 'loading'/)
  assert.match(source, /function collapseInlineDetail\(\) \{[\s\S]*expandedGameId\.value = null[\s\S]*selectedGameDetail\.value = null[\s\S]*selectedMatchHistory\.value = null[\s\S]*selectedGameDetailStatus\.value = 'idle'/)
  assert.match(collapseFunction, /const collapsedGameId = expandedGameId\.value/)
  assert.match(collapseFunction, /clearInlineDetailTab\(collapsedGameId\)/)
  assert.match(source, /function clearInlineDetailTab\(gameId: number \| null\): void \{[\s\S]*delete nextTabs\[String\(gameId\)\][\s\S]*activeInlineDetailTabByGameId\.value = nextTabs[\s\S]*\}/)
  assert.match(source, /function isActiveMatchDetailRequest\(requestId: number, matchId: string\): boolean \{[\s\S]*requestId === matchDetailRequestId[\s\S]*String\(expandedGameId\.value\) === matchId[\s\S]*\}/)
  assert.match(source, /function isActiveUserTagSummaryRequest\(requestId: number, matchId: string\): boolean \{[\s\S]*String\(expandedGameId\.value\) === matchId/)
})

test('postgame auto-open token triggers an all-mode refresh and opens the latest visible match once', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')
  const propsBlock = source.match(/const props = withDefaults\(defineProps<\{[\s\S]*?\}>\(\), \{[\s\S]*?\}\)/)?.[0] || ''
  const refreshFunction = source.match(/async function refreshRemoteMatchHistory\(options: MatchHistoryLoadOptions = \{\}\) \{[\s\S]*?async function handleRemoteMatchHistoryFailure/)?.[0] || ''
  const tokenWatcher = source.match(/watch\(\s*\(\) => props\.autoOpenLatestMatchToken,[\s\S]*?\n\)/)?.[0] || ''
  const autoOpenFunction = source.match(/async function openPendingAutoLatestMatch\(token: string, requestId = matchHistoryRequestId\): Promise<boolean> \{[\s\S]*?\n\}/)?.[0] || ''
  const cacheUpdateBlock = source.match(/unsubscribeCacheUpdate = wsClient\.onCacheUpdate\(async \(event: CacheUpdateEvent\) => \{[\s\S]*?\n\s*\}\)/)?.[0] || ''

  assert.match(propsBlock, /autoOpenLatestMatchToken\?: string/)
  assert.match(source, /import \{ clearPostgameAutoOpenLatestMatchToken \} from '@\/services\/gameflowAutoNavigation'/)
  assert.match(source, /const pendingAutoOpenLatestMatchToken = ref\(''\)/)
  assert.match(source, /function requestAutoOpenLatestMatch\(token: string\)/)
  assert.match(tokenWatcher, /requestAutoOpenLatestMatch\(token\)/)
  assert.match(source, /filterChampionId\.value = -1/)
  assert.match(source, /filterQueueId\.value = 0/)
  assert.match(source, /await refreshRemoteMatchHistory\(\{ forceRefresh: true, requestId, autoOpenLatestMatchToken: token \}\)/)
  assert.match(refreshFunction, /if \(options\.autoOpenLatestMatchToken && visibleListUpdated && requestId === matchHistoryRequestId\) \{[\s\S]*await openPendingAutoLatestMatch\(options\.autoOpenLatestMatchToken, requestId\)/)
  assert.match(autoOpenFunction, /const latestMatch = matchHistory\.value\[0\]/)
  assert.match(autoOpenFunction, /await openInlineDetail\(latestMatch\)/)
  assert.match(autoOpenFunction, /clearPostgameAutoOpenLatestMatchToken\(token\)/)
  assert.match(cacheUpdateBlock, /await openPendingAutoLatestMatch\(pendingAutoOpenLatestMatchToken\.value\)/)
})
