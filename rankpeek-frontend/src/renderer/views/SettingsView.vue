<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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
  clearStoredRankPeekAuthSession,
  getStoredRankPeekAuthSession,
  loginRankPeekAccount,
  logoutRankPeekAccount,
  registerRankPeekAccount,
  requestRankPeekPasswordReset,
  storeRankPeekAuthSession,
  type RankPeekAuthSession
} from '@/services/rankpeekAuthClient'
import { checkRankPeekServerDiagnostics } from '@/services/rankpeekServerClient'
import { clearFrontendTransientCache } from '@/utils/frontendCache'
import { getDefaultMatchQueueMode, setCachedDefaultMatchQueueMode } from '@/utils/matchPreferences'
import brandSymbolBlack from '@/assets/branding/rankpeek-symbol-black.png'
import brandSymbolWhite from '@/assets/branding/rankpeek-symbol-white.png'
import brandEyeBlack from '@/assets/branding/rankpeek-eye-black.png'
import brandEyeWhite from '@/assets/branding/rankpeek-eye-white.png'

const themeStore = useThemeStore()
const { t } = useI18n()
type AuthMode = 'login' | 'register' | 'forgotPassword'

const appVersion = ref('1.0.0')
const defaultMatchQueueMode = ref(0)
const matchModeOptions = ref<GameModeOption[]>([])
const savingMatchSettings = ref(false)
const clearingUserCacheMode = ref<CacheClearMode | null>(null)
const checkingLocalServer = ref(false)
const authSession = ref<RankPeekAuthSession | null>(getStoredRankPeekAuthSession())
const authModalOpen = ref(false)
const authMode = ref<AuthMode>('login')
const authEmail = ref('')
const authPassword = ref('')
const authPasswordConfirm = ref('')
const showAuthPassword = ref(false)
const showAuthPasswordConfirm = ref(false)
const authBusy = ref(false)
const authError = ref('')
const authInfo = ref('')

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

const signedInUser = computed(() => authSession.value?.user ?? null)

const authModalTitle = computed(() => {
  if (authMode.value === 'login') {
    return t('settings.authLoginTitle')
  }
  if (authMode.value === 'register') {
    return t('settings.authRegisterTitle')
  }
  return t('settings.authForgotTitle')
})

const authSubmitLabel = computed(() => {
  if (authMode.value === 'login') {
    return t('settings.authSubmitLogin')
  }
  if (authMode.value === 'register') {
    return t('settings.authSubmitRegister')
  }
  return t('settings.authSubmitForgotPassword')
})

const authPasswordAutocomplete = computed(() =>
  authMode.value === 'login' ? 'current-password' : 'new-password'
)

if (window.electronAPI) {
  window.electronAPI.getVersion().then(version => {
    appVersion.value = version
  })
}

onMounted(async () => {
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
})

function openAuthModal(mode: AuthMode) {
  authMode.value = mode
  authEmail.value = signedInUser.value?.email ?? ''
  authPassword.value = ''
  authPasswordConfirm.value = ''
  showAuthPassword.value = false
  showAuthPasswordConfirm.value = false
  authError.value = ''
  authInfo.value = ''
  authModalOpen.value = true
}

function closeAuthModal() {
  if (authBusy.value) {
    return
  }

  authModalOpen.value = false
  authError.value = ''
  authInfo.value = ''
}

function switchAuthMode(mode: AuthMode) {
  authMode.value = mode
  authError.value = ''
  authInfo.value = ''
  authPassword.value = ''
  authPasswordConfirm.value = ''
  showAuthPassword.value = false
  showAuthPasswordConfirm.value = false
}

async function submitAuthForm() {
  if (!authEmail.value || (authMode.value !== 'forgotPassword' && !authPassword.value)) {
    authError.value = t('settings.authRequiredFields')
    return
  }

  if (authMode.value === 'register' && authPassword.value !== authPasswordConfirm.value) {
    authError.value = t('settings.authPasswordMismatch')
    return
  }

  authBusy.value = true
  authError.value = ''
  authInfo.value = ''

  try {
    if (authMode.value === 'forgotPassword') {
      const result = await requestRankPeekPasswordReset({ email: authEmail.value })
      if (!result.ok) {
        authError.value = result.message
        return
      }

      authInfo.value = t('settings.authResetRequestSent')
      return
    }

    const result = authMode.value === 'login'
      ? await loginRankPeekAccount({
        email: authEmail.value,
        password: authPassword.value
      })
      : await registerRankPeekAccount({
        email: authEmail.value,
        password: authPassword.value
      })

    if (!result.ok) {
      authError.value = result.message || (
        authMode.value === 'login' ? t('settings.authLoginFailed') : t('settings.authRegisterFailed')
      )
      return
    }

    storeRankPeekAuthSession(result.session)
    authSession.value = result.session
    authModalOpen.value = false
    authPassword.value = ''
    authPasswordConfirm.value = ''
  } finally {
    authBusy.value = false
  }
}

async function handleLogout() {
  const refreshToken = authSession.value?.refreshToken
  clearStoredRankPeekAuthSession()
  authSession.value = null

  const result = await logoutRankPeekAccount(refreshToken)
  if (!result.ok) {
    console.warn('Failed to revoke RankPeek refresh token:', result.message)
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
    window.alert(message ? `${t('settings.clearCacheFailed')}：${message}` : t('settings.clearCacheFailed'))
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

    <section class="account-card">
      <div class="account-copy">
        <h2>{{ t('settings.accountTitle') }}</h2>
        <p
          v-if="signedInUser"
          class="account-status"
        >
          {{ t('settings.signedInAs', { email: signedInUser.email }) }}
        </p>
        <p v-else>
          {{ t('settings.accountDescription') }}
        </p>
        <p
          v-if="signedInUser"
          class="account-role"
        >
          {{ t('settings.accountRole', { role: signedInUser.role }) }}
        </p>
      </div>
      <div class="account-actions">
        <button
          v-if="!signedInUser"
          class="primary-btn"
          type="button"
          @click="openAuthModal('login')"
        >
          {{ t('settings.login') }}
        </button>
        <button
          v-else
          class="secondary-btn"
          type="button"
          @click="handleLogout"
        >
          {{ t('settings.logout') }}
        </button>
        <button
          class="secondary-btn"
          type="button"
          :disabled="checkingLocalServer"
          @click="checkLocalRankPeekServer"
        >
          {{ checkingLocalServer ? t('settings.checkingLocalServer') : t('settings.checkLocalServer') }}
        </button>
      </div>
    </section>

    <div
      v-if="authModalOpen"
      class="auth-modal-overlay"
      @click.self="closeAuthModal"
    >
      <section
        class="auth-modal"
        role="dialog"
        aria-modal="true"
        :aria-label="authModalTitle"
      >
        <header class="auth-modal-header">
          <h2>{{ authModalTitle }}</h2>
          <button
            class="auth-close-btn"
            type="button"
            :disabled="authBusy"
            :aria-label="t('common.cancel')"
            @click="closeAuthModal"
          >
            X
          </button>
        </header>

        <form
          class="auth-form"
          @submit.prevent="submitAuthForm"
        >
          <label class="auth-field">
            <span>{{ t('settings.authEmail') }}</span>
            <input
              v-model.trim="authEmail"
              autocomplete="email"
              name="email"
              type="email"
            >
          </label>

          <label
            v-if="authMode !== 'forgotPassword'"
            class="auth-field"
          >
            <span>{{ t('settings.authPassword') }}</span>
            <span class="auth-password-control">
              <input
                v-model="authPassword"
                :autocomplete="authPasswordAutocomplete"
                name="password"
                :type="showAuthPassword ? 'text' : 'password'"
              >
              <button
                class="auth-password-toggle"
                type="button"
                :aria-label="showAuthPassword ? t('settings.authPasswordHide') : t('settings.authPasswordShow')"
                @click="showAuthPassword = !showAuthPassword"
              >
                <svg
                  v-if="!showAuthPassword"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z" />
                  <circle
                    cx="12"
                    cy="12"
                    r="3"
                  />
                </svg>
                <svg
                  v-else
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path d="M3 3l18 18" />
                  <path d="M2 12s3.5-6 10-6c1.2 0 2.3.2 3.3.6" />
                  <path d="M21.1 13.9C19.7 16 16.6 18 12 18c-1.2 0-2.3-.2-3.3-.6" />
                </svg>
              </button>
            </span>
          </label>

          <label
            v-if="authMode === 'register'"
            class="auth-field"
          >
            <span>{{ t('settings.authPasswordConfirm') }}</span>
            <span class="auth-password-control">
              <input
                v-model="authPasswordConfirm"
                autocomplete="new-password"
                name="passwordConfirm"
                :type="showAuthPasswordConfirm ? 'text' : 'password'"
              >
              <button
                class="auth-password-toggle"
                type="button"
                :aria-label="showAuthPasswordConfirm ? t('settings.authPasswordHide') : t('settings.authPasswordShow')"
                @click="showAuthPasswordConfirm = !showAuthPasswordConfirm"
              >
                <svg
                  v-if="!showAuthPasswordConfirm"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z" />
                  <circle
                    cx="12"
                    cy="12"
                    r="3"
                  />
                </svg>
                <svg
                  v-else
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path d="M3 3l18 18" />
                  <path d="M2 12s3.5-6 10-6c1.2 0 2.3.2 3.3.6" />
                  <path d="M21.1 13.9C19.7 16 16.6 18 12 18c-1.2 0-2.3-.2-3.3-.6" />
                </svg>
              </button>
            </span>
          </label>

          <p
            v-if="authError"
            class="auth-error"
          >
            {{ authError }}
          </p>
          <p
            v-if="authInfo"
            class="auth-info"
          >
            {{ authInfo }}
          </p>

          <button
            class="primary-btn auth-submit"
            type="submit"
            :disabled="authBusy"
          >
            {{ authBusy ? t('settings.saving') : authSubmitLabel }}
          </button>
        </form>

        <div class="auth-switch">
          <button
            v-if="authMode === 'login'"
            class="auth-link-btn"
            type="button"
            :disabled="authBusy"
            @click="switchAuthMode('forgotPassword')"
          >
            {{ t('settings.authForgotPassword') }}
          </button>
          <button
            v-if="authMode === 'login'"
            class="auth-link-btn"
            type="button"
            :disabled="authBusy"
            @click="switchAuthMode('register')"
          >
            {{ t('settings.authSwitchToRegister') }}
          </button>
          <button
            v-else
            class="auth-link-btn"
            type="button"
            :disabled="authBusy"
            @click="switchAuthMode('login')"
          >
            {{ t('settings.authSwitchToLogin') }}
          </button>
        </div>
      </section>
    </div>

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
            <span class="cache-clear-tooltip-anchor">
              <button
                class="secondary-btn compact"
                type="button"
                aria-describedby="normal-cache-clear-tooltip"
                :disabled="clearingUserCacheMode !== null"
                @click="clearUserCache('normal')"
              >
                {{ clearingUserCacheMode === 'normal' ? t('settings.clearingCache') : t('settings.normalClearCacheAction') }}
              </button>
              <span
                id="normal-cache-clear-tooltip"
                class="cache-clear-tooltip"
                role="tooltip"
              >
                {{ t('settings.normalClearCacheTooltip') }}
              </span>
            </span>
            <span class="cache-clear-tooltip-anchor">
              <button
                class="secondary-btn compact"
                type="button"
                aria-describedby="deep-cache-clear-tooltip"
                :disabled="clearingUserCacheMode !== null"
                @click="clearUserCache('deep')"
              >
                {{ clearingUserCacheMode === 'deep' ? t('settings.clearingCache') : t('settings.deepClearCacheAction') }}
              </button>
              <span
                id="deep-cache-clear-tooltip"
                class="cache-clear-tooltip"
                role="tooltip"
              >
                {{ t('settings.deepClearCacheTooltip') }}
              </span>
            </span>
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
  max-width: 720px;
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

.page-header p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 15px;
}

.account-card,
.settings-list,
.about-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}

.account-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 24px;
  margin-bottom: 30px;
  box-shadow:
    0 12px 28px rgba(0, 0, 0, 0.18),
    0 0 0 1px rgba(var(--accent-rgb), 0.04);
}

.account-copy {
  min-width: 0;
}

.account-copy h2,
.setting-copy h3,
.app-info h3 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-weight: 650;
  letter-spacing: 0;
}

.account-copy h2 {
  font-size: 20px;
}

.account-copy p,
.setting-copy p,
.app-info p {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.account-status {
  color: var(--text-primary);
  font-weight: 650;
}

.account-role {
  color: var(--text-tertiary);
}

.account-actions {
  display: flex;
  gap: 10px;
  flex: 0 0 auto;
}

.auth-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(2, 6, 23, 0.58);
}

.auth-modal {
  box-sizing: border-box;
  width: min(100%, 420px);
  padding: 22px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--bg-secondary);
  box-shadow: 0 22px 52px rgba(0, 0, 0, 0.34);
}

.auth-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.auth-modal-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0;
}

.auth-close-btn,
.auth-link-btn {
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.auth-close-btn {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 750;
}

.auth-close-btn:hover:not(:disabled) {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.auth-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 650;
}

.auth-field input {
  box-sizing: border-box;
  width: 100%;
  height: 42px;
  padding: 0 12px;
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  background: var(--input-bg);
  color: var(--text-primary);
  font-size: 14px;
  outline: none;
}

.auth-password-control {
  position: relative;
  display: block;
}

.auth-password-control input {
  padding-right: 44px;
}

.auth-password-toggle {
  position: absolute;
  top: 50%;
  right: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-secondary);
  transform: translateY(-50%);
  cursor: pointer;
}

.auth-password-toggle:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.auth-password-toggle svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.auth-field input:focus {
  border-color: var(--input-focus-border);
  box-shadow:
    0 0 0 1px rgba(var(--accent-rgb), 0.18),
    0 0 16px rgba(var(--accent-rgb), 0.18);
}

.auth-error {
  margin: 0;
  color: #ef4444;
  font-size: 13px;
  line-height: 1.45;
}

.auth-info {
  margin: 0;
  color: #5eead4;
  font-size: 13px;
  line-height: 1.45;
}

.auth-submit {
  width: 100%;
}

.auth-switch {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-top: 14px;
}

.auth-link-btn {
  padding: 6px 8px;
  font-size: 13px;
  font-weight: 650;
}

.auth-link-btn:hover:not(:disabled) {
  color: var(--accent-color);
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

.cache-clear-control {
  align-items: center;
}

.cache-clear-tooltip-anchor {
  position: relative;
  display: inline-flex;
}

.cache-clear-tooltip {
  position: absolute;
  right: 0;
  bottom: calc(100% + 10px);
  z-index: var(--z-tooltip);
  box-sizing: border-box;
  width: 260px;
  max-width: min(260px, calc(100vw - 32px));
  padding: 10px 12px;
  border: 1px solid rgba(var(--accent-rgb), 0.26);
  border-radius: var(--radius-md);
  background: rgba(15, 23, 42, 0.96);
  color: rgba(255, 255, 255, 0.88);
  box-shadow:
    0 14px 28px rgba(0, 0, 0, 0.3),
    0 0 0 1px rgba(255, 255, 255, 0.04);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.45;
  text-align: left;
  white-space: normal;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: translateY(4px);
  transition:
    opacity 0.14s ease,
    transform 0.14s ease,
    visibility 0.14s ease;
}

.cache-clear-tooltip::after {
  content: "";
  position: absolute;
  right: 18px;
  bottom: -6px;
  width: 10px;
  height: 10px;
  border-right: 1px solid rgba(var(--accent-rgb), 0.26);
  border-bottom: 1px solid rgba(var(--accent-rgb), 0.26);
  background: rgba(15, 23, 42, 0.96);
  transform: rotate(45deg);
}

.cache-clear-tooltip-anchor:hover .cache-clear-tooltip,
.cache-clear-tooltip-anchor:focus-within .cache-clear-tooltip {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

.select-input {
  box-sizing: border-box;
  min-width: 210px;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  background: var(--input-bg);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
  outline: none;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    background 0.18s ease;
}

.select-input:focus {
  border-color: var(--input-focus-border);
  box-shadow:
    0 0 0 1px rgba(var(--accent-rgb), 0.18),
    0 0 16px rgba(var(--accent-rgb), 0.18);
}

.primary-btn,
.secondary-btn,
.theme-option {
  box-sizing: border-box;
  min-height: 40px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.2s ease,
    color 0.18s ease,
    opacity 0.18s ease;
}

.primary-btn {
  padding: 0 18px;
  background: var(--accent-color);
  color: #fff;
  box-shadow: 0 0 14px rgba(var(--accent-rgb), 0.22);
}

.primary-btn:hover:not(:disabled) {
  box-shadow:
    0 0 0 1px rgba(var(--accent-rgb), 0.16),
    0 0 18px rgba(var(--accent-rgb), 0.3);
}

.secondary-btn {
  padding: 0 18px;
  border-color: var(--border-subtle);
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.secondary-btn:hover:not(:disabled) {
  border-color: rgba(var(--accent-rgb), 0.38);
  background: var(--bg-hover);
  box-shadow: 0 0 14px rgba(var(--accent-rgb), 0.16);
}

.compact {
  min-width: 88px;
  padding-inline: 16px;
}

.primary-btn:disabled,
.secondary-btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.theme-toggle {
  display: flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-tertiary);
}

.theme-option {
  min-width: 68px;
  padding: 0 14px;
  background: transparent;
  color: var(--text-secondary);
}

.theme-option:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.theme-option.active {
  background: var(--accent-color);
  color: #fff;
  box-shadow: 0 0 12px rgba(var(--accent-rgb), 0.24);
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
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
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
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
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
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.12);
}

.about-card.theme-light .app-logo,
.about-card.theme-light .app-showcase {
  background: linear-gradient(180deg, #05070f, #0d1220);
  border-color: rgba(148, 163, 184, 0.18);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.06),
    0 18px 36px rgba(2, 6, 23, 0.14);
}

.about-card.theme-dark .showcase-track {
  color: rgba(15, 23, 42, 0.15);
}

.about-card.theme-dark .showcase-center-mark::before {
  background: radial-gradient(circle, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0));
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

.about-card.theme-light .showcase-track {
  color: rgba(241, 245, 249, 0.13);
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
  .account-card,
  .setting-row {
    align-items: stretch;
    flex-direction: column;
  }

  .account-actions,
  .setting-control,
  .match-mode-control,
  .theme-toggle {
    width: 100%;
  }

  .account-actions,
  .setting-control {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .select-input {
    min-width: 0;
    flex: 1 1 180px;
  }

  .primary-btn,
  .secondary-btn {
    flex: 1 1 120px;
  }

  .theme-option {
    flex: 1;
  }

  .about-card {
    grid-template-columns: 120px 1fr;
  }

  .app-showcase {
    grid-column: 1 / -1;
  }

  .about-links {
    justify-content: flex-start;
  }
}
</style>
