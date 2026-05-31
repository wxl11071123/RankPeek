<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { apiClient } from '@/api/httpClient'
import { useThemeStore } from '@/stores/theme'
import { useI18n } from '@/i18n'
import type { GameModeOption } from '@/types/api'
import type { CacheClearMode } from '@/types/api'
import {
  buildCacheClearAlertMessage,
  extractCacheClearErrorMessage
} from '@/services/cacheClearFeedback'
import {
  getLocalAiProviders,
  getLocalAiSettings,
  saveLocalAiSettings,
  type LocalAiPricing,
  type LocalAiProviderProfile,
  type LocalAiSettings,
  type SaveLocalAiSettingsRequest
} from '@/services/localAiProviderClient'
import { checkRankPeekServerDiagnostics } from '@/services/rankpeekServerClient'
import { clearFrontendTransientCache } from '@/utils/frontendCache'
import { getDefaultMatchQueueMode, setCachedDefaultMatchQueueMode } from '@/utils/matchPreferences'
import brandSymbolBlack from '@/assets/branding/rankpeek-symbol-black.png'
import brandSymbolWhite from '@/assets/branding/rankpeek-symbol-white.png'
import brandEyeBlack from '@/assets/branding/rankpeek-eye-black.png'
import brandEyeWhite from '@/assets/branding/rankpeek-eye-white.png'

type PricingMode = 'preset' | 'custom'
interface AiProviderFormState extends Omit<SaveLocalAiSettingsRequest, 'pricing'> {
  pricing: LocalAiPricing
}

const themeStore = useThemeStore()
const { t } = useI18n()

const appVersion = ref('1.0.0')
const defaultMatchQueueMode = ref(0)
const matchModeOptions = ref<GameModeOption[]>([])
const savingMatchSettings = ref(false)
const clearingUserCacheMode = ref<CacheClearMode | null>(null)
const checkingLocalServer = ref(false)
const localAiProviderProfiles = ref<LocalAiProviderProfile[]>([])
const localAiSettings = ref<LocalAiSettings | null>(null)
const loadingAiSettings = ref(false)
const savingAiSettings = ref(false)
const testingAiConnection = ref(false)
const aiProviderStatusMessage = ref('')
const apiKeyInput = ref('')
const saveApiKey = ref(false)
const pricingMode = ref<PricingMode>('preset')

const defaultAiPricing = {
  currency: 'CNY',
  inputCacheHitCnyPerMillionTokens: 0.02,
  inputCacheMissCnyPerMillionTokens: 1,
  outputCnyPerMillionTokens: 2
}

const aiProviderForm = reactive<AiProviderFormState>({
  enabled: true,
  providerId: 'deepseek',
  baseUrl: 'https://api.deepseek.com',
  model: 'deepseek-v4-flash',
  apiKey: '',
  saveApiKey: false,
  temperature: 0.4,
  maxTokens: 4096,
  pricing: { ...defaultAiPricing }
})

const githubRepoUrl = 'https://github.com/wxl11071123/rankpeek'
const githubIssuesUrl = 'https://github.com/wxl11071123/rankpeek/issues'

const showcaseBackgroundLines = computed(() => [
  t('settings.showcaseLine1'),
  t('settings.showcaseLine2'),
  t('settings.showcaseLine3')
])

const aboutLogoSrc = computed(() =>
  themeStore.theme === 'dark' ? brandSymbolBlack : brandSymbolWhite
)

const aboutShowcaseSrc = computed(() =>
  themeStore.theme === 'dark' ? brandEyeBlack : brandEyeWhite
)

const selectedProviderProfile = computed(() =>
  localAiProviderProfiles.value.find(provider => provider.id === aiProviderForm.providerId) ?? null
)

const aiSavedKeyLabel = computed(() => {
  if (!localAiSettings.value?.apiKeySaved) {
    return t('settings.aiNoSavedKey')
  }
  return localAiSettings.value.apiKeyMasked
    ? t('settings.aiSavedKey', { key: localAiSettings.value.apiKeyMasked })
    : t('settings.aiSavedKey', { key: 'saved' })
})

if (window.electronAPI) {
  window.electronAPI.getVersion().then(version => {
    appVersion.value = version
  })
}

onMounted(() => {
  void loadUserSettings()
  void loadLocalAiProviderSettings()
})

async function loadUserSettings() {
  try {
    const [config, modes, savedDefaultQueueMode] = await Promise.all([
      apiClient.getConfig(),
      apiClient.getGameModes(),
      getDefaultMatchQueueMode(true)
    ])

    matchModeOptions.value = modes
    defaultMatchQueueMode.value = config?.settings?.match?.defaultQueueMode ?? savedDefaultQueueMode
  } catch (error) {
    console.error('Failed to load settings', error)
  }
}

async function loadLocalAiProviderSettings() {
  loadingAiSettings.value = true
  aiProviderStatusMessage.value = ''

  try {
    const [providers, settings] = await Promise.all([
      getLocalAiProviders(),
      getLocalAiSettings()
    ])
    localAiProviderProfiles.value = providers
    applyLocalAiSettings(settings)
  } catch (error) {
    console.error('Failed to load local AI settings', error)
    aiProviderStatusMessage.value = t('settings.aiSettingsUnavailable')
  } finally {
    loadingAiSettings.value = false
  }
}

function applyLocalAiSettings(settings: LocalAiSettings) {
  localAiSettings.value = settings
  aiProviderForm.enabled = settings.enabled
  aiProviderForm.providerId = settings.providerId
  aiProviderForm.baseUrl = settings.baseUrl
  aiProviderForm.model = settings.model
  aiProviderForm.temperature = settings.temperature
  aiProviderForm.maxTokens = settings.maxTokens
  aiProviderForm.pricing = settings.pricing ? { ...settings.pricing } : { ...defaultAiPricing }
  apiKeyInput.value = ''
  saveApiKey.value = false
  pricingMode.value = settings.pricing ? 'custom' : 'preset'
}

function applyProviderDefaults() {
  const provider = selectedProviderProfile.value
  if (!provider) {
    return
  }
  aiProviderForm.baseUrl = provider.defaultBaseUrl || aiProviderForm.baseUrl
  aiProviderForm.model = provider.models[0] || aiProviderForm.model
  if (provider.id === 'deepseek') {
    aiProviderForm.pricing = { ...defaultAiPricing }
    pricingMode.value = 'preset'
  }
}

async function saveAiProviderSettings(showAlert = true): Promise<boolean> {
  savingAiSettings.value = true
  aiProviderStatusMessage.value = ''

  try {
    const settings = await saveLocalAiSettings(buildAiProviderRequest())
    applyLocalAiSettings(settings)
    aiProviderStatusMessage.value = t('settings.aiSettingsSaved')
    if (showAlert) {
      window.alert(t('settings.aiSettingsSaved'))
    }
    return true
  } catch (error) {
    console.error('Failed to save local AI settings', error)
    aiProviderStatusMessage.value = t('settings.aiSettingsUnavailable')
    if (showAlert) {
      window.alert(t('settings.aiSettingsUnavailable'))
    }
    return false
  } finally {
    savingAiSettings.value = false
  }
}

async function testLocalAiProviderConnection() {
  testingAiConnection.value = true
  try {
    const saved = await saveAiProviderSettings(false)
    aiProviderStatusMessage.value = saved
      ? t('settings.aiConnectionReady')
      : t('settings.aiSettingsUnavailable')
    window.alert(aiProviderStatusMessage.value)
  } finally {
    testingAiConnection.value = false
  }
}

function buildAiProviderRequest(): SaveLocalAiSettingsRequest {
  return {
    enabled: aiProviderForm.enabled,
    providerId: aiProviderForm.providerId,
    baseUrl: aiProviderForm.baseUrl,
    model: aiProviderForm.model,
    apiKey: apiKeyInput.value,
    saveApiKey: saveApiKey.value,
    temperature: aiProviderForm.temperature,
    maxTokens: aiProviderForm.maxTokens,
    pricing: pricingMode.value === 'custom' ? aiProviderForm.pricing : { ...defaultAiPricing }
  }
}

async function checkLocalRankPeekServer() {
  checkingLocalServer.value = true

  try {
    const result = await checkRankPeekServerDiagnostics()
    if (result.available) {
      window.alert(t('settings.localServerAvailable', {
        mode: result.mode,
        version: result.version
      }))
      return
    }

    window.alert(t('settings.localServerUnavailable', { message: result.message }))
  } finally {
    checkingLocalServer.value = false
  }
}

async function saveMatchSettings() {
  savingMatchSettings.value = true

  try {
    await apiClient.setConfig('settings.match.defaultQueueMode', defaultMatchQueueMode.value)
    setCachedDefaultMatchQueueMode(defaultMatchQueueMode.value)
    window.alert(t('settings.defaultModeSaved'))
  } catch (error) {
    console.error('Failed to save default match mode', error)
    window.alert(t('settings.saveFailed'))
  } finally {
    savingMatchSettings.value = false
  }
}

async function clearUserCache(mode: CacheClearMode) {
  if (!window.confirm(t('settings.confirmClearCache'))) {
    return
  }

  clearingUserCacheMode.value = mode

  try {
    clearFrontendTransientCache()
    const result = await apiClient.clearCache('all', true, mode)
    if (mode === 'deep') {
      const [electronCacheResult, storageRetentionResult] = await Promise.all([
        window.electronAPI?.clearChromiumCache?.(),
        window.electronAPI?.database?.runStorageRetention?.()
      ])
      if (electronCacheResult && !electronCacheResult.success) {
        throw new Error(electronCacheResult.error)
      }
      if (storageRetentionResult && !storageRetentionResult.success) {
        throw new Error(storageRetentionResult.error)
      }
    }
    window.alert(buildCacheClearAlertMessage(result, {
      cleared: t('settings.cacheCleared'),
      partial: t('settings.clearCachePartialFailed'),
      failed: t('settings.clearCacheFailed')
    }))
  } catch (error) {
    console.error('Failed to clear cache', error)
    const message = extractCacheClearErrorMessage(error)
    window.alert(message ? `${t('settings.clearCacheFailed')}: ${message}` : t('settings.clearCacheFailed'))
  } finally {
    clearingUserCacheMode.value = null
  }
}

async function openExternal(url: string) {
  if (!window.electronAPI) {
    window.open(url, '_blank', 'noopener,noreferrer')
    return
  }

  try {
    const result = await window.electronAPI.openExternal(url)
    if (result && !result.success) {
      console.error('Failed to open link:', result.error)
      window.open(url, '_blank', 'noopener,noreferrer')
    }
  } catch (error) {
    console.error('Failed to open external link', error)
    window.open(url, '_blank', 'noopener,noreferrer')
  }
}
</script>

<template>
  <div class="settings-view">
    <header class="page-header">
      <h1>{{ t('settings.title') }}</h1>
      <p>{{ t('settings.subtitle') }}</p>
    </header>

    <section class="ai-provider-card">
      <div class="ai-provider-header">
        <div>
          <h2>{{ t('settings.aiProviderTitle') }}</h2>
          <p>{{ t('settings.aiProviderDescription') }}</p>
        </div>
        <button
          class="secondary-btn compact"
          type="button"
          :disabled="checkingLocalServer"
          @click="checkLocalRankPeekServer"
        >
          {{ checkingLocalServer ? t('settings.checkingLocalServer') : t('settings.checkLocalServer') }}
        </button>
      </div>

      <form class="ai-provider-form" @submit.prevent="saveAiProviderSettings()">
        <label class="toggle-row">
          <input v-model="aiProviderForm.enabled" type="checkbox">
          <span>{{ t('settings.aiEnabled') }}</span>
        </label>

        <div class="form-grid">
          <label class="form-field">
            <span>{{ t('settings.aiProviderSelect') }}</span>
            <select
              v-model="aiProviderForm.providerId"
              class="select-input"
              :disabled="loadingAiSettings"
              @change="applyProviderDefaults"
            >
              <option
                v-for="provider in localAiProviderProfiles"
                :key="provider.id"
                :value="provider.id"
              >
                {{ provider.label }}
              </option>
              <option value="custom-openai-compatible">Custom OpenAI Compatible</option>
            </select>
          </label>

          <label class="form-field">
            <span>{{ t('settings.aiModel') }}</span>
            <input
              v-model.trim="aiProviderForm.model"
              class="text-input"
              list="ai-model-options"
              type="text"
            >
            <datalist id="ai-model-options">
              <option
                v-for="model in selectedProviderProfile?.models ?? []"
                :key="model"
                :value="model"
              />
            </datalist>
          </label>

          <label class="form-field wide">
            <span>{{ t('settings.aiBaseUrl') }}</span>
            <input
              v-model.trim="aiProviderForm.baseUrl"
              class="text-input"
              type="url"
            >
          </label>

          <label class="form-field">
            <span>{{ t('settings.aiApiKey') }}</span>
            <input
              v-model="apiKeyInput"
              autocomplete="off"
              class="text-input"
              :placeholder="t('settings.aiApiKeyPlaceholder')"
              type="password"
            >
          </label>

          <div class="form-field saved-key">
            <span>{{ t('settings.aiSavedKeyLabel') }}</span>
            <strong>{{ aiSavedKeyLabel }}</strong>
          </div>

          <label class="toggle-row">
            <input v-model="saveApiKey" type="checkbox">
            <span>{{ t('settings.aiSaveApiKey') }}</span>
          </label>

          <label class="form-field">
            <span>{{ t('settings.aiTemperature') }}</span>
            <input
              v-model.number="aiProviderForm.temperature"
              class="text-input"
              max="2"
              min="0"
              step="0.1"
              type="number"
            >
          </label>

          <label class="form-field">
            <span>{{ t('settings.aiMaxTokens') }}</span>
            <input
              v-model.number="aiProviderForm.maxTokens"
              class="text-input"
              min="256"
              step="256"
              type="number"
            >
          </label>
        </div>

        <div class="pricing-panel">
          <div class="pricing-header">
            <span>{{ t('settings.aiPricingMode') }}</span>
            <div class="segmented-control">
              <button
                class="segment-button"
                type="button"
                :class="{ active: pricingMode === 'preset' }"
                @click="pricingMode = 'preset'"
              >
                {{ t('settings.aiPricingPreset') }}
              </button>
              <button
                class="segment-button"
                type="button"
                :class="{ active: pricingMode === 'custom' }"
                @click="pricingMode = 'custom'"
              >
                {{ t('settings.aiPricingCustom') }}
              </button>
            </div>
          </div>

          <div class="form-grid compact-grid">
            <label class="form-field">
              <span>{{ t('settings.aiInputCacheHitPrice') }}</span>
              <input
                v-model.number="aiProviderForm.pricing.inputCacheHitCnyPerMillionTokens"
                class="text-input"
                min="0"
                step="0.001"
                type="number"
                :disabled="pricingMode === 'preset'"
              >
            </label>
            <label class="form-field">
              <span>{{ t('settings.aiInputCacheMissPrice') }}</span>
              <input
                v-model.number="aiProviderForm.pricing.inputCacheMissCnyPerMillionTokens"
                class="text-input"
                min="0"
                step="0.001"
                type="number"
                :disabled="pricingMode === 'preset'"
              >
            </label>
            <label class="form-field">
              <span>{{ t('settings.aiOutputPrice') }}</span>
              <input
                v-model.number="aiProviderForm.pricing.outputCnyPerMillionTokens"
                class="text-input"
                min="0"
                step="0.001"
                type="number"
                :disabled="pricingMode === 'preset'"
              >
            </label>
          </div>
        </div>

        <p v-if="aiProviderStatusMessage" class="status-message">
          {{ aiProviderStatusMessage }}
        </p>

        <div class="form-actions">
          <button
            class="primary-btn"
            type="submit"
            :disabled="savingAiSettings || loadingAiSettings"
          >
            {{ savingAiSettings ? t('settings.saving') : t('settings.aiSaveProvider') }}
          </button>
          <button
            class="secondary-btn"
            type="button"
            :disabled="testingAiConnection || savingAiSettings || loadingAiSettings"
            @click="testLocalAiProviderConnection"
          >
            {{ testingAiConnection ? t('settings.checkingLocalServer') : t('settings.aiTestConnection') }}
          </button>
        </div>
      </form>
    </section>

    <section class="settings-section essentials-section">
      <h2>{{ t('settings.commonSettings') }}</h2>

      <div class="settings-list">
        <article class="setting-row">
          <div class="setting-copy">
            <h3>{{ t('settings.defaultMatchModeUser') }}</h3>
            <p>{{ t('settings.defaultMatchModeUserDescription') }}</p>
          </div>
          <div class="setting-control match-mode-control">
            <select
              v-model.number="defaultMatchQueueMode"
              class="select-input"
            >
              <option
                v-for="mode in matchModeOptions"
                :key="mode.id"
                :value="mode.id"
              >
                {{ mode.name }}
              </option>
            </select>
            <button
              class="primary-btn compact"
              type="button"
              :disabled="savingMatchSettings"
              @click="saveMatchSettings"
            >
              {{ savingMatchSettings ? t('settings.saving') : t('settings.saveDefaultMode') }}
            </button>
          </div>
        </article>

        <article class="setting-row">
          <div class="setting-copy">
            <h3>{{ t('settings.clearCacheUser') }}</h3>
            <p>{{ t('settings.clearCacheUserDescription') }}</p>
          </div>
          <div
            class="setting-control cache-clear-control"
            role="group"
            :aria-label="t('settings.clearCacheUser')"
          >
            <button
              class="secondary-btn compact"
              type="button"
              :disabled="clearingUserCacheMode !== null"
              @click="clearUserCache('normal')"
            >
              {{ clearingUserCacheMode === 'normal' ? t('settings.clearingCache') : t('settings.normalClearCacheAction') }}
            </button>
            <button
              class="secondary-btn compact"
              type="button"
              :disabled="clearingUserCacheMode !== null"
              @click="clearUserCache('deep')"
            >
              {{ clearingUserCacheMode === 'deep' ? t('settings.clearingCache') : t('settings.deepClearCacheAction') }}
            </button>
          </div>
        </article>

        <article class="setting-row">
          <div class="setting-copy">
            <h3>{{ t('settings.appearanceTheme') }}</h3>
            <p>{{ t('settings.appearanceThemeDescription') }}</p>
          </div>
          <div
            class="theme-toggle"
            role="group"
            :aria-label="t('settings.appearanceTheme')"
          >
            <button
              class="theme-option"
              type="button"
              :class="{ active: themeStore.theme === 'light' }"
              @click="themeStore.setTheme('light')"
            >
              {{ t('settings.lightMode') }}
            </button>
            <button
              class="theme-option"
              type="button"
              :class="{ active: themeStore.theme === 'dark' }"
              @click="themeStore.setTheme('dark')"
            >
              {{ t('settings.darkMode') }}
            </button>
          </div>
        </article>
      </div>
    </section>

    <section class="settings-section about-section">
      <h2>{{ t('settings.aboutRankPeek') }}</h2>
      <div
        class="about-card"
        :class="`theme-${themeStore.theme}`"
      >
        <div class="app-logo">
          <img
            :src="aboutLogoSrc"
            alt="RankPeek app symbol"
          >
        </div>
        <div class="app-info">
          <h3>RankPeek</h3>
          <p>{{ t('settings.tagline') }}</p>
          <p class="version">
            {{ t('settings.version', { version: appVersion }) }}
          </p>
          <div class="about-links">
            <a
              :href="githubRepoUrl"
              @click.prevent="openExternal(githubRepoUrl)"
            >
              {{ t('settings.githubRepo') }}
            </a>
            <a
              :href="githubIssuesUrl"
              @click.prevent="openExternal(githubIssuesUrl)"
            >
              {{ t('settings.issueFeedback') }}
            </a>
          </div>
        </div>
        <div class="app-showcase">
          <div
            class="showcase-backdrop"
            aria-hidden="true"
          >
            <div
              v-for="(line, index) in showcaseBackgroundLines"
              :key="`${line}-${index}`"
              class="showcase-track"
              :class="{ mirrored: index % 2 === 1 }"
            >
              <span
                v-for="copy in 2"
                :key="`${line}-${copy}`"
              >{{ line }}</span>
            </div>
          </div>

          <div class="showcase-center-mark">
            <img
              class="showcase-mark"
              :src="aboutShowcaseSrc"
              alt="RankPeek eye logo artwork"
            >
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.settings-view {
  max-width: 880px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 22px;
}

.page-header h1 {
  margin: 0 0 6px;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0;
}

.page-header p,
.ai-provider-header p,
.setting-copy p,
.app-info p {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.ai-provider-card,
.settings-list,
.about-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}

.ai-provider-card {
  padding: 22px 24px;
  margin-bottom: 30px;
}

.ai-provider-header,
.form-actions,
.pricing-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.ai-provider-header {
  margin-bottom: 18px;
}

.ai-provider-header h2,
.setting-copy h3,
.app-info h3 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-weight: 650;
  letter-spacing: 0;
}

.ai-provider-header h2 {
  font-size: 20px;
}

.ai-provider-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.compact-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.form-field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 7px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 650;
}

.form-field.wide {
  grid-column: 1 / -1;
}

.saved-key strong {
  min-height: 40px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.toggle-row {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 650;
}

.toggle-row input {
  width: 16px;
  height: 16px;
}

.pricing-panel {
  padding: 14px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-tertiary);
}

.pricing-header {
  margin-bottom: 12px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.segmented-control,
.theme-toggle {
  display: flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-tertiary);
}

.segment-button,
.theme-option {
  min-height: 34px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  padding: 0 12px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0;
  cursor: pointer;
}

.segment-button.active,
.theme-option.active {
  background: var(--accent-color);
  color: #fff;
}

.status-message {
  margin: 0;
  color: var(--accent-color);
  font-size: 13px;
  font-weight: 650;
}

.settings-section {
  margin-bottom: 30px;
}

.settings-section h2 {
  margin: 0 0 12px;
  color: var(--text-secondary);
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.settings-list {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--border-subtle);
}

.setting-row:last-child {
  border-bottom: 0;
}

.setting-copy {
  min-width: 0;
  flex: 1 1 auto;
}

.setting-copy h3 {
  font-size: 15px;
}

.setting-control {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex: 0 0 auto;
}

.match-mode-control {
  min-width: min(100%, 360px);
}

.select-input,
.text-input {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  background: var(--input-bg);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
  outline: none;
}

.select-input:focus,
.text-input:focus {
  border-color: var(--input-focus-border);
  box-shadow:
    0 0 0 1px rgba(var(--accent-rgb), 0.18),
    0 0 16px rgba(var(--accent-rgb), 0.18);
}

.primary-btn,
.secondary-btn {
  box-sizing: border-box;
  min-height: 40px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0;
  cursor: pointer;
}

.primary-btn {
  padding: 0 18px;
  background: var(--accent-color);
  color: #fff;
  box-shadow: 0 0 14px rgba(var(--accent-rgb), 0.22);
}

.secondary-btn {
  padding: 0 18px;
  border-color: var(--border-subtle);
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.compact {
  min-width: 88px;
  padding-inline: 16px;
}

.primary-btn:disabled,
.secondary-btn:disabled,
.text-input:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.about-section {
  margin-top: 8px;
  margin-bottom: 0;
}

.about-card {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) 248px;
  align-items: center;
  gap: 24px;
  padding: 24px;
  opacity: 0.92;
}

.app-logo {
  width: 120px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  border-radius: 28px;
  overflow: hidden;
}

.app-logo img {
  width: 96%;
  height: 96%;
  object-fit: contain;
}

.app-info h3 {
  font-size: 20px;
}

.app-info .version {
  margin-top: 8px;
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 12px;
}

.about-links {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.about-links a {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
}

.about-links a:hover {
  color: var(--accent-color);
}

.app-showcase {
  height: 144px;
  padding: 18px 20px;
  position: relative;
  display: flex;
  align-items: stretch;
  justify-content: center;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 28px;
  overflow: hidden;
  isolation: isolate;
  pointer-events: none;
}

.showcase-backdrop {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  padding: 18px 0;
  overflow: hidden;
  z-index: 0;
}

.showcase-backdrop::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.22),
    transparent 22%,
    transparent 78%,
    rgba(255, 255, 255, 0.22)
  );
  pointer-events: none;
}

.showcase-track {
  display: flex;
  width: max-content;
  gap: 22px;
  color: rgba(15, 23, 42, 0.15);
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  white-space: nowrap;
  animation: showcase-scroll-left 24s linear infinite;
}

.showcase-track.mirrored {
  animation-name: showcase-scroll-right;
}

.showcase-track span {
  display: flex;
  align-items: center;
  gap: 22px;
}

.showcase-center-mark {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.showcase-center-mark::before {
  content: "";
  position: absolute;
  width: 166px;
  height: 166px;
  border-radius: 999px;
  filter: blur(10px);
  opacity: 0.56;
  z-index: -1;
}

.showcase-mark {
  width: 154px;
  height: 154px;
  object-fit: contain;
}

.about-card.theme-dark .app-logo,
.about-card.theme-dark .app-showcase {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(245, 247, 250, 0.92));
  border-color: rgba(15, 23, 42, 0.08);
}

.about-card.theme-light .app-logo,
.about-card.theme-light .app-showcase {
  background: linear-gradient(180deg, #05070f, #0d1220);
  border-color: rgba(148, 163, 184, 0.18);
}

.about-card.theme-light .showcase-track {
  color: rgba(241, 245, 249, 0.13);
}

.about-card.theme-light .showcase-backdrop::after {
  background: linear-gradient(
    90deg,
    rgba(5, 7, 15, 0.44),
    transparent 22%,
    transparent 78%,
    rgba(5, 7, 15, 0.44)
  );
}

.about-card.theme-dark .showcase-center-mark::before {
  background: radial-gradient(circle, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0));
}

.about-card.theme-light .showcase-center-mark::before {
  background: radial-gradient(circle, rgba(255, 255, 255, 0.16), rgba(255, 255, 255, 0));
}

@keyframes showcase-scroll-left {
  from {
    transform: translateX(0);
  }

  to {
    transform: translateX(-34%);
  }
}

@keyframes showcase-scroll-right {
  from {
    transform: translateX(-34%);
  }

  to {
    transform: translateX(0);
  }
}

@media (max-width: 760px) {
  .ai-provider-header,
  .setting-row,
  .form-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .form-grid,
  .compact-grid {
    grid-template-columns: 1fr;
  }

  .setting-control,
  .match-mode-control,
  .theme-toggle {
    width: 100%;
  }

  .setting-control {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .primary-btn,
  .secondary-btn {
    flex: 1 1 120px;
  }

  .about-card {
    grid-template-columns: 120px 1fr;
  }

  .app-showcase {
    grid-column: 1 / -1;
  }
}
</style>
