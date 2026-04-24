<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useAutomationStore } from '@/stores/automation'
import { apiClient } from '@/api/httpClient'
import type { ChampionOption } from '@/types/api'

const automationStore = useAutomationStore()

// 英雄列表
const championOptions = ref<ChampionOption[]>([])
const loadingChampions = ref(false)

// 编辑弹窗
const showEditModal = ref(false)
const editType = ref<'pick' | 'ban'>('pick')
const searchQuery = ref('')
const selectedChampions = ref<number[]>([])

onMounted(async () => {
  await automationStore.init()
  await loadChampions()
})

// 加载英雄列表
async function loadChampions() {
  loadingChampions.value = true
  try {
    championOptions.value = await apiClient.getChampionOptions()
  } catch (e) {
    console.error('加载英雄列表失败', e)
  } finally {
    loadingChampions.value = false
  }
}

// 过滤英雄列表
const filteredChampions = computed(() => {
  if (!searchQuery.value) return championOptions.value
  const query = searchQuery.value.toLowerCase()
  return championOptions.value.filter(c =>
    c.label.toLowerCase().includes(query) ||
    c.realName.toLowerCase().includes(query) ||
    c.nickname.toLowerCase().includes(query) ||
    c.value.toString() === query
  )
})

// 打开编辑弹窗
function openEditModal(type: 'pick' | 'ban') {
  editType.value = type
  selectedChampions.value = type === 'pick'
    ? [...automationStore.pickChampions]
    : [...automationStore.banChampions]
  searchQuery.value = ''
  showEditModal.value = true
}

// 切换英雄选择
function toggleChampion(championId: number) {
  const index = selectedChampions.value.indexOf(championId)
  if (index === -1) {
    selectedChampions.value.push(championId)
  } else {
    selectedChampions.value.splice(index, 1)
  }
}

// 移除已选英雄
function removeChampion(championId: number) {
  const index = selectedChampions.value.indexOf(championId)
  if (index !== -1) {
    selectedChampions.value.splice(index, 1)
  }
}

// 上移英雄
function moveUp(championId: number) {
  const index = selectedChampions.value.indexOf(championId)
  if (index > 0) {
    [selectedChampions.value[index - 1], selectedChampions.value[index]] =
    [selectedChampions.value[index], selectedChampions.value[index - 1]]
  }
}

// 下移英雄
function moveDown(championId: number) {
  const index = selectedChampions.value.indexOf(championId)
  if (index < selectedChampions.value.length - 1) {
    [selectedChampions.value[index], selectedChampions.value[index + 1]] =
    [selectedChampions.value[index + 1], selectedChampions.value[index]]
  }
}

// 保存
async function saveChampions() {
  if (editType.value === 'pick') {
    await automationStore.updatePickChampions(selectedChampions.value)
  } else {
    await automationStore.updateBanChampions(selectedChampions.value)
  }
  showEditModal.value = false
}

// 获取英雄名称
function getChampionName(id: number): string {
  const champion = championOptions.value.find(c => c.value === id)
  return champion?.label || String(id)
}

// 更新延迟时间
async function updateMatchDelay(delay: number) {
  const validDelay = Math.max(0, Math.min(10, delay))
  await automationStore.updateAutoMatchDelay(validDelay)
}

async function updateAcceptDelay(delay: number) {
  const validDelay = Math.max(0, Math.min(10, delay))
  await automationStore.updateAutoAcceptDelay(validDelay)
}

const toggleItems = [
  {
    key: 'autoMatch',
    label: '自动开始匹配',
    description: '在大厅时自动开始匹配，取消匹配后自动禁用',
    value: automationStore.autoMatch,
    toggle: () => automationStore.setAutoMatch(!automationStore.autoMatch),
    delayValue: automationStore.autoMatchDelay,
    delayKey: 'matchDelay',
    delayLabel: '延迟时间',
    delayUnit: '秒',
    onDelayChange: updateMatchDelay
  },
  {
    key: 'autoAccept',
    label: '自动接受对局',
    description: '匹配成功后自动接受',
    value: automationStore.autoAccept,
    toggle: () => automationStore.setAutoAccept(!automationStore.autoAccept),
    delayValue: automationStore.autoAcceptDelay,
    delayKey: 'acceptDelay',
    delayLabel: '延迟时间',
    delayUnit: '秒',
    onDelayChange: updateAcceptDelay
  },
  {
    key: 'autoPick',
    label: '自动选择英雄',
    description: '在选人阶段自动选择预设英雄',
    value: automationStore.autoPick,
    toggle: () => automationStore.setAutoPick(!automationStore.autoPick)
  },
  {
    key: 'autoBan',
    label: '自动禁用英雄',
    description: '在禁人阶段自动禁用预设英雄',
    value: automationStore.autoBan,
    toggle: () => automationStore.setAutoBan(!automationStore.autoBan)
  }
]
</script>

<template>
  <div class="automation-view">
    <div class="page-header">
      <h1>自动化设置</h1>
      <p>配置游戏自动化功能</p>
    </div>

    <!-- 自动化开关 -->
    <div class="settings-section">
      <h2>功能开关</h2>
      <div class="toggle-list">
        <div
          v-for="item in toggleItems"
          :key="item.key"
          class="toggle-item"
          :class="{ 'has-delay': item.delayKey }"
        >
          <div class="toggle-info">
            <span class="toggle-label">{{ item.label }}</span>
            <span class="toggle-desc">{{ item.description }}</span>
          </div>
          <div class="toggle-controls">
            <!-- 延迟时间输入 -->
            <div v-if="item.delayKey" class="delay-input-group">
              <label class="delay-label">{{ item.delayLabel }}</label>
              <input
                type="number"
                :value="item.delayValue"
                @input="e => item.onDelayChange(Number((e.target as HTMLInputElement).value))"
                min="0"
                max="10"
                class="delay-input"
              />
              <span class="delay-unit">{{ item.delayUnit }}</span>
            </div>
            <label class="toggle-switch">
              <input
                type="checkbox"
                :checked="item.value"
                @change="item.toggle"
              />
              <span class="slider"></span>
            </label>
          </div>
        </div>
      </div>
    </div>

    <!-- 英雄选择 -->
    <div class="settings-section">
      <h2>英雄预设</h2>

      <div class="champion-section">
        <h3>选择英雄列表</h3>
        <p class="section-desc">设置自动选人时优先选择的英雄（按优先级排序）</p>
        <div class="champion-input">
          <div class="champion-tags">
            <span
              v-for="id in automationStore.pickChampions"
              :key="id"
              class="champion-tag"
            >
              {{ getChampionName(id) }}
            </span>
            <span v-if="automationStore.pickChampions.length === 0" class="no-champion">
              未设置
            </span>
          </div>
          <button class="edit-btn" @click="openEditModal('pick')">编辑</button>
        </div>
      </div>

      <div class="champion-section">
        <h3>禁用英雄列表</h3>
        <p class="section-desc">设置自动禁人时优先禁用的英雄（按优先级排序）</p>
        <div class="champion-input">
          <div class="champion-tags">
            <span
              v-for="id in automationStore.banChampions"
              :key="id"
              class="champion-tag"
            >
              {{ getChampionName(id) }}
            </span>
            <span v-if="automationStore.banChampions.length === 0" class="no-champion">
              未设置
            </span>
          </div>
          <button class="edit-btn" @click="openEditModal('ban')">编辑</button>
        </div>
      </div>
    </div>

    <!-- 使用说明 -->
    <div class="tips-section">
      <h2>使用说明</h2>
      <ul class="tips-list">
        <li>自动匹配：在大厅时自动开始匹配，手动取消匹配后会自动禁用。可设置延迟时间（0-10秒）</li>
        <li>自动接受：匹配成功后自动点击接受按钮。可设置延迟时间（0-10秒）</li>
        <li>自动选人：轮到你选择时自动选择预设英雄，会跳过已被选择/禁用的英雄</li>
        <li>自动禁人：轮到你禁人时自动禁用预设英雄，不会禁用队友预选的英雄</li>
      </ul>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="showEditModal" class="modal-overlay" @click.self="showEditModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h2>{{ editType === 'pick' ? '选择英雄列表' : '禁用英雄列表' }}</h2>
          <button class="close-btn" @click="showEditModal = false">&times;</button>
        </div>

        <div class="modal-body">
          <!-- 搜索框 -->
          <input
            v-model="searchQuery"
            type="text"
            class="search-input"
            placeholder="搜索英雄名称..."
          />

          <!-- 已选英雄 -->
          <div class="selected-section">
            <h4>已选英雄（拖动排序）</h4>
            <div class="selected-list">
              <template v-if="selectedChampions.length > 0">
                <div
                  v-for="(id, index) in selectedChampions"
                  :key="id"
                  class="selected-item"
                >
                  <span class="order">{{ index + 1 }}</span>
                  <img
                    class="mini-icon"
                    :src="`http://127.0.0.1:8080/api/v1/asset/champion/${id}`"
                  />
                  <span class="name">{{ getChampionName(id) }}</span>
                  <div class="item-actions">
                    <button class="action-btn" @click="moveUp(id)" :disabled="index === 0">↑</button>
                    <button class="action-btn" @click="moveDown(id)" :disabled="index === selectedChampions.length - 1">↓</button>
                    <button class="action-btn remove" @click="removeChampion(id)">×</button>
                  </div>
                </div>
              </template>
              <p v-else class="empty-hint">请从下方列表中选择英雄</p>
            </div>
          </div>

          <!-- 英雄列表 -->
          <div class="champion-list">
            <h4>全部英雄</h4>
            <div class="champion-grid">
              <div
                v-for="champion in filteredChampions"
                :key="champion.value"
                class="champion-item"
                :class="{ selected: selectedChampions.includes(champion.value) }"
                @click="toggleChampion(champion.value)"
              >
                <img
                  class="champion-icon"
                  :src="`http://127.0.0.1:8080/api/v1/asset/champion/${champion.value}`"
                />
                <span class="champion-name">{{ champion.label }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button class="cancel-btn" @click="showEditModal = false">取消</button>
          <button class="save-btn" @click="saveChampions">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.automation-view {
  max-width: 800px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 32px;
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

.settings-section {
  margin-bottom: 32px;
}

.settings-section h2 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 16px 0;
  color: var(--text-primary);
}

.toggle-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toggle-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: var(--bg-secondary);
  border-radius: 10px;
}

.toggle-item.has-delay {
  flex-wrap: wrap;
  gap: 12px;
}

.toggle-controls {
  display: flex;
  align-items: center;
  gap: 16px;
}

.delay-input-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.delay-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.delay-input {
  width: 60px;
  padding: 6px 8px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 13px;
  text-align: center;
}

.delay-input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.delay-unit {
  font-size: 12px;
  color: var(--text-tertiary);
}

.toggle-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.toggle-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.toggle-desc {
  font-size: 12px;
  color: var(--text-tertiary);
}

/* Toggle Switch */
.toggle-switch {
  position: relative;
  width: 48px;
  height: 26px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-switch .slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--bg-tertiary);
  border-radius: 26px;
  transition: 0.2s;
}

.toggle-switch .slider::before {
  position: absolute;
  content: '';
  height: 20px;
  width: 20px;
  left: 3px;
  bottom: 3px;
  background: white;
  border-radius: 50%;
  transition: 0.2s;
}

.toggle-switch input:checked + .slider {
  background: var(--accent-color);
}

.toggle-switch input:checked + .slider::before {
  transform: translateX(22px);
}

.champion-section {
  margin-bottom: 20px;
}

.champion-section h3 {
  font-size: 14px;
  font-weight: 500;
  margin: 0 0 4px 0;
  color: var(--text-primary);
}

.section-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  margin: 0 0 12px 0;
}

.champion-input {
  display: flex;
  gap: 8px;
  align-items: center;
}

.champion-tags {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  min-height: 42px;
}

.champion-tag {
  padding: 4px 10px;
  background: var(--accent-color);
  color: white;
  border-radius: 4px;
  font-size: 12px;
}

.no-champion {
  color: var(--text-tertiary);
  font-size: 13px;
}

.edit-btn {
  padding: 10px 16px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}

.edit-btn:hover {
  background: var(--bg-hover);
}

.tips-section {
  padding: 20px;
  background: var(--bg-secondary);
  border-radius: 12px;
}

.tips-section h2 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 12px 0;
  color: var(--text-primary);
}

.tips-list {
  margin: 0;
  padding-left: 20px;
}

.tips-list li {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
  line-height: 1.5;
}

/* Modal styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.modal-content {
  background: var(--bg-primary);
  border-radius: 12px;
  width: 100%;
  max-width: 600px;
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
}

.modal-header h2 {
  margin: 0;
  font-size: 18px;
  color: var(--text-primary);
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.close-btn:hover {
  color: var(--text-primary);
}

.modal-body {
  padding: 16px;
  overflow-y: auto;
  flex: 1;
}

.search-input {
  width: 100%;
  padding: 10px 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 14px;
  margin-bottom: 16px;
}

.search-input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.selected-section {
  margin-bottom: 16px;
}

.selected-section h4,
.champion-list h4 {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin: 0 0 8px 0;
}

.selected-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.selected-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-secondary);
  border-radius: 6px;
}

.order {
  width: 20px;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
}

.mini-icon {
  width: 24px;
  height: 24px;
  border-radius: 4px;
}

.selected-item .name {
  flex: 1;
  font-size: 13px;
  color: var(--text-primary);
}

.item-actions {
  display: flex;
  gap: 4px;
}

.action-btn {
  width: 24px;
  height: 24px;
  background: var(--bg-tertiary);
  border: none;
  border-radius: 4px;
  color: var(--text-primary);
  cursor: pointer;
  font-size: 12px;
}

.action-btn:hover:not(:disabled) {
  background: var(--bg-hover);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-btn.remove {
  color: var(--error-color);
}

.empty-hint {
  color: var(--text-tertiary);
  font-size: 13px;
  margin: 0;
  padding: 16px;
  text-align: center;
}

.champion-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(70px, 1fr));
  gap: 8px;
}

.champion-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  background: var(--bg-secondary);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  border: 2px solid transparent;
}

.champion-item:hover {
  background: var(--bg-hover);
}

.champion-item.selected {
  border-color: var(--accent-color);
  background: rgba(var(--accent-rgb), 0.1);
}

.champion-icon {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  margin-bottom: 4px;
}

.champion-name {
  font-size: 11px;
  color: var(--text-primary);
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 60px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid var(--border-color);
}

.cancel-btn,
.save-btn {
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.15s;
}

.cancel-btn {
  background: var(--bg-secondary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
}

.save-btn {
  background: var(--accent-color);
  color: white;
  border: none;
}

.cancel-btn:hover,
.save-btn:hover {
  opacity: 0.9;
}
</style>
