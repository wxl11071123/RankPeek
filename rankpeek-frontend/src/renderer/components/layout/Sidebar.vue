<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const menuItems = [
  { path: '/', icon: '🏠', label: '首页' },
  { path: '/gaming', icon: '🎮', label: '对战信息' },
  { path: '/summoner', icon: '👤', label: '战绩查询' },
  { path: '/match-history', icon: '📊', label: '召唤师信息' },
  { path: '/user-tag', icon: '🏷️', label: '标签分析' },
  { path: '/tag-config', icon: '📋', label: '标签配置' },
  { path: '/automation', icon: '⚙️', label: '自动化' },
  { path: '/settings', icon: '🔧', label: '设置' }
]

const currentPath = computed(() => route.path)

function navigateTo(path: string) {
  router.push(path)
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-header">
      <div class="app-brand">
        <span class="brand-icon">🎮</span>
        <span class="brand-text">RankPeek</span>
      </div>
    </div>

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
          <span class="nav-label">{{ item.label }}</span>
        </li>
      </ul>
    </nav>

    <div class="sidebar-footer">
      <div class="version">v0.0.8</div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 220px;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
}

.sidebar-header {
  padding: 20px 20px 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.app-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-icon {
  font-size: 24px;
}

.brand-text {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.28px;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 12px;
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
  gap: 12px;
  padding: 10px 14px;
  margin-bottom: 2px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 400;
  letter-spacing: -0.224px;
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
  font-size: 16px;
  width: 20px;
  text-align: center;
  flex-shrink: 0;
}

.nav-label {
  font-size: 13px;
}

.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--border-subtle);
}

.version {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  color: var(--text-tertiary);
  text-align: center;
  letter-spacing: -0.12px;
}
</style>
