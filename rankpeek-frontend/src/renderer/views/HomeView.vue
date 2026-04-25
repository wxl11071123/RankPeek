<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AICoachCards from '@/components/AICoachCards.vue'
import HomeChart from '@/components/HomeChart.vue'
import { useGameStore } from '@/stores/game'
import {
  FORTUNE_POOL,
  drawDailyFortune,
  getCurrentFortune,
  loadFortuneRecord,
  saveFortuneRecord
} from '@/utils/homeInsights'
import { t } from '@/i18n'
import type { QueueInfo } from '@/types/api'
import type { Fortune, FortuneRecord } from '@/utils/homeInsights'

const gameStore = useGameStore()

const TIER_CN_MAP: Record<string, string> = {
  iron: '黑铁',
  bronze: '青铜',
  silver: '白银',
  gold: '黄金',
  platinum: '铂金',
  emerald: '翡翠',
  diamond: '钻石',
  master: '大师',
  grandmaster: '宗师',
  challenger: '王者',
  坚韧黑铁: '黑铁',
  黑铁: '黑铁',
  英勇黄铜: '青铜',
  青铜: '青铜',
  不屈白银: '白银',
  白银: '白银',
  荣耀黄金: '黄金',
  黄金: '黄金',
  华贵铂金: '铂金',
  铂金: '铂金',
  流光翡翠: '翡翠',
  翡翠: '翡翠',
  璀璨钻石: '钻石',
  钻石: '钻石',
  超凡大师: '大师',
  大师: '大师',
  傲世宗师: '宗师',
  宗师: '宗师',
  最强王者: '王者',
  王者: '王者'
}

const DIVISION_CN_MAP: Record<string, string> = {
  i: '1',
  ii: '2',
  iii: '3',
  iv: '4',
  '1': '1',
  '2': '2',
  '3': '3',
  '4': '4'
}

const UNRANKED_TIER_VALUES = new Set(['', 'unranked', 'none', 'null', 'undefined', '无', '未设置', '未定级'])
const AUTO_ANALYSIS_STORAGE_PREFIX = 'rankpeek.home.aiCoachAutoAnalysis'
const AI_COACH_NOTICE = 'AI 分析功能即将接入，敬请期待'

const RANK_TONE_MAP: Record<string, string> = {
  iron: 'iron',
  黑铁: 'iron',
  bronze: 'bronze',
  青铜: 'bronze',
  silver: 'silver',
  白银: 'silver',
  gold: 'gold',
  黄金: 'gold',
  platinum: 'platinum',
  铂金: 'platinum',
  emerald: 'emerald',
  翡翠: 'emerald',
  diamond: 'diamond',
  钻石: 'diamond',
  master: 'master',
  大师: 'master',
  grandmaster: 'grandmaster',
  宗师: 'grandmaster',
  challenger: 'challenger',
  王者: 'challenger'
}

const RANK_BADGE_STYLES: Record<string, Record<string, string>> = {
  iron: {
    '--rank-start': '#2f353c',
    '--rank-end': '#7d8794',
    '--rank-text': '#f3f5f8'
  },
  bronze: {
    '--rank-start': '#5f412a',
    '--rank-end': '#b88959',
    '--rank-text': '#fff3e2'
  },
  silver: {
    '--rank-start': '#7c8798',
    '--rank-end': '#d4dce8',
    '--rank-text': '#111827'
  },
  gold: {
    '--rank-start': '#8c6815',
    '--rank-end': '#d4af37',
    '--rank-text': '#fff7d1'
  },
  platinum: {
    '--rank-start': '#2f7f81',
    '--rank-end': '#88d7d2',
    '--rank-text': '#ecfffb'
  },
  emerald: {
    '--rank-start': '#14784b',
    '--rank-end': '#50c878',
    '--rank-text': '#effff5'
  },
  diamond: {
    '--rank-start': '#355fd2',
    '--rank-end': '#8ac5ff',
    '--rank-text': '#f2f7ff'
  },
  master: {
    '--rank-start': '#6d3bd8',
    '--rank-end': '#d4af37',
    '--rank-text': '#fff7ef'
  },
  grandmaster: {
    '--rank-start': '#9b253f',
    '--rank-end': '#d4af37',
    '--rank-text': '#fff4ef'
  },
  challenger: {
    '--rank-start': '#176fc2',
    '--rank-end': '#f2d572',
    '--rank-text': '#ffffff'
  },
  unranked: {
    '--rank-start': '#3f4652',
    '--rank-end': '#7a8494',
    '--rank-text': '#f6f7fb'
  }
}

interface AutoAnalysisSettings {
  enabled: boolean
}

const autoAnalysis = ref<AutoAnalysisSettings>({ enabled: false })
const coachNotice = ref('')

const fortuneRecord = ref<FortuneRecord>({ history: [] })
const currentFortune = ref<Fortune | null>(null)
const fortuneRolling = ref(false)
const rollingFortuneLabel = ref('？？？')
let fortuneTimer: number | null = null
let coachNoticeTimer: number | null = null

const currentSummoner = computed(() => gameStore.currentSummoner)
const accountKey = computed(() => currentSummoner.value?.puuid || 'local')
const accountConnected = computed(() => gameStore.connected && Boolean(currentSummoner.value))
const soloRank = computed(() => gameStore.soloRank)
const flexRank = computed(() => gameStore.flexRank)
const displayName = computed(() => gameStore.summonerName || t('common.summoner'))
const profileIconUrl = computed(() =>
  currentSummoner.value?.profileIconId
    ? `http://127.0.0.1:8080/api/v1/asset/profile/${currentSummoner.value.profileIconId}`
    : ''
)

const fortuneLabel = computed(() => currentFortune.value?.label || '？？？')
const fortuneTone = computed(() => currentFortune.value?.tone || 'neutral')
const fortuneButtonText = computed(() => {
  if (fortuneRolling.value) {
    return t('home.fortuneDrawing')
  }
  return currentFortune.value ? t('home.fortuneComeTomorrow') : t('home.drawFortune')
})

onMounted(() => {
  void gameStore.checkConnection()
  loadLocalHomeState()
})

onBeforeUnmount(() => {
  clearFortuneTimer()
  clearCoachNoticeTimer()
})

watch(accountKey, () => {
  loadLocalHomeState()
})

function loadLocalHomeState() {
  const key = accountKey.value
  autoAnalysis.value = loadAutoAnalysisSettings(key)
  fortuneRecord.value = loadFortuneRecord(key)
  currentFortune.value = getCurrentFortune(fortuneRecord.value)
  coachNotice.value = ''
}

function runAnalysis() {
  showCoachNotice()
}

function toggleAutoAnalysis() {
  autoAnalysis.value = {
    enabled: !autoAnalysis.value.enabled
  }
  saveAutoAnalysisSettings(accountKey.value, autoAnalysis.value)
  showCoachNotice()
}

function drawFortune() {
  if (currentFortune.value || fortuneRolling.value) {
    return
  }

  fortuneRolling.value = true
  clearFortuneTimer()
  fortuneTimer = window.setInterval(() => {
    const index = Math.floor(Math.random() * FORTUNE_POOL.length) % FORTUNE_POOL.length
    rollingFortuneLabel.value = FORTUNE_POOL[index].label
  }, 72)

  window.setTimeout(() => {
    clearFortuneTimer()
    const result = drawDailyFortune(fortuneRecord.value)
    fortuneRecord.value = result.record
    currentFortune.value = result.fortune
    rollingFortuneLabel.value = result.fortune.label
    fortuneRolling.value = false
    saveFortuneRecord(accountKey.value, fortuneRecord.value)
  }, 1180)
}

function clearFortuneTimer() {
  if (fortuneTimer) {
    window.clearInterval(fortuneTimer)
    fortuneTimer = null
  }
}

function showCoachNotice() {
  coachNotice.value = AI_COACH_NOTICE
  clearCoachNoticeTimer()
  coachNoticeTimer = window.setTimeout(() => {
    coachNotice.value = ''
    coachNoticeTimer = null
  }, 2600)
}

function clearCoachNoticeTimer() {
  if (coachNoticeTimer) {
    window.clearTimeout(coachNoticeTimer)
    coachNoticeTimer = null
  }
}

function loadAutoAnalysisSettings(key: string): AutoAnalysisSettings {
  try {
    const rawValue = localStorage.getItem(`${AUTO_ANALYSIS_STORAGE_PREFIX}.${key}`)
    if (!rawValue) {
      return { enabled: false }
    }
    const parsedValue = JSON.parse(rawValue) as Partial<AutoAnalysisSettings>
    return { enabled: Boolean(parsedValue.enabled) }
  } catch {
    return { enabled: false }
  }
}

function saveAutoAnalysisSettings(key: string, settings: AutoAnalysisSettings) {
  localStorage.setItem(`${AUTO_ANALYSIS_STORAGE_PREFIX}.${key}`, JSON.stringify(settings))
}

function formatRank(rank: QueueInfo | null): string {
  if (!rank || isUnrankedTier(rank.tier)) {
    return '未定级'
  }

  const tier = formatTierCn(rank)
  const division = formatDivision(rank)
  return division ? `${tier} ${division}` : tier
}

function isUnrankedTier(tier?: string): boolean {
  return UNRANKED_TIER_VALUES.has((tier || '').trim().toLowerCase())
}

function formatTierCn(rank: QueueInfo): string {
  const displayTier = rank.displayRank?.trim().split(/\s+/)[0]
  const candidates = [rank.tier, rank.tierCn, displayTier]
  let fallback = ''

  for (const candidate of candidates) {
    const key = candidate?.trim()
    if (!key) {
      continue
    }
    const mappedTier = TIER_CN_MAP[key.toLowerCase()] || TIER_CN_MAP[key]
    if (mappedTier) {
      return mappedTier
    }
    if (!isUnrankedTier(key)) {
      fallback ||= key
    }
  }

  return fallback || '未定级'
}

function formatDivision(rank: QueueInfo): string {
  const displayDivision = rank.displayRank?.trim().split(/\s+/)[1]
  const candidates = [rank.division, displayDivision]

  for (const candidate of candidates) {
    const key = candidate?.trim()
    if (!key) {
      continue
    }
    return DIVISION_CN_MAP[key.toLowerCase()] || key
  }

  return ''
}

function rankTone(rank: QueueInfo | null): string {
  if (!rank || isUnrankedTier(rank.tier)) {
    return 'unranked'
  }

  const displayTier = rank.displayRank?.trim().split(/\s+/)[0]
  const candidates = [rank.tier, rank.tierCn, displayTier]

  for (const candidate of candidates) {
    const key = candidate?.trim()
    if (!key) {
      continue
    }
    const normalizedKey = key.toLowerCase()
    const mappedTier = TIER_CN_MAP[normalizedKey] || TIER_CN_MAP[key] || key
    const tone = RANK_TONE_MAP[normalizedKey] || RANK_TONE_MAP[mappedTier]
    if (tone) {
      return tone
    }
  }

  return 'unranked'
}

function rankBadgeStyle(rank: QueueInfo | null): Record<string, string> {
  return RANK_BADGE_STYLES[rankTone(rank)] || RANK_BADGE_STYLES.unranked
}

</script>

<template>
  <div class="home-view">
    <section v-if="accountConnected && currentSummoner" class="account-panel">
      <div class="account-identity">
        <img class="account-avatar" :src="profileIconUrl" alt="" />
        <div class="account-main">
          <div class="summoner-heading">
            <h2>{{ displayName }}</h2>
            <span class="connection-pill connected">{{ t('home.clientConnected') }}</span>
          </div>
          <div class="rank-row">
            <span class="rank-badge" :style="rankBadgeStyle(soloRank)">
              <span class="rank-emblem" aria-hidden="true"></span>
              <span class="rank-label">{{ t('home.soloQueue') }}：{{ formatRank(soloRank) }}</span>
            </span>
            <span class="rank-badge" :style="rankBadgeStyle(flexRank)">
              <span class="rank-emblem" aria-hidden="true"></span>
              <span class="rank-label">{{ t('home.flexQueue') }}：{{ formatRank(flexRank) }}</span>
            </span>
          </div>
        </div>
      </div>
      <button class="secondary-btn" type="button" @click="gameStore.refreshSummoner">
        {{ t('home.refreshAccount') }}
      </button>
    </section>

    <section v-else class="account-panel disconnected-panel">
      <div class="account-identity">
        <div class="disconnected-mark">!</div>
        <div class="account-main">
          <div class="account-kicker">
            <span class="connection-pill">{{ t('common.disconnected') }}</span>
          </div>
          <h2>{{ t('home.noClientTitle') }}</h2>
          <p>{{ t('home.noClientBody') }}</p>
        </div>
      </div>
      <button class="primary-btn" type="button" @click="gameStore.checkConnection">
        {{ t('common.refreshConnection') }}
      </button>
    </section>

    <section class="ai-analysis-card">
      <div class="card-copy">
        <h2>电子教练</h2>
        <p>{{ t('home.aiAnalysisBody') }}</p>
      </div>

      <div class="action-row">
        <button class="primary-btn" type="button" @click="runAnalysis">
          {{ t('home.analyzeNow') }}
        </button>
        <button
          class="auto-analysis-switch"
          type="button"
          role="switch"
          :aria-checked="autoAnalysis.enabled"
          :class="{ active: autoAnalysis.enabled }"
          :disabled="!accountConnected"
          @click="toggleAutoAnalysis"
        >
          <span class="switch-track">
            <span class="switch-thumb"></span>
          </span>
          <span class="switch-label">自动分析</span>
        </button>
      </div>

      <p v-if="coachNotice" class="coach-notice">{{ coachNotice }}</p>
    </section>

    <section class="coach-report-grid">
      <div class="coach-report-panel">
        <AICoachCards />
      </div>
      <article class="fortune-card" :class="fortuneTone">
        <div class="panel-eyebrow fortune-eyebrow">抽个签</div>
        <div class="fortune-layout">
          <div class="slot-reel" :class="{ rolling: fortuneRolling }">
            {{ fortuneRolling ? rollingFortuneLabel : fortuneLabel }}
          </div>
          <p class="fortune-text">
            {{ currentFortune?.text || t('home.fortuneIdle') }}
          </p>
          <button
            class="fortune-button"
            type="button"
            :disabled="Boolean(currentFortune) || fortuneRolling"
            @click="drawFortune"
          >
            {{ fortuneButtonText }}
          </button>
          <p class="fortune-disclaimer">
            <span v-if="currentFortune">{{ t('home.fortuneOnceDaily') }}</span>
            {{ t('home.fortuneDisclaimer') }}
          </p>
        </div>
      </article>
    </section>

    <HomeChart :puuid="currentSummoner?.puuid" :connected="accountConnected" />
  </div>
</template>

<style scoped>
.home-view {
  --home-theme-glow: 0 0 0 1px rgba(212, 175, 55, 0.28), 0 0 18px rgba(212, 175, 55, 0.24);
  --home-theme-glow-strong: 0 0 0 1px rgba(212, 175, 55, 0.42), 0 0 24px rgba(212, 175, 55, 0.34);
  --home-glow-border: rgba(212, 175, 55, 0.42);
  max-width: 1180px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.account-panel,
.ai-analysis-card,
.fortune-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  transition: border-color 0.3s ease, box-shadow 0.3s ease;
}

.account-panel:hover,
.ai-analysis-card:hover,
.fortune-card:hover {
  border-color: var(--home-glow-border);
  box-shadow: var(--home-theme-glow);
}

.account-main p,
.card-copy p,
.coach-notice,
.fortune-text,
.fortune-disclaimer {
  color: var(--text-secondary);
}

.connection-pill {
  flex-shrink: 0;
  padding: 8px 12px;
  border-radius: var(--radius-pill);
  background: var(--error-bg);
  color: var(--error-color);
  font-size: 13px;
  font-weight: 700;
}

.connection-pill.connected {
  background: var(--success-bg);
  color: var(--success-color);
}

.account-panel {
  min-height: 122px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  padding: 22px;
}

.account-identity {
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 18px;
}

.account-avatar,
.disconnected-mark {
  width: 88px;
  height: 88px;
  border-radius: 20px;
  background: var(--bg-tertiary);
}

.account-avatar {
  object-fit: cover;
}

.disconnected-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--warning-color);
  border: 1px solid var(--warning-color);
  font-size: 42px;
  font-weight: 900;
}

.panel-eyebrow {
  color: var(--text-tertiary);
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 6px;
}

.account-main {
  min-width: 0;
}

.summoner-heading {
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 10px;
  margin-bottom: 8px;
  white-space: nowrap;
}

.account-kicker {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 8px;
}

.account-kicker .panel-eyebrow {
  margin-bottom: 0;
}

.account-main h2,
.card-copy h2,
.fortune-card h2 {
  color: var(--text-primary);
  margin: 0;
}

.account-main h2 {
  font-size: 30px;
  line-height: 1.15;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summoner-heading h2 {
  min-width: 0;
}

.rank-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.rank-badge {
  --rank-start: #3f4652;
  --rank-end: #7a8494;
  --rank-text: #f6f7fb;
  position: relative;
  isolation: isolate;
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  border: 1px solid rgba(255, 235, 159, 0.46);
  border-radius: 999px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--rank-start) 92%, #000 8%), var(--rank-end)),
    var(--bg-tertiary);
  color: var(--rank-text);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.2),
    inset 0 -10px 18px rgba(0, 0, 0, 0.18),
    0 0 10px rgba(212, 175, 55, 0.7),
    0 0 20px rgba(212, 175, 55, 0.3);
  overflow: hidden;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.38);
  font-size: 14px;
  font-weight: 900;
  letter-spacing: 0;
}

.rank-badge::before {
  content: '';
  position: absolute;
  inset: -2px;
  border-radius: inherit;
  background: conic-gradient(from 0deg, transparent, rgba(255, 232, 149, 0.78), transparent 36%, transparent);
  opacity: 0.58;
  animation: rank-ring-spin 8s linear infinite;
  pointer-events: none;
  z-index: 0;
}

.rank-badge::after {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: inherit;
  background: linear-gradient(115deg, rgba(255, 255, 255, 0.2), transparent 30%, rgba(255, 255, 255, 0.08));
  pointer-events: none;
  z-index: 0;
}

.rank-emblem {
  position: relative;
  z-index: 1;
  width: 14px;
  height: 14px;
  flex: 0 0 auto;
  transform: rotate(45deg);
  border: 1px solid rgba(255, 244, 187, 0.84);
  border-radius: 3px;
  background: linear-gradient(135deg, rgba(255, 252, 211, 0.96), rgba(212, 175, 55, 0.72));
  box-shadow: 0 0 10px rgba(255, 226, 128, 0.52);
}

.rank-emblem::after {
  content: '';
  position: absolute;
  inset: 3px;
  border-radius: 2px;
  background: var(--rank-start);
}

.rank-label {
  position: relative;
  z-index: 1;
  white-space: nowrap;
}

.coach-report-grid {
  --fortune-column-width: 286px;
  --coach-grid-gap: 16px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) var(--fortune-column-width);
  gap: var(--coach-grid-gap);
  overflow: visible;
}

.coach-report-panel {
  min-width: 0;
  position: relative;
  z-index: 1;
  overflow: visible;
}

.ai-analysis-card,
.fortune-card {
  padding: 22px;
}

.ai-analysis-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
  overflow: visible;
}

.fortune-card {
  min-height: 250px;
}

.card-copy h2 {
  font-size: 26px;
  margin-bottom: 8px;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.primary-btn,
.secondary-btn,
.fortune-button {
  min-height: 46px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 800;
}

.primary-btn,
.fortune-button {
  padding: 0 18px;
  background: var(--accent-color);
  color: #ffffff;
  transition: box-shadow 0.3s ease, filter 0.3s ease;
}

.primary-btn:hover:not(:disabled),
.fortune-button:hover:not(:disabled),
.fortune-card:hover .fortune-button:not(:disabled) {
  box-shadow: var(--home-theme-glow-strong);
  filter: brightness(1.04);
}

.secondary-btn {
  padding: 0 14px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  transition: border-color 0.3s ease, box-shadow 0.3s ease;
}

.secondary-btn:hover:not(:disabled) {
  border-color: var(--home-glow-border);
  box-shadow: var(--home-theme-glow);
}

.secondary-btn.active {
  border-color: rgba(var(--accent-rgb), 0.7);
  color: var(--accent-hover);
}

.auto-analysis-switch {
  min-height: 46px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 800;
  transition: border-color 0.3s ease, box-shadow 0.3s ease, background 0.3s ease;
}

.auto-analysis-switch:hover:not(:disabled) {
  border-color: var(--home-glow-border);
  box-shadow: var(--home-theme-glow);
}

.switch-track {
  width: 54px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  padding: 3px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  transition: background 0.18s ease;
}

.switch-thumb {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: var(--text-secondary);
  transition: transform 0.18s ease, background 0.18s ease;
}

.auto-analysis-switch.active {
  border-color: rgba(var(--accent-rgb), 0.72);
}

.auto-analysis-switch.active .switch-track {
  background: var(--accent-color);
}

.auto-analysis-switch.active .switch-thumb {
  transform: translateX(24px);
  background: #ffffff;
}

.auto-analysis-switch:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}

.primary-btn:disabled,
.secondary-btn:disabled,
.fortune-button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}

.coach-notice {
  min-height: 20px;
  margin: 0;
  color: rgba(212, 175, 55, 0.96);
  font-size: 14px;
  font-weight: 800;
}

.coach-report-panel {
  --coach-title-color: rgba(238, 205, 112, 0.96);
  --coach-body-color: var(--text-secondary);
  --coach-placeholder-color: rgba(238, 205, 112, 0.9);
}

.coach-report-panel :deep(.ai-coach-cards) {
  min-height: max(298px, var(--coach-stack-height, 298px));
  overflow: visible;
}

.coach-report-panel :deep(.coach-card-dots) {
  left: 0;
  right: 0;
  justify-content: center;
}

.coach-report-panel :deep(.coach-stack-card),
.coach-report-panel :deep(.coach-expanded-card) {
  font-family: 'Noto Serif SC', 'Source Han Serif SC', SimSun, PMingLiU, 'Times New Roman', serif;
  letter-spacing: 0.5px;
  line-height: 1.7;
}

.coach-report-panel :deep(.coach-stack-card) {
  transition:
    transform 0.4s ease,
    opacity 0.4s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.coach-report-panel :deep(.coach-stack-card:hover),
.coach-report-panel :deep(.coach-stack-card:focus-visible) {
  border-color: rgba(212, 175, 55, 0.62);
  box-shadow: 0 0 34px rgba(212, 175, 55, 0.28);
  transform: var(--card-transform);
  animation: none;
}

.coach-report-panel :deep(.coach-stack-card::after),
.coach-report-panel :deep(.coach-stack-card:hover::after),
.coach-report-panel :deep(.coach-stack-card:focus-visible::after) {
  animation: none;
}

.coach-report-panel :deep(.coach-stack-card:hover::after),
.coach-report-panel :deep(.coach-stack-card:focus-visible::after) {
  border-color: rgba(247, 217, 122, 0.62);
  box-shadow: 0 0 34px rgba(212, 175, 55, 0.3);
  opacity: 1;
}

.coach-report-panel :deep(.coach-card-title),
.coach-report-panel :deep(.coach-expanded-card h3) {
  color: var(--coach-title-color);
  font-size: 1.25rem;
  line-height: 1.7;
}

.coach-report-panel :deep(.coach-card-body),
.coach-report-panel :deep(.coach-expanded-card p) {
  color: var(--coach-body-color);
  font-size: 0.95rem;
  line-height: 1.7;
}

.coach-report-panel :deep(.coach-expanded-layer) {
  width: calc(100% + var(--fortune-column-width) + var(--coach-grid-gap));
  right: auto;
  z-index: 999;
  transition: opacity 0.3s ease-in-out;
}

.coach-report-panel :deep(.coach-expanded-card) {
  min-height: 270px;
  transition:
    width 0.3s ease-in-out,
    transform 0.3s ease-in-out,
    opacity 0.3s ease-in-out;
}

:global([data-theme="light"]) .coach-report-panel {
  --coach-title-color: #2f2918;
  --coach-body-color: #4b4638;
  --coach-placeholder-color: #6f5b19;
}

:global([data-theme="light"]) .home-view {
  --home-theme-glow: 0 0 0 1px rgba(171, 125, 23, 0.24), 0 0 16px rgba(171, 125, 23, 0.18);
  --home-theme-glow-strong: 0 0 0 1px rgba(171, 125, 23, 0.34), 0 0 22px rgba(171, 125, 23, 0.26);
  --home-glow-border: rgba(171, 125, 23, 0.34);
}

.fortune-card {
  text-align: left;
}

.fortune-eyebrow {
  color: var(--text-primary);
  font-size: 26px;
  font-weight: bold;
}

.fortune-layout {
  display: flex;
  min-height: 198px;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 14px;
}

.slot-reel {
  min-width: 142px;
  min-height: 70px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 18px;
  border-radius: 16px;
  background: rgba(196, 220, 255, 0.94);
  color: #0b64d8;
  font-size: 34px;
  line-height: 1;
  font-weight: 900;
  letter-spacing: 0;
}

.slot-reel.rolling {
  animation: slot-pop 0.16s linear infinite;
}

.fortune-card.bad .slot-reel {
  color: #8a1f17;
  background: rgba(255, 214, 204, 0.96);
}

.fortune-card.good .slot-reel {
  color: #085f2d;
  background: rgba(199, 245, 212, 0.96);
}

.fortune-text {
  margin: 0;
  min-height: 42px;
  font-size: 15px;
}

.fortune-button {
  min-width: 172px;
}

.fortune-disclaimer {
  margin: 0;
  font-size: 13px;
}

@keyframes slot-pop {
  0% {
    transform: translateY(-2px);
  }
  100% {
    transform: translateY(2px);
  }
}

@keyframes rank-ring-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 920px) {
  .coach-report-grid,
  .account-panel {
    grid-template-columns: 1fr;
  }

  .coach-report-grid {
    --fortune-column-width: 0px;
  }

  .account-panel {
    align-items: flex-start;
  }

  .summoner-heading h2 {
    font-size: 24px;
  }

  .account-avatar,
  .disconnected-mark {
    width: 76px;
    height: 76px;
  }

  .action-row {
    justify-content: flex-start;
  }

  .coach-report-panel :deep(.coach-expanded-layer) {
    width: 100%;
  }
}
</style>
