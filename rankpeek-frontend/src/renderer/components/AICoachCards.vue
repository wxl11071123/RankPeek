<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getCoachReportHeadline } from '@/services/localAiAnalysis'

interface CoachReport {
  id?: number | string
  headline?: string
  cardTitle?: string
  shortTitle?: string
  title: string
  body: string
  detail?: string
}

type DisplayReport = CoachReport & {
  isDevPlaceholder?: boolean
  meta?: string
}

type SwitchDirection = 'next' | 'prev'

interface LeavingCardSnapshot {
  key: number
  title: string
  meta: string
}

const DEV_PLACEHOLDER_REPORTS: DisplayReport[] = [
  { title: '中期资源团失误正在吞掉你的优势。', body: '', meta: '04/27 · 排位 20', isDevPlaceholder: true },
  { title: '优势局收尾过慢正在拉低胜率。', body: '', meta: '04/26 · 排位 20', isDevPlaceholder: true },
  { title: '前十分钟视野断档影响节奏。', body: '', meta: '04/25 · 排位 10', isDevPlaceholder: true },
  { title: '过度追击正在放大死亡成本。', body: '', meta: '04/24 · 排位 20', isDevPlaceholder: true },
  { title: '边线处理正在决定你的团战空间。', body: '', meta: '04/23 · 排位 10', isDevPlaceholder: true },
  { title: '逆风局视野缺口正在拖垮翻盘窗口。', body: '', meta: '04/22 · 排位 20', isDevPlaceholder: true }
]

const COACH_GLOW_RESET_DELAY_MS = 190
const CARD_SWITCH_ANIMATION_MS = 230

const props = withDefaults(defineProps<{ reports?: CoachReport[] }>(), {
  reports: () => []
})

const emit = defineEmits<{
  (event: 'open-report', report: CoachReport | null, index: number): void
}>()

const activeIndex = ref(0)
const activeCardRef = ref<HTMLElement | null>(null)
const leavingCard = ref<LeavingCardSnapshot | null>(null)
const switchDirection = ref<SwitchDirection>('next')
const switchSerial = ref(0)
let lastWheelAt = 0
let glowResetTimer: number | undefined
let cardSwitchTimer: number | undefined

const reportCount = computed(() => props.reports.length)
const hasDevPlaceholders = computed(() => import.meta.env.DEV && reportCount.value === 0)
const displayReports = computed<DisplayReport[]>(() =>
  hasDevPlaceholders.value ? DEV_PLACEHOLDER_REPORTS : props.reports
)
const displayCount = computed(() => displayReports.value.length)
const hasDisplayReports = computed(() => displayCount.value > 0)
const activeReport = computed<DisplayReport | null>(() =>
  hasDisplayReports.value ? displayReports.value[activeIndex.value] : null
)
const activeTitle = computed(() => {
  if (!activeReport.value) {
    return '暂无复盘记录。'
  }

  return getReportTitle(activeReport.value, activeIndex.value)
})
const activeMeta = computed(() => {
  if (!activeReport.value) {
    return ''
  }

  return getReportMeta(activeReport.value, activeIndex.value)
})
const activeCardAriaLabel = computed(() =>
  hasDisplayReports.value ? `查看第 ${activeIndex.value + 1} 份复盘：${activeTitle.value}` : '暂无复盘记录'
)
const activeCardKey = computed(() => `active-${activeIndex.value}-${switchSerial.value}`)
const controlItems = computed(() =>
  hasDisplayReports.value
    ? displayReports.value.map((report, index) => ({
        key: report.isDevPlaceholder ? `placeholder-${index}` : `report-${index}`
      }))
    : [{ key: 'empty' }]
)
const stackLayers = computed(() => {
  const layerCount = Math.min(Math.max(displayCount.value - 1, 0), 2)
  return Array.from({ length: layerCount }, (_item, index) => layerCount - index)
})
const activeReportForEmit = computed<CoachReport | null>(() =>
  activeReport.value?.isDevPlaceholder ? null : activeReport.value
)

watch(displayCount, (count) => {
  if (count === 0) {
    activeIndex.value = 0
    return
  }

  if (activeIndex.value > count - 1) {
    activeIndex.value = count - 1
  }
})

onBeforeUnmount(() => {
  if (glowResetTimer !== undefined) {
    window.clearTimeout(glowResetTimer)
  }

  if (cardSwitchTimer !== undefined) {
    window.clearTimeout(cardSwitchTimer)
  }
})

function getReportTitle(report: DisplayReport, index: number): string {
  const title = report.title.trim()
  if (report.isDevPlaceholder) {
    return title
  }

  return getCoachReportHeadline({ report }) || title || `第 ${index + 1} 份复盘`
}

function getReportMeta(report: DisplayReport, index: number): string {
  return report.meta || `第 ${index + 1} 份复盘`
}

function resolveSwitchDirection(fromIndex: number, toIndex: number, count: number): SwitchDirection {
  if (count < 2) {
    return 'next'
  }

  if ((fromIndex + 1) % count === toIndex) {
    return 'next'
  }

  if ((fromIndex - 1 + count) % count === toIndex) {
    return 'prev'
  }

  return toIndex > fromIndex ? 'next' : 'prev'
}

function queueCardSwitch(previousIndex: number, direction: SwitchDirection) {
  const previousReport = displayReports.value[previousIndex]
  if (!previousReport) {
    return
  }

  switchDirection.value = direction
  switchSerial.value += 1
  leavingCard.value = {
    key: switchSerial.value,
    title: getReportTitle(previousReport, previousIndex),
    meta: getReportMeta(previousReport, previousIndex)
  }

  if (cardSwitchTimer !== undefined) {
    window.clearTimeout(cardSwitchTimer)
  }

  cardSwitchTimer = window.setTimeout(() => {
    leavingCard.value = null
    cardSwitchTimer = undefined
  }, CARD_SWITCH_ANIMATION_MS)
}

function selectReport(index: number, direction?: SwitchDirection) {
  const count = displayCount.value
  if (count === 0) {
    return
  }

  const nextIndex = ((index % count) + count) % count
  if (nextIndex === activeIndex.value) {
    return
  }

  queueCardSwitch(
    activeIndex.value,
    direction || resolveSwitchDirection(activeIndex.value, nextIndex, count)
  )
  activeIndex.value = nextIndex
}

function handleWheel(event: WheelEvent) {
  if (displayCount.value < 2) {
    return
  }

  const delta = Math.abs(event.deltaY) >= Math.abs(event.deltaX) ? event.deltaY : event.deltaX
  if (delta === 0) {
    return
  }

  event.preventDefault()

  const now = Date.now()
  if (now - lastWheelAt < 180) {
    return
  }

  lastWheelAt = now
  const direction: SwitchDirection = delta > 0 ? 'next' : 'prev'
  selectReport(activeIndex.value + (direction === 'next' ? 1 : -1), direction)
}

function clampPercent(value: number): number {
  return Math.min(Math.max(value, 0), 100)
}

function setCoachGlow(target: HTMLElement, x: string, y: string, strength: string) {
  target.style.setProperty('--coach-glow-x', x)
  target.style.setProperty('--coach-glow-y', y)
  target.style.setProperty('--coach-glow-strength', strength)
}

function updateCoachGlow(event: PointerEvent) {
  const container = event.currentTarget as HTMLElement | null
  const activeCard = activeCardRef.value
  if (!container || !activeCard) {
    return
  }

  if (glowResetTimer !== undefined) {
    window.clearTimeout(glowResetTimer)
    glowResetTimer = undefined
  }

  const rect = activeCard.getBoundingClientRect()
  const x = rect.width > 0 ? ((event.clientX - rect.left) / rect.width) * 100 : 50
  const y = rect.height > 0 ? ((event.clientY - rect.top) / rect.height) * 100 : 50
  const glowX = `${clampPercent(x)}%`
  const glowY = `${clampPercent(y)}%`

  setCoachGlow(container, glowX, glowY, '1')
  setCoachGlow(activeCard, glowX, glowY, '1')
}

function resetCoachGlow(event: PointerEvent) {
  const container = event.currentTarget as HTMLElement | null
  if (!container) {
    return
  }

  container.style.setProperty('--coach-glow-strength', '0')
  if (activeCardRef.value) {
    activeCardRef.value.style.setProperty('--coach-glow-strength', '0')
  }

  if (glowResetTimer !== undefined) {
    window.clearTimeout(glowResetTimer)
  }

  glowResetTimer = window.setTimeout(() => {
    setCoachGlow(container, '50%', '50%', '0.82')

    if (activeCardRef.value) {
      setCoachGlow(activeCardRef.value, '50%', '50%', '0.82')
    }

    glowResetTimer = undefined
  }, COACH_GLOW_RESET_DELAY_MS)
}

function openActiveReport() {
  emit('open-report', activeReportForEmit.value, activeIndex.value)
}
</script>

<template>
  <section
    class="ai-coach-cards"
    :class="{
      'using-dev-placeholders': hasDevPlaceholders,
      'is-switching': leavingCard,
      'switch-next': switchDirection === 'next',
      'switch-prev': switchDirection === 'prev'
    }"
    aria-label="复盘记录"
    @pointermove="updateCoachGlow"
    @pointerleave="resetCoachGlow"
    @wheel="handleWheel"
  >
    <header class="record-header">
      <h3 class="record-heading">复盘记录</h3>
      <span class="record-count" aria-label="复盘记录数量">{{ reportCount }}</span>
    </header>

    <div class="record-preview" :class="{ empty: !hasDisplayReports }">
      <span
        v-for="layer in stackLayers"
        :key="layer"
        class="record-stack-card"
        :class="`layer-${layer}`"
        aria-hidden="true"
      ></span>

      <span
        v-if="leavingCard"
        :key="`leaving-${leavingCard.key}`"
        class="record-main-card record-main-card-leaving"
        aria-hidden="true"
      >
        <span class="record-title">{{ leavingCard.title }}</span>
        <span v-if="leavingCard.meta" class="record-meta">{{ leavingCard.meta }}</span>
      </span>

      <button
        :key="activeCardKey"
        ref="activeCardRef"
        class="record-main-card"
        type="button"
        :aria-label="activeCardAriaLabel"
        @click="openActiveReport"
      >
        <span class="record-title">{{ activeTitle }}</span>
        <span v-if="activeMeta" class="record-meta">{{ activeMeta }}</span>
      </button>
    </div>

    <div class="record-controls" aria-label="复盘记录切换">
      <button
        v-for="(item, index) in controlItems"
        :key="item.key"
        class="record-dot"
        :class="{ active: hasDisplayReports && index === activeIndex, placeholder: !hasDisplayReports }"
        :aria-current="hasDisplayReports && index === activeIndex ? 'true' : undefined"
        :aria-label="hasDisplayReports ? `切换到第 ${index + 1} 份复盘` : undefined"
        :disabled="!hasDisplayReports"
        type="button"
        @click="selectReport(index)"
      >
        <span aria-hidden="true"></span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.ai-coach-cards {
  --coach-glow-x: 50%;
  --coach-glow-y: 50%;
  --coach-glow-strength: 0.82;
  --record-panel-bg: var(--bg-secondary, #1d1d1f);
  --record-panel-border: var(--border-color, rgba(255, 255, 255, 0.1));
  --record-panel-border-hover: rgba(88, 166, 255, 0.34);
  --record-panel-glow: rgba(41, 151, 255, 0.2);
  --record-panel-glow-soft: rgba(41, 151, 255, 0.08);
  --record-panel-hover-shadow:
    0 0 0 1px rgba(41, 151, 255, 0.08),
    0 12px 24px rgba(41, 151, 255, 0.08);
  --record-heading-color: var(--text-primary, rgba(230, 238, 246, 0.96));
  --record-count-color: rgba(190, 212, 232, 0.76);
  --record-card-bg: var(--bg-tertiary, #272729);
  --record-placeholder-card-bg: rgba(39, 39, 41, 0.78);
  --record-card-border: rgba(255, 255, 255, 0.1);
  --record-card-border-hover: rgba(112, 185, 255, 0.54);
  --record-card-local-glow: rgba(41, 151, 255, 0.15);
  --record-card-shadow: 0 10px 22px rgba(0, 0, 0, 0.12);
  --record-card-hover-shadow:
    0 0 0 1px rgba(41, 151, 255, 0.12),
    0 12px 24px rgba(0, 0, 0, 0.14),
    0 0 18px rgba(41, 151, 255, 0.16);
  --record-card-title: rgba(231, 239, 246, 0.94);
  --record-stack-bg: rgba(39, 39, 41, 0.68);
  --record-stack-border: rgba(255, 255, 255, 0.08);
  --record-stack-border-hover: rgba(112, 185, 255, 0.24);
  --record-focus: rgba(41, 151, 255, 0.72);
  --record-gold: #d4af37;
  --record-gold-muted: rgba(212, 175, 55, 0.34);
  --record-switch-duration: 220ms;
  --record-switch-ease: cubic-bezier(0.2, 0, 0, 1);
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  padding: 18px 20px;
  border: 1px solid var(--record-panel-border);
  border-radius: 12px;
  background: var(--record-panel-bg);
  box-shadow: none;
  isolation: isolate;
  overflow: visible;
  position: relative;
  transition:
    border-color 0.2s ease,
    box-shadow 0.22s ease,
    background 0.22s ease;
}

.ai-coach-cards::before,
.ai-coach-cards::after {
  content: '';
  position: absolute;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.18s ease;
}

.ai-coach-cards::before {
  inset: -1px;
  z-index: 0;
  border-radius: inherit;
  padding: 1px;
  background: radial-gradient(
    circle 190px at var(--coach-glow-x) var(--coach-glow-y),
    var(--record-panel-glow) 0%,
    var(--record-panel-glow-soft) 46%,
    transparent 78%
  );
  -webkit-mask:
    linear-gradient(#000 0 0) content-box,
    linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
}

.ai-coach-cards::after {
  inset: 0;
  z-index: 0;
  border-radius: inherit;
  background: radial-gradient(
    circle 220px at var(--coach-glow-x) var(--coach-glow-y),
    rgba(41, 151, 255, 0.055),
    transparent 68%
  );
}

.ai-coach-cards:hover,
.ai-coach-cards:focus-within {
  border-color: var(--record-panel-border-hover);
  box-shadow: var(--record-panel-hover-shadow);
}

.ai-coach-cards:hover::before,
.ai-coach-cards:hover::after,
.ai-coach-cards:focus-within::before,
.ai-coach-cards:focus-within::after {
  opacity: 1;
}

.record-header {
  flex: 0 0 auto;
  min-height: 30px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.record-heading {
  margin: 0;
  color: var(--record-heading-color);
  font-size: 22px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: 0;
}

.record-count {
  flex: 0 0 auto;
  color: var(--record-count-color);
  font-size: 22px;
  font-weight: 800;
  line-height: 1.25;
  font-variant-numeric: tabular-nums;
}

.record-preview {
  --record-card-width: calc(100% - 8px);
  --record-card-height: min(154px, 70%);
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 0 12px;
  isolation: isolate;
  z-index: 1;
}

.record-main-card,
.record-stack-card {
  width: var(--record-card-width);
  height: var(--record-card-height);
  min-height: 118px;
  box-sizing: border-box;
  border-radius: 10px;
}

.record-stack-card {
  position: absolute;
  top: 50%;
  left: 50%;
  border: 1px solid var(--record-stack-border);
  background: var(--record-stack-bg);
  pointer-events: none;
  transition:
    border-color 0.2s ease,
    opacity 0.2s ease,
    box-shadow 0.22s ease;
}

.record-stack-card.layer-1 {
  z-index: 1;
  opacity: 0.66;
  transform: translate(-50%, -50%) translate(7px, 14px);
}

.record-stack-card.layer-2 {
  z-index: 0;
  opacity: 0.42;
  transform: translate(-50%, -50%) translate(-7px, 25px);
}

.record-main-card {
  --record-card-hover-y: 0px;
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 10px;
  padding: 22px 28px;
  border: 1px solid var(--record-card-border);
  background: var(--record-card-bg);
  box-shadow: var(--record-card-shadow);
  color: inherit;
  cursor: pointer;
  overflow: hidden;
  transform: translate3d(0, var(--record-card-hover-y), 0);
  transition:
    border-color 0.2s ease,
    box-shadow 0.22s ease,
    transform 0.18s ease,
    background 0.2s ease;
}

.record-main-card::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  background: radial-gradient(
    circle 132px at var(--coach-glow-x) var(--coach-glow-y),
    var(--record-card-local-glow),
    transparent 72%
  );
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.18s ease;
}

.ai-coach-cards:hover .record-main-card:not(.record-main-card-leaving),
.ai-coach-cards:focus-within .record-main-card:not(.record-main-card-leaving) {
  --record-card-hover-y: -1px;
  border-color: var(--record-card-border-hover);
  box-shadow: var(--record-card-hover-shadow);
}

.ai-coach-cards:hover .record-main-card:not(.record-main-card-leaving)::before,
.ai-coach-cards:focus-within .record-main-card:not(.record-main-card-leaving)::before {
  opacity: var(--coach-glow-strength);
}

.ai-coach-cards:hover .record-stack-card,
.ai-coach-cards:focus-within .record-stack-card {
  border-color: var(--record-stack-border-hover);
  box-shadow: 0 0 12px rgba(41, 151, 255, 0.08);
}

.record-main-card:not(.record-main-card-leaving):hover,
.record-main-card:not(.record-main-card-leaving):focus-visible {
  --record-card-hover-y: -2px;
  border-color: var(--record-card-border-hover);
  box-shadow: var(--record-card-hover-shadow);
}

.record-main-card:not(.record-main-card-leaving):hover::before,
.record-main-card:not(.record-main-card-leaving):focus-visible::before {
  opacity: var(--coach-glow-strength);
}

.record-main-card:not(.record-main-card-leaving):focus-visible {
  outline: 2px solid var(--record-focus);
  outline-offset: 4px;
}

.record-main-card-leaving {
  --record-card-hover-y: 0px;
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 3;
  pointer-events: none;
  cursor: default;
  transform: translate(-50%, -50%);
}

.record-main-card-leaving::before {
  opacity: 0;
}

.ai-coach-cards.is-switching.switch-next .record-main-card:not(.record-main-card-leaving) {
  animation: record-card-enter-next var(--record-switch-duration) var(--record-switch-ease) both;
}

.ai-coach-cards.is-switching.switch-prev .record-main-card:not(.record-main-card-leaving) {
  animation: record-card-enter-prev var(--record-switch-duration) var(--record-switch-ease) both;
}

.ai-coach-cards.is-switching.switch-next .record-main-card-leaving {
  animation: record-card-leave-next var(--record-switch-duration) var(--record-switch-ease) both;
}

.ai-coach-cards.is-switching.switch-prev .record-main-card-leaving {
  animation: record-card-leave-prev var(--record-switch-duration) var(--record-switch-ease) both;
}

.ai-coach-cards.is-switching.switch-next .record-stack-card.layer-1 {
  animation: record-stack-one-next var(--record-switch-duration) var(--record-switch-ease) both;
}

.ai-coach-cards.is-switching.switch-next .record-stack-card.layer-2 {
  animation: record-stack-two-next var(--record-switch-duration) var(--record-switch-ease) both;
}

.ai-coach-cards.is-switching.switch-prev .record-stack-card.layer-1 {
  animation: record-stack-one-prev var(--record-switch-duration) var(--record-switch-ease) both;
}

.ai-coach-cards.is-switching.switch-prev .record-stack-card.layer-2 {
  animation: record-stack-two-prev var(--record-switch-duration) var(--record-switch-ease) both;
}

@keyframes record-card-enter-next {
  0% {
    opacity: 0.56;
    transform: translate3d(8px, calc(var(--record-card-hover-y) + 9px), 0);
  }

  58% {
    opacity: 0.94;
  }

  100% {
    opacity: 1;
    transform: translate3d(0, var(--record-card-hover-y), 0);
  }
}

@keyframes record-card-enter-prev {
  0% {
    opacity: 0.56;
    transform: translate3d(-8px, calc(var(--record-card-hover-y) - 8px), 0);
  }

  58% {
    opacity: 0.94;
  }

  100% {
    opacity: 1;
    transform: translate3d(0, var(--record-card-hover-y), 0);
  }
}

@keyframes record-card-leave-next {
  0% {
    opacity: 1;
    transform: translate(-50%, -50%);
  }

  100% {
    opacity: 0;
    transform: translate(calc(-50% - 6px), calc(-50% - 8px));
  }
}

@keyframes record-card-leave-prev {
  0% {
    opacity: 1;
    transform: translate(-50%, -50%);
  }

  100% {
    opacity: 0;
    transform: translate(calc(-50% + 6px), calc(-50% + 8px));
  }
}

@keyframes record-stack-one-next {
  0% {
    opacity: 0.5;
    transform: translate(-50%, -50%) translate(11px, 19px);
  }

  100% {
    opacity: 0.66;
    transform: translate(-50%, -50%) translate(7px, 14px);
  }
}

@keyframes record-stack-two-next {
  0% {
    opacity: 0.28;
    transform: translate(-50%, -50%) translate(-3px, 29px);
  }

  100% {
    opacity: 0.42;
    transform: translate(-50%, -50%) translate(-7px, 25px);
  }
}

@keyframes record-stack-one-prev {
  0% {
    opacity: 0.5;
    transform: translate(-50%, -50%) translate(2px, 9px);
  }

  100% {
    opacity: 0.66;
    transform: translate(-50%, -50%) translate(7px, 14px);
  }
}

@keyframes record-stack-two-prev {
  0% {
    opacity: 0.28;
    transform: translate(-50%, -50%) translate(-11px, 21px);
  }

  100% {
    opacity: 0.42;
    transform: translate(-50%, -50%) translate(-7px, 25px);
  }
}

.record-title {
  position: relative;
  z-index: 1;
  max-width: 100%;
  color: var(--record-card-title);
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", serif;
  font-size: clamp(20px, 2.4vw, 31px);
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 0;
  text-align: left;
  overflow: hidden;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.record-meta {
  position: relative;
  z-index: 1;
  color: var(--record-count-color);
  font-family: inherit;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: 0;
  text-align: left;
}

.record-controls {
  flex: 0 0 28px;
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  position: relative;
  z-index: 1;
}

.record-dot {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
}

.record-dot span {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--record-gold-muted);
  transition:
    width 0.18s ease,
    background 0.18s ease,
    opacity 0.18s ease;
}

.record-dot.active span {
  width: 22px;
  background: var(--record-gold);
}

.record-dot.placeholder {
  cursor: default;
  opacity: 0.58;
}

.record-dot:focus-visible {
  outline: 2px solid var(--record-focus);
  outline-offset: 2px;
}

.record-dot:disabled {
  pointer-events: none;
}

.using-dev-placeholders .record-main-card {
  background: var(--record-placeholder-card-bg);
  border-color: rgba(120, 151, 178, 0.16);
}

.using-dev-placeholders .record-title {
  opacity: 0.78;
}

.using-dev-placeholders .record-stack-card {
  border-color: rgba(120, 151, 178, 0.12);
}

:global([data-theme="light"] .ai-coach-cards) {
  --record-panel-bg: var(--bg-secondary, #ffffff);
  --record-panel-border: var(--border-color, rgba(0, 0, 0, 0.1));
  --record-panel-border-hover: rgba(41, 151, 255, 0.3);
  --record-panel-glow: rgba(41, 151, 255, 0.2);
  --record-panel-glow-soft: rgba(41, 151, 255, 0.065);
  --record-panel-hover-shadow:
    0 0 0 1px rgba(41, 151, 255, 0.08),
    0 10px 22px rgba(41, 151, 255, 0.08);
  --record-heading-color: #2c2c2c;
  --record-count-color: rgba(49, 87, 120, 0.64);
  --record-card-bg: var(--bg-tertiary, #fafafc);
  --record-placeholder-card-bg: rgba(250, 250, 252, 0.72);
  --record-card-border: rgba(0, 0, 0, 0.1);
  --record-card-border-hover: rgba(41, 151, 255, 0.42);
  --record-card-local-glow: rgba(41, 151, 255, 0.12);
  --record-card-shadow: 0 8px 18px rgba(44, 67, 92, 0.055);
  --record-card-hover-shadow:
    0 0 0 1px rgba(41, 151, 255, 0.08),
    0 10px 22px rgba(44, 67, 92, 0.08),
    0 0 16px rgba(41, 151, 255, 0.11);
  --record-card-title: #243746;
  --record-stack-bg: rgba(250, 250, 252, 0.68);
  --record-stack-border: rgba(0, 0, 0, 0.08);
  --record-stack-border-hover: rgba(41, 151, 255, 0.18);
  --record-focus: rgba(41, 151, 255, 0.62);
  --record-gold: #b88916;
  --record-gold-muted: rgba(184, 137, 22, 0.34);
}

@media (prefers-reduced-motion: reduce) {
  .record-main-card,
  .record-main-card::before,
  .record-stack-card,
  .record-dot span {
    transition: none;
    animation: none;
  }

  .record-main-card-leaving {
    display: none;
  }
}

@media (max-width: 720px) {
  .ai-coach-cards {
    padding: 14px;
  }

  .record-heading,
  .record-count {
    font-size: 20px;
  }

  .record-preview {
    --record-card-width: calc(100% - 6px);
    --record-card-height: min(136px, 66%);
    padding-inline: 0;
  }

  .record-main-card,
  .record-stack-card {
    min-height: 108px;
  }

  .record-main-card {
    padding: 18px 20px;
  }

  .record-title {
    font-size: clamp(19px, 6vw, 26px);
  }
}

@media (max-width: 420px) {
  .ai-coach-cards {
    padding: 12px;
  }

  .record-header {
    min-height: 26px;
  }

  .record-heading,
  .record-count {
    font-size: 18px;
  }

  .record-preview {
    --record-card-width: 100%;
    --record-card-height: min(124px, 64%);
    padding-block: 12px 8px;
  }

  .record-main-card,
  .record-stack-card {
    min-height: 100px;
  }

  .record-stack-card.layer-1 {
    transform: translate(-50%, -50%) translate(7px, 11px);
  }

  .record-stack-card.layer-2 {
    transform: translate(-50%, -50%) translate(-7px, 19px);
  }
}
</style>
