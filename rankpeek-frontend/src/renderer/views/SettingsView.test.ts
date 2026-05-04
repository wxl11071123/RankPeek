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
  assert.match(source, /@click="handleAccountAction\('login'\)"/)
  assert.match(source, /@click="handleAccountAction\('register'\)"/)
  assert.doesNotMatch(source, /apiClient\.(login|register|auth)/)
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

  assert.match(source, /async function clearUserCache\(\)/)
  assert.match(source, /clearFrontendTransientCache\(\)/)
  assert.match(source, /apiClient\.clearCache\(['"]all['"]\)/)
  assert.match(source, /settings\.clearCacheUser/)
  assert.match(source, /settings\.clearCacheUserDescription/)
  assert.doesNotMatch(source, /@click="clearLocalCache\('memory'\)"/)
  assert.doesNotMatch(source, /@click="clearLocalCache\('localDb'\)"/)

  assert.match(source, /themeStore\.setTheme\('light'\)/)
  assert.match(source, /themeStore\.setTheme\('dark'\)/)
  assert.match(source, /settings\.appearanceTheme/)
  assert.match(source, /settings\.appearanceThemeDescription/)
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
    'settings.commonSettings',
    'settings.defaultMatchModeUser',
    'settings.defaultMatchModeUserDescription',
    'settings.clearCacheUser',
    'settings.clearCacheUserDescription',
    'settings.appearanceTheme',
    'settings.appearanceThemeDescription',
    'settings.aboutRankPeek'
  ]) {
    assert.ok(zh.includes(`'${key}'`), `zh-CN should include ${key}`)
    assert.ok(en.includes(`'${key}'`), `en-US should include ${key}`)
  }

  assert.match(zh, /'settings\.accountTitle': 'RankPeek 账号'/)
  assert.match(en, /'settings\.accountTitle': 'RankPeek Account'/)
})
