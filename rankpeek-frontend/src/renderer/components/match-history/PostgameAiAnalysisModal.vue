<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import {
  parsePartialPostgameAiStructuredResult,
  parsePostgameAiStructuredResult,
  type PostgameAiReviewRosterPlayer,
  type PostgameAiStructuredPlayer
} from '@/services/postgameAiStructuredResult'
import {
  downloadPostgameReviewImage,
  resolvePostgameReviewPlayerIconUrl
} from '@/services/postgameAiShareImage'
import type { PostgameAiTokenUsage } from '@/services/postgameAiServerStream'
import { getChampionIconUrl } from '@/utils/gameAssetUrls'

type PostgameAiAnalysisMode = 'review' | 'praise'
type PostgameAiStreamState = 'idle' | 'preparing' | 'streaming' | 'completed' | 'failed'

const props = withDefaults(defineProps<{
  open: boolean
  mode?: PostgameAiAnalysisMode
  streamState?: PostgameAiStreamState
  streamText?: string
  streamError?: string
  streamUsage?: PostgameAiTokenUsage | null
  rosterPlayers?: PostgameAiReviewRosterPlayer[]
  championIdByName?: Record<string, number>
  showStartButton?: boolean
}>(), {
  mode: 'review',
  streamState: 'idle',
  streamText: '',
  streamError: '',
  streamUsage: null,
  rosterPlayers: () => [],
  championIdByName: () => ({}),
  showStartButton: true
})

const emit = defineEmits<{
  (event: 'start-analysis'): void
  (event: 'cancel-analysis'): void
  (event: 'close'): void
}>()

const isBusy = computed(() => props.streamState === 'preparing' || props.streamState === 'streaming')
const hasStreamOutput = computed(() => props.streamState === 'streaming' || props.streamState === 'completed')
const hasFailed = computed(() => props.streamState === 'failed')
const parsedPostgameReview = computed(() => props.mode === 'review'
  ? parsePostgameAiStructuredResult(props.streamText)
  : { ok: false as const, error: '不是赛后复盘模式' })
const structuredPostgameReview = computed(() => parsedPostgameReview.value.ok ? parsedPostgameReview.value.result : null)
const partialPostgameReview = computed(() => props.mode === 'review' && !structuredPostgameReview.value
  ? parsePartialPostgameAiStructuredResult(props.streamText)
  : { ok: false as const, error: '不是部分赛后复盘结果' })
const displayedPostgameReview = computed(() => structuredPostgameReview.value
  ?? (partialPostgameReview.value.ok ? partialPostgameReview.value.result : null))
const postgameReviewRows = computed(() => displayedPostgameReview.value?.levels ?? [])
const modalTitle = computed(() => props.mode === 'praise' ? '夸夸机' : '赛后复盘')
const modalDescription = computed(() => props.mode === 'praise'
  ? '只做情绪价值和夸赞安慰，本轮先验证赛后 snapshot 数据接收。'
  : '从夯到拉排位表会按 5 档展示 10 个玩家，并把客观总结一起放进可分享图片。')
const tokenUsageLine = computed(() => props.streamUsage
  ? `输入 ${formatTokenCount(props.streamUsage.promptTokens)}（缓存命中 ${formatTokenCount(props.streamUsage.promptCacheHitTokens)} / 未命中 ${formatTokenCount(props.streamUsage.promptCacheMissTokens)}），输出 ${formatTokenCount(props.streamUsage.completionTokens)}，总计 ${formatTokenCount(props.streamUsage.totalTokens)}`
  : '')
const tokenCostLine = computed(() => props.streamUsage
  ? `估算成本：¥${formatCny(props.streamUsage.cost.totalCny)}（按大陆 API 人民币价格）`
  : '')
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

function formatTokenCount(value: number): string {
  return Math.max(0, Math.round(value)).toLocaleString('zh-CN')
}

function formatCny(value: number): string {
  if (value > 0 && value < 0.000001) {
    return value.toFixed(10)
  }
  if (value < 0.01) {
    return value.toFixed(6)
  }
  return value.toFixed(4)
}

function getPostgameReviewPlayerIconUrl(player: PostgameAiStructuredPlayer): string {
  return resolvePostgameReviewPlayerIconUrl(player, props.rosterPlayers, getChampionIconUrl, props.championIdByName)
}

function downloadPostgameAiShareImage(): void {
  if (!structuredPostgameReview.value) {
    return
  }

  void downloadPostgameReviewImage(structuredPostgameReview.value, {
    title: modalTitle.value,
    rosterPlayers: props.rosterPlayers,
    championIdByName: props.championIdByName,
    getChampionIconUrl
  }).catch(() => undefined)
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
            {{ props.streamState === 'preparing' ? '正在整理本局数据' : '正在接收 rankpeek-server stream' }}
          </section>

          <section
            v-if="props.streamUsage"
            class="postgame-ai-token-usage"
          >
            <strong>Token 用量</strong>
            <span>{{ tokenUsageLine }}</span>
            <span>{{ tokenCostLine }}</span>
          </section>

          <section
            v-if="displayedPostgameReview"
            class="postgame-ai-analysis-result postgame-ai-analysis-structured"
          >
            <div class="postgame-ladu-chart" aria-label="从夯到拉赛后复盘表">
              <div
                v-for="row in postgameReviewRows"
                :key="row.label"
                class="postgame-ladu-row"
              >
                <div class="postgame-ladu-label">{{ row.label }}</div>
                <div class="postgame-ladu-players">
                  <article
                    v-for="player in row.players"
                    :key="`${row.label}:${player.playerRef}`"
                    class="postgame-ladu-player"
                    :title="player.phrase"
                  >
                    <img
                      v-if="getPostgameReviewPlayerIconUrl(player)"
                      class="postgame-ladu-avatar"
                      :src="getPostgameReviewPlayerIconUrl(player)"
                      :alt="player.championName"
                    >
                    <span
                      v-else
                      class="postgame-ladu-avatar postgame-ladu-avatar-fallback"
                    >
                      {{ player.championName.slice(0, 2) }}
                    </span>
                    <strong>{{ player.championName }}</strong>
                    <span>{{ player.phrase }}</span>
                  </article>
                </div>
              </div>
              <div class="postgame-ladu-summary">
                <strong>客观总结</strong>
                <p>{{ displayedPostgameReview.summary || '正在生成客观总结...' }}</p>
              </div>
            </div>

            <button
              v-if="structuredPostgameReview"
              class="postgame-ai-analysis-share"
              type="button"
              @click="downloadPostgameAiShareImage"
            >
              生成图片
            </button>
          </section>

          <section
            v-else-if="hasStreamOutput && mode === 'review'"
            class="postgame-ai-analysis-result"
          >
            <p class="postgame-ai-analysis-streaming-note">正在解析结构化复盘结果，玩家排位会在字段完整后逐步上表。</p>
          </section>

          <section
            v-else-if="hasStreamOutput && streamText && (mode !== 'review' || props.streamState === 'completed')"
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
            v-if="showStartButton"
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
  width: min(760px, calc(100vw - 64px));
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

.postgame-ai-analysis-structured {
  gap: 12px;
  padding: 0;
  border: 0;
  background: transparent;
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

.postgame-ai-token-usage {
  display: grid;
  gap: 5px;
  padding: 10px 12px;
  border: 1px solid rgba(98, 212, 158, 0.22);
  border-radius: 8px;
  background: rgba(98, 212, 158, 0.08);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 750;
  line-height: 1.4;
}

.postgame-ai-token-usage strong {
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 900;
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

.postgame-ai-analysis-streaming-note {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 750;
  line-height: 1.45;
}

.postgame-ladu-chart {
  overflow: hidden;
  border: 1px solid rgba(17, 24, 32, 0.36);
  border-radius: 8px;
  background: #f6f6f2;
  color: #111318;
  box-shadow: 0 14px 34px rgba(0, 0, 0, 0.22);
}

.postgame-ladu-row {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  min-height: 104px;
  border-bottom: 1px solid rgba(17, 24, 32, 0.26);
}

.postgame-ladu-row:nth-child(1) .postgame-ladu-label {
  background: #f04b3e;
}

.postgame-ladu-row:nth-child(2) .postgame-ladu-label {
  background: #f47a3e;
}

.postgame-ladu-row:nth-child(3) .postgame-ladu-label {
  background: #f6c85f;
}

.postgame-ladu-row:nth-child(4) .postgame-ladu-label {
  background: #fff1a8;
}

.postgame-ladu-row:nth-child(5) .postgame-ladu-label {
  background: #f7f0d0;
}

.postgame-ladu-label {
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid rgba(17, 24, 32, 0.28);
  color: #1a1209;
  font-size: 15px;
  font-weight: 900;
  line-height: 1;
}

.postgame-ladu-players {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(96px, 1fr));
  gap: 8px;
  align-content: center;
  align-items: center;
  padding: 10px;
  background: #f6f6f2;
}

.postgame-ladu-player {
  min-width: 0;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  grid-template-rows: auto auto;
  gap: 1px 7px;
  align-content: center;
  align-items: center;
}

.postgame-ladu-avatar {
  grid-row: 1 / span 2;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(17, 24, 32, 0.22);
  border-radius: 5px;
  object-fit: cover;
  background: #28313d;
}

.postgame-ladu-avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #d8dfeb;
  font-size: 12px;
  font-weight: 900;
}

.postgame-ladu-player strong,
.postgame-ladu-player span:last-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.postgame-ladu-player strong {
  color: #111318;
  font-size: 12px;
  font-weight: 900;
  line-height: 1.2;
  white-space: nowrap;
}

.postgame-ladu-player span:last-child {
  display: block;
  color: #404855;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.5;
  overflow: visible;
  text-overflow: clip;
  white-space: normal;
  overflow-wrap: anywhere;
}

.postgame-ladu-summary {
  display: grid;
  gap: 6px;
  padding: 13px 15px 15px;
  background: #f6f6f2;
}

.postgame-ladu-summary strong {
  color: #111318;
  font-size: 13px;
  font-weight: 900;
}

.postgame-ladu-summary p {
  margin: 0;
  color: #303642;
  font-size: 13px;
  font-weight: 750;
  line-height: 1.5;
}

.postgame-ai-analysis-share {
  min-height: 34px;
  justify-self: start;
  padding: 0 14px;
  border: 1px solid rgba(98, 212, 158, 0.28);
  border-radius: 8px;
  background: rgba(98, 212, 158, 0.12);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 850;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease;
}

.postgame-ai-analysis-share:hover,
.postgame-ai-analysis-share:focus-visible {
  border-color: rgba(98, 212, 158, 0.5);
  background: rgba(98, 212, 158, 0.18);
  outline: none;
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
