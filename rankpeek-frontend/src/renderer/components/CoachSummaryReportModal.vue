<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import CoachSummaryReportContent from '@/components/CoachSummaryReportContent.vue'
import { getCoachReportHeadline } from '@/services/localAiAnalysis'
import type { CoachSummaryReportV1 } from '@/types/coachSummaryReport'

type ReportLoadState = 'loading' | 'ready' | 'missing' | 'unsupported' | 'invalid' | 'error'

const props = withDefaults(defineProps<{
  open: boolean
  report: CoachSummaryReportV1 | null
  reportLoadState?: ReportLoadState
  errorMessage?: string
  createdAt?: string | null
  isPreview?: boolean
}>(), {
  reportLoadState: 'ready',
  errorMessage: '',
  createdAt: null,
  isPreview: false
})

const emit = defineEmits<{
  (event: 'close'): void
}>()

const title = computed(() => (
  props.report ? getCoachReportHeadline({ report: props.report }) : '复盘报告'
))
const subtitle = computed(() => props.report?.summary || props.errorMessage || '正在读取本地报告...')

function emitClose() {
  emit('close')
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) {
    emitClose()
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      document.addEventListener('keydown', handleKeydown)
      return
    }
    document.removeEventListener('keydown', handleKeydown)
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="coach-report-modal-overlay"
      @click.self="emitClose"
    >
      <section
        class="coach-report-modal-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="coach-report-modal-title"
      >
        <header class="coach-report-modal-header">
          <div class="coach-report-modal-title">
            <p>{{ isPreview ? 'AI 复盘报告 · DEV 预览' : 'AI 复盘报告' }}</p>
            <h2 id="coach-report-modal-title">{{ title }}</h2>
            <span>{{ subtitle }}</span>
          </div>
          <button
            class="coach-report-modal-close"
            type="button"
            aria-label="关闭报告"
            @click="emitClose"
          >
            ×
          </button>
        </header>

        <div class="coach-report-modal-body">
          <CoachSummaryReportContent
            :report="report"
            :report-load-state="reportLoadState"
            :error-message="errorMessage"
            :created-at="createdAt"
            mode="modal"
          />
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.coach-report-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 36px 48px;
  background: rgba(0, 0, 0, 0.52);
  backdrop-filter: blur(12px);
}

.coach-report-modal-panel {
  width: min(1180px, calc(100vw - 96px));
  max-height: calc(100vh - 72px);
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(circle at 16% 0%, rgba(var(--accent-rgb), 0.1), transparent 34%),
    var(--bg-secondary);
  box-shadow:
    0 28px 80px rgba(0, 0, 0, 0.42),
    0 0 0 1px rgba(var(--accent-rgb), 0.06);
}

.coach-report-modal-header {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 20px 22px 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.coach-report-modal-title {
  min-width: 0;
}

.coach-report-modal-title p {
  margin: 0 0 4px;
  color: var(--accent-color);
  font-size: 12px;
  font-weight: 800;
}

.coach-report-modal-title h2 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: 0;
}

.coach-report-modal-title span {
  display: block;
  max-width: 780px;
  margin-top: 7px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.coach-report-modal-close {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, color 0.18s ease;
}

.coach-report-modal-close:hover,
.coach-report-modal-close:focus-visible {
  border-color: rgba(var(--accent-rgb), 0.36);
  background: rgba(var(--accent-rgb), 0.1);
  color: var(--text-primary);
  outline: none;
}

.coach-report-modal-body {
  min-height: 0;
  padding: 18px 22px 22px;
  overflow-y: auto;
}

@media (max-width: 720px) {
  .coach-report-modal-overlay {
    padding: 16px;
  }

  .coach-report-modal-panel {
    width: calc(100vw - 32px);
    max-height: calc(100vh - 32px);
  }

  .coach-report-modal-header {
    padding: 16px;
  }

  .coach-report-modal-title h2 {
    font-size: 20px;
  }

  .coach-report-modal-body {
    padding: 14px;
  }
}
</style>
