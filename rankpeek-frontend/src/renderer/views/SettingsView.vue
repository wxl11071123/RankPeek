<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { apiClient } from '@/api/httpClient'
import { useThemeStore } from '@/stores/theme'
import { useI18n } from '@/i18n'
import type { GameModeOption } from '@/types/api'
import {
  buildCacheClearAlertMessage,
  extractCacheClearErrorMessage
} from '@/services/cacheClearFeedback'
import { clearFrontendTransientCache } from '@/utils/frontendCache'
import { getDefaultMatchQueueMode, setCachedDefaultMatchQueueMode } from '@/utils/matchPreferences'
import brandSymbolBlack from '@/assets/branding/rankpeek-symbol-black.png'
import brandSymbolWhite from '@/assets/branding/rankpeek-symbol-white.png'
import brandEyeBlack from '@/assets/branding/rankpeek-eye-black.png'
import brandEyeWhite from '@/assets/branding/rankpeek-eye-white.png'

const themeStore = useThemeStore()
const { t } = useI18n()

const appVersion = ref('1.0.0')
const defaultMatchQueueMode = ref(0)
const matchModeOptions = ref<GameModeOption[]>([])
const savingMatchSettings = ref(false)
const clearingUserCache = ref(false)

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

function handleAccountAction(action: 'login' | 'register') {
  console.info(`RankPeek account ${action} placeholder clicked`)
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

async function clearUserCache() {
  if (!window.confirm(t('settings.confirmClearCache'))) {
    return
  }

  clearingUserCache.value = true

  try {
    clearFrontendTransientCache()
    const result = await apiClient.clearCache('all')
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
    clearingUserCache.value = false
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
        <p>{{ t('settings.accountDescription') }}</p>
      </div>
      <div class="account-actions">
        <button class="primary-btn" type="button" @click="handleAccountAction('login')">
          {{ t('settings.login') }}
        </button>
        <button class="secondary-btn" type="button" @click="handleAccountAction('register')">
          {{ t('settings.register') }}
        </button>
      </div>
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
            <select v-model.number="defaultMatchQueueMode" class="select-input">
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
          <div class="setting-control">
            <button
              class="secondary-btn compact"
              type="button"
              :disabled="clearingUserCache"
              @click="clearUserCache"
            >
              {{ clearingUserCache ? t('settings.clearingCache') : t('settings.clearCacheAction') }}
            </button>
          </div>
        </article>

        <article class="setting-row">
          <div class="setting-copy">
            <h3>{{ t('settings.appearanceTheme') }}</h3>
            <p>{{ t('settings.appearanceThemeDescription') }}</p>
          </div>
          <div class="theme-toggle" role="group" :aria-label="t('settings.appearanceTheme')">
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
      <div class="about-card" :class="`theme-${themeStore.theme}`">
        <div class="app-logo">
          <img :src="aboutLogoSrc" alt="RankPeek app symbol" />
        </div>
        <div class="app-info">
          <h3>RankPeek</h3>
          <p>{{ t('settings.tagline') }}</p>
          <p class="version">{{ t('settings.version', { version: appVersion }) }}</p>
          <div class="about-links">
            <a :href="githubRepoUrl" @click.prevent="openExternal(githubRepoUrl)">
              {{ t('settings.githubRepo') }}
            </a>
            <a :href="githubIssuesUrl" @click.prevent="openExternal(githubIssuesUrl)">
              {{ t('settings.issueFeedback') }}
            </a>
          </div>
        </div>
        <div class="app-showcase">
          <div class="showcase-backdrop" aria-hidden="true">
            <div
              v-for="(line, index) in showcaseBackgroundLines"
              :key="`${line}-${index}`"
              class="showcase-track"
              :class="{ mirrored: index % 2 === 1 }"
            >
              <span v-for="copy in 2" :key="`${line}-${copy}`">{{ line }}</span>
            </div>
          </div>

          <div class="showcase-center-mark">
            <img class="showcase-mark" :src="aboutShowcaseSrc" alt="RankPeek eye logo artwork" />
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

.account-actions {
  display: flex;
  gap: 10px;
  flex: 0 0 auto;
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
