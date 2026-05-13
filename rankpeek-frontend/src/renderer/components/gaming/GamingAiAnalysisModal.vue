<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import type {
  GamingAiAnalysisMode,
  GamingAiAnalysisPreview,
  GamingAiPlayerInsight
} from '@/services/gamingAiAnalysisPreview'
import type { GamingAiPlayerStreamVerdict } from '@/services/gamingAiServerStream'
import { getChampionIconUrl, getProfileIconUrl, markAssetLoadFailed } from '@/utils/gameAssetUrls'

const props = withDefaults(defineProps<{
  open: boolean
  mode?: GamingAiAnalysisMode
  preview: GamingAiAnalysisPreview | null
  queueLabel?: string
  analysisEnabled?: boolean
  streamState?: 'idle' | 'preparing' | 'streaming' | 'completed' | 'failed'
  streamText?: string
  streamError?: string
  playerVerdicts?: Record<string, GamingAiPlayerStreamVerdict>
}>(), {
  mode: 'teammate',
  queueLabel: '',
  analysisEnabled: true,
  streamState: 'idle',
  streamText: '',
  streamError: '',
  playerVerdicts: () => ({})
})

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'start-analysis'): void
  (event: 'cancel-analysis'): void
}>()

const fallbackTitle = computed(() => props.mode === 'teammate' ? '队友成分分析' : '赛前对手分析')
const fallbackSubtitle = computed(() => '未知模式')
const streamBusy = computed(() => props.streamState === 'preparing' || props.streamState === 'streaming')
const analysisButtonDisabled = computed(() => streamBusy.value || !props.analysisEnabled)
const analysisButtonText = computed(() => streamBusy.value ? '分析中...' : '开始分析')
const playerVerdictList = computed(() => {
  const playersByKey = new Map((props.preview?.players ?? []).map(player => [player.key, player]))
  return Object.values(props.playerVerdicts)
    .filter(verdict => verdict.playerKey && verdict.label)
    .map(verdict => ({
      ...verdict,
      player: playersByKey.get(verdict.playerKey)
    }))
})
const streamVisible = computed(() => (
  props.streamState === 'preparing' ||
  props.streamState === 'streaming' ||
  props.streamState === 'completed' ||
  props.streamState === 'failed' ||
  Boolean(props.streamText.trim())
))
const streamStatusText = computed(() => {
  if (props.streamState === 'failed') {
    return '服务器暂不可用，请稍后再试。'
  }
  if (props.streamState === 'completed') {
    return '分析完成'
  }
  if (props.streamState === 'preparing') {
    return '正在准备请求...'
  }
  if (props.streamState === 'streaming') {
    return props.streamText.trim() ? '' : '正在等待服务器返回...'
  }
  return ''
})

function emitClose() {
  emit('close')
}

function emitStartAnalysis() {
  if (!analysisButtonDisabled.value) {
    emit('start-analysis')
  }
}

function emitCancelAnalysis() {
  emit('cancel-analysis')
}

function handleKeydown(event: KeyboardEvent) {
  if (!props.open) {
    return
  }

  if (event.key === 'Escape') {
    emitClose()
  }
}

function playerAvatarUrl(player: GamingAiPlayerInsight | undefined): string {
  if (!player) {
    return ''
  }
  if (player.championId && player.championId > 0) {
    return getChampionIconUrl(player.championId)
  }
  if (player.profileIconId && player.profileIconId > 0) {
    return getProfileIconUrl(player.profileIconId)
  }
  return ''
}

function displayPlayerName(verdict: GamingAiPlayerStreamVerdict & { player?: GamingAiPlayerInsight }): string {
  return verdict.player?.name || verdict.playerKey
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
      class="gaming-ai-analysis-overlay"
      @click.self="emitClose"
    >
      <section
        class="gaming-ai-analysis-panel"
        :class="`analysis-mode-${mode}`"
        role="dialog"
        aria-modal="true"
        aria-labelledby="gaming-ai-analysis-title"
      >
        <header class="gaming-ai-analysis-header">
          <div class="gaming-ai-analysis-heading">
            <p class="gaming-ai-analysis-eyebrow">RankPeek 分析</p>
            <h2 id="gaming-ai-analysis-title">{{ preview?.title || fallbackTitle }}</h2>
            <span>{{ queueLabel || preview?.subtitle || fallbackSubtitle }}</span>
            <p
              v-if="analysisEnabled"
              class="gaming-ai-analysis-note"
            >
              点击开始分析后，将基于当前对战信息生成临时分析。
            </p>
            <p
              v-else
              class="gaming-ai-analysis-note"
            >
              当前仅支持单双排位和灵活排位分析。
            </p>
          </div>
          <div class="gaming-ai-analysis-actions">
            <button
              class="gaming-ai-analysis-start"
              type="button"
              :disabled="analysisButtonDisabled"
              @click="emitStartAnalysis"
            >
              {{ analysisButtonText }}
            </button>
            <button
              v-if="streamBusy"
              class="gaming-ai-analysis-stop"
              type="button"
              @click="emitCancelAnalysis"
            >
              停止
            </button>
            <button
              class="gaming-ai-analysis-close"
              type="button"
              aria-label="关闭分析弹窗"
              @click="emitClose"
            >
              ×
            </button>
          </div>
        </header>

        <div class="gaming-ai-analysis-body">
          <section v-if="streamVisible" class="gaming-ai-analysis-section gaming-ai-analysis-stream">
            <h3>服务器分析</h3>
            <p
              v-if="streamStatusText"
              class="gaming-ai-analysis-stream-status"
              :class="`stream-${streamState}`"
              :title="streamState === 'failed' ? streamError : undefined"
            >
              {{ streamStatusText }}
            </p>
            <pre v-if="streamText.trim()" class="gaming-ai-analysis-stream-text">{{ streamText }}</pre>
          </section>

          <section v-if="playerVerdictList.length" class="gaming-ai-analysis-section">
            <h3>玩家判断</h3>
            <ul class="gaming-ai-analysis-player-list">
              <li
                v-for="verdict in playerVerdictList"
                :key="verdict.playerKey"
                class="gaming-ai-analysis-player"
                :class="`tone-${verdict.tone || 'unknown'}`"
              >
                <div class="gaming-ai-analysis-avatar">
                  <img
                    v-if="playerAvatarUrl(verdict.player)"
                    :src="playerAvatarUrl(verdict.player)"
                    :alt="displayPlayerName(verdict)"
                    @error="markAssetLoadFailed"
                  />
                  <span v-else>{{ displayPlayerName(verdict).slice(0, 1) }}</span>
                </div>

                <div class="gaming-ai-analysis-copy">
                  <div class="gaming-ai-analysis-player-title">
                    <strong>{{ displayPlayerName(verdict) }}</strong>
                    <span v-if="verdict.player?.rankText">{{ verdict.player.rankText }}</span>
                  </div>
                  <p v-if="verdict.reason">{{ verdict.reason }}</p>
                </div>

                <div class="gaming-ai-analysis-side">
                  <span class="gaming-ai-analysis-verdict">{{ verdict.label }}</span>
                </div>
              </li>
            </ul>
          </section>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.gaming-ai-analysis-overlay {
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

.gaming-ai-analysis-panel {
  width: min(920px, calc(100vw - 64px));
  max-height: calc(100vh - 64px);
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background:
    linear-gradient(180deg, rgba(var(--accent-rgb), 0.07), transparent 180px),
    var(--bg-secondary);
  color: var(--text-primary);
  box-shadow:
    0 28px 76px rgba(0, 0, 0, 0.42),
    0 0 0 1px rgba(var(--accent-rgb), 0.04);
}

.gaming-ai-analysis-header {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px 16px;
  border-bottom: 1px solid var(--border-color);
}

.gaming-ai-analysis-heading {
  min-width: 0;
}

.gaming-ai-analysis-eyebrow {
  margin: 0 0 7px;
  color: rgba(var(--accent-rgb), 0.9);
  font-size: 12px;
  font-weight: 850;
  line-height: 1;
}

.gaming-ai-analysis-heading h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 22px;
  font-weight: 850;
  line-height: 1.25;
  letter-spacing: 0;
}

.gaming-ai-analysis-heading span {
  display: block;
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 750;
  line-height: 1.35;
}

.gaming-ai-analysis-note {
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
}

.gaming-ai-analysis-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.gaming-ai-analysis-start,
.gaming-ai-analysis-stop {
  min-height: 34px;
  padding: 0 13px;
  border: 1px solid rgba(var(--accent-rgb), 0.32);
  border-radius: 8px;
  background: rgba(var(--accent-rgb), 0.12);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 850;
  line-height: 1;
  cursor: pointer;
  white-space: nowrap;
  transition: border-color 0.16s ease, background 0.16s ease, color 0.16s ease, opacity 0.16s ease;
}

.gaming-ai-analysis-start:hover:not(:disabled),
.gaming-ai-analysis-start:focus-visible:not(:disabled),
.gaming-ai-analysis-stop:hover,
.gaming-ai-analysis-stop:focus-visible {
  border-color: rgba(var(--accent-rgb), 0.58);
  background: rgba(var(--accent-rgb), 0.18);
  outline: none;
}

.gaming-ai-analysis-start:disabled {
  cursor: default;
  opacity: 0.72;
}

.gaming-ai-analysis-stop {
  border-color: rgba(240, 179, 90, 0.34);
  background: rgba(240, 179, 90, 0.12);
  color: #f0b35a;
}

.gaming-ai-analysis-close {
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

.gaming-ai-analysis-close:hover,
.gaming-ai-analysis-close:focus-visible {
  border-color: rgba(var(--accent-rgb), 0.44);
  background: rgba(var(--accent-rgb), 0.12);
  color: var(--text-primary);
  outline: none;
}

.gaming-ai-analysis-body {
  min-height: 0;
  padding: 18px 20px 20px;
  overflow-y: auto;
}

.gaming-ai-analysis-opening {
  margin: 0 0 18px;
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 750;
  line-height: 1.55;
}

.gaming-ai-analysis-stream {
  padding: 12px 14px;
  border: 1px solid rgba(var(--accent-rgb), 0.16);
  border-radius: 8px;
  background: rgba(var(--accent-rgb), 0.045);
}

.gaming-ai-analysis-stream-status {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 750;
  line-height: 1.45;
}

.gaming-ai-analysis-stream-status.stream-failed {
  color: #f0b35a;
}

.gaming-ai-analysis-stream-status.stream-completed {
  color: #55d187;
}

.gaming-ai-analysis-stream-text {
  margin: 10px 0 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: var(--text-primary);
  font-family: inherit;
  font-size: 13px;
  font-weight: 750;
  line-height: 1.65;
}

.gaming-ai-analysis-section + .gaming-ai-analysis-section,
.gaming-ai-analysis-empty + .gaming-ai-analysis-section {
  margin-top: 20px;
}

.gaming-ai-analysis-section h3 {
  margin: 0 0 10px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 850;
  line-height: 1.25;
}

.gaming-ai-analysis-player-list,
.gaming-ai-analysis-bullets {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.gaming-ai-analysis-player {
  min-width: 0;
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) minmax(220px, auto);
  align-items: center;
  gap: 12px;
  padding: 9px 0;
  border-top: 1px solid rgba(var(--accent-rgb), 0.1);
}

.gaming-ai-analysis-player:first-child {
  border-top: 0;
}

.gaming-ai-analysis-avatar {
  width: 44px;
  height: 44px;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-weight: 850;
}

.gaming-ai-analysis-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.gaming-ai-analysis-avatar img[data-asset-failed='true'] {
  display: none;
}

.gaming-ai-analysis-copy {
  min-width: 0;
}

.gaming-ai-analysis-player-title {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.gaming-ai-analysis-player-title strong {
  min-width: 0;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 850;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gaming-ai-analysis-player-title span {
  flex: 0 0 auto;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 750;
  line-height: 1.25;
}

.gaming-ai-analysis-copy p {
  margin: 5px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.gaming-ai-analysis-side {
  min-width: 0;
  display: grid;
  justify-items: end;
  gap: 8px;
}

.gaming-ai-analysis-verdict {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  padding: 4px 9px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.1);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 850;
  line-height: 1;
  white-space: nowrap;
}

.tone-carry .gaming-ai-analysis-verdict {
  border-color: rgba(245, 194, 87, 0.35);
  background: rgba(245, 194, 87, 0.13);
  color: #e9b84f;
}

.tone-stable .gaming-ai-analysis-verdict {
  border-color: rgba(61, 155, 122, 0.34);
  background: rgba(61, 155, 122, 0.12);
  color: #55d187;
}

.tone-risk .gaming-ai-analysis-verdict,
.tone-weak .gaming-ai-analysis-verdict {
  border-color: rgba(255, 107, 107, 0.34);
  background: rgba(255, 107, 107, 0.12);
  color: #ff7d7d;
}

.gaming-ai-analysis-metrics {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px 10px;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 850;
  line-height: 1;
}

.gaming-ai-analysis-metrics span {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  white-space: nowrap;
}

.gaming-ai-analysis-metrics small {
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 750;
}

.gaming-ai-analysis-bullets {
  gap: 9px;
}

.gaming-ai-analysis-bullets li {
  position: relative;
  padding-left: 14px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.gaming-ai-analysis-bullets li::before {
  content: '';
  position: absolute;
  top: 0.72em;
  left: 0;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(var(--accent-rgb), 0.72);
}

.gaming-ai-analysis-advice {
  padding: 12px 14px;
  border: 1px solid rgba(var(--accent-rgb), 0.16);
  border-radius: 8px;
  background: rgba(var(--accent-rgb), 0.055);
}

.gaming-ai-analysis-advice p {
  margin: 0;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.55;
}

.gaming-ai-analysis-empty {
  padding: 18px;
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.55;
  text-align: center;
}

@media (max-width: 720px) {
  .gaming-ai-analysis-overlay {
    padding: 16px;
  }

  .gaming-ai-analysis-panel {
    width: calc(100vw - 32px);
    max-height: calc(100vh - 32px);
  }

  .gaming-ai-analysis-header,
  .gaming-ai-analysis-body {
    padding-left: 14px;
    padding-right: 14px;
  }

  .gaming-ai-analysis-header {
    align-items: stretch;
    flex-direction: column;
  }

  .gaming-ai-analysis-actions {
    justify-content: flex-start;
  }

  .gaming-ai-analysis-player {
    grid-template-columns: 40px minmax(0, 1fr);
    align-items: start;
    gap: 10px;
  }

  .gaming-ai-analysis-avatar {
    width: 40px;
    height: 40px;
  }

  .gaming-ai-analysis-side {
    grid-column: 2;
    justify-items: start;
  }

  .gaming-ai-analysis-metrics {
    justify-content: flex-start;
  }

  .gaming-ai-analysis-player-title {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>
