<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAutomationStore } from '@/stores/automation'

const router = useRouter()
const automationStore = useAutomationStore()

const actions = [
  {
    id: 'auto-accept',
    label: '自动接受',
    description: '自动接受匹配到的对局',
    icon: '✅',
    enabled: automationStore.autoAccept,
    toggle: () => automationStore.setAutoAccept(!automationStore.autoAccept)
  },
  {
    id: 'auto-match',
    label: '自动匹配',
    description: '自动开始匹配',
    icon: '🎮',
    enabled: automationStore.autoMatch,
    toggle: () => automationStore.setAutoMatch(!automationStore.autoMatch)
  },
  {
    id: 'auto-pick',
    label: '自动选人',
    description: '自动选择英雄',
    icon: '🗡️',
    enabled: automationStore.autoPick,
    toggle: () => automationStore.setAutoPick(!automationStore.autoPick)
  },
  {
    id: 'auto-ban',
    label: '自动禁人',
    description: '自动禁用英雄',
    icon: '🚫',
    enabled: automationStore.autoBan,
    toggle: () => automationStore.setAutoBan(!automationStore.autoBan)
  }
]
</script>

<template>
  <div class="quick-actions">
    <div class="section-header">
      <h2>快捷操作</h2>
      <button class="settings-link" @click="router.push('/automation')">
        详细设置 →
      </button>
    </div>

    <div class="actions-grid">
      <div
        v-for="action in actions"
        :key="action.id"
        class="action-card"
        :class="{ active: action.enabled }"
      >
        <div class="action-icon">{{ action.icon }}</div>
        <div class="action-info">
          <div class="action-label">{{ action.label }}</div>
          <div class="action-desc">{{ action.description }}</div>
        </div>
        <label class="toggle">
          <input
            type="checkbox"
            :checked="action.enabled"
            @change="action.toggle"
          />
          <span class="slider"></span>
        </label>
      </div>
    </div>
  </div>
</template>

<style scoped>
.quick-actions {
  margin-top: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h2 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.settings-link {
  background: none;
  border: none;
  color: var(--accent-color);
  font-size: 14px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.15s;
}

.settings-link:hover {
  background: var(--bg-hover);
}

.actions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}

.action-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  transition: all 0.15s;
}

.action-card.active {
  border-color: var(--accent-color);
  background: rgba(var(--accent-rgb), 0.05);
}

.action-icon {
  font-size: 24px;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-tertiary);
  border-radius: 10px;
}

.action-info {
  flex: 1;
}

.action-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.action-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

/* Toggle Switch */
.toggle {
  position: relative;
  width: 44px;
  height: 24px;
}

.toggle input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--bg-tertiary);
  border-radius: 24px;
  transition: 0.2s;
}

.slider::before {
  position: absolute;
  content: '';
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background: white;
  border-radius: 50%;
  transition: 0.2s;
}

.toggle input:checked + .slider {
  background: var(--accent-color);
}

.toggle input:checked + .slider::before {
  transform: translateX(20px);
}
</style>
