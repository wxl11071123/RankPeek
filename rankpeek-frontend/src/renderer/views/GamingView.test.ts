import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  isGamingAiAnalysisEnabledQueue,
  normalizeGamingQueueLabel
} from '../services/gamingAiQueue.ts'

test('gaming refresh action uses the shared refresh icon button', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')

  assert.match(source, /import RefreshIconButton from '@\/components\/common\/RefreshIconButton\.vue'/)
  assert.match(source, /<RefreshIconButton[\s\S]*:aria-label="refreshButtonLabel"[\s\S]*:loading="loading"[\s\S]*:disabled="loading"[\s\S]*@click="\(\) => fetchSessionData\(\)"/)
  assert.doesNotMatch(source, /class="refresh-btn-small"/)
  assert.doesNotMatch(source, /\.refresh-btn-small/)
  assert.doesNotMatch(source, /class="refresh-icon"[\s\S]*spinning/)
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
  assert.match(source, /const data = await getGamingSessionData\(\)/)
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
  assert.match(source, /import type \{ CacheUpdateEvent, SessionData, SessionSummoner \} from '@\/types\/api'/)
  assert.match(source, /let unsubscribeCacheUpdate: \(\(\) => void\) \| null = null/)
  assert.match(source, /unsubscribeCacheUpdate = wsClient\.onCacheUpdate\(\(event: CacheUpdateEvent\) => \{/)
  assert.match(source, /if \(isCacheUpdateRelevant\(event\)\) \{\s*scheduleCacheUpdateRefresh\(\)/)
  assert.match(source, /function collectCurrentSessionPuuids\(\): Set<string>/)
  assert.match(source, /for \(const player of sessionData\.value\.teamOne \|\| \[\]\)/)
  assert.match(source, /for \(const player of sessionData\.value\.teamTwo \|\| \[\]\)/)
  assert.match(source, /const puuid = player\?\.summoner\?\.puuid/)
  assert.match(source, /return currentPuuids\.has\(event\.puuid\)/)
  assert.match(source, /getGamingSessionData\(\)/)
  assert.doesNotMatch(source, /apiClient\.getSessionData\(\)/)
  assert.doesNotMatch(source, /DEFAULT_ANALYSIS_QUEUE_MODE/)
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

test('gaming player cards render per-card inline recent panels with independent Set expansion', () => {
  const source = readFileSync(new URL('./GamingView.vue', import.meta.url), 'utf8')
  const toggleFunction = source.match(/function toggleParticipantRecentMatches\(player: SessionSummoner\) \{[\s\S]*?\n\}/)?.[0] || ''
  const cleanupWatcher = source.match(/watch\(\s*\(\) => allSessionPlayers\.value\.map\(getParticipantKey\)\.join\('\|'\),[\s\S]*?\n\)/)?.[0] || ''

  assert.match(source, /import ParticipantRecentMatchesPanel from '@\/components\/gaming\/ParticipantRecentMatchesPanel\.vue'/)
  assert.match(source, /import type \{ CacheUpdateEvent, SessionData, SessionSummoner \} from '@\/types\/api'/)
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
  assert.match(source, /function openGamingAiAnalysis\(mode: GamingAiAnalysisMode\)/)
  assert.match(source, /:queue-label="gamingAiQueueLabel"/)
  assert.match(source, /:analysis-enabled="gamingAiAnalysisEnabled"/)
  assert.match(source, /:player-verdicts="gamingAiPlayerVerdicts"/)
  assert.match(source, /<GamingAiAnalysisModal[\s\S]*:open="gamingAiModalOpen"[\s\S]*:mode="gamingAiModalMode"[\s\S]*:preview="gamingAiPreview"[\s\S]*:queue-label="gamingAiQueueLabel"[\s\S]*:analysis-enabled="gamingAiAnalysisEnabled"[\s\S]*:stream-state="gamingAiStreamState"[\s\S]*:stream-text="gamingAiStreamText"[\s\S]*:stream-error="gamingAiStreamError"[\s\S]*:player-verdicts="gamingAiPlayerVerdicts"[\s\S]*@start-analysis="startGamingAiServerAnalysis"[\s\S]*@cancel-analysis="cancelGamingAiServerAnalysis"[\s\S]*@close="closeGamingAiAnalysis"/)
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
  assert.match(source, /isGamingAiAnalysisEnabledQueue/)
  assert.match(source, /createGamingAiStreamRequest/)
  assert.match(source, /streamGamingAiAnalysis/)
  assert.match(source, /let gamingAiStreamAbortController: AbortController \| null = null/)
  assert.doesNotMatch(openAnalysisBlock, /buildGamingAiInputSnapshot/)
  assert.doesNotMatch(openAnalysisBlock, /streamGamingAiAnalysis/)
  assert.match(startAnalysisBlock, /if \(!isGamingAiAnalysisEnabledQueue\(sessionData\.value\)\) \{/)
  assert.match(startAnalysisBlock, /return/)
  assert.match(startAnalysisBlock, /const players = gamingAiModalMode\.value === 'teammate' \? blueTeamPlayers\.value : redTeamPlayers\.value/)
  assert.match(startAnalysisBlock, /buildGamingAiInputSnapshot\(\{/)
  assert.match(startAnalysisBlock, /selectedPlayers: players/)
  assert.match(startAnalysisBlock, /createGamingAiStreamRequest\(snapshot\)/)
  assert.match(startAnalysisBlock, /streamGamingAiAnalysis\(/)
  assert.match(startAnalysisBlock, /gamingAiPlayerVerdicts\.value = \{\}/)
  assert.match(startAnalysisBlock, /event\.type === 'player_verdict'/)
  assert.match(startAnalysisBlock, /gamingAiPlayerVerdicts\.value = \{[\s\S]*\[event\.playerKey\]: event/)
  assert.match(startAnalysisBlock, /onDelta: \(text\) => \{/)
  assert.match(startAnalysisBlock, /gamingAiStreamText\.value \+= text/)
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
  assert.equal(normalizeGamingQueueLabel({ queueId: 450, typeCn: '极地大乱斗' }), '未知模式')
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
