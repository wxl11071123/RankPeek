<script setup lang="ts">
import { apiClient } from "@/api/httpClient";
import { useThemeStore } from "@/stores/theme";
import { useI18n } from "@/i18n";
import type { GameModeOption } from "@/types/api";
import { getDefaultMatchQueueMode, setCachedDefaultMatchQueueMode } from "@/utils/matchPreferences";
import { computed, onMounted, ref } from "vue";
import brandSymbolBlack from "@/assets/branding/rankpeek-symbol-black.png";
import brandSymbolWhite from "@/assets/branding/rankpeek-symbol-white.png";
import brandEyeBlack from "@/assets/branding/rankpeek-eye-black.png";
import brandEyeWhite from "@/assets/branding/rankpeek-eye-white.png";

const themeStore = useThemeStore();
const { t } = useI18n();
const appVersion = ref("1.0.0");
const defaultMatchQueueMode = ref(0);
const matchModeOptions = ref<GameModeOption[]>([]);
const githubRepoUrl = "https://github.com/wxl11071123/rankpeek";
const githubIssuesUrl = "https://github.com/wxl11071123/rankpeek/issues";

const showcaseBackgroundLines = computed(() => [
  t("settings.showcaseLine1"),
  t("settings.showcaseLine2"),
  t("settings.showcaseLine3"),
]);

const aboutLogoSrc = computed(() =>
  themeStore.theme === "dark" ? brandSymbolBlack : brandSymbolWhite,
);

const aboutShowcaseSrc = computed(() =>
  themeStore.theme === "dark" ? brandEyeBlack : brandEyeWhite,
);

if (window.electronAPI) {
  window.electronAPI.getVersion().then((version) => {
    appVersion.value = version;
  });
}

onMounted(async () => {
  try {
    const [config, modes, savedDefaultQueueMode] = await Promise.all([
      apiClient.getConfig(),
      apiClient.getGameModes(),
      getDefaultMatchQueueMode(true),
    ]);
    matchModeOptions.value = modes;
    defaultMatchQueueMode.value = savedDefaultQueueMode;
    if (config?.settings?.match) {
      defaultMatchQueueMode.value = config.settings.match.defaultQueueMode ?? savedDefaultQueueMode;
    }
  } catch (error) {
    console.error("Failed to load settings", error);
  }
});

async function saveMatchSettings() {
  try {
    await apiClient.setConfig("settings.match.defaultQueueMode", defaultMatchQueueMode.value);
    setCachedDefaultMatchQueueMode(defaultMatchQueueMode.value);
    alert(t("settings.defaultModeSaved"));
  } catch (error) {
    console.error("Failed to save default match mode", error);
    alert(t("settings.saveFailed"));
  }
}

async function clearCache() {
  if (!confirm(t("settings.confirmClearCache"))) {
    return;
  }

  try {
    const currentTheme = themeStore.theme;
    localStorage.clear();
    themeStore.setTheme(currentTheme);
    await apiClient.getConfig();
    alert(t("settings.cacheCleared"));
  } catch (error) {
    console.error("Failed to clear cache", error);
    alert(t("settings.clearCacheFailed"));
  }
}

async function exportConfig() {
  try {
    const config = await apiClient.getConfig();
    const blob = new Blob([JSON.stringify(config, null, 2)], {
      type: "application/json",
    });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `rankpeek-config-${new Date().toISOString().slice(0, 10)}.json`;
    anchor.click();
    URL.revokeObjectURL(url);
  } catch (error) {
    console.error("Failed to export config", error);
    alert(t("settings.exportFailed"));
  }
}

async function importConfig() {
  const input = document.createElement("input");
  input.type = "file";
  input.accept = ".json";

  input.onchange = async (event) => {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) {
      return;
    }

    try {
      const text = await file.text();
      const config = JSON.parse(text);

      if (config.settings) {
        await apiClient.setConfig("settings", config.settings);
      }

      alert(t("settings.configImported"));
    } catch (error) {
      console.error("Failed to import config", error);
      alert(t("settings.importInvalid"));
    }
  };

  input.click();
}

async function openExternal(url: string) {
  if (!window.electronAPI) {
    window.open(url, "_blank", "noopener,noreferrer");
    return;
  }

  try {
    const result = await window.electronAPI.openExternal(url);
    if (result && !result.success) {
      console.error("Failed to open link:", result.error);
      window.open(url, "_blank", "noopener,noreferrer");
    }
  } catch (error) {
    console.error("Failed to open external link", error);
    window.open(url, "_blank", "noopener,noreferrer");
  }
}
</script>

<template>
  <div class="settings-view">
    <div class="page-header">
      <h1>{{ t("settings.title") }}</h1>
      <p>{{ t("settings.subtitle") }}</p>
    </div>

    <div class="settings-section">
      <h2>{{ t("settings.about") }}</h2>
      <div class="about-card" :class="`theme-${themeStore.theme}`">
        <div class="app-logo">
          <img :src="aboutLogoSrc" alt="RankPeek app symbol" />
        </div>
        <div class="app-info">
          <h3>RankPeek</h3>
          <p>{{ t("settings.tagline") }}</p>
          <p class="version">{{ t("settings.version", { version: appVersion }) }}</p>
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
    </div>

    <div class="settings-section">
      <h2>{{ t("settings.appearance") }}</h2>
      <div class="appearance-settings">
        <div class="setting-item">
          <div class="setting-info">
            <span class="setting-label">{{ t("settings.themeMode") }}</span>
            <span class="setting-desc">{{ t("settings.themeDescription") }}</span>
          </div>
          <div class="theme-toggle">
            <button
              class="theme-btn"
              :class="{ active: themeStore.theme === 'light' }"
              :title="t('settings.lightMode')"
              @click="themeStore.setTheme('light')"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="5" />
                <path
                  d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"
                />
              </svg>
            </button>
            <button
              class="theme-btn"
              :class="{ active: themeStore.theme === 'dark' }"
              :title="t('settings.darkMode')"
              @click="themeStore.setTheme('dark')"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="settings-section">
      <h2>{{ t("settings.matchLookup") }}</h2>
      <div class="appearance-settings">
        <div class="setting-item">
          <div class="setting-info">
            <span class="setting-label">{{ t("settings.defaultMatchMode") }}</span>
            <span class="setting-desc">{{ t("settings.defaultMatchModeDescription") }}</span>
          </div>
          <select v-model="defaultMatchQueueMode" class="select-input">
            <option
              v-for="mode in matchModeOptions"
              :key="mode.id"
              :value="mode.id"
            >
              {{ mode.name }}
            </option>
          </select>
        </div>

        <div class="setting-actions">
          <button class="save-btn" @click="saveMatchSettings">{{ t("settings.saveDefaultMode") }}</button>
          <span class="setting-desc">{{ t("settings.queueModeNote") }}</span>
        </div>
      </div>
    </div>

    <div class="settings-section">
      <h2>{{ t("settings.shortcuts") }}</h2>
      <div class="shortcut-list">
        <div class="shortcut-item">
          <span class="shortcut-key">Ctrl + R</span>
          <span class="shortcut-action">{{ t("settings.shortcutRefresh") }}</span>
        </div>
        <div class="shortcut-item">
          <span class="shortcut-key">Ctrl + W</span>
          <span class="shortcut-action">{{ t("settings.shortcutClose") }}</span>
        </div>
        <div class="shortcut-item">
          <span class="shortcut-key">F12</span>
          <span class="shortcut-action">{{ t("settings.shortcutDevTools") }}</span>
        </div>
      </div>
    </div>

    <div class="settings-section">
      <h2>{{ t("settings.links") }}</h2>
      <div class="link-list">
        <a
          :href="githubRepoUrl"
          class="link-item"
          @click.prevent="openExternal(githubRepoUrl)"
        >
          <span class="link-icon">📁</span>
          <span class="link-text">{{ t("settings.githubRepo") }}</span>
          <span class="link-arrow">→</span>
        </a>
        <a
          :href="githubIssuesUrl"
          class="link-item"
          @click.prevent="openExternal(githubIssuesUrl)"
        >
          <span class="link-icon">💬</span>
          <span class="link-text">{{ t("settings.issueFeedback") }}</span>
          <span class="link-arrow">→</span>
        </a>
      </div>
    </div>

    <div class="settings-section">
      <h2>{{ t("settings.dataManagement") }}</h2>
      <div class="data-actions">
        <button class="data-btn" @click="clearCache">{{ t("settings.clearCache") }}</button>
        <button class="data-btn" @click="exportConfig">{{ t("settings.exportConfig") }}</button>
        <button class="data-btn" @click="importConfig">{{ t("settings.importConfig") }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-view {
  max-width: 720px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 36px;
}

.page-header h1 {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 6px 0;
  color: var(--text-primary);
  letter-spacing: -0.56px;
}

.page-header p {
  font-size: 15px;
  color: var(--text-secondary);
  margin: 0;
  letter-spacing: -0.224px;
}

.settings-section {
  margin-bottom: 36px;
}

.settings-section h2 {
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 600;
  margin: 0 0 12px 0;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.about-card {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) 248px;
  gap: 24px;
  align-items: center;
  padding: 24px;
  background: var(--bg-secondary);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-subtle);
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
  transition: background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.app-logo img {
  width: 96%;
  height: 96%;
  object-fit: contain;
}

.app-info h3 {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: var(--text-primary);
  letter-spacing: -0.4px;
}

.app-info p {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
  letter-spacing: -0.224px;
}

.app-info .version {
  margin-top: 8px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-tertiary);
}

.app-showcase {
  height: 144px;
  padding: 18px 20px;
  position: relative;
  display: flex;
  align-items: stretch;
  justify-content: center;
  border-radius: 28px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  overflow: hidden;
  isolation: isolate;
  transition: background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
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
  white-space: nowrap;
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
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
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06), 0 18px 36px rgba(2, 6, 23, 0.14);
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

.appearance-settings {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
  gap: 16px;
}

.setting-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex: 1;
}

.setting-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: -0.224px;
}

.setting-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  letter-spacing: -0.12px;
}

.toggle-switch {
  position: relative;
  width: 51px;
  height: 31px;
  flex-shrink: 0;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background-color: rgba(120, 120, 128, 0.32);
  transition: 0.3s;
  border-radius: var(--radius-pill);
}

.toggle-slider:before {
  position: absolute;
  content: "";
  height: 27px;
  width: 27px;
  left: 2px;
  bottom: 2px;
  background-color: #ffffff;
  transition: 0.3s;
  border-radius: 50%;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.toggle-switch input:checked + .toggle-slider {
  background-color: var(--success-color);
}

.toggle-switch input:checked + .toggle-slider:before {
  transform: translateX(20px);
}

.text-input,
.select-input {
  padding: 10px 14px;
  background: var(--input-bg);
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: 13px;
  min-width: 220px;
  letter-spacing: -0.224px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.text-input:focus,
.select-input:focus {
  outline: none;
  border-color: var(--input-focus-border);
  box-shadow: 0 0 0 3px rgba(var(--accent-rgb), 0.2);
}

.setting-actions {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 16px 20px;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
}

.save-btn {
  padding: 10px 24px;
  background: var(--accent-color);
  color: white;
  border: none;
  border-radius: var(--radius-pill);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
  letter-spacing: -0.224px;
}

.save-btn:hover {
  opacity: 0.85;
}

.theme-toggle {
  display: flex;
  gap: 6px;
  padding: 4px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-md);
}

.theme-btn {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  transition: all 0.2s ease;
}

.theme-btn:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.theme-btn.active {
  background: var(--accent-color);
  color: white;
}

.theme-btn svg {
  width: 18px;
  height: 18px;
}

.shortcut-list,
.link-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.shortcut-item {
  display: flex;
  justify-content: space-between;
  padding: 12px 20px;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
}

.shortcut-key {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  padding: 4px 8px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
}

.shortcut-action {
  font-size: 14px;
  color: var(--text-secondary);
  letter-spacing: -0.224px;
}

.link-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
  text-decoration: none;
  transition: background 0.15s;
}

.link-item:hover {
  background: var(--bg-hover);
}

.link-icon {
  font-size: 20px;
}

.link-text {
  flex: 1;
  font-size: 14px;
  color: var(--text-primary);
  letter-spacing: -0.224px;
}

.link-arrow {
  color: var(--text-tertiary);
}

.data-actions {
  display: flex;
  gap: 10px;
}

.data-btn {
  padding: 10px 18px;
  background: var(--bg-secondary);
  color: var(--text-primary);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
  letter-spacing: -0.224px;
}

.data-btn:hover {
  background: var(--bg-hover);
}

@media (max-width: 960px) {
  .about-card {
    grid-template-columns: 120px 1fr;
  }

  .app-showcase {
    grid-column: 1 / -1;
  }
}
</style>
