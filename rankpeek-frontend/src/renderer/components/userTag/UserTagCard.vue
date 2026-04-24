<script setup lang="ts">
import type { UserTag } from '@/types/api'

defineProps<{
  tag: UserTag
}>()
</script>

<template>
  <div class="user-tag-card">
    <h2>玩家标签</h2>

    <div v-if="tag.tag && tag.tag.length > 0" class="tags-container">
      <div
        v-for="(item, index) in tag.tag"
        :key="index"
        class="tag-item"
        :class="{
          good: item.good === true,
          bad: item.good === false,
          neutral: item.good === null
        }"
      >
        <span class="tag-icon">
          {{ item.good === true ? '✓' : item.good === false ? '✗' : '•' }}
        </span>
        <div class="tag-content">
          <span class="tag-name">{{ item.tagName }}</span>
          <span class="tag-desc">{{ item.tagDesc }}</span>
        </div>
      </div>
    </div>

    <div v-else class="no-tags">
      <span class="empty-icon">-</span>
      <span>暂无显著标签</span>
    </div>
  </div>
</template>

<style scoped>
.user-tag-card {
  background: var(--bg-secondary);
  border-radius: 12px;
  padding: 20px;
}

.user-tag-card h2 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 16px 0;
  color: var(--text-primary);
}

.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.tag-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 10px;
  background: var(--bg-tertiary);
}

.tag-item.good {
  background: rgba(46, 204, 113, 0.15);
  border: 1px solid rgba(46, 204, 113, 0.3);
}

.tag-item.bad {
  background: rgba(231, 76, 60, 0.15);
  border: 1px solid rgba(231, 76, 60, 0.3);
}

.tag-item.neutral {
  background: rgba(52, 152, 219, 0.15);
  border: 1px solid rgba(52, 152, 219, 0.3);
}

.tag-icon {
  font-size: 18px;
  font-weight: bold;
}

.tag-item.good .tag-icon {
  color: var(--success-color);
}

.tag-item.bad .tag-icon {
  color: var(--error-color);
}

.tag-item.neutral .tag-icon {
  color: #3498db;
}

.tag-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tag-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.tag-desc {
  font-size: 12px;
  color: var(--text-secondary);
}

.no-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px;
  color: var(--text-tertiary);
  font-size: 14px;
}

.empty-icon {
  font-size: 20px;
}
</style>
