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
  assert.match(pageShellBlock, /v-model\.number="selectedLimit"/)
  assert.match(pageShellBlock, /v-model\.number="filterChampionId"/)
  assert.match(pageShellBlock, /v-model\.number="filterQueueId"/)
  assert.match(pageShellBlock, /<RefreshIconButton[\s\S]*@click="handleRefresh"/)
  assert.doesNotMatch(pageShellBlock, /sticky|fixed|floating|is-sticky|match-history-sticky/)
  assert.doesNotMatch(pageShellStyles, /position:\s*(?:sticky|fixed)/)
  assert.doesNotMatch(pageShellStyles, /top:\s*[^;]+;/)
  assert.doesNotMatch(pageShellStyles, /z-index:\s*80/)
  assert.ok(templateBlock.indexOf('class="page-shell surface-glow"') < templateBlock.indexOf('<SummonerOverviewPanel'))
  assert.ok(templateBlock.indexOf('<SummonerOverviewPanel') < templateBlock.indexOf('<MatchHistoryInlineDetail'))
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

  assert.match(toggleFunction, /if \(expandedGameId\.value === match\.gameId\) \{[\s\S]*collapseInlineDetail\(\)[\s\S]*return/)
  assert.match(toggleFunction, /expandedGameId\.value = match\.gameId/)
  assert.match(toggleFunction, /selectedMatchHistory\.value = match/)
  assert.match(toggleFunction, /selectedGameDetail\.value = null/)
  assert.match(toggleFunction, /selectedGameDetailStatus\.value = 'loading'/)
  assert.match(source, /function collapseInlineDetail\(\) \{[\s\S]*expandedGameId\.value = null[\s\S]*selectedGameDetail\.value = null[\s\S]*selectedMatchHistory\.value = null[\s\S]*selectedGameDetailStatus\.value = 'idle'/)
  assert.match(source, /function isActiveMatchDetailRequest\(requestId: number, matchId: string\): boolean \{[\s\S]*requestId === matchDetailRequestId[\s\S]*String\(expandedGameId\.value\) === matchId[\s\S]*\}/)
  assert.match(source, /function isActiveUserTagSummaryRequest\(requestId: number, matchId: string\): boolean \{[\s\S]*String\(expandedGameId\.value\) === matchId/)
})
