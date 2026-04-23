<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { apiClient } from '@/api/httpClient'
import type { TagConfig } from '@/types/api'

const tagConfigs = ref<TagConfig[]>([])
const loading = ref(false)
const editingTag = ref<TagConfig | null>(null)
const showEditor = ref(false)

onMounted(async () => {
  await loadConfigs()
})

async function loadConfigs() {
  loading.value = true
  try {
    tagConfigs.value = await apiClient.getTagConfigs()
  } catch (e) {
    console.error('Failed to load tag configs:', e)
  } finally {
    loading.value = false
  }
}

async function toggleEnabled(id: string) {
  try {
    await apiClient.toggleTagConfig(id)
    await loadConfigs()
  } catch (e) {
    console.error('Failed to toggle tag:', e)
  }
}

async function deleteTag(id: string) {
  if (!confirm('确定要删除这个标签配置吗？')) return

  try {
    await apiClient.deleteTagConfig(id)
    await loadConfigs()
  } catch (e) {
    console.error('Failed to delete tag:', e)
  }
}

async function resetToDefault() {
  if (!confirm('确定要重置为默认配置吗？这将覆盖所有自定义配置。')) return

  try {
    await apiClient.resetTagConfigs()
    await loadConfigs()
  } catch (e) {
    console.error('Failed to reset configs:', e)
  }
}

function editTag(tag: TagConfig) {
  editingTag.value = JSON.parse(JSON.stringify(tag))
  showEditor.value = true
}

function createNewTag() {
  editingTag.value = {
    id: 'custom_' + Date.now(),
    name: '新标签',
    desc: '标签描述',
    good: null,
    enabled: true,
    isDefault: false,
    condition: {
      type: 'history',
      filters: [],
      refresh: { type: 'count', op: '>=', value: 1 }
    }
  }
  showEditor.value = true
}

async function saveTag() {
  if (!editingTag.value) return

  try {
    if (tagConfigs.value.some(t => t.id === editingTag.value?.id)) {
      await apiClient.updateTagConfig(editingTag.value.id, editingTag.value)
    } else {
      await apiClient.addTagConfig(editingTag.value)
    }
    showEditor.value = false
    editingTag.value = null
    await loadConfigs()
  } catch (e) {
    console.error('Failed to save tag:', e)
  }
}

function cancelEdit() {
  showEditor.value = false
  editingTag.value = null
}

function getConditionSummary(condition: any): string {
  if (!condition) return '无条件'

  if (condition.type === 'history') {
    const filterCount = condition.filters?.length || 0
    const refreshType = condition.refresh?.type || 'unknown'
    return `${filterCount} 个过滤器, 刷新类型: ${refreshType}`
  }

  if (condition.type === 'and') {
    return `AND (${condition.conditions?.length || 0} 个条件)`
  }

  if (condition.type === 'or') {
    return `OR (${condition.conditions?.length || 0} 个条件)`
  }

  return condition.type
}
</script>

<template>
  <div class="tag-config-view">
    <div class="page-header">
      <h1>标签配置</h1>
      <p>自定义玩家标签规则</p>
    </div>

    <!-- 操作按钮 -->
    <div class="action-bar">
      <button @click="createNewTag" class="primary-btn">+ 新建标签</button>
      <button @click="resetToDefault" class="secondary-btn">重置默认</button>
    </div>

    <!-- 标签列表 -->
    <div v-if="loading" class="loading">加载中...</div>

    <div v-else class="tag-list">
      <div
        v-for="tag in tagConfigs"
        :key="tag.id"
        class="tag-card"
        :class="{ disabled: !tag.enabled }"
      >
        <div class="tag-header">
          <div class="tag-info">
            <span class="tag-name" :class="{ good: tag.good, bad: tag.good === false }">
              {{ tag.name }}
            </span>
            <span v-if="tag.isDefault" class="default-badge">默认</span>
          </div>
          <div class="tag-actions">
            <label class="toggle">
              <input
                type="checkbox"
                :checked="tag.enabled"
                @change="toggleEnabled(tag.id)"
              />
              <span class="slider"></span>
            </label>
            <button @click="editTag(tag)" class="icon-btn" title="编辑">✏️</button>
            <button
              v-if="!tag.isDefault"
              @click="deleteTag(tag.id)"
              class="icon-btn"
              title="删除"
            >🗑️</button>
          </div>
        </div>

        <div class="tag-desc">{{ tag.desc }}</div>

        <div class="tag-condition">
          <span class="label">条件:</span>
          <span class="value">{{ getConditionSummary(tag.condition) }}</span>
        </div>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="showEditor" class="modal-overlay" @click.self="cancelEdit">
      <div class="modal">
        <div class="modal-header">
          <h2>{{ editingTag?.isDefault ? '查看标签' : '编辑标签' }}</h2>
          <button @click="cancelEdit" class="close-btn">×</button>
        </div>

        <div class="modal-body">
          <div class="form-group">
            <label>标签名称</label>
            <input v-model="editingTag!.name" type="text" placeholder="支持 {N} 占位符" />
          </div>

          <div class="form-group">
            <label>标签描述</label>
            <input v-model="editingTag!.desc" type="text" />
          </div>

          <div class="form-group">
            <label>标签类型</label>
            <select v-model="editingTag!.good">
              <option :value="null">中性</option>
              <option :value="true">正面标签</option>
              <option :value="false">负面标签</option>
            </select>
          </div>

          <div class="form-group">
            <label>启用状态</label>
            <label class="toggle">
              <input type="checkbox" v-model="editingTag!.enabled" />
              <span class="slider"></span>
            </label>
          </div>

          <div class="condition-preview">
            <h3>条件预览</h3>
            <pre>{{ JSON.stringify(editingTag!.condition, null, 2) }}</pre>
          </div>

          <p class="hint">
            完整的条件编辑器需要更复杂的 UI。当前版本支持直接编辑 JSON 配置。
          </p>
        </div>

        <div class="modal-footer">
          <button @click="cancelEdit" class="secondary-btn">取消</button>
          <button @click="saveTag" class="primary-btn">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tag-config-view {
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px 0;
  color: var(--text-primary);
}

.page-header p {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.action-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.primary-btn {
  padding: 10px 20px;
  background: var(--accent-color);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.15s;
}

.primary-btn:hover {
  opacity: 0.9;
}

.secondary-btn {
  padding: 10px 20px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s;
}

.secondary-btn:hover {
  background: var(--bg-hover);
}

.loading {
  text-align: center;
  padding: 40px;
  color: var(--text-secondary);
}

.tag-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tag-card {
  background: var(--bg-secondary);
  border-radius: 12px;
  padding: 16px;
  transition: opacity 0.15s;
}

.tag-card.disabled {
  opacity: 0.6;
}

.tag-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.tag-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tag-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.tag-name.good {
  color: var(--success-color);
}

.tag-name.bad {
  color: var(--error-color);
}

.default-badge {
  font-size: 11px;
  padding: 2px 6px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  color: var(--text-tertiary);
}

.tag-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-btn {
  background: none;
  border: none;
  font-size: 16px;
  cursor: pointer;
  padding: 4px;
  opacity: 0.7;
  transition: opacity 0.15s;
}

.icon-btn:hover {
  opacity: 1;
}

.tag-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.tag-condition {
  font-size: 12px;
  color: var(--text-tertiary);
}

.tag-condition .label {
  margin-right: 8px;
}

.tag-condition .value {
  color: var(--text-secondary);
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

.toggle .slider {
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

.toggle .slider::before {
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

/* Modal */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: var(--bg-secondary);
  border-radius: 16px;
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid var(--border-color);
}

.modal-header h2 {
  font-size: 18px;
  margin: 0;
  color: var(--text-primary);
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  color: var(--text-secondary);
  cursor: pointer;
}

.modal-body {
  padding: 20px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.form-group input[type="text"],
.form-group select {
  width: 100%;
  padding: 10px 12px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 14px;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: var(--accent-color);
}

.condition-preview {
  background: var(--bg-tertiary);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 16px;
}

.condition-preview h3 {
  font-size: 14px;
  margin: 0 0 8px 0;
  color: var(--text-primary);
}

.condition-preview pre {
  font-size: 11px;
  color: var(--text-secondary);
  margin: 0;
  overflow-x: auto;
  white-space: pre-wrap;
}

.hint {
  font-size: 12px;
  color: var(--text-tertiary);
  margin: 0;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px;
  border-top: 1px solid var(--border-color);
}
</style>
