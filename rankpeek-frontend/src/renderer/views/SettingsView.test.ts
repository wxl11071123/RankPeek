import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./SettingsView.vue', import.meta.url), 'utf8')
const zh = readFileSync(new URL('../i18n/locales/zh-CN.ts', import.meta.url), 'utf8')
const en = readFileSync(new URL('../i18n/locales/en-US.ts', import.meta.url), 'utf8')

test('settings page is organized for users instead of diagnostics', () => {
  const accountIndex = source.indexOf('class="account-card"')
  const essentialsIndex = source.indexOf('class="settings-section essentials-section"')
  const aboutIndex = source.indexOf('class="settings-section about-section"')

  assert.ok(accountIndex > -1, 'account card should render first')
  assert.ok(essentialsIndex > accountIndex, 'common settings should follow account card')
  assert.ok(aboutIndex > essentialsIndex, 'about section should move below common settings')

  assert.match(source, /settings\.accountTitle/)
  assert.match(source, /settings\.accountDescription/)
  assert.match(source, /@click="openAuthModal\('login'\)"/)
  assert.doesNotMatch(source, /@click="handleAccountAction\('register'\)"/)
})

test('settings account card uses one login entry and shows stored account state', () => {
  const accountActions = source.match(/<div class="account-actions">[\s\S]*?<\/div>/)?.[0] || ''

  assert.match(source, /import \{[\s\S]*getStoredRankPeekAuthSession[\s\S]*logoutRankPeekAccount[\s\S]*storeRankPeekAuthSession/)
  assert.match(source, /const authSession = ref<RankPeekAuthSession \| null>\(getStoredRankPeekAuthSession\(\)\)/)
  assert.match(source, /const signedInUser = computed\(\(\) => authSession\.value\?\.user \?\? null\)/)
  assert.match(source, /settings\.signedInAs/)
  assert.match(source, /settings\.accountRole/)
  assert.match(source, /settings\.logout/)
  assert.match(accountActions, /settings\.login/)
  assert.doesNotMatch(accountActions, /settings\.register/)
})

test('settings auth modal supports login first with an inline register mode', () => {
  assert.match(source, /v-if="authModalOpen"/)
  assert.match(source, /<form[\s\S]*class="auth-form"[\s\S]*@submit\.prevent="submitAuthForm"[\s\S]*>/)
  assert.match(source, /v-model\.trim="authEmail"/)
  assert.match(source, /v-model="authPassword"/)
  assert.match(source, /v-model="authPasswordConfirm"/)
  assert.match(source, /showAuthPassword/)
  assert.match(source, /showAuthPasswordConfirm/)
  assert.match(source, /authMode === 'register'/)
  assert.match(source, /forgotPassword/)
  assert.match(source, /@click="switchAuthMode\('register'\)"/)
  assert.match(source, /@click="switchAuthMode\('login'\)"/)
  assert.match(source, /@click="switchAuthMode\('forgotPassword'\)"/)
  assert.match(source, /loginRankPeekAccount\(/)
  assert.match(source, /registerRankPeekAccount\(/)
  assert.match(source, /requestRankPeekPasswordReset\(/)
  assert.match(source, /storeRankPeekAuthSession\(result\.session\)/)
  assert.match(source, /settings\.authSwitchToRegister/)
  assert.match(source, /settings\.authSwitchToLogin/)
  assert.match(source, /settings\.authForgotPassword/)
  assert.match(source, /settings\.authPasswordMismatch/)
  assert.match(source, /settings\.authResetRequestSent/)
  assert.doesNotMatch(source, /authDisplayName/)
  assert.doesNotMatch(source, /settings\.authDisplayName/)
  assert.doesNotMatch(source, /name="displayName"/)
})

test('settings page keeps only the three common user settings', () => {
  assert.match(source, /defaultMatchQueueMode/)
  assert.match(source, /matchModeOptions/)
  assert.match(source, /async function saveMatchSettings\(\)/)
  assert.match(source, /apiClient\.setConfig\(['"]settings\.match\.defaultQueueMode['"], defaultMatchQueueMode\.value\)/)
  assert.match(source, /setCachedDefaultMatchQueueMode\(defaultMatchQueueMode\.value\)/)
  assert.match(source, /settings\.defaultMatchModeUser/)
  assert.match(source, /settings\.defaultMatchModeUserDescription/)
  assert.match(source, /settings\.saveDefaultMode/)

  assert.match(source, /async function clearUserCache\(mode: CacheClearMode\)/)
  assert.match(source, /clearFrontendTransientCache\(\)/)
  assert.match(source, /apiClient\.clearCache\(['"]all['"],\s*true,\s*mode\)/)
  assert.match(source, /buildCacheClearAlertMessage\(/)
  assert.match(source, /extractCacheClearErrorMessage\(/)
  assert.match(source, /settings\.clearCacheUser/)
  assert.match(source, /settings\.clearCacheUserDescription/)
  assert.match(source, /settings\.normalClearCacheAction/)
  assert.match(source, /settings\.deepClearCacheAction/)
  assert.match(source, /window\.electronAPI\?\.clearChromiumCache/)
  assert.match(source, /window\.electronAPI\?\.database\?\.runStorageRetention/)
  assert.match(source, /settings\.clearCachePartialFailed/)
  assert.doesNotMatch(source, /@click="clearLocalCache\('memory'\)"/)
  assert.doesNotMatch(source, /@click="clearLocalCache\('localDb'\)"/)
  assert.doesNotMatch(source, /deleteAiMemory|clearAiMemory|deleteMemory|clearMemory/)

  assert.match(source, /themeStore\.setTheme\('light'\)/)
  assert.match(source, /themeStore\.setTheme\('dark'\)/)
  assert.match(source, /settings\.appearanceTheme/)
  assert.match(source, /settings\.appearanceThemeDescription/)
})

test('settings cache clear buttons explain normal and deep clearing with accessible tooltips', () => {
  assert.match(source, /id="normal-cache-clear-tooltip"/)
  assert.match(source, /id="deep-cache-clear-tooltip"/)
  assert.match(source, /aria-describedby="normal-cache-clear-tooltip"/)
  assert.match(source, /aria-describedby="deep-cache-clear-tooltip"/)
  assert.match(source, /role="tooltip"/)
  assert.match(source, /settings\.normalClearCacheTooltip/)
  assert.match(source, /settings\.deepClearCacheTooltip/)
  assert.match(source, /\.cache-clear-tooltip-anchor:hover \.cache-clear-tooltip/)
  assert.match(source, /\.cache-clear-tooltip-anchor:focus-within \.cache-clear-tooltip/)
  assert.match(source, /\.cache-clear-tooltip\s*\{[\s\S]*opacity:\s*0/)
  assert.match(source, /\.cache-clear-tooltip\s*\{[\s\S]*visibility:\s*hidden/)
  assert.doesNotMatch(source, /title="[^"]*Clear/)
  assert.doesNotMatch(source, /title="\{\{ t\('settings\.(?:normalClearCacheTooltip|deepClearCacheTooltip)'\) \}\}"/)
})

test('settings account card checks local rankpeek-server without exposing raw diagnostics', () => {
  assert.match(source, /checkRankPeekServerDiagnostics/)
  assert.match(source, /async function checkLocalRankPeekServer\(\)/)
  assert.match(source, /settings\.checkLocalServer/)
  assert.match(source, /settings\.localServerAvailable/)
  assert.match(source, /settings\.localServerUnavailable/)

  for (const forbidden of [
    'database.status',
    'productName',
    'productVersion',
    'flyway',
    'currentVersion',
    'appliedCount'
  ]) {
    assert.ok(!source.includes(forbidden), `raw server diagnostics should stay hidden: ${forbidden}`)
  }
})

test('settings page no longer exposes developer panels or raw cache fields', () => {
  for (const forbidden of [
    'settings.shortcuts',
    'shortcutDevTools',
    'F12',
    'settings.exportConfig',
    'settings.importConfig',
    'exportConfig',
    'importConfig',
    'cacheStats',
    'userStoreStats',
    'loadCacheStatus',
    'loadUserStoreStatus',
    'getCacheStatus',
    'getUserStoreStatus',
    'enabled',
    'databaseSizeBytes',
    'summonerCount',
    'rankCount',
    'matchCount',
    'gameDetailCount',
    'participantCount',
    'trackedPlayerCount',
    'latestMatchCreation'
  ]) {
    assert.ok(!source.includes(forbidden), `developer content should be hidden: ${forbidden}`)
  }
})

test('settings copy is user-facing in both locales', () => {
  for (const key of [
    'settings.accountTitle',
    'settings.accountDescription',
    'settings.login',
    'settings.register',
    'settings.logout',
    'settings.authEmail',
    'settings.authPassword',
    'settings.authPasswordConfirm',
    'settings.authPasswordShow',
    'settings.authPasswordHide',
    'settings.authSubmitLogin',
    'settings.authSubmitRegister',
    'settings.authSubmitForgotPassword',
    'settings.authSwitchToRegister',
    'settings.authSwitchToLogin',
    'settings.authForgotPassword',
    'settings.authLoginTitle',
    'settings.authRegisterTitle',
    'settings.authForgotTitle',
    'settings.authLoginFailed',
    'settings.authRegisterFailed',
    'settings.authRequiredFields',
    'settings.authPasswordMismatch',
    'settings.authResetRequestSent',
    'settings.signedInAs',
    'settings.accountRole',
    'settings.checkLocalServer',
    'settings.checkingLocalServer',
    'settings.localServerAvailable',
    'settings.localServerUnavailable',
    'settings.commonSettings',
    'settings.defaultMatchModeUser',
    'settings.defaultMatchModeUserDescription',
    'settings.clearCacheUser',
    'settings.clearCacheUserDescription',
    'settings.normalClearCacheAction',
    'settings.normalClearCacheTooltip',
    'settings.deepClearCacheAction',
    'settings.deepClearCacheTooltip',
    'settings.appearanceTheme',
    'settings.appearanceThemeDescription',
    'settings.aboutRankPeek'
  ]) {
    assert.ok(zh.includes(`'${key}'`), `zh-CN should include ${key}`)
    assert.ok(en.includes(`'${key}'`), `en-US should include ${key}`)
  }

  assert.match(zh, /'settings\.accountTitle': 'RankPeek 账号'/)
  assert.match(zh, /'settings\.checkLocalServer': '检查连接'/)
  assert.match(en, /'settings\.accountTitle': 'RankPeek Account'/)
  assert.match(en, /'settings\.checkLocalServer': 'Check Connection'/)
})
