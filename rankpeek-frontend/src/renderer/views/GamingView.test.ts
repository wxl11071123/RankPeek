import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  isGamingAiAnalysisEnabledQueue,
  normalizeGamingQueueLabel
} from '../services/gamingAiQueue.ts'

function extractRule(source: string, selector: string) {
  const normalizedSource = source.replace(/\r\n/g, '\n')
  const start = normalizedSource.indexOf(selector)
  assert.notEqual(start, -1, `${selector} should exist`)

  const open = normalizedSource.indexOf('{', start)
  assert.notEqual(open, -1, `${selector} should have a body`)

  let depth = 0
  for (let index = open; index < normalizedSource.length; index += 1) {
    if (normalizedSource[index] === '{') {
      depth += 1
    }

    if (normalizedSource[index] === '}') {
      depth -= 1
      if (depth === 0) {
        return normalizedSource.slice(open + 1, index)
      }
    }
  }

  assert.fail(`${selector} should close`)
}

test('gaming refresh action uses the shared refresh icon button', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')

  assert.match(source, /import RefreshIconButton from '@\/components\/common\/RefreshIconButton\.vue'/)
  assert.match(source, /<RefreshIconButton[\s\S]*:aria-label="refreshButtonLabel"[\s\S]*:loading="loading"[\s\S]*:disabled="loading"[\s\S]*@click="\(\) => fetchSessionData\(\)"/)
  assert.doesNotMatch(source, /class="refresh-btn-small"/)
  assert.doesNotMatch(source, /\.refresh-btn-small/)
  assert.doesNotMatch(source, /class="refresh-icon"[\s\S]*spinning/)
})

test('gaming header exposes OP.GG action immediately before refresh and opens the independent window', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const opggIndex = source.indexOf('class="opgg-action-btn control-glow"')
  const refreshIndex = source.indexOf('<RefreshIconButton')
  const opggButtonBlock = source.slice(opggIndex, source.indexOf('</button>', opggIndex))

  assert.match(source, /import \{ buildOpggChampionQuery \} from '@\/services\/opggChampionQuery'/)
  assert.equal(opggIndex > -1 && refreshIndex > -1 && opggIndex < refreshIndex, true)
  assert.match(source, /<button[\s\S]*class="opgg-action-btn control-glow"[\s\S]*:title="opggButtonTitle"[\s\S]*@click="openOpggWindow"[\s\S]*>[\s\S]*OP\.GG[\s\S]*<\/button>/)
  assert.doesNotMatch(opggButtonBlock, /:disabled=/)
  assert.match(source, /const opggQuery = computed\(\(\) => buildOpggChampionQuery\(sessionData\.value\)\)/)
  assert.match(source, /const opggButtonTitle = computed\(\(\) => opggQuery\.value\.reason \|\| 'OP\.GG'\)/)
  assert.match(source, /async function openOpggWindow\(\)/)
  assert.match(source, /window\.electronAPI\?\.openOpggWindow\?\.\(opggQuery\.value\)/)
  assert.doesNotMatch(source, /OpggChampionModal/)
  assert.doesNotMatch(source, /opggModalOpen|opggDetail|opggLoading|opggError|loadOpggDetail|canLoadOpggDetail/)
  assert.doesNotMatch(source, /getOpggChampionDetail/)
  assert.match(source, /\.opgg-action-btn\s*\{/)
})

test('gaming page applies the home-style cursor glow to panels, buttons, and player cards', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')

  assert.match(source, /const CONTROL_GLOW_RANGE = 96/)
  assert.match(source, /const SURFACE_GLOW_RANGE = 220/)
  assert.match(source, /const EDGE_GLOW_MIN = 0\.03/)
  assert.match(source, /const PAGE_GLOW_SELECTOR = '\.surface-glow, \.control-glow'/)
  assert.match(source, /ref="gamingViewRef"/)
  assert.match(source, /window\.addEventListener\('pointermove', updatePageGlow\)/)
  assert.match(source, /window\.removeEventListener\('pointermove', updatePageGlow\)/)
  assert.match(source, /class="gaming-header surface-glow"/)
  assert.match(source, /class="team-panel team-blue surface-glow"/)
  assert.match(source, /class="team-panel team-red surface-glow"/)
  assert.match(source, /class="team-analysis-btn team-analysis-btn-blue control-glow"/)
  assert.match(source, /class="team-analysis-btn team-analysis-btn-red control-glow"/)
  assert.match(source, /<PlayerCard[\s\S]*class="gaming-player-card surface-glow"/)
  assert.match(source, /\.control-glow::before,\s*\.surface-glow::before/)
  assert.match(source, /\.gaming-player-card\.surface-glow/)
})

test('gaming page follows the shared home module and control glow contract', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const playerCardSource = readFileSync(new URL('../components/gaming/PlayerCard.vue', import.meta.url), 'utf8')
  const baseRule = extractRule(source, '.gaming-view')
  const lightRule = extractRule(source, ':global([data-theme="light"] .gaming-view)')
  const headerRule = extractRule(source, '.gaming-header')
  const teamPanelRule = extractRule(source, '.team-panel')
  const headerHoverRule = extractRule(source, '.gaming-header:hover,\n.gaming-header:focus-within')
  const teamHoverRule = extractRule(source, '.team-panel:hover,\n.team-panel:focus-within')
  const buttonHoverRule = extractRule(source, '.team-analysis-btn:hover,\n.team-analysis-btn:focus-visible')
  const selectedRule = extractRule(playerCardSource, '.player-card.selected')

  assert.match(baseRule, /--gaming-module-hover-rgb:\s*212, 175, 55/)
  assert.match(baseRule, /--gaming-module-hover-border:\s*rgba\(var\(--gaming-module-hover-rgb\), 0\.48\)/)
  assert.match(baseRule, /--gaming-module-hover-shadow:/)
  assert.match(baseRule, /--gaming-hover-border:\s*var\(--gaming-module-hover-border\)/)
  assert.match(baseRule, /--gaming-hover-shadow:\s*var\(--gaming-module-hover-shadow\)/)
  assert.match(baseRule, /--gaming-control-border-hover:\s*rgba\(96, 176, 255, 0\.58\)/)
  assert.match(baseRule, /--gaming-control-bg-hover-local:[\s\S]*var\(--gaming-control-border-hover\) 72%[\s\S]*\) border-box/)

  assert.match(lightRule, /--gaming-module-hover-rgb:\s*86, 109, 134/)
  assert.match(lightRule, /--gaming-module-hover-border:\s*rgba\(var\(--gaming-module-hover-rgb\), 0\.42\)/)
  assert.match(lightRule, /--gaming-control-border-local-glow:\s*rgba\(255, 218, 76, 0\.94\)/)
  assert.match(lightRule, /--gaming-control-border-local-glow-fade:\s*rgba\(244, 183, 24, 0\.52\)/)
  assert.match(lightRule, /--gaming-control-edge-rgb:\s*255, 210, 62/)
  assert.match(lightRule, /--gaming-control-border-hover:\s*var\(--border-color\)/)
  assert.match(lightRule, /--gaming-control-bg-hover:\s*rgba\(252, 238, 198, 0\.98\)/)

  assert.match(headerRule, /border:\s*1px solid var\(--border-color\)/)
  assert.match(headerRule, /box-shadow:\s*none/)
  assert.match(teamPanelRule, /border:\s*1px solid var\(--border-color\)/)
  assert.match(teamPanelRule, /box-shadow:\s*none/)
  assert.doesNotMatch(source, /\.team-blue\s*\{[\s\S]*?border-color:/)
  assert.doesNotMatch(source, /\.team-red\s*\{[\s\S]*?border-color:/)

  assert.match(headerHoverRule, /border-color:\s*var\(--gaming-module-hover-border\)/)
  assert.match(headerHoverRule, /box-shadow:\s*var\(--gaming-module-hover-shadow\)/)
  assert.match(teamHoverRule, /border-color:\s*var\(--gaming-module-hover-border\)/)
  assert.match(teamHoverRule, /box-shadow:\s*var\(--gaming-module-hover-shadow\)/)
  assert.match(buttonHoverRule, /background:\s*var\(--gaming-control-bg-hover-local\)/)
  assert.match(buttonHoverRule, /box-shadow:\s*var\(--gaming-control-hover-shadow\), var\(--gaming-control-edge-shadow\)/)

  assert.match(selectedRule, /border-color:\s*rgba\(240, 196, 79, 0\.48\)/)
  assert.match(selectedRule, /box-shadow:\s*0 0 0 1px rgba\(240, 196, 79, 0\.14\)/)
})

test('uses compact team panels and reads session data through the gaming adapter', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const headerIndex = source.indexOf('class="gaming-header surface-glow"')
  const teamsIndex = source.indexOf('class="teams-container"')

  assert.match(source, /import \{ getGamingSessionData \} from '@\/api\/sessionDataAdapter'/)
  assert.match(source, /class="gaming-header surface-glow"/)
  assert.match(source, /class="team-panel team-blue surface-glow"/)
  assert.match(source, /class="team-panel team-red surface-glow"/)
  assert.match(source, /class="team-analysis-btn team-analysis-btn-blue control-glow"/)
  assert.match(source, /class="team-analysis-btn team-analysis-btn-red control-glow"/)
  assert.match(source, /<PlayerCard[\s\S]*class="gaming-player-card surface-glow"[\s\S]*:session-summoner="player"[\s\S]*:selected="isParticipantExpanded\(player\)"[\s\S]*@select-player="toggleParticipantRecentMatches\(player\)"/)
  assert.match(source, /const data = await getGamingSessionData\(\{ forceRefresh: options\.force === true \}\)/)
  assert.doesNotMatch(source, /apiClient\.getSessionData\(\)/)
  assert.doesNotMatch(source, /DEFAULT_ANALYSIS_QUEUE_MODE/)
  assert.doesNotMatch(source, /apiClient\.getSessionData\([^)]*mode/)
  assert.equal(headerIndex > -1 && teamsIndex > -1 && headerIndex < teamsIndex, true)
  assert.match(source, /\.teams-container\s*{\s*display:\s*grid;[\s\S]*grid-template-columns:\s*1fr;/)
  assert.match(source, /@media \(min-width:\s*1180px\)[\s\S]*grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\)/)
})

test('subscribes to cache update events and refreshes only relevant current-session players', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')

  assert.match(source, /import \{ wsClient \} from '@\/api\/websocketClient'/)
  assert.match(source, /import type \{ CacheUpdateEvent, Lobby, SessionData, SessionSummoner, Summoner \} from '@\/types\/api'/)
  assert.match(source, /let unsubscribeCacheUpdate: \(\(\) => void\) \| null = null/)
  assert.match(source, /unsubscribeCacheUpdate = wsClient\.onCacheUpdate\(\(event: CacheUpdateEvent\) => \{/)
  assert.match(source, /if \(isCacheUpdateRelevant\(event\)\) \{\s*scheduleCacheUpdateRefresh\(\)/)
  assert.match(source, /function collectCurrentSessionPuuids\(\): Set<string>/)
  assert.match(source, /for \(const player of sessionData\.value\.teamOne \|\| \[\]\)/)
  assert.match(source, /for \(const player of sessionData\.value\.teamTwo \|\| \[\]\)/)
  assert.match(source, /const puuid = player\?\.summoner\?\.puuid/)
  assert.match(source, /return currentPuuids\.has\(event\.puuid\)/)
  assert.match(source, /getGamingSessionData\(\{ forceRefresh: options\.force === true \}\)/)
  assert.doesNotMatch(source, /apiClient\.getSessionData\(\)/)
  assert.doesNotMatch(source, /DEFAULT_ANALYSIS_QUEUE_MODE/)
})

test('subscribes to renderer gameflow phase changes and cleans up the listener', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const mountBlock = source.match(/onMounted\(\(\) => \{[\s\S]*?\n\}\)/)?.[0] || ''
  const unmountBlock = source.match(/onUnmounted\(\(\) => \{[\s\S]*?\n\}\)/)?.[0] || ''

  assert.match(source, /import \{ listenGameflowPhase \} from '@\/services\/gameflowPhaseListener'/)
  assert.match(source, /let unsubscribeGameflowPhase: \(\(\) => void\) \| null = null/)
  assert.match(mountBlock, /unsubscribeGameflowPhase = listenGameflowPhase\(handleGameflowPhaseChange\)/)
  assert.match(unmountBlock, /if \(unsubscribeGameflowPhase\) \{/)
  assert.match(unmountBlock, /unsubscribeGameflowPhase\(\)/)
  assert.match(unmountBlock, /unsubscribeGameflowPhase = null/)
})

test('gameflow phase handler refreshes active scout phases and clears stale lobby or post-game data without navigation', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const handler = source.match(/function handleGameflowPhaseChange\(phase: string\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(source, /createGameflowPhaseTransitionTracker/)
  assert.match(source, /const gameflowPhaseTransitions = createGameflowPhaseTransitionTracker\(\)/)
  assert.match(source, /isGameflowLobbyDisplayPhase/)
  assert.match(source, /isGameflowSessionRefreshPhase/)
  assert.match(source, /isGameflowSessionClearPhase/)
  assert.match(handler, /console\.debug\(`\[gameflow\] phase=\$\{phase\}`\)/)
  assert.match(handler, /if \(!gameflowPhaseTransitions\.shouldHandlePhase\(phase\)\) \{[\s\S]*return[\s\S]*\}/)
  assert.match(handler, /isGameflowSessionRefreshPhase\(phase\)[\s\S]*fetchSessionData\(\{ force: true \}\)/)
  assert.match(handler, /未来这里可跳转到对战信息/)
  assert.match(handler, /isGameflowSessionClearPhase\(phase\)[\s\S]*clearSessionDataForPhase\(phase\)/)
  assert.match(handler, /未来这里可跳转到我的战绩/)
  assert.doesNotMatch(handler, /router\.push|useRouter/)
})

test('lobby phase reads lobby status separately without restoring cleared scout teams', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const handler = source.match(/function handleGameflowPhaseChange\(phase: string\) \{[\s\S]*?\n\}/)?.[0] || ''
  const queueName = source.match(/const queueName = computed\(\(\) => \{[\s\S]*?\n\}\)/)?.[0] || ''

  assert.match(source, /import \{ apiClient \} from '@\/api\/httpClient'/)
  assert.match(source, /import type \{ CacheUpdateEvent, Lobby, SessionData, SessionSummoner, Summoner \} from '@\/types\/api'/)
  assert.match(source, /formatLobbyQueueName/)
  assert.match(source, /isGameflowLobbyDisplayPhase/)
  assert.match(source, /buildLobbyDisplaySessionSummoners/)
  assert.match(source, /const lobbyData = ref<Lobby \| null>\(null\)/)
  assert.match(source, /const currentSummoner = ref<Summoner \| null>\(null\)/)
  assert.match(source, /const lobbyTeamPlayers = computed<SessionSummoner\[\]>/)
  assert.match(source, /buildLobbyDisplaySessionSummoners\(lobbyData\.value, currentSummoner\.value, sessionData\.value\)/)
  assert.match(source, /const blueTeamPlayers = computed\(\(\) => hasActiveSession\.value \? \(sessionData\.value\.teamOne \|\| \[\]\) : lobbyTeamPlayers\.value\)/)
  assert.match(source, /let unsubscribeLobby: \(\(\) => void\) \| null = null/)
  assert.match(source, /async function fetchLobbyData/)
  assert.match(source, /const lobby = await apiClient\.getLobby\(\)/)
  assert.match(source, /apiClient\.getGameState\(\)/)
  assert.match(handler, /isGameflowLobbyDisplayPhase\(phase\)[\s\S]*fetchLobbyData\(\{ force: true \}\)/)
  assert.match(handler, /isGameflowLobbyDisplayPhase\(phase\)[\s\S]*fetchSessionData\(\{ showLoading: false, force: true \}\)/)
  assert.match(handler, /clearLobbyStatus\(\)/)
  assert.match(source, /unsubscribeLobby = wsClient\.onLobby/)
  assert.match(queueName, /hasLobbyPhase\.value[\s\S]*lobbyQueueLabel\.value/)
  assert.match(queueName, /isGameflowLobbyDisplayPhase\(sessionData\.value\.phase\)[\s\S]*sessionData\.value\.typeCn/)
  assert.match(queueName, /return '大厅'/)
  assert.doesNotMatch(handler, /router\.push|useRouter/)
})

test('session fetches use request ids, support forced refresh, and clear teams on API failure', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const fetchFunction = source.match(/async function fetchSessionData\([\s\S]*?\n\}/)?.[0] || ''

  assert.match(source, /createGamingSessionDataState/)
  assert.match(source, /const sessionState = createGamingSessionDataState\(\)/)
  assert.match(source, /const initialLoading = ref\(false\)/)
  assert.match(source, /const refreshing = ref\(false\)/)
  assert.match(source, /const lastError = ref\(''\)/)
  assert.match(source, /const loading = computed\(\(\) => initialLoading\.value \|\| refreshing\.value\)/)
  assert.match(fetchFunction, /options: \{ showLoading\?: boolean; force\?: boolean \} = \{\}/)
  assert.match(fetchFunction, /if \(isRefreshPaused\.value \|\| \(sessionFetchInFlight && !options\.force\)\) return/)
  assert.match(fetchFunction, /const shouldShowFetchState = options\.showLoading !== false/)
  assert.match(fetchFunction, /const showInitialLoading = shouldShowFetchState && !hasCompletedInitialSessionFetch/)
  assert.match(fetchFunction, /const requestId = sessionState\.beginFetch\(\)/)
  assert.match(fetchFunction, /const data = await getGamingSessionData\(\{ forceRefresh: options\.force === true \}\)/)
  assert.match(fetchFunction, /sessionState\.applyFetchedData\(requestId, data\)/)
  assert.match(fetchFunction, /sessionState\.applyFetchFailure\(requestId, currentGameflowPhase\.value \|\| sessionData\.value\.phase\)/)
  assert.match(fetchFunction, /lastError\.value = extractFetchErrorMessage\(e\)/)
  assert.match(fetchFunction, /syncSessionDataFromState\(\)/)
  assert.match(fetchFunction, /sessionState\.isCurrentRequest\(requestId\)/)
  assert.match(fetchFunction, /initialLoading\.value = false/)
  assert.match(fetchFunction, /refreshing\.value = false/)
})

test('background polling and retries stay quiet instead of showing global loading', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const mountBlock = source.match(/onMounted\(\(\) => \{[\s\S]*?\n\}\)/)?.[0] || ''
  const watcherBlock = source.match(/watch\(\(\) => sessionData\.value\.phase,[\s\S]*?\n\}\)/)?.[0] || ''
  const retryFunction = source.match(/function checkAndRetryFetch\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(mountBlock, /refreshInterval = setInterval\(\(\) => fetchSessionData\(\{ showLoading: false \}\), 5000\)/)
  assert.match(watcherBlock, /if \(!hasActiveSession\.value\) \{[\s\S]*return[\s\S]*\}/)
  assert.match(watcherBlock, /fetchSessionData\(\{ showLoading: false \}\)/)
  assert.match(retryFunction, /if \(!hasActiveSession\.value\) return/)
  assert.match(retryFunction, /fetchSessionData\(\{ showLoading: false \}\)/)
})

test('active session visibility rejects stale or empty session payloads', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const hasActiveSession = source.match(/const hasActiveSession = computed\(\(\) => \{[\s\S]*?\n\}\)/)?.[0] || ''

  assert.match(hasActiveSession, /!sessionData\.value\.stale/)
  assert.match(hasActiveSession, /!sessionData\.value\.empty/)
  assert.match(hasActiveSession, /!isGameflowSessionClearPhase\(phase\)/)
})

test('gaming phase labels include simulator post-game variants', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const phaseMap = source.match(/const phaseMap: Record<string, MessageKey> = \{[\s\S]*?\n\s*\}/)?.[0] || ''
  const phaseClass = source.match(/const phaseClass = computed\(\(\) => \{[\s\S]*?\n\}\)/)?.[0] || ''

  assert.match(phaseMap, /PostGame:\s*'gaming\.phase\.EndOfGame'/)
  assert.match(phaseMap, /POST_GAME:\s*'gaming\.phase\.EndOfGame'/)
  assert.match(phaseClass, /phase === 'PostGame'/)
  assert.match(phaseClass, /phase === 'POST_GAME'/)
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
  assert.match(fetchFunction, /options: \{ showLoading\?: boolean; force\?: boolean \} = \{\}/)
  assert.match(fetchFunction, /if \(isRefreshPaused\.value \|\| \(sessionFetchInFlight && !options\.force\)\) return/)
  assert.match(fetchFunction, /const shouldShowFetchState = options\.showLoading !== false/)
  assert.match(fetchFunction, /sessionFetchInFlight = true/)
  assert.match(fetchFunction, /if \(showInitialLoading\) \{[\s\S]*initialLoading\.value = true/)
  assert.match(fetchFunction, /else if \(shouldShowFetchState\) \{[\s\S]*refreshing\.value = true/)
  assert.match(fetchFunction, /initialLoading\.value = false/)
  assert.match(fetchFunction, /refreshing\.value = false/)
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

test('gaming player cards render per-card inline recent panels with independent Set expansion', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const toggleFunction = source.match(/function toggleParticipantRecentMatches\(player: SessionSummoner\) \{[\s\S]*?\n\}/)?.[0] || ''
  const cleanupWatcher = source.match(/watch\(\s*\(\) => allSessionPlayers\.value\.map\(getParticipantKey\)\.join\('\|'\),[\s\S]*?\n\)/)?.[0] || ''

  assert.match(source, /import ParticipantRecentMatchesPanel from '@\/components\/gaming\/ParticipantRecentMatchesPanel\.vue'/)
  assert.match(source, /import type \{ CacheUpdateEvent, Lobby, SessionData, SessionSummoner, Summoner \} from '@\/types\/api'/)
  assert.match(source, /const expandedParticipantKeys = ref<Set<string>>\(new Set\(\)\)/)
  assert.match(source, /function getParticipantKey\(player: SessionSummoner \| null \| undefined\): string/)
  assert.match(source, /return `puuid:\$\{puuid\}`/)
  assert.match(source, /return `summoner:\$\{summonerId\}`/)
  assert.match(source, /return `riot:\$\{gameName\}#\$\{tagLine \|\| ''\}`/)
  assert.match(source, /:key="getParticipantKey\(player\) \|\| `blue-\$\{idx\}`"/)
  assert.match(source, /:key="getParticipantKey\(player\) \|\| `red-\$\{idx\}`"/)
  assert.match(source, /:selected="isParticipantExpanded\(player\)"/)
  assert.match(source, /@select-player="toggleParticipantRecentMatches\(player\)"/)
  assert.equal(source.match(/<ParticipantRecentMatchesPanel/g)?.length, 2)
  assert.match(source, /<ParticipantRecentMatchesPanel[\s\S]*v-if="isParticipantExpanded\(player\)"[\s\S]*:player="player"/)
  assert.match(toggleFunction, /const nextKeys = new Set\(expandedParticipantKeys\.value\)/)
  assert.match(toggleFunction, /nextKeys\.delete\(key\)/)
  assert.match(toggleFunction, /nextKeys\.add\(key\)/)
  assert.match(toggleFunction, /expandedParticipantKeys\.value = nextKeys/)
  assert.match(cleanupWatcher, /const currentKeys = new Set/)
  assert.match(cleanupWatcher, /nextKeys\.size !== expandedParticipantKeys\.value\.size/)
  assert.doesNotMatch(source, /const selectedPlayerKey|selectedSessionSummoner|selected-player-recent-panel/)
  assert.doesNotMatch(source, /useRouter|router\.push/)
})

test('gaming AI analysis buttons open modal for teammates and opponents without disabling entry buttons', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const blueButton = source.match(/<button[\s\S]*?@click="openGamingAiAnalysis\('teammate'\)"[\s\S]*?<\/button>/)?.[0] || ''
  const redButton = source.match(/<button[\s\S]*?@click="openGamingAiAnalysis\('opponent'\)"[\s\S]*?<\/button>/)?.[0] || ''

  assert.match(source, /import GamingAiAnalysisModal from '@\/components\/gaming\/GamingAiAnalysisModal\.vue'/)
  assert.match(source, /import \{[\s\S]*createGamingAiAnalysisPreview[\s\S]*\} from '@\/services\/gamingAiAnalysisPreview'/)
  assert.match(source, /GamingAiAnalysisMode/)
  assert.match(source, /GamingAiAnalysisPreview/)
  assert.match(blueButton, /@click="openGamingAiAnalysis\('teammate'\)"/)
  assert.match(redButton, /@click="openGamingAiAnalysis\('opponent'\)"/)
  assert.doesNotMatch(blueButton, /aria-disabled/)
  assert.doesNotMatch(redButton, /aria-disabled/)
  assert.match(source, /const gamingAiModalOpen = ref\(false\)/)
  assert.match(source, /const gamingAiModalMode = ref<GamingAiAnalysisMode>\('teammate'\)/)
  assert.match(source, /const gamingAiPreview = ref<GamingAiAnalysisPreview \| null>\(null\)/)
  assert.match(source, /const gamingAiStreamState = ref<GamingAiStreamState>\('idle'\)/)
  assert.match(source, /const gamingAiStreamText = ref\(''\)/)
  assert.match(source, /const gamingAiStreamError = ref\(''\)/)
  assert.match(source, /const gamingAiPlayerVerdicts = ref<Record<string, GamingAiPlayerStreamVerdict>>\(\{\}\)/)
  assert.match(source, /const gamingAiPlayerInsights = ref<Record<string, GamingAiPlayerInsightEvent>>\(\{\}\)/)
  assert.match(source, /const gamingAiAnalysisReady = computed\(\(\) => isGamingAiAnalysisReady\(\{/)
  assert.match(source, /function openGamingAiAnalysis\(mode: GamingAiAnalysisMode\)/)
  assert.match(source, /function refreshGamingAiPreview\(mode: GamingAiAnalysisMode = gamingAiModalMode\.value\)/)
  assert.match(source, /const players = mode === 'teammate' \? blueTeamPlayers\.value : redTeamPlayers\.value/)
  assert.match(source, /createGamingAiAnalysisPreview\(\{[\s\S]*players,[\s\S]*sessionData: sessionData\.value/)
  assert.match(source, /:queue-label="gamingAiQueueLabel"/)
  assert.match(source, /:analysis-enabled="gamingAiAnalysisReady"/)
  assert.match(source, /:player-verdicts="gamingAiPlayerVerdicts"/)
  assert.match(source, /:player-insights="gamingAiPlayerInsights"/)
  assert.match(source, /<GamingAiAnalysisModal[\s\S]*:open="gamingAiModalOpen"[\s\S]*:mode="gamingAiModalMode"[\s\S]*:preview="gamingAiPreview"[\s\S]*:queue-label="gamingAiQueueLabel"[\s\S]*:analysis-enabled="gamingAiAnalysisReady"[\s\S]*:stream-state="gamingAiStreamState"[\s\S]*:stream-error="gamingAiStreamError"[\s\S]*:player-verdicts="gamingAiPlayerVerdicts"[\s\S]*:player-insights="gamingAiPlayerInsights"[\s\S]*@start-analysis="startGamingAiServerAnalysis"[\s\S]*@cancel-analysis="cancelGamingAiServerAnalysis"[\s\S]*@close="closeGamingAiAnalysis"/)
})

test('gaming AI analysis only builds and streams the snapshot after modal start-analysis', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const fetchSessionBlock = source.slice(
    source.indexOf('async function fetchSessionData'),
    source.indexOf('function collectCurrentSessionPuuids')
  )
  const openAnalysisBlock = source.slice(
    source.indexOf('function openGamingAiAnalysis'),
    source.indexOf('function closeGamingAiAnalysis')
  )
  const startAnalysisBlock = source.slice(
    source.indexOf('async function startGamingAiServerAnalysis'),
    source.indexOf('function cancelGamingAiServerAnalysis')
  )

  assert.match(source, /import \{ buildGamingAiInputSnapshot \} from '@\/services\/gamingAiInputSnapshot'/)
  assert.match(source, /isGamingAiAnalysisReady/)
  assert.match(source, /createGamingAiStreamRequest/)
  assert.match(source, /streamGamingAiAnalysis/)
  assert.match(source, /let gamingAiStreamAbortController: AbortController \| null = null/)
  assert.doesNotMatch(openAnalysisBlock, /buildGamingAiInputSnapshot/)
  assert.doesNotMatch(openAnalysisBlock, /streamGamingAiAnalysis/)
  assert.match(startAnalysisBlock, /if \(!isGamingAiAnalysisReady\(\{[\s\S]*mode: gamingAiModalMode\.value,[\s\S]*sessionData: sessionData\.value[\s\S]*\}\)\) \{/)
  assert.match(startAnalysisBlock, /return/)
  assert.match(startAnalysisBlock, /const players = gamingAiModalMode\.value === 'teammate' \? blueTeamPlayers\.value : redTeamPlayers\.value/)
  assert.match(startAnalysisBlock, /buildGamingAiInputSnapshot\(\{/)
  assert.match(startAnalysisBlock, /selectedPlayers: players/)
  assert.match(startAnalysisBlock, /createGamingAiStreamRequest\(snapshot\)/)
  assert.match(startAnalysisBlock, /streamGamingAiAnalysis\(/)
  assert.match(startAnalysisBlock, /gamingAiPlayerVerdicts\.value = \{\}/)
  assert.match(startAnalysisBlock, /gamingAiPlayerInsights\.value = \{\}/)
  assert.match(startAnalysisBlock, /event\.type === 'player_insight'/)
  assert.match(startAnalysisBlock, /gamingAiPlayerInsights\.value = \{[\s\S]*\[event\.playerKey\]: event/)
  assert.match(startAnalysisBlock, /event\.type === 'player_verdict'/)
  assert.match(startAnalysisBlock, /gamingAiPlayerVerdicts\.value = \{[\s\S]*\[event\.playerKey\]: event/)
  assert.doesNotMatch(startAnalysisBlock, /gamingAiStreamText\.value \+= text/)
  assert.match(startAnalysisBlock, /onError: \(message\) => \{/)
  assert.match(startAnalysisBlock, /gamingAiStreamState\.value = 'failed'/)
  assert.match(startAnalysisBlock, /onDone: \(\) => \{/)
  assert.match(startAnalysisBlock, /gamingAiStreamState\.value = 'completed'/)
  assert.doesNotMatch(fetchSessionBlock, /buildGamingAiInputSnapshot/)
  assert.doesNotMatch(fetchSessionBlock, /streamGamingAiAnalysis/)
})

test('gaming AI queue labels normalize ranked modes and exclude phase prefixes', () => {
  assert.equal(normalizeGamingQueueLabel({ queueId: 420, typeCn: '单排/双排' }), '单双排位')
  assert.equal(normalizeGamingQueueLabel({ queueId: 420, typeCn: '单双排' }), '单双排位')
  assert.equal(normalizeGamingQueueLabel({ queueId: 420, typeCn: '单双排位' }), '单双排位')
  assert.equal(normalizeGamingQueueLabel({ queueId: 440, typeCn: '灵活组排' }), '灵活排位')
  assert.equal(normalizeGamingQueueLabel({ queueId: 440, typeCn: '灵活排位' }), '灵活排位')
  assert.equal(normalizeGamingQueueLabel({ queueId: 450, typeCn: '极地大乱斗' }), '海克斯大乱斗')
  assert.equal(normalizeGamingQueueLabel({ queueId: 0, typeCn: '海克斯大乱斗' }), '海克斯大乱斗')
  assert.equal(normalizeGamingQueueLabel({ queueId: 0, typeCn: '匹配' }), '匹配')
  assert.equal(isGamingAiAnalysisEnabledQueue({ queueId: 420, typeCn: '单排/双排' }), true)
  assert.equal(isGamingAiAnalysisEnabledQueue({ queueId: 440, typeCn: '灵活组排' }), true)
  assert.equal(isGamingAiAnalysisEnabledQueue({ queueId: 450, typeCn: '极地大乱斗' }), false)
})

test('gaming AI modal subtitle source uses queue label without phase prefixes', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')

  assert.match(source, /const gamingAiQueueLabel = computed\(\(\) => normalizeGamingQueueLabel\(sessionData\.value\)\)/)
  assert.match(source, /:queue-label="gamingAiQueueLabel"/)
  assert.doesNotMatch(source, /大厅 ·|英雄选择 ·|游戏中 ·/)
})
