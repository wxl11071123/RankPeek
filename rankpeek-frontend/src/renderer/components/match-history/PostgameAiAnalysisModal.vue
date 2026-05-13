<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'

type PostgameAiAnalysisMode = 'review' | 'praise'
type PostgameAiStreamState = 'idle' | 'preparing' | 'streaming' | 'completed' | 'failed'

const props = withDefaults(defineProps<{
  open: boolean
  mode?: PostgameAiAnalysisMode
  streamState?: PostgameAiStreamState
  streamText?: string
  streamError?: string
}>(), {
  mode: 'review',
  streamState: 'idle',
  streamText: '',
  streamError: ''
})

const emit = defineEmits<{
  (event: 'start-analysis'): void
  (event: 'cancel-analysis'): void
  (event: 'close'): void
}>()

const isBusy = computed(() => props.streamState === 'preparing' || props.streamState === 'streaming')
const hasStreamOutput = computed(() => props.streamState === 'streaming' || props.streamState === 'completed')
const hasFailed = computed(() => props.streamState === 'failed')
const modalTitle = computed(() => props.mode === 'praise' ? '夸夸机' : '赛后复盘')
const modalDescription = computed(() => props.mode === 'praise'
  ? '只做情绪价值和夸赞安慰，本轮先验证赛后 snapshot 数据接收。'
  : '基于本局玩家表现做复盘入口，本轮先验证赛后 snapshot 数据接收。')
const primaryButtonText = computed(() => {
  if (props.streamState === 'preparing') {
    return '准备中'
  }
  if (props.streamState === 'streaming') {
    return '分析中'
  }
  if (props.streamState === 'completed') {
    return props.mode === 'praise' ? '重新夸夸' : '重新复盘'
  }
  if (props.streamState === 'failed') {
    return '重试'
  }
  return props.mode === 'praise' ? '开始夸夸' : '开始复盘'
})

function emitStartAnalysis(): void {
  if (isBusy.value) {
    return
  }
  emit('start-analysis')
}

function requestClose(): void {
  if (isBusy.value) {
    emit('cancel-analysis')
  }
  emit('close')
}

function handleKeydown(event: KeyboardEvent): void {
  if (!props.open) {
    return
  }

  if (event.key === 'Escape') {
    requestClose()
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
      class="postgame-ai-analysis-overlay"
      @click.self="requestClose"
    >
      <section
        class="postgame-ai-analysis-panel"
        :class="`postgame-ai-analysis-${mode}`"
        role="dialog"
        aria-modal="true"
        aria-labelledby="postgame-ai-analysis-title"
      >
        <header class="postgame-ai-analysis-header">
          <div class="postgame-ai-analysis-heading">
            <h2 id="postgame-ai-analysis-title">{{ modalTitle }}</h2>
            <p>{{ modalDescription }}</p>
          </div>
          <button
            class="postgame-ai-analysis-close"
            type="button"
            aria-label="关闭赛后 AI 弹窗"
            @click="requestClose"
          >
            ×
          </button>
        </header>

        <div class="postgame-ai-analysis-body">
          <section
            v-if="props.streamState === 'preparing' || props.streamState === 'streaming'"
            class="postgame-ai-analysis-status"
            role="status"
          >
            {{ props.streamState === 'preparing' ? '正在整理本局数据' : '正在接收 rankpeek-server mock stream' }}
          </section>

          <section
            v-if="hasStreamOutput && streamText"
            class="postgame-ai-analysis-result"
          >
            <pre class="postgame-ai-analysis-stream-text">{{ streamText }}</pre>
          </section>

          <section
            v-else-if="hasFailed"
            class="postgame-ai-analysis-error"
            role="alert"
          >
            {{ streamError || 'rankpeek-server 暂不可用' }}
          </section>

          <section
            v-else
            class="postgame-ai-analysis-placeholder"
          >
            <span class="postgame-ai-analysis-eyebrow">AI 数据闭环</span>
            <p>点击开始后才会构建本局 snapshot 并发送到 rankpeek-server。</p>
          </section>

          <button
            class="postgame-ai-analysis-start"
            type="button"
            :disabled="isBusy"
            @click="emitStartAnalysis"
          >
            {{ primaryButtonText }}
          </button>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.postgame-ai-analysis-overlay {
  position: fixed;
  inset: 0;
  z-index: 1120;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(12px);
}

.postgame-ai-analysis-panel {
  width: min(560px, calc(100vw - 64px));
  max-height: calc(100vh - 64px);
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background:
    linear-gradient(180deg, rgba(var(--accent-rgb), 0.07), transparent 170px),
    var(--bg-secondary);
  color: var(--text-primary);
  box-shadow:
    0 28px 76px rgba(0, 0, 0, 0.42),
    0 0 0 1px rgba(var(--accent-rgb), 0.04);
}

.postgame-ai-analysis-header {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px 16px;
  border-bottom: 1px solid var(--border-color);
}

.postgame-ai-analysis-heading {
  min-width: 0;
}

.postgame-ai-analysis-heading h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 22px;
  font-weight: 850;
  line-height: 1.25;
  letter-spacing: 0;
}

.postgame-ai-analysis-heading p {
  margin: 7px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 750;
  line-height: 1.45;
}

.postgame-ai-analysis-close {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, color 0.16s ease;
}

.postgame-ai-analysis-close:hover,
.postgame-ai-analysis-close:focus-visible {
  border-color: rgba(var(--accent-rgb), 0.44);
  background: rgba(var(--accent-rgb), 0.12);
  color: var(--text-primary);
  outline: none;
}

.postgame-ai-analysis-body {
  min-height: 0;
  display: grid;
  gap: 14px;
  padding: 18px 20px 20px;
  overflow-y: auto;
}

.postgame-ai-analysis-placeholder,
.postgame-ai-analysis-status,
.postgame-ai-analysis-result,
.postgame-ai-analysis-error {
  display: grid;
  gap: 6px;
  padding: 14px;
  border: 1px dashed rgba(var(--accent-rgb), 0.22);
  border-radius: 8px;
  background: rgba(var(--accent-rgb), 0.055);
}

.postgame-ai-analysis-status {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 750;
}

.postgame-ai-analysis-error {
  border-color: rgba(239, 111, 122, 0.32);
  background: rgba(239, 111, 122, 0.08);
  color: #ef6f7a;
  font-size: 13px;
  font-weight: 750;
}

.postgame-ai-analysis-eyebrow {
  color: rgba(var(--accent-rgb), 0.9);
  font-size: 12px;
  font-weight: 850;
  line-height: 1;
}

.postgame-ai-analysis-placeholder p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.45;
}

.postgame-ai-analysis-stream-text {
  margin: 0;
  color: var(--text-primary);
  font-family: inherit;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.55;
  white-space: pre-wrap;
}

.postgame-ai-analysis-start {
  min-height: 36px;
  justify-self: start;
  padding: 0 15px;
  border: 1px solid rgba(var(--accent-rgb), 0.28);
  border-radius: 8px;
  background: rgba(var(--accent-rgb), 0.11);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 850;
  line-height: 1;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, opacity 0.16s ease;
}

.postgame-ai-analysis-start:hover,
.postgame-ai-analysis-start:focus-visible {
  border-color: rgba(var(--accent-rgb), 0.48);
  background: rgba(var(--accent-rgb), 0.17);
  outline: none;
}

.postgame-ai-analysis-start:disabled {
  opacity: 0.62;
  cursor: wait;
}

@media (max-width: 720px) {
  .postgame-ai-analysis-overlay {
    padding: 16px;
  }

  .postgame-ai-analysis-panel {
    width: calc(100vw - 32px);
    max-height: calc(100vh - 32px);
  }

  .postgame-ai-analysis-header,
  .postgame-ai-analysis-body {
    padding-left: 14px;
    padding-right: 14px;
  }
}
</style>
