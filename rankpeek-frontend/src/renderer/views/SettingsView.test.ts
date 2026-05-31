import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./SettingsView.vue', import.meta.url), 'utf8')
const zh = readFileSync(new URL('../i18n/locales/zh-CN.ts', import.meta.url), 'utf8')
const en = readFileSync(new URL('../i18n/locales/en-US.ts', import.meta.url), 'utf8')
const oldAuthClientPattern = new RegExp(['rankpeek', 'AuthClient'].join(''))

test('settings page uses local AI provider configuration', () => {
  assert.match(source, /getLocalAiProviders/)
  assert.match(source, /getLocalAiSettings/)
  assert.match(source, /saveLocalAiSettings/)
  assert.match(source, /localAiProviderProfiles/)
  assert.match(source, /aiProviderForm/)
  assert.match(source, /apiKeyInput/)
  assert.match(source, /saveApiKey/)
  assert.match(source, /testLocalAiProviderConnection/)
  assert.match(source, /settings\.aiProviderTitle/)
  assert.match(source, /settings\.aiProviderDescription/)
  assert.match(source, /settings\.aiProviderSelect/)
  assert.match(source, /settings\.aiBaseUrl/)
  assert.match(source, /settings\.aiModel/)
  assert.match(source, /settings\.aiApiKey/)
  assert.match(source, /settings\.aiSaveApiKey/)
  assert.match(source, /settings\.aiTemperature/)
  assert.match(source, /settings\.aiMaxTokens/)
  assert.match(source, /settings\.aiPricingMode/)
  assert.match(source, /settings\.aiTestConnection/)
  assert.match(source, /settings\.aiSavedKey/)

  assert.doesNotMatch(source, oldAuthClientPattern)
  assert.doesNotMatch(source, /loginRankPeekAccount|registerRankPeekAccount|requestRankPeekRegisterEmailCode|requestRankPeekPasswordReset/)
  assert.doesNotMatch(source, /authModalOpen|auth-form|authVerificationCode|handleLogout/)
  assert.doesNotMatch(source, /settings\.accountTitle|settings\.login|settings\.logout/)
})

test('settings page keeps common local settings and local service diagnostics', () => {
  assert.match(source, /defaultMatchQueueMode/)
  assert.match(source, /saveMatchSettings/)
  assert.match(source, /clearUserCache/)
  assert.match(source, /themeStore\.setTheme\('light'\)/)
  assert.match(source, /themeStore\.setTheme\('dark'\)/)
  assert.match(source, /checkRankPeekServerDiagnostics/)
  assert.match(source, /checkLocalRankPeekServer/)
})

test('settings local AI copy exists in both locales', () => {
  for (const key of [
    'settings.aiProviderTitle',
    'settings.aiProviderDescription',
    'settings.aiProviderSelect',
    'settings.aiEnabled',
    'settings.aiBaseUrl',
    'settings.aiModel',
    'settings.aiApiKey',
    'settings.aiApiKeyPlaceholder',
    'settings.aiSaveApiKey',
    'settings.aiSavedKey',
    'settings.aiNoSavedKey',
    'settings.aiTemperature',
    'settings.aiMaxTokens',
    'settings.aiPricingMode',
    'settings.aiPricingPreset',
    'settings.aiPricingCustom',
    'settings.aiInputCacheHitPrice',
    'settings.aiInputCacheMissPrice',
    'settings.aiOutputPrice',
    'settings.aiSaveProvider',
    'settings.aiTestConnection',
    'settings.aiSettingsSaved',
    'settings.aiConnectionReady',
    'settings.aiSettingsUnavailable'
  ]) {
    assert.ok(zh.includes(`'${key}'`), `zh-CN should include ${key}`)
    assert.ok(en.includes(`'${key}'`), `en-US should include ${key}`)
  }
})
