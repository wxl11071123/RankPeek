import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('uses a vertical-first team layout with analysis actions above the teams', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const toolbarIndex = source.indexOf("'match-analysis-toolbar'")
  const teamsIndex = source.indexOf('class="teams-container"')

  assert.match(source, /class="team-panel team-blue"/)
  assert.match(source, /class="team-panel team-red"/)
  assert.match(source, /'match-analysis-toolbar'/)
  assert.doesNotMatch(source, /data-watermark="RANKPEEK AI"/)
  assert.match(source, /class="ai-wordmark"/)
  assert.match(source, /viewBox="0 0 1240 220"/)
  assert.match(source, />RANKPEEK</)
  assert.match(source, />AI</)
  assert.match(source, /class="wordmark-echo wordmark-ai-echo" x="970"/)
  assert.match(source, /class="wordmark-main wordmark-ai" x="960"/)
  assert.match(source, />队友成分</)
  assert.match(source, />赛前分析</)
  assert.match(source, />检查大腿or拖油瓶</)
  assert.match(source, />分析小代or软柿子</)
  assert.match(source, /class="analysis-help"/)
  assert.match(source, /class="\['match-analysis-toolbar', \{ 'has-analysis-output': latestAnalysisSummary \}\]"/)
  assert.match(source, /class="analysis-actions"/)
  assert.match(source, /v-if="latestAnalysisSummary"/)
  assert.match(source, /class="analysis-result"/)
  assert.match(source, /latestAnalysisSummary/)
  assert.match(source, /\.match-analysis-toolbar\s*{[\s\S]*align-items:\s*center;[\s\S]*min-height:\s*142px;[\s\S]*max-height:\s*142px;/)
  assert.match(source, /\.ai-wordmark\s*{[\s\S]*height:\s*clamp\(170px, 24vw, 300px\);/)
  assert.match(source, /\.ai-wordmark\s*{[\s\S]*z-index:\s*0;/)
  assert.match(source, /\.match-analysis-toolbar\.has-analysis-output\s*{[\s\S]*max-height:\s*360px;/)
  assert.match(source, /\.analysis-result\s*{[\s\S]*margin-top:\s*18px;/)
  assert.match(source, /\.analysis-actions\s*{[\s\S]*justify-content:\s*flex-start;/)
  assert.match(source, /\.analysis-action\s*{[\s\S]*flex:\s*0 1 220px;[\s\S]*min-width:\s*178px;/)
  assert.match(source, /\.analysis-btn\s*{[\s\S]*justify-content:\s*flex-start;[\s\S]*min-height:\s*84px;[\s\S]*font-size:\s*28px;[\s\S]*line-height:\s*1;[\s\S]*text-align:\s*left;/)
  assert.match(source, /\.analysis-btn::before[\s\S]*linear-gradient\(120deg/)
  assert.match(source, /\.analysis-action:hover \.analysis-btn[\s\S]*translateY\(-2px\)/)
  assert.match(source, /@media \(max-width:\s*720px\)[\s\S]*\.match-analysis-toolbar\s*{[\s\S]*align-items:\s*center;[\s\S]*\.analysis-btn\s*{[\s\S]*min-height:\s*76px;[\s\S]*font-size:\s*24px;/)
  assert.doesNotMatch(source, />队友成分分析</)
  assert.doesNotMatch(source, />BP 前分析</)
  assert.doesNotMatch(source, />BP 后分析</)
  assert.equal(toolbarIndex > -1 && teamsIndex > -1 && toolbarIndex < teamsIndex, true)
  assert.match(source, /\.teams-container\s*{\s*display:\s*grid;[\s\S]*grid-template-columns:\s*1fr;/)
  assert.match(source, /@media \(min-width:\s*1180px\)[\s\S]*grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\)/)
})

test('subscribes to cache update events and refreshes only relevant current-session players', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')

  assert.match(source, /import \{ wsClient \} from '@\/api\/websocketClient'/)
  assert.match(source, /import type \{ CacheUpdateEvent, SessionData \} from '@\/types\/api'/)
  assert.match(source, /let unsubscribeCacheUpdate: \(\(\) => void\) \| null = null/)
  assert.match(source, /unsubscribeCacheUpdate = wsClient\.onCacheUpdate\(\(event: CacheUpdateEvent\) => \{/)
  assert.match(source, /if \(isCacheUpdateRelevant\(event\)\) \{\s*scheduleCacheUpdateRefresh\(\)/)
  assert.match(source, /function collectCurrentSessionPuuids\(\): Set<string>/)
  assert.match(source, /for \(const player of sessionData\.value\.teamOne \|\| \[\]\)/)
  assert.match(source, /for \(const player of sessionData\.value\.teamTwo \|\| \[\]\)/)
  assert.match(source, /const puuid = player\?\.summoner\?\.puuid/)
  assert.match(source, /return currentPuuids\.has\(event\.puuid\)/)
  assert.match(source, /apiClient\.getSessionData\(DEFAULT_ANALYSIS_QUEUE_MODE\)/)
})

test('throttles cache update refreshes and respects paused refresh state', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const scheduleFunction = source.match(/function scheduleCacheUpdateRefresh\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(source, /let cacheUpdateRefreshTimer: ReturnType<typeof setTimeout> \| null = null/)
  assert.match(source, /let lastCacheUpdateRefreshAt = 0/)
  assert.match(source, /const cacheUpdateRefreshDelay = 800/)
  assert.match(source, /const minCacheUpdateRefreshInterval = 2500/)
  assert.match(scheduleFunction, /if \(isRefreshPaused\.value\) return/)
  assert.match(scheduleFunction, /if \(elapsed >= minCacheUpdateRefreshInterval\)/)
  assert.match(scheduleFunction, /fetchSessionData\(\{ showLoading: false \}\)/)
  assert.match(scheduleFunction, /if \(cacheUpdateRefreshTimer\) return/)
  assert.match(scheduleFunction, /cacheUpdateRefreshTimer = setTimeout/)
  assert.match(scheduleFunction, /cacheUpdateRefreshDelay/)
})

test('cache update refreshes stay quiet and do not overlap polling requests', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const fetchFunction = source.match(/async function fetchSessionData\([\s\S]*?\n\}/)?.[0] || ''
  const scheduleFunction = source.match(/function scheduleCacheUpdateRefresh\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(source, /let sessionFetchInFlight = false/)
  assert.match(fetchFunction, /options: \{ showLoading\?: boolean \} = \{\}/)
  assert.match(fetchFunction, /if \(isRefreshPaused\.value \|\| sessionFetchInFlight\) return/)
  assert.match(fetchFunction, /const showLoading = options\.showLoading !== false/)
  assert.match(fetchFunction, /sessionFetchInFlight = true/)
  assert.match(fetchFunction, /if \(showLoading\) loading\.value = true/)
  assert.match(fetchFunction, /if \(showLoading\) loading\.value = false/)
  assert.match(fetchFunction, /sessionFetchInFlight = false/)
  assert.match(scheduleFunction, /fetchSessionData\(\{ showLoading: false \}\)/)
})

test('cleans up cache update subscription and pending refresh timer on unmount', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const unmountBlock = source.match(/onUnmounted\(\(\) => \{[\s\S]*?\n\}\)/)?.[0] || ''

  assert.match(unmountBlock, /if \(unsubscribeCacheUpdate\) \{/)
  assert.match(unmountBlock, /unsubscribeCacheUpdate\(\)/)
  assert.match(unmountBlock, /unsubscribeCacheUpdate = null/)
  assert.match(unmountBlock, /if \(cacheUpdateRefreshTimer\) \{/)
  assert.match(unmountBlock, /clearTimeout\(cacheUpdateRefreshTimer\)/)
  assert.match(unmountBlock, /cacheUpdateRefreshTimer = null/)
})
