<script setup lang="ts">
import { computed, ref } from 'vue'

interface CoachCard {
  title: string
  body: string
  detail: string
  isPlaceholder?: boolean
}

interface CoachReport {
  title: string
  body: string
  detail?: string
}

interface PositionedCoachCard {
  card: CoachCard
  index: number
  order: number
}

const MIN_STACK_CARDS = 3
const MAX_STACK_CARDS = 7
const EMPTY_REPORT_TEXT = '使用电子教练创建第一份个人报告。'
const WAITING_REPORT_TEXT = '等待更多数据。'

const props = withDefaults(defineProps<{ reports?: CoachReport[] }>(), {
  reports: () => []
})

const activeIndex = ref(0)
const expandedIndex = ref<number | null>(null)
const touchStartY = ref<number | null>(null)
let lastWheelAt = 0

const cards = computed<CoachCard[]>(() => {
  const reportCards: CoachCard[] = props.reports.slice(0, MAX_STACK_CARDS).map((report, index) => ({
    title: report.title || `第 ${index + 1} 份报告`,
    body: report.body || 'AI 教练报告即将上线',
    detail: report.detail || '详细报告即将上线'
  }))

  if (reportCards.length === 0) {
    return Array.from({ length: MIN_STACK_CARDS }, (_, index) => ({
      title: index === 0 ? '个人报告' : '待生成报告',
      body: EMPTY_REPORT_TEXT,
      detail: EMPTY_REPORT_TEXT,
      isPlaceholder: true
    }))
  }

  while (reportCards.length < MIN_STACK_CARDS) {
    reportCards.push({
      title: '待生成报告',
      body: WAITING_REPORT_TEXT,
      detail: WAITING_REPORT_TEXT,
      isPlaceholder: true
    })
  }

  return reportCards
})

const stackStyle = computed<Record<string, string>>(() => {
  const depth = Math.min(cards.value.length, MAX_STACK_CARDS)
  return {
    '--coach-stack-height': `${178 + (depth - 1) * 32 + 76}px`
  }
})

const positionedCards = computed<PositionedCoachCard[]>(() =>
  cards.value.map((card, index) => ({
    card,
    index,
    order: (index - activeIndex.value + cards.value.length) % cards.value.length
  }))
)

const expandedCard = computed(() =>
  expandedIndex.value == null ? null : cards.value[expandedIndex.value]
)

function cardStyle(item: PositionedCoachCard): Record<string, string> {
  const scale = Math.max(0.74, 1 - item.order * 0.035)
  return {
    '--card-transform': `translateY(${item.order * 32 - 3}px) scale(${scale})`,
    zIndex: String(cards.value.length - item.order),
    opacity: String(Math.max(0.3, 1 - item.order * 0.1))
  }
}

function moveStack(direction: 1 | -1) {
  activeIndex.value = (activeIndex.value + direction + cards.value.length) % cards.value.length
}

function handleWheel(event: WheelEvent) {
  if (Math.abs(event.deltaY) < 8) {
    return
  }

  const now = Date.now()
  if (now - lastWheelAt < 420) {
    return
  }

  lastWheelAt = now
  moveStack(event.deltaY > 0 ? 1 : -1)
}

function handleTouchStart(event: TouchEvent) {
  touchStartY.value = event.touches[0]?.clientY ?? null
}

function handleTouchEnd(event: TouchEvent) {
  if (touchStartY.value == null) {
    return
  }

  const endY = event.changedTouches[0]?.clientY ?? touchStartY.value
  const delta = touchStartY.value - endY
  touchStartY.value = null

  if (Math.abs(delta) < 24) {
    return
  }

  moveStack(delta > 0 ? 1 : -1)
}

function openCard(index: number) {
  activeIndex.value = index
  expandedIndex.value = index
}

function closeCard() {
  expandedIndex.value = null
}
</script>

<template>
  <div
    class="ai-coach-cards"
    :style="stackStyle"
    @wheel.prevent="handleWheel"
    @touchstart.passive="handleTouchStart"
    @touchend.passive="handleTouchEnd"
  >
    <button
      v-for="item in positionedCards"
      :key="`${item.card.title}-${item.index}`"
      class="coach-stack-card"
      type="button"
      :class="{ active: item.order === 0, placeholder: item.card.isPlaceholder }"
      :style="cardStyle(item)"
      @click="openCard(item.index)"
    >
      <span class="coach-card-shine"></span>
      <span class="coach-card-title">{{ item.card.title }}</span>
      <span class="coach-card-body">{{ item.card.body }}</span>
    </button>

    <div class="coach-card-dots" aria-hidden="true">
      <span
        v-for="(_card, index) in cards"
        :key="index"
        :class="{ active: index === activeIndex }"
      ></span>
    </div>

    <Transition name="coach-expand">
      <div v-if="expandedCard" class="coach-expanded-layer" @click.self="closeCard">
        <article class="coach-expanded-card">
          <button class="coach-close" type="button" aria-label="关闭" @click="closeCard">
            ×
          </button>
          <h3>{{ expandedCard.title }}</h3>
          <p>{{ expandedCard.body }}</p>
          <strong>{{ expandedCard.detail }}</strong>
        </article>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.ai-coach-cards {
  position: relative;
  min-height: var(--coach-stack-height, 286px);
  overflow: visible;
  padding: 10px 14px 56px 0;
  touch-action: pan-y;
}

.coach-stack-card {
  position: absolute;
  top: 10px;
  left: 0;
  right: 8px;
  min-height: 178px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 10px;
  padding: 26px;
  border: 1px solid rgba(212, 175, 55, 0.3);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-primary);
  text-align: left;
  backdrop-filter: blur(12px);
  box-shadow: 0 0 20px rgba(212, 175, 55, 0.15);
  transform: var(--card-transform);
  transform-origin: center top;
  transition:
    transform 0.4s ease,
    opacity 0.4s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease,
    background 0.22s ease;
  overflow: hidden;
  isolation: isolate;
  will-change: transform;
}

.coach-stack-card::after {
  content: '';
  position: absolute;
  inset: -1px;
  border: 1px solid rgba(212, 175, 55, 0.26);
  border-radius: inherit;
  box-shadow: 0 0 20px rgba(212, 175, 55, 0.15);
  opacity: 0.82;
  pointer-events: none;
  transition:
    border-color 0.22s ease,
    box-shadow 0.22s ease,
    opacity 0.22s ease;
  z-index: 0;
}

.coach-stack-card:hover,
.coach-stack-card:focus-visible {
  border-color: rgba(212, 175, 55, 0.58);
  background: rgba(255, 255, 255, 0.1);
  box-shadow: 0 0 32px rgba(212, 175, 55, 0.28);
}

.coach-stack-card:hover::after,
.coach-stack-card:focus-visible::after {
  animation: coach-border-breathe 1.5s ease-in-out infinite;
}

.coach-stack-card:focus-visible {
  outline: 2px solid rgba(212, 175, 55, 0.62);
  outline-offset: 3px;
}

.coach-card-shine {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(118deg, rgba(212, 175, 55, 0.18), transparent 42%, rgba(255, 255, 255, 0.04));
  opacity: 0.62;
  pointer-events: none;
  z-index: 0;
}

.coach-card-title,
.coach-card-body {
  position: relative;
  z-index: 2;
}

.coach-card-title {
  color: var(--text-primary);
  font-size: 22px;
  font-weight: 900;
}

.coach-card-body {
  color: var(--text-secondary);
  font-size: 15px;
  font-weight: 700;
}

.coach-stack-card.placeholder .coach-card-body {
  color: var(--coach-placeholder-color, rgba(238, 205, 112, 0.9));
}

.coach-card-dots {
  position: absolute;
  left: 0;
  bottom: 18px;
  display: flex;
  gap: 8px;
}

.coach-card-dots span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: rgba(212, 175, 55, 0.24);
  transition: width 0.22s ease, background 0.22s ease;
}

.coach-card-dots span.active {
  width: 24px;
  background: rgba(212, 175, 55, 0.82);
}

.coach-expanded-layer {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: stretch;
  padding: 10px 8px 16px 0;
  background: rgba(10, 10, 12, 0.2);
  backdrop-filter: blur(4px);
}

.coach-expanded-card {
  position: relative;
  width: 100%;
  min-height: 236px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  padding: 34px;
  border: 1px solid rgba(212, 175, 55, 0.45);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-primary);
  backdrop-filter: blur(14px);
  box-shadow: 0 0 36px rgba(212, 175, 55, 0.24);
}

.coach-expanded-card h3,
.coach-expanded-card p {
  margin: 0;
}

.coach-expanded-card h3 {
  font-size: 28px;
}

.coach-expanded-card p {
  color: var(--text-secondary);
  font-size: 16px;
  font-weight: 700;
}

.coach-expanded-card strong {
  color: rgba(212, 175, 55, 0.96);
  font-size: 18px;
}

.coach-close {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(212, 175, 55, 0.38);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-primary);
  font-size: 22px;
  line-height: 1;
}

.coach-expand-enter-active,
.coach-expand-leave-active {
  transition: opacity 0.28s ease;
}

.coach-expand-enter-active .coach-expanded-card,
.coach-expand-leave-active .coach-expanded-card {
  transition: transform 0.28s ease, opacity 0.28s ease;
}

.coach-expand-enter-from,
.coach-expand-leave-to {
  opacity: 0;
}

.coach-expand-enter-from .coach-expanded-card,
.coach-expand-leave-to .coach-expanded-card {
  opacity: 0;
  transform: scale(0.94);
}

:global([data-theme="light"]) .coach-stack-card {
  background: rgba(255, 255, 255, 0.5);
}

:global([data-theme="light"]) .coach-stack-card:hover,
:global([data-theme="light"]) .coach-stack-card:focus-visible {
  background: rgba(255, 255, 255, 0.72);
}

:global([data-theme="light"]) .coach-expanded-card {
  background: rgba(255, 255, 255, 0.72);
}

@keyframes coach-border-breathe {
  0%,
  100% {
    border-color: rgba(212, 175, 55, 0.34);
    box-shadow: 0 0 28px rgba(212, 175, 55, 0.22);
    opacity: 0.84;
  }

  50% {
    border-color: rgba(247, 217, 122, 0.7);
    box-shadow: 0 0 40px rgba(212, 175, 55, 0.34);
    opacity: 1;
  }
}

@media (max-width: 720px) {
  .ai-coach-cards {
    min-height: 300px;
    padding-right: 0;
  }

  .coach-stack-card {
    right: 0;
    padding: 22px;
  }

  .coach-expanded-layer {
    padding-right: 0;
  }
}
</style>
