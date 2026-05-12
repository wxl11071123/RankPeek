<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import type {
  GamingAiAnalysisMode,
  GamingAiAnalysisPreview,
  GamingAiPlayerInsight
} from '@/services/gamingAiAnalysisPreview'
import { getChampionIconUrl, getProfileIconUrl, markAssetLoadFailed } from '@/utils/gameAssetUrls'

const props = withDefaults(defineProps<{
  open: boolean
  mode?: GamingAiAnalysisMode
  preview: GamingAiAnalysisPreview | null
  serverSyncState?: 'idle' | 'syncing' | 'synced' | 'failed'
  serverSyncMessage?: string
}>(), {
  mode: 'teammate',
  serverSyncState: 'idle',
  serverSyncMessage: ''
})

const emit = defineEmits<{
  (event: 'close'): void
}>()

const fallbackTitle = computed(() => props.mode === 'teammate' ? '队友成分分析' : '赛前对手分析')
const fallbackSubtitle = computed(() => '等待对局 · 未知模式')
const sectionTitle = computed(() => props.mode === 'teammate' ? '队友逐个分析' : '对手威胁列表')
const bulletTitle = computed(() => props.mode === 'teammate' ? '本局队友风险摘要' : '突破点')

const serverSyncText = computed(() => {
  if (props.serverSyncMessage.trim()) {
    return props.serverSyncMessage.trim()
  }

  if (props.serverSyncState === 'syncing') {
    return '正在整理并发送临时数据...'
  }
  if (props.serverSyncState === 'synced') {
    return '临时数据已发送到本地服务器 mock。'
  }
  if (props.serverSyncState === 'failed') {
    return '服务器暂不可用，当前展示本地规则预览。'
  }
  return '本地规则预览，不是正式 AI 结果。'
})

function emitClose() {
  emit('close')
}

function handleKeydown(event: KeyboardEvent) {
  if (!props.open) {
    return
  }

  if (event.key === 'Escape') {
    emitClose()
  }
}

function playerAvatarUrl(player: GamingAiPlayerInsight): string {
  if (player.championId && player.championId > 0) {
    return getChampionIconUrl(player.championId)
  }
  if (player.profileIconId && player.profileIconId > 0) {
    return getProfileIconUrl(player.profileIconId)
  }
  return ''
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
            <p class="gaming-ai-analysis-eyebrow">AI 占位 / 本地规则预览</p>
            <template v-if="preview">
              <h2 id="gaming-ai-analysis-title">{{ preview.title }}</h2>
              <span>{{ preview.subtitle }}</span>
            </template>
            <template v-else>
              <h2 id="gaming-ai-analysis-title">{{ fallbackTitle }}</h2>
              <span>{{ fallbackSubtitle }}</span>
            </template>
            <p
              class="gaming-ai-analysis-sync"
              :class="`sync-${serverSyncState}`"
            >
              {{ serverSyncText }}
            </p>
          </div>
          <button
            class="gaming-ai-analysis-close"
            type="button"
            aria-label="关闭分析弹窗"
            @click="emitClose"
          >
            ×
          </button>
        </header>

        <div class="gaming-ai-analysis-body">
          <p class="gaming-ai-analysis-opening">
            {{ preview?.opening || '当前还没有可用玩家数据，请进入英雄选择或加载阶段后再试。' }}
          </p>

          <section v-if="preview && preview.players.length" class="gaming-ai-analysis-section">
            <h3>{{ sectionTitle }}</h3>
            <ul class="gaming-ai-analysis-player-list">
              <li
                v-for="player in preview.players"
                :key="player.key"
                class="gaming-ai-analysis-player"
                :class="`tone-${player.tone}`"
              >
                <div class="gaming-ai-analysis-avatar">
                  <img
                    v-if="playerAvatarUrl(player)"
                    :src="playerAvatarUrl(player)"
                    :alt="player.name"
                    @error="markAssetLoadFailed"
                  />
                  <span v-else>{{ player.name.slice(0, 1) }}</span>
                </div>

                <div class="gaming-ai-analysis-copy">
                  <div class="gaming-ai-analysis-player-title">
                    <strong>{{ player.name }}</strong>
                    <span>{{ player.rankText }}</span>
                  </div>
                  <p>{{ player.reason }}</p>
                </div>

                <div class="gaming-ai-analysis-side">
                  <span class="gaming-ai-analysis-verdict">{{ player.verdict }}</span>
                  <div class="gaming-ai-analysis-metrics" aria-label="关键数据">
                    <span><small>KDA</small>{{ player.kdaText }}</span>
                    <span><small>胜率</small>{{ player.winRateText }}</span>
                    <span><small>伤转率</small>{{ player.damageRateText }}</span>
                    <span><small>样本</small>{{ player.sampleText }}</span>
                  </div>
                </div>
              </li>
            </ul>
          </section>

          <section v-else class="gaming-ai-analysis-empty">
            当前还没有可用玩家数据，请进入英雄选择或加载阶段后再试。
          </section>

          <section v-if="preview?.bullets.length" class="gaming-ai-analysis-section">
            <h3>{{ bulletTitle }}</h3>
            <ul class="gaming-ai-analysis-bullets">
              <li v-for="bullet in preview.bullets" :key="bullet">{{ bullet }}</li>
            </ul>
          </section>

          <section v-if="preview?.laneAdvice" class="gaming-ai-analysis-section gaming-ai-analysis-advice">
            <h3>前期建议</h3>
            <p>{{ preview.laneAdvice }}</p>
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

.gaming-ai-analysis-sync {
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
}

.gaming-ai-analysis-sync.sync-syncing {
  color: rgba(var(--accent-rgb), 0.86);
}

.gaming-ai-analysis-sync.sync-synced {
  color: #55d187;
}

.gaming-ai-analysis-sync.sync-failed {
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
