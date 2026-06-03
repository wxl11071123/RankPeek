<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { currentLocale } from '@/i18n'
import {
  fetchRankPeekAnnouncementArchive,
  fetchRankPeekAnnouncements,
  isRankPeekAnnouncementDismissed,
  isRankPeekAnnouncementRead,
  markRankPeekAnnouncementRead,
  type RankPeekAnnouncement,
  type RankPeekAnnouncementQuery
} from '@/services/rankpeekCloudClient'

const ARCHIVE_LIMIT = 20

const activeAnnouncements = ref<RankPeekAnnouncement[]>([])
const archivedAnnouncements = ref<RankPeekAnnouncement[]>([])
const isPanelOpen = ref(false)
const isLoading = ref(false)
const popupAnnouncement = ref<RankPeekAnnouncement | null>(null)
const readRevision = ref(0)

const unreadAnnouncements = computed(() => {
  readRevision.value
  return activeAnnouncements.value.filter(announcement => isAnnouncementUnread(announcement))
})
const unreadCount = computed(() => unreadAnnouncements.value.length)
const panelAnnouncements = computed(() => {
  return archivedAnnouncements.value.length ? archivedAnnouncements.value : activeAnnouncements.value
})

onMounted(() => {
  void loadAnnouncements({ notify: true })
})

async function loadAnnouncements(options: { notify: boolean }) {
  if (isLoading.value) {
    return
  }

  isLoading.value = true
  try {
    const query = await buildAnnouncementQuery()
    const [active, archive] = await Promise.all([
      fetchRankPeekAnnouncements(query, { includeDismissedAnnouncements: true }),
      fetchRankPeekAnnouncementArchive(query, { limit: ARCHIVE_LIMIT })
    ])

    activeAnnouncements.value = active
    archivedAnnouncements.value = mergeAnnouncementLists(active, archive)

    if (options.notify && !isPanelOpen.value) {
      popupAnnouncement.value = active.find(announcement => isAnnouncementUnread(announcement)) ?? null
    }
  } finally {
    isLoading.value = false
  }
}

async function buildAnnouncementQuery(): Promise<RankPeekAnnouncementQuery> {
  let version = '1.0.0'
  try {
    version = await window.electronAPI?.getVersion?.() ?? version
  } catch {
    version = '1.0.0'
  }

  return {
    version,
    platform: window.electronAPI?.platform ?? navigator.platform,
    locale: currentLocale.value,
    channel: 'stable'
  }
}

function togglePanel() {
  if (isPanelOpen.value) {
    isPanelOpen.value = false
    return
  }

  openPanel()
}

function openPanel() {
  isPanelOpen.value = true
  popupAnnouncement.value = null
  markActiveAnnouncementsRead()
}

function closePanel() {
  isPanelOpen.value = false
}

function dismissPopup() {
  if (popupAnnouncement.value) {
    markAnnouncementRead(popupAnnouncement.value.id)
  }
  popupAnnouncement.value = null
}

function markActiveAnnouncementsRead() {
  for (const announcement of activeAnnouncements.value) {
    markAnnouncementRead(announcement.id)
  }
}

function markAnnouncementRead(id: string) {
  markRankPeekAnnouncementRead(id)
  readRevision.value += 1
}

function isAnnouncementUnread(announcement: RankPeekAnnouncement): boolean {
  return !isRankPeekAnnouncementRead(announcement.id) && !isRankPeekAnnouncementDismissed(announcement.id)
}

async function openAnnouncementLink(announcement: RankPeekAnnouncement) {
  if (!announcement.linkUrl) {
    return
  }

  if (!window.electronAPI?.openExternal) {
    window.open(announcement.linkUrl, '_blank', 'noopener,noreferrer')
    return
  }

  const result = await window.electronAPI.openExternal(announcement.linkUrl)
  if (result && !result.success) {
    window.open(announcement.linkUrl, '_blank', 'noopener,noreferrer')
  }
}

function mergeAnnouncementLists(
  active: RankPeekAnnouncement[],
  archive: RankPeekAnnouncement[]
): RankPeekAnnouncement[] {
  const seen = new Set<string>()
  return [...active, ...archive].filter(announcement => {
    if (seen.has(announcement.id)) {
      return false
    }
    seen.add(announcement.id)
    return true
  })
}

function levelLabel(level: RankPeekAnnouncement['level']): string {
  if (level === 'critical') {
    return '紧急'
  }
  if (level === 'warning') {
    return '重要'
  }
  return '普通'
}

function announcementMeta(announcement: RankPeekAnnouncement): string {
  const parts = [levelLabel(announcement.level)]
  if (announcement.startsAt) {
    parts.push(formatDate(announcement.startsAt))
  } else if (announcement.endsAt) {
    parts.push(formatDate(announcement.endsAt))
  }
  return parts.join(' · ')
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) {
    return ''
  }
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<template>
  <div class="announcement-center">
    <button
      class="announcement-center-button"
      type="button"
      title="公告中心"
      aria-label="公告中心"
      @click="togglePanel"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M4 10.5v3a2 2 0 0 0 2 2h1.5l1.7 3.4a1 1 0 0 0 1.8-.9l-1.25-2.5H12l6 3.5V5l-6 3.5H6a2 2 0 0 0-2 2Z"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linejoin="round"
        />
        <path d="M20 9v6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
      </svg>
      <span v-if="unreadCount" class="announcement-badge">{{ unreadCount }}</span>
    </button>

    <section
      v-if="popupAnnouncement"
      class="announcement-popup"
      :class="`level-${popupAnnouncement.level}`"
      role="dialog"
      aria-live="polite"
    >
      <div class="announcement-popup-copy">
        <span>{{ levelLabel(popupAnnouncement.level) }}</span>
        <strong>{{ popupAnnouncement.title }}</strong>
        <p>{{ popupAnnouncement.body }}</p>
      </div>
      <div class="announcement-popup-actions">
        <button type="button" @click="openPanel">查看公告</button>
        <button
          v-if="popupAnnouncement.linkUrl"
          type="button"
          @click="openAnnouncementLink(popupAnnouncement)"
        >
          打开链接
        </button>
        <button type="button" @click="dismissPopup">知道了</button>
      </div>
    </section>

    <section v-if="isPanelOpen" class="announcement-panel">
      <header class="announcement-panel-header">
        <div>
          <strong>公告中心</strong>
          <span>{{ unreadCount ? `${unreadCount} 条未读` : '已读完' }}</span>
        </div>
        <div class="announcement-panel-actions">
          <button type="button" title="刷新公告" @click="loadAnnouncements({ notify: false })">
            刷新
          </button>
          <button type="button" title="关闭公告中心" @click="closePanel">关闭</button>
        </div>
      </header>

      <div v-if="!panelAnnouncements.length" class="announcement-empty">
        {{ isLoading ? '加载中' : '暂无公告' }}
      </div>

      <div v-else class="announcement-list">
        <article
          v-for="announcement in panelAnnouncements"
          :key="announcement.id"
          class="announcement-panel-item"
          :class="[`level-${announcement.level}`, { unread: isAnnouncementUnread(announcement) }]"
        >
          <div class="announcement-item-heading">
            <strong>{{ announcement.title }}</strong>
            <span>{{ announcementMeta(announcement) }}</span>
          </div>
          <p>{{ announcement.body }}</p>
          <button
            v-if="announcement.linkUrl"
            type="button"
            @click="openAnnouncementLink(announcement)"
          >
            查看
          </button>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.announcement-center {
  position: relative;
  display: inline-flex;
  align-items: center;
  -webkit-app-region: no-drag;
}

.announcement-center-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 28px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.announcement-center-button:hover {
  border-color: var(--border-subtle);
  background: var(--bg-hover);
  color: var(--text-primary);
}

.announcement-center-button svg {
  width: 18px;
  height: 18px;
}

.announcement-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 14px;
  height: 14px;
  padding: 0 4px;
  border: 1px solid var(--bg-secondary);
  border-radius: var(--radius-pill);
  background: var(--error-color);
  color: white;
  font-size: 10px;
  font-weight: 700;
  line-height: 12px;
}

.announcement-popup {
  position: fixed;
  top: 52px;
  right: 24px;
  z-index: 80;
  width: min(380px, calc(100vw - 48px));
  padding: 14px;
  border: 1px solid rgba(var(--accent-rgb), 0.36);
  border-radius: var(--radius-lg);
  background: var(--bg-secondary);
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.36);
  color: var(--text-primary);
}

.announcement-popup.level-warning {
  border-color: rgba(245, 158, 11, 0.46);
}

.announcement-popup.level-critical {
  border-color: rgba(239, 68, 68, 0.46);
}

.announcement-popup-copy span,
.announcement-panel-header span,
.announcement-item-heading span {
  color: var(--text-tertiary);
  font-size: 12px;
}

.announcement-popup-copy strong {
  display: block;
  margin-top: 4px;
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 720;
}

.announcement-popup-copy p,
.announcement-panel-item p {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;
}

.announcement-popup-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.announcement-popup-actions button,
.announcement-panel-actions button,
.announcement-panel-item button {
  min-height: 28px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--bg-tertiary);
  color: var(--text-primary);
  cursor: pointer;
  font-size: 12px;
  font-weight: 650;
}

.announcement-popup-actions button {
  padding: 0 10px;
}

.announcement-popup-actions button:hover,
.announcement-panel-actions button:hover,
.announcement-panel-item button:hover {
  border-color: rgba(var(--accent-rgb), 0.42);
  color: var(--accent-color);
}

.announcement-panel {
  position: absolute;
  top: 34px;
  left: 0;
  z-index: 90;
  width: min(390px, calc(100vw - 28px));
  max-height: min(520px, calc(100vh - 58px));
  overflow: hidden;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--bg-secondary);
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.36);
  color: var(--text-primary);
}

.announcement-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-bottom: 1px solid var(--border-subtle);
}

.announcement-panel-header div:first-child {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.announcement-panel-header strong {
  font-size: 14px;
  font-weight: 720;
}

.announcement-panel-actions {
  display: inline-flex;
  gap: 8px;
  flex: 0 0 auto;
}

.announcement-panel-actions button {
  padding: 0 9px;
}

.announcement-empty {
  padding: 24px 12px;
  color: var(--text-tertiary);
  font-size: 13px;
  text-align: center;
}

.announcement-list {
  max-height: 452px;
  overflow-y: auto;
  padding: 10px;
}

.announcement-panel-item {
  padding: 11px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-primary);
}

.announcement-panel-item + .announcement-panel-item {
  margin-top: 8px;
}

.announcement-panel-item.unread {
  border-color: rgba(var(--accent-rgb), 0.42);
}

.announcement-panel-item.level-warning {
  border-left: 3px solid rgb(245, 158, 11);
}

.announcement-panel-item.level-critical {
  border-left: 3px solid rgb(239, 68, 68);
}

.announcement-item-heading {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.announcement-item-heading strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 720;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.announcement-panel-item button {
  margin-top: 9px;
  padding: 0 10px;
}
</style>
