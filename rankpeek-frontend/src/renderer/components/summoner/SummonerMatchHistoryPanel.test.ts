import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('match history list hydrates loadout fields from cached and opened game details', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /hydrateMatchHistoryFromLocalDetailIfAvailable\(match, puuid\) \?\? match/)
  assert.match(source, /function applyGameDetailToVisibleMatchHistory\(match: MatchHistory, detail: GameDetail\)/)
  assert.match(source, /mergeGameDetailIntoMatchHistory\(existingMatch, detail\)/)
  assert.match(source, /selectedMatchHistory\.value = mergedMatch/)
  assert.match(source, /applyGameDetailToVisibleMatchHistory\(match, cachedDetail\)/)
  assert.match(source, /applyGameDetailToVisibleMatchHistory\(match, gameDetail\)/)
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

test('lookup rank summary is requested with the viewed summoner puuid', () => {
  const source = readFileSync(new URL('./SummonerMatchHistoryPanel.vue', import.meta.url), 'utf8')
  const setupBlock = source.match(/<script setup lang="ts">[\s\S]*?<\/script>/)?.[0] || ''
  const watcherBlock = setupBlock.match(/watch\(\s*\(\) => currentSummoner\.value\?\.puuid[\s\S]*?\{ immediate: true \}\s*\)/)?.[0] || ''
  const refreshFunction = setupBlock.match(/async function refreshRemoteMatchHistory\(options: MatchHistoryLoadOptions = \{\}\) \{[\s\S]*?async function handleRemoteMatchHistoryFailure/)?.[0] || ''
  const rankFunction = setupBlock.match(/async function loadRankSummary\(puuid: string, requestId: number\) \{[\s\S]*?\n\}/)?.[0] || ''
  const sgpRecordFunction = setupBlock.match(/async function loadSgpRankedRecords\(puuid: string, requestId: number, forceRefresh: boolean\) \{[\s\S]*?function calculateSgpRankedRecords/)?.[0] || ''

  assert.match(setupBlock, /const currentSummoner = computed\(\(\) => props\.summoner\)/)
  assert.doesNotMatch(setupBlock, /useGameStore/)
  assert.match(watcherBlock, /const requestId = matchHistoryRequestId/)
  assert.match(watcherBlock, /void refreshRemoteMatchHistory\(\{ forceRefresh: true, requestId \}\)/)
  assert.match(refreshFunction, /const puuid = currentSummoner\.value\?\.puuid/)
  assert.match(refreshFunction, /void loadRankSummary\(puuid, requestId\)/)
  assert.match(refreshFunction, /void loadSgpRankedRecords\(puuid, requestId, options\.forceRefresh === true\)/)
  assert.match(rankFunction, /apiClient\.getRank\(puuid\)/)
  assert.match(source, /:ranked-records="sgpRankedRecords"/)
  assert.match(sgpRecordFunction, /apiClient\.getMatchHistoryPage\(puuid, \{[\s\S]*pageSize:\s*200[\s\S]*source:\s*'sgp'/)
  assert.match(sgpRecordFunction, /currentSummoner\.value\?\.puuid !== puuid/)
  assert.match(setupBlock, /function getRankedQueueKey\(queueId\?: number\): RankedQueueKey \| null[\s\S]*queueId === 420[\s\S]*'RANKED_SOLO_5x5'[\s\S]*queueId === 440[\s\S]*'RANKED_FLEX_SR'/)
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
