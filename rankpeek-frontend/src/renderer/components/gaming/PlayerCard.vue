<template>
  <article class="player-card" :class="[teamClass, statusClass, { loading: sessionSummoner.isLoading }]">
    <div v-if="sessionSummoner.isLoading" class="skeleton">
      <div class="avatar-skeleton"></div>
      <div class="copy-skeleton">
        <span></span>
        <span></span>
      </div>
    </div>

    <template v-else-if="sessionSummoner.summoner">
      <div class="player-head">
        <div class="avatar-wrap">
          <img :src="avatarUrl" class="avatar" alt="" />
          <span v-if="sessionSummoner.preGroupMarkers?.name" class="pregroup-badge">
            {{ sessionSummoner.preGroupMarkers.name }}
          </span>
        </div>

        <div class="player-copy">
          <button class="player-id" type="button" @click="onNameClick">
            {{ sessionSummoner.summoner.gameName }}#{{ sessionSummoner.summoner.tagLine }}
          </button>

          <div class="meta-row">
            <div class="tier-row">
              <img :src="tierImgUrl" class="tier-icon" alt="" />
              <span>{{ tierText }}</span>
            </div>

            <div
              v-if="!recordStatusMeta && userTags.length"
              ref="tagContainerRef"
              class="name-tags"
            >
              <div ref="tagMeasureRef" class="tag-measure" aria-hidden="true">
                <span
                  v-for="(tag, index) in userTags"
                  :key="`${tag.tagName}-measure-${index}`"
                  class="tag-chip"
                  :class="tag.good === true ? 'good' : tag.good === false ? 'bad' : 'neutral'"
                  :title="tag.tagDesc"
                  data-tag-measure
                >
                  {{ tag.tagName }}
                </span>
                <button class="more-chip" type="button" tabindex="-1" data-overflow-measure>
                  +{{ overflowMeasureCount }}
                </button>
              </div>

              <div class="visible-tags">
                <span
                  v-for="(tag, index) in visibleUserTags"
                  :key="`${tag.tagName}-${index}`"
                  class="tag-chip"
                  :class="tag.good === true ? 'good' : tag.good === false ? 'bad' : 'neutral'"
                  :title="tag.tagDesc"
                >
                  {{ tag.tagName }}
                </span>
              </div>
              <div v-if="hiddenUserTagCount" class="tag-overflow">
                <button class="more-chip" type="button" :aria-label="`还有 ${hiddenUserTagCount} 个标签`">
                  +{{ hiddenUserTagCount }}
                </button>
                <div class="hidden-tags-popover">
                  <span
                    v-for="(tag, index) in hiddenUserTags"
                    :key="`${tag.tagName}-hidden-${index}`"
                    class="tag-chip"
                    :class="tag.good === true ? 'good' : tag.good === false ? 'bad' : 'neutral'"
                    :title="tag.tagDesc"
                  >
                    {{ tag.tagName }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

      </div>

      <div v-if="recordStatusMeta" class="status-banner">
        <strong>{{ recordStatusMeta.label }}</strong>
        <span>{{ recordStatusMeta.hint }}</span>
      </div>

      <template v-else>
        <div class="stats-grid">
          <div class="stat-item">
            <span class="stat-label">KDA</span>
            <strong>{{ kdaText }}</strong>
          </div>
          <div class="stat-item">
            <span class="stat-label">胜率</span>
            <strong>{{ winRate }}%</strong>
          </div>
          <div class="stat-item">
            <span class="stat-label">场次</span>
            <strong>{{ totalGames }}</strong>
          </div>
        </div>
      </template>
    </template>

    <div v-else class="empty-state">
      <strong>暂无数据</strong>
      <span>正在等待会话数据...</span>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { QueueInfo, RecordStatus, SessionSummoner } from '@/types/api'

import unranked from '@/assets/imgs/tier/unranked.png'
import iron from '@/assets/imgs/tier/iron.png'
import bronze from '@/assets/imgs/tier/bronze.png'
import silver from '@/assets/imgs/tier/silver.png'
import gold from '@/assets/imgs/tier/gold.png'
import platinum from '@/assets/imgs/tier/platinum.png'
import emerald from '@/assets/imgs/tier/emerald.png'
import diamond from '@/assets/imgs/tier/diamond.png'
import master from '@/assets/imgs/tier/master.png'
import grandmaster from '@/assets/imgs/tier/grandmaster.png'
import challenger from '@/assets/imgs/tier/challenger.png'

const props = defineProps<{
  sessionSummoner: SessionSummoner
  team?: 'blue' | 'red'
  isGameInProgress?: boolean
}>()

const emit = defineEmits<{
  navigateToPlayer: [gameName: string, tagLine: string]
}>()

const tierIconMap: Record<string, string> = {
  unranked,
  iron,
  bronze,
  silver,
  gold,
  platinum,
  emerald,
  diamond,
  master,
  grandmaster,
  challenger
}

const recordStatus = computed<RecordStatus>(() => props.sessionSummoner.userTag?.recordStatus || 'NORMAL')

const recordStatusMeta = computed(() => {
  switch (recordStatus.value) {
    case 'PRIVATE':
      return {
        label: '战绩隐藏',
        hint: 'LCU 能识别到这个玩家，但近期战绩处于隐藏状态。'
      }
    case 'EMPTY':
      return {
        label: '暂无近期对局',
        hint: '近期样本不足，暂时还无法判断这个玩家。'
      }
    case 'ERROR':
      return {
        label: '加载失败',
        hint: '最近一次标签请求没有返回可用数据。'
      }
    default:
      return null
  }
})

const userTags = computed(() => props.sessionSummoner.userTag?.tag || [])
const measuredVisibleTagCount = ref<number | null>(null)
const tagContainerRef = ref<HTMLElement | null>(null)
const tagMeasureRef = ref<HTMLElement | null>(null)

const visibleTagLimit = computed(() => measuredVisibleTagCount.value ?? userTags.value.length)
const visibleUserTags = computed(() => userTags.value.slice(0, visibleTagLimit.value))
const hiddenUserTags = computed(() => userTags.value.slice(visibleTagLimit.value))
const hiddenUserTagCount = computed(() => hiddenUserTags.value.length)
const overflowMeasureCount = computed(() => Math.max(userTags.value.length, 1))

const TAG_GAP = 6
const MIN_VISIBLE_TAGS_WHEN_COLLAPSED = 1

let tagResizeObserver: ResizeObserver | null = null
let tagMeasureFrame = 0

function getPackedTagWidth(widths: number[], count: number): number {
  if (count <= 0) {
    return 0
  }
  return widths.slice(0, count).reduce((total, width) => total + width, 0) + TAG_GAP * (count - 1)
}

function updateVisibleTagCount() {
  const tags = userTags.value
  if (!tags.length) {
    measuredVisibleTagCount.value = 0
    return
  }

  const container = tagContainerRef.value
  const measure = tagMeasureRef.value
  if (tags.length === 1) {
    measuredVisibleTagCount.value = 1
    return
  }

  if (!container || !measure) {
    measuredVisibleTagCount.value = tags.length
    return
  }

  const availableWidth = Math.floor(container.clientWidth)
  if (availableWidth <= 0) {
    measuredVisibleTagCount.value = tags.length
    return
  }

  const tagWidths = Array.from(measure.querySelectorAll<HTMLElement>('.tag-chip')).map((node) =>
    Math.ceil(node.offsetWidth)
  )
  const moreChipWidth = Math.ceil(measure.querySelector<HTMLElement>('.more-chip')?.offsetWidth || 38)

  if (!tagWidths.length || getPackedTagWidth(tagWidths, tags.length) <= availableWidth) {
    measuredVisibleTagCount.value = tags.length
    return
  }

  let visibleCount = Math.max(
    0,
    Math.min(tags.length - 1, tagWidths.findIndex((_, index) => getPackedTagWidth(tagWidths, index + 1) > availableWidth))
  )

  if (visibleCount < 0) {
    visibleCount = tags.length - 1
  }

  while (visibleCount > MIN_VISIBLE_TAGS_WHEN_COLLAPSED) {
    const packedWidth = getPackedTagWidth(tagWidths, visibleCount) + TAG_GAP + moreChipWidth
    if (packedWidth <= availableWidth) {
      break
    }
    visibleCount -= 1
  }

  if (
    visibleCount === MIN_VISIBLE_TAGS_WHEN_COLLAPSED &&
    getPackedTagWidth(tagWidths, visibleCount) + TAG_GAP + moreChipWidth > availableWidth &&
    moreChipWidth <= availableWidth
  ) {
    visibleCount = 0
  }

  measuredVisibleTagCount.value = visibleCount
}

function scheduleVisibleTagUpdate() {
  if (tagMeasureFrame) {
    window.cancelAnimationFrame(tagMeasureFrame)
  }
  tagMeasureFrame = window.requestAnimationFrame(() => {
    tagMeasureFrame = 0
    updateVisibleTagCount()
  })
}

function observeTagContainer() {
  tagResizeObserver?.disconnect()
  if (tagContainerRef.value) {
    tagResizeObserver?.observe(tagContainerRef.value)
  }
}

onMounted(() => {
  tagResizeObserver = new ResizeObserver(scheduleVisibleTagUpdate)
  observeTagContainer()
  window.addEventListener('resize', scheduleVisibleTagUpdate)
  nextTick(scheduleVisibleTagUpdate)
})

onBeforeUnmount(() => {
  tagResizeObserver?.disconnect()
  window.removeEventListener('resize', scheduleVisibleTagUpdate)
  if (tagMeasureFrame) {
    window.cancelAnimationFrame(tagMeasureFrame)
  }
})

watch(
  () => userTags.value.map((tag) => tag.tagName).join('|'),
  () => {
    measuredVisibleTagCount.value = null
    nextTick(() => {
      observeTagContainer()
      scheduleVisibleTagUpdate()
    })
  },
  { flush: 'post' }
)

const tierLabelMap: Record<string, string> = {
  UNRANKED: '未定级',
  IRON: '黑铁',
  BRONZE: '青铜',
  SILVER: '白银',
  GOLD: '黄金',
  PLATINUM: '铂金',
  EMERALD: '翡翠',
  DIAMOND: '钻石',
  MASTER: '超凡大师',
  GRANDMASTER: '傲世宗师',
  CHALLENGER: '最强王者'
}

const divisionLabelMap: Record<string, string> = {
  I: '一',
  II: '二',
  III: '三',
  IV: '四'
}

const teamClass = computed(() => {
  if (props.team === 'blue') return 'team-blue'
  if (props.team === 'red') return 'team-red'
  return 'team-neutral'
})

const statusClass = computed(() => {
  if (recordStatus.value === 'PRIVATE') return 'status-private'
  if (recordStatus.value === 'EMPTY') return 'status-empty'
  if (recordStatus.value === 'ERROR') return 'status-error'
  return 'status-normal'
})

const avatarUrl = computed(() => {
  if (props.sessionSummoner.championId > 0) {
    return `http://127.0.0.1:8080/api/v1/asset/champion/${props.sessionSummoner.championId}`
  }
  if (props.sessionSummoner.summoner?.profileIconId) {
    return `http://127.0.0.1:8080/api/v1/asset/profile/${props.sessionSummoner.summoner.profileIconId}`
  }
  return ''
})

const winRate = computed(() => {
  const wins = props.sessionSummoner.userTag?.recentData?.selectWins || 0
  const losses = props.sessionSummoner.userTag?.recentData?.selectLosses || 0
  const total = wins + losses
  return total > 0 ? Math.round((wins / total) * 100) : 0
})

const totalGames = computed(() => {
  const wins = props.sessionSummoner.userTag?.recentData?.selectWins || 0
  const losses = props.sessionSummoner.userTag?.recentData?.selectLosses || 0
  return wins + losses
})

const kdaText = computed(() => {
  const kda = props.sessionSummoner.userTag?.recentData?.kda
  return kda != null ? kda.toFixed(1) : '--'
})

const primaryQueueInfo = computed<QueueInfo | null>(() => {
  const queueMap = props.sessionSummoner.rank?.queueMap
  return queueMap?.RANKED_SOLO_5x5 || queueMap?.RANKED_FLEX_SR || null
})

function getQueueTotalGames(queueInfo?: QueueInfo | null): number {
  if (!queueInfo) {
    return 0
  }
  if (typeof queueInfo.totalGames === 'number' && Number.isFinite(queueInfo.totalGames)) {
    return queueInfo.totalGames
  }
  const wins = typeof queueInfo.wins === 'number' ? queueInfo.wins : 0
  const losses = typeof queueInfo.losses === 'number' ? queueInfo.losses : 0
  return wins + losses
}

function hasTier(queueInfo?: QueueInfo | null): boolean {
  return Boolean(queueInfo?.tier && queueInfo.tier !== 'UNRANKED')
}

function normalizeTierKey(tier?: string): string {
  return tier?.toUpperCase() || 'UNRANKED'
}

function getTierLabel(tier?: string): string {
  const tierKey = normalizeTierKey(tier)
  return tierLabelMap[tierKey] || tier || tierLabelMap.UNRANKED
}

function hasRankSignal(queueInfo?: QueueInfo | null): boolean {
  if (!queueInfo) {
    return false
  }
  return queueInfo.isProvisional || hasTier(queueInfo) || getQueueTotalGames(queueInfo) > 0
}

const tierImgUrl = computed(() => {
  const tier = primaryQueueInfo.value?.tier?.toLowerCase()
  if (!tier || tier === 'unranked') {
    return tierIconMap.unranked
  }
  return tierIconMap[tier] || tierIconMap.unranked
})

const tierText = computed(() => {
  const queueInfo = primaryQueueInfo.value
  if (!queueInfo) {
    return '未定级'
  }

  if (queueInfo.isProvisional) {
    const games = getQueueTotalGames(queueInfo)
    return games > 0 ? `定级中 · ${games}场` : '定级中'
  }

  if (hasTier(queueInfo)) {
    const tierKey = normalizeTierKey(queueInfo.tier)
    const tierLabel = getTierLabel(queueInfo.tier)

    if (['MASTER', 'GRANDMASTER', 'CHALLENGER'].includes(tierKey)) {
      return `${tierLabel} ${queueInfo.leaguePoints} LP`
    }

    const divisionKey = queueInfo.division?.toUpperCase()
    const divisionLabel = divisionKey ? (divisionLabelMap[divisionKey] || queueInfo.division) : ''
    if (divisionLabel) {
      return `${tierLabel} ${divisionLabel} ${queueInfo.leaguePoints} LP`
    }

    return `${tierLabel} ${queueInfo.leaguePoints} LP`
  }

  if (hasRankSignal(queueInfo)) {
    const games = getQueueTotalGames(queueInfo)
    return games > 0 ? `定级中 · ${games}场` : '定级中'
  }

  return '未定级'
})

function onNameClick() {
  const name = props.sessionSummoner.summoner?.gameName
  const tag = props.sessionSummoner.summoner?.tagLine
  if (name && tag) {
    emit('navigateToPlayer', name, tag)
  }
}

</script>

<style scoped>
.player-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border-radius: 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
}

.player-card.team-blue {
  border-left: 4px solid rgba(92, 163, 234, 0.7);
}

.player-card.team-red {
  border-left: 4px solid rgba(222, 111, 111, 0.7);
}

.player-card.status-private,
.player-card.status-empty {
  background: linear-gradient(180deg, rgba(215, 166, 75, 0.08), rgba(255, 255, 255, 0.02));
}

.player-card.status-error {
  background: linear-gradient(180deg, rgba(196, 92, 92, 0.08), rgba(255, 255, 255, 0.02));
}

.player-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.avatar-wrap {
  position: relative;
}

.avatar {
  width: 58px;
  height: 58px;
  border-radius: 12px;
  object-fit: cover;
  background: var(--bg-tertiary);
}

.pregroup-badge {
  position: absolute;
  right: -4px;
  bottom: -4px;
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(92, 163, 234, 0.18);
  color: #5ca3ea;
  font-size: 10px;
  line-height: 1.1;
}

.player-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.player-id {
  min-width: 0;
  max-width: 100%;
  padding: 0;
  border: 0;
  background: none;
  color: var(--text-primary);
  text-align: left;
  font-size: 17px;
  line-height: 1.2;
  font-weight: 800;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-id:hover {
  color: var(--accent-color);
}

.name-tags {
  position: relative;
  display: flex;
  align-items: center;
  flex: 1 1 auto;
  flex-wrap: nowrap;
  gap: 6px;
  max-width: 100%;
  min-width: 42px;
}

.visible-tags {
  display: flex;
  align-items: center;
  flex: 0 1 auto;
  gap: 6px;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}

.tag-measure {
  position: absolute;
  left: -9999px;
  top: -9999px;
  display: flex;
  align-items: center;
  gap: 6px;
  visibility: hidden;
  pointer-events: none;
  white-space: nowrap;
}

.tag-overflow {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
}

.more-chip {
  min-width: 34px;
  min-height: 24px;
  padding: 2px 8px;
  border: 1px solid rgba(var(--accent-rgb), 0.35);
  border-radius: 999px;
  background: rgba(var(--accent-rgb), 0.12);
  color: var(--accent-hover);
  font-size: 12px;
  line-height: 1.1;
  font-weight: 800;
  cursor: pointer;
}

.hidden-tags-popover {
  position: absolute;
  left: 0;
  top: calc(100% + 6px);
  z-index: 4;
  width: max-content;
  max-width: min(260px, 70vw);
  display: none;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-elevated);
  box-shadow: var(--shadow-lg);
}

.tag-overflow:hover .hidden-tags-popover,
.tag-overflow:focus-within .hidden-tags-popover {
  display: flex;
}

.meta-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.tier-row span,
.status-banner span,
.empty-state span {
  color: var(--text-secondary);
  font-size: 13px;
}

.tier-row {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 5px;
  min-width: 0;
}

.tier-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.status-banner {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
}

.status-banner strong,
.empty-state strong {
  color: var(--text-primary);
  font-size: 13px;
  white-space: nowrap;
}

.tag-chip {
  flex: 0 0 auto;
  max-width: 116px;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.15;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag-chip.good {
  background: rgba(61, 155, 122, 0.14);
  color: #3d9b7a;
}

.tag-chip.bad {
  background: rgba(196, 92, 92, 0.14);
  color: #c45c5c;
}

.tag-chip.neutral {
  background: rgba(184, 192, 204, 0.16);
  color: var(--text-secondary);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.stat-item strong {
  color: var(--text-primary);
  font-size: 18px;
  line-height: 1.2;
}

.skeleton {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar-skeleton,
.copy-skeleton span {
  background: rgba(255, 255, 255, 0.06);
  animation: pulse 1.2s ease-in-out infinite;
}

.avatar-skeleton {
  width: 48px;
  height: 48px;
  border-radius: 12px;
}

.copy-skeleton {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.copy-skeleton span:first-child {
  width: 140px;
  height: 15px;
  border-radius: 8px;
}

.copy-skeleton span:last-child {
  width: 100px;
  height: 11px;
  border-radius: 6px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.45;
  }
  50% {
    opacity: 0.9;
  }
}

@media (max-width: 720px) {
  .player-card {
    padding: 12px;
  }

  .player-head {
    grid-template-columns: auto minmax(0, 1fr);
    align-items: start;
  }

  .avatar {
    width: 52px;
    height: 52px;
  }

  .player-id {
    font-size: 16px;
  }

  .stats-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 6px;
  }

  .stat-item {
    padding: 8px;
  }

  .stat-label {
    font-size: 11px;
  }

  .stat-item strong {
    font-size: 16px;
  }
}
</style>
