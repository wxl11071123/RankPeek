<template>
  <div class="settings-view">
    <section class="settings-hero surface-glow">
      <div class="brand-lockup">
        <img class="brand-symbol" :src="aboutLogoSrc" alt="" />
        <div>
          <span class="eyebrow">RankPeek</span>
          <h1>Settings</h1>
          <p>Public desktop scout configuration.</p>
        </div>
      </div>
      <span class="version-pill">v{{ appVersion }}</span>
    </section>

    <section class="settings-grid">
      <article class="settings-card surface-glow">
        <header>
          <h2>Match History</h2>
          <p>Choose the default queue used when loading your match list.</p>
        </header>

        <label class="field-label" for="default-match-mode">Default queue</label>
        <select id="default-match-mode" v-model.number="defaultMatchQueueMode">
          <option :value="0">All modes</option>
          <option v-for="mode in matchModeOptions" :key="mode.id" :value="mode.id">
            {{ mode.name }}
          </option>
        </select>

        <button
          type="button"
          class="primary-action"
          :disabled="savingMatchSettings"
          @click="saveMatchSettings"
        >
          {{ savingMatchSettings ? 'Saving...' : 'Save' }}
        </button>
      </article>

      <article class="settings-card surface-glow">
        <header>
          <h2>Appearance</h2>
          <p>Switch between the bundled light and dark themes.</p>
        </header>

        <button type="button" class="secondary-action" @click="themeStore.toggleTheme">
          {{ themeStore.theme === 'dark' ? 'Use light theme' : 'Use dark theme' }}
        </button>
      </article>

      <article class="settings-card surface-glow">
        <header>
          <h2>Cache</h2>
          <p>Clear local match, asset, and UI cache data when the app state is stale.</p>
        </header>

        <div class="action-row">
          <button
            type="button"
            class="secondary-action"
            :disabled="Boolean(clearingUserCacheMode)"
            @click="clearUserCache('normal')"
          >
            {{ clearingUserCacheMode === 'normal' ? 'Clearing...' : 'Clear cache' }}
          </button>
          <button
            type="button"
            class="danger-action"
            :disabled="Boolean(clearingUserCacheMode)"
            @click="clearUserCache('deep')"
          >
            {{ clearingUserCacheMode === 'deep' ? 'Clearing...' : 'Deep clear' }}
          </button>
        </div>
      </article>

      <article class="settings-card surface-glow">
        <header>
          <h2>Open Source</h2>
          <p>This public edition excludes proprietary AI features, RP Index algorithms, hosted services, and secrets.</p>
        </header>

        <div class="action-row">
          <button type="button" class="secondary-action" @click="openExternal(githubRepoUrl)">
            GitHub
          </button>
          <button type="button" class="secondary-action" @click="openExternal(githubIssuesUrl)">
            Issues
          </button>
        </div>
      </article>
    </section>

    <section class="about-panel surface-glow">
      <img :src="aboutShowcaseSrc" alt="" />
      <div>
        <h2>RankPeek Public Edition</h2>
        <p>League client scouting, match history, tags, local cache, and desktop shell code are available for review and community contribution.</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { apiClient } from '@/api/httpClient'
import { useThemeStore } from '@/stores/theme'
import type { CacheClearMode, GameModeOption } from '@/types/api'
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
const appVersion = ref('1.0.0')
const defaultMatchQueueMode = ref(0)
const matchModeOptions = ref<GameModeOption[]>([])
const savingMatchSettings = ref(false)
const clearingUserCacheMode = ref<CacheClearMode | null>(null)

const githubRepoUrl = 'https://github.com/wxl11071123/rankpeek'
const githubIssuesUrl = 'https://github.com/wxl11071123/rankpeek/issues'

const aboutLogoSrc = computed(() =>
  themeStore.theme === 'dark' ? brandSymbolBlack : brandSymbolWhite
)

const aboutShowcaseSrc = computed(() =>
  themeStore.theme === 'dark' ? brandEyeBlack : brandEyeWhite
)

onMounted(() => {
  void loadUserSettings()
  if (window.electronAPI) {
    void window.electronAPI.getVersion().then(version => {
      appVersion.value = version
    })
  }
})

async function loadUserSettings(): Promise<void> {
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

async function saveMatchSettings(): Promise<void> {
  savingMatchSettings.value = true

  try {
    await apiClient.setConfig('settings.match.defaultQueueMode', defaultMatchQueueMode.value)
    setCachedDefaultMatchQueueMode(defaultMatchQueueMode.value)
    window.alert('Default match mode saved.')
  } catch (error) {
    console.error('Failed to save default match mode', error)
    window.alert('Failed to save settings.')
  } finally {
    savingMatchSettings.value = false
  }
}

async function clearUserCache(mode: CacheClearMode): Promise<void> {
  if (!window.confirm('Clear RankPeek local cache?')) {
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
      cleared: 'Cache cleared.',
      partial: 'Cache partially cleared.',
      failed: 'Cache clear failed.'
    }))
  } catch (error) {
    console.error('Failed to clear cache', error)
    const message = extractCacheClearErrorMessage(error)
    window.alert(message ? `Cache clear failed: ${message}` : 'Cache clear failed.')
  } finally {
    clearingUserCacheMode.value = null
  }
}

async function openExternal(url: string): Promise<void> {
  if (!window.electronAPI) {
    window.open(url, '_blank', 'noopener,noreferrer')
    return
  }
  await window.electronAPI.openExternal(url)
}
</script>

<style scoped>
.settings-view {
  display: grid;
  gap: 18px;
  padding: 22px;
}

.settings-hero,
.settings-card,
.about-panel {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.settings-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
  padding: 20px;
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-symbol {
  width: 48px;
  height: 48px;
  object-fit: contain;
}

.eyebrow,
.version-pill,
.field-label,
p {
  color: var(--text-secondary);
}

.eyebrow {
  display: block;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0;
}

h1,
h2 {
  margin: 0;
  color: var(--text-primary);
}

h1 {
  font-size: 28px;
}

h2 {
  font-size: 18px;
}

p {
  margin: 6px 0 0;
  line-height: 1.5;
}

.version-pill {
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 6px 10px;
  background: var(--bg-primary);
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.settings-card {
  display: grid;
  gap: 14px;
  align-content: start;
  padding: 16px;
}

.field-label {
  font-size: 13px;
  font-weight: 700;
}

select {
  width: 100%;
  min-height: 36px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-primary);
  color: var(--text-primary);
  padding: 0 10px;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.primary-action,
.secondary-action,
.danger-action {
  min-height: 36px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 0 14px;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.primary-action {
  background: var(--accent-primary);
  border-color: var(--accent-primary);
  color: #fff;
}

.secondary-action {
  background: var(--bg-primary);
  color: var(--text-primary);
}

.danger-action {
  background: rgba(224, 82, 82, 0.12);
  border-color: rgba(224, 82, 82, 0.35);
  color: var(--danger-color, #e05252);
}

button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.about-panel {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 18px;
  align-items: center;
  padding: 18px;
}

.about-panel img {
  width: 100%;
  max-width: 120px;
  object-fit: contain;
}

@media (max-width: 860px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .settings-view {
    padding: 12px;
  }

  .settings-hero,
  .about-panel {
    align-items: flex-start;
    grid-template-columns: 1fr;
  }

  .settings-hero {
    flex-direction: column;
  }
}
</style>
