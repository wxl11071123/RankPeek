<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { t, type MessageKey } from '@/i18n'
import homeIcon from '@/assets/icons/nav-home.svg'
import gamingIcon from '@/assets/icons/nav-gamepad.svg'
import summonerIcon from '@/assets/icons/nav-user-search.svg'
import matchRecordIcon from '@/assets/icons/nav-record-bars.svg'
import userTagIcon from '@/assets/icons/nav-tag.svg'
import tagConfigIcon from '@/assets/icons/nav-clipboard.svg'
import settingsGearIcon from '@/assets/icons/nav-gear-five.svg'

const route = useRoute()
const router = useRouter()

const menuItems: Array<{ path: string; icon: string; labelKey: MessageKey }> = [
  { path: '/', icon: homeIcon, labelKey: 'nav.home' },
  { path: '/gaming', icon: gamingIcon, labelKey: 'nav.gaming' },
  { path: '/summoner', icon: summonerIcon, labelKey: 'nav.summoner' },
  { path: '/match-history', icon: matchRecordIcon, labelKey: 'nav.matchHistory' },
  { path: '/user-tag', icon: userTagIcon, labelKey: 'nav.userTag' },
  { path: '/tag-config', icon: tagConfigIcon, labelKey: 'nav.tagConfig' },
  { path: '/settings', icon: settingsGearIcon, labelKey: 'nav.settings' }
]

const currentPath = computed(() => route.path)

function navigateTo(path: string) {
  void router.push(path)
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-brand" aria-label="RankPeek">
      <svg
        class="sidebar-logo"
        viewBox="0 0 36 36"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
        focusable="false"
      >
        <rect x="4.5" y="4.5" width="27" height="27" rx="7.2" />
        <path d="M9.5 18c2.3-4.2 5.1-6.3 8.5-6.3s6.2 2.1 8.5 6.3c-2.3 4.2-5.1 6.3-8.5 6.3s-6.2-2.1-8.5-6.3Z" />
        <circle cx="18" cy="18" r="3.2" />
      </svg>
      <span class="brand-name">RankPeek</span>
    </div>

    <nav class="sidebar-nav">
      <ul class="nav-list">
        <li
          v-for="item in menuItems"
          :key="item.path"
          class="nav-item"
          :class="{ active: currentPath === item.path }"
          :title="t(item.labelKey)"
          :aria-label="t(item.labelKey)"
          @click="navigateTo(item.path)"
        >
          <span class="nav-icon" aria-hidden="true">
            <span class="nav-icon-svg" :style="{ '--nav-icon-url': `url(${item.icon})` }"></span>
          </span>
          <span class="nav-label">{{ t(item.labelKey) }}</span>
        </li>
      </ul>
    </nav>

    <div class="sidebar-footer">
      <div class="version">v1.0.0</div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 252px;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 76px;
  padding: 20px 22px 14px;
  color: var(--text-primary);
}

.sidebar-logo {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
}

.brand-name {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 750;
  line-height: 1;
  letter-spacing: 0;
}

.sidebar-nav {
  flex: 1;
  padding: 6px 14px 12px;
  overflow-y: auto;
}

.nav-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 62px;
  padding: 16px 18px;
  margin-bottom: 6px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  color: var(--text-secondary);
  font-size: 17px;
  font-weight: 650;
  letter-spacing: 0;
}

.nav-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.nav-item.active {
  background: var(--accent-color);
  color: #ffffff;
  font-weight: 500;
}

.nav-icon {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nav-icon-svg {
  width: 25px;
  height: 25px;
  background: currentColor;
  mask: var(--nav-icon-url) center / contain no-repeat;
  -webkit-mask: var(--nav-icon-url) center / contain no-repeat;
}

.nav-label {
  font-size: 17px;
  line-height: 1.25;
}

.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--border-subtle);
}

.version {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-tertiary);
  text-align: center;
  letter-spacing: -0.12px;
}

@media (max-width: 760px) {
  .sidebar {
    width: 96px;
  }

  .sidebar-brand {
    justify-content: center;
    min-height: 72px;
    padding: 18px 10px 12px;
  }

  .brand-name {
    display: none;
  }

  .sidebar-nav {
    padding: 8px 10px 16px;
  }

  .nav-item {
    justify-content: center;
    min-height: 66px;
    padding: 16px 10px;
  }

  .nav-icon {
    width: 34px;
    height: 34px;
  }

  .nav-icon-svg {
    width: 28px;
    height: 28px;
  }

  .nav-label {
    display: none;
  }

  .sidebar-footer {
    padding: 12px 8px;
  }
}
</style>
