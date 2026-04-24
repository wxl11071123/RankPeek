<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { t, type MessageKey } from '@/i18n'

const route = useRoute()
const router = useRouter()

const menuItems: Array<{ path: string; icon: string; labelKey: MessageKey }> = [
  { path: '/', icon: '🏠', labelKey: 'nav.home' },
  { path: '/gaming', icon: '🎮', labelKey: 'nav.gaming' },
  { path: '/summoner', icon: '👤', labelKey: 'nav.summoner' },
  { path: '/match-history', icon: '📊', labelKey: 'nav.matchHistory' },
  { path: '/user-tag', icon: '🏷️', labelKey: 'nav.userTag' },
  { path: '/tag-config', icon: '📝', labelKey: 'nav.tagConfig' },
  { path: '/settings', icon: '🔧', labelKey: 'nav.settings' }
]

const currentPath = computed(() => route.path)

function navigateTo(path: string) {
  void router.push(path)
}
</script>

<template>
  <aside class="sidebar">
    <nav class="sidebar-nav">
      <ul class="nav-list">
        <li
          v-for="item in menuItems"
          :key="item.path"
          class="nav-item"
          :class="{ active: currentPath === item.path }"
          @click="navigateTo(item.path)"
        >
          <span class="nav-icon">{{ item.icon }}</span>
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

.sidebar-nav {
  flex: 1;
  padding: 20px 14px 12px;
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
  font-size: 25px;
  width: 34px;
  line-height: 1;
  text-align: center;
  flex-shrink: 0;
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

  .sidebar-nav {
    padding: 16px 10px;
  }

  .nav-item {
    justify-content: center;
    min-height: 66px;
    padding: 16px 10px;
  }

  .nav-icon {
    width: auto;
    font-size: 30px;
  }

  .nav-label {
    display: none;
  }

  .sidebar-footer {
    padding: 12px 8px;
  }
}
</style>
