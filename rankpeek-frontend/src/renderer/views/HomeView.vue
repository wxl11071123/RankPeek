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
  i: 'Ⅰ',
  ii: 'Ⅱ',
  iii: 'Ⅲ',
  iv: 'Ⅳ',
  '1': 'Ⅰ',
  '2': 'Ⅱ',
  '3': 'Ⅲ',
  '4': 'Ⅳ'
}

const UNRANKED_TIER_VALUES = new Set(['', 'unranked', 'none', 'null', 'undefined', '无', '未设置', '未定级'])
const AUTO_ANALYSIS_STORAGE_PREFIX = 'rankpeek.home.aiCoachAutoAnalysis'
const AI_COACH_NOTICE = 'AI 分析功能即将接入，敬请期待'
const RANK_BADGE_ORBIT_SPEED_PX_PER_SECOND = 3
const RANK_BADGE_ORBIT_INTERVAL_MS = 33

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
    '--rank-border': '#a88462',
    '--rank-border-light': '#6f5136',
    '--rank-hover-border': '#b89571',
    '--rank-hover-border-light': '#8c6b4a',
    '--rank-fill-rgb': '140, 107, 74',
    '--rank-inner-outline': '0 0 0 rgba(0, 0, 0, 0)'
  },
  bronze: {
    '--rank-border': '#bec8d5',
    '--rank-border-light': '#7d8794',
    '--rank-hover-border': '#d1d9e3',
    '--rank-hover-border-light': '#a9b4c2',
    '--rank-fill-rgb': '169, 180, 194',
    '--rank-inner-outline': '0 0 0 rgba(0, 0, 0, 0)'
  },
  silver: {
    '--rank-border': '#c7d0d9',
    '--rank-border-light': '#818b96',
    '--rank-hover-border': '#d7dfe7',
    '--rank-hover-border-light': '#b0b8c1',
    '--rank-fill-rgb': '176, 184, 193',
    '--rank-inner-outline': '0 0 0 rgba(0, 0, 0, 0)'
  },
  gold: {
    '--rank-border': '#d2b862',
    '--rank-border-light': '#967d2d',
    '--rank-hover-border': '#dec778',
    '--rank-hover-border-light': '#c4a747',
    '--rank-fill-rgb': '196, 167, 71',
    '--rank-inner-outline': '0 0 0 rgba(0, 0, 0, 0)'
  },
  platinum: {
    '--rank-border': '#78c7c4',
    '--rank-border-light': '#397b7a',
    '--rank-hover-border': '#8ed4d2',
    '--rank-hover-border-light': '#5da8a6',
    '--rank-fill-rgb': '93, 168, 166',
    '--rank-inner-outline': '0 0 0 rgba(0, 0, 0, 0)'
  },
  emerald: {
    '--rank-border': '#72dc94',
    '--rank-border-light': '#32985c',
    '--rank-hover-border': '#86e6a4',
    '--rank-hover-border-light': '#50c878',
    '--rank-fill-rgb': '80, 200, 120',
    '--rank-inner-outline': '0 0 0 rgba(0, 0, 0, 0)'
  },
  diamond: {
    '--rank-border': '#83aaf1',
    '--rank-border-light': '#3f69bd',
    '--rank-hover-border': '#9bbdf5',
    '--rank-hover-border-light': '#5b8ce9',
    '--rank-fill-rgb': '91, 140, 233',
    '--rank-inner-outline': '0 0 0 rgba(0, 0, 0, 0)'
  },
  master: {
    '--rank-border': '#b5aad7',
    '--rank-border-light': '#75679d',
    '--rank-hover-border': '#c5bbe2',
    '--rank-hover-border-light': '#9b8ec4',
    '--rank-fill-rgb': '155, 142, 196',
    '--rank-inner-outline': '0 0 0 rgba(0, 0, 0, 0)'
  },
  grandmaster: {
    '--rank-border': '#e1c45e',
    '--rank-border-light': '#a98d2f',
    '--rank-hover-border': '#ead275',
    '--rank-hover-border-light': '#c4a747',
    '--rank-fill-rgb': '196, 167, 71',
    '--rank-inner-outline': '0 0 0 rgba(0, 0, 0, 0)'
  },
  challenger: {
    '--rank-border': '#e2c567',
    '--rank-border-light': '#a88722',
    '--rank-hover-border': '#ead27d',
    '--rank-hover-border-light': '#d4af37',
    '--rank-fill-rgb': '212, 175, 55',
    '--rank-inner-outline': 'inset 0 0 0 1px rgba(212, 175, 55, 0.3)'
  },
  unranked: {
    '--rank-border': '#8a94a3',
    '--rank-border-light': '#6b7280',
    '--rank-hover-border': '#a2acbb',
    '--rank-hover-border-light': '#7a8494',
    '--rank-fill-rgb': '122, 132, 148',
    '--rank-inner-outline': '0 0 0 rgba(0, 0, 0, 0)'
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
let rankBadgeOrbitTimer: number | null = null
let rankBadgeOrbitStartedAt = 0

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
  startRankBadgeOrbit()
})

onBeforeUnmount(() => {
  clearFortuneTimer()
  clearCoachNoticeTimer()
  stopRankBadgeOrbit()
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
    return DIVISION_CN_MAP[key.toLowerCase()] || toRomanNumeral(key)
  }

  return ''
}

function toRomanNumeral(value: string): string {
  const numericValue = Number.parseInt(value, 10)
  if (!Number.isFinite(numericValue) || numericValue <= 0) {
    return value
  }

  const romanPairs: Array<[number, string]> = [
    [10, 'Ⅹ'],
    [9, 'Ⅸ'],
    [5, 'Ⅴ'],
    [4, 'Ⅳ'],
    [1, 'Ⅰ']
  ]
  let remainingValue = numericValue
  let result = ''

  for (const [amount, symbol] of romanPairs) {
    while (remainingValue >= amount) {
      result += symbol
      remainingValue -= amount
    }
  }

  return result
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

function startRankBadgeOrbit() {
  if (rankBadgeOrbitTimer !== null) {
    return
  }

  rankBadgeOrbitStartedAt = window.performance.now()
  updateRankBadgeOrbit(rankBadgeOrbitStartedAt)
  rankBadgeOrbitTimer = window.setInterval(() => {
    updateRankBadgeOrbit(window.performance.now())
  }, RANK_BADGE_ORBIT_INTERVAL_MS)
}

function stopRankBadgeOrbit() {
  if (rankBadgeOrbitTimer === null) {
    return
  }

  window.clearInterval(rankBadgeOrbitTimer)
  rankBadgeOrbitTimer = null
}

function updateRankBadgeOrbit(now: number) {
  const badges = Array.from(document.querySelectorAll<HTMLElement>('.rank-badge'))
  const badgeMetrics = badges.map((badge) => {
    const rect = badge.getBoundingClientRect()
    const radius = Math.min(
      parseFloat(window.getComputedStyle(badge).borderTopLeftRadius) || 0,
      rect.width / 2,
      rect.height / 2
    )
    const perimeter = getRoundedRectPerimeter(rect.width, rect.height, radius)
    return { badge, rect, radius, perimeter }
  })
  const maxPerimeter = Math.max(...badgeMetrics.map((metric) => metric.perimeter), 0)

  if (!maxPerimeter) {
    return
  }

  const traveledDistance = ((now - rankBadgeOrbitStartedAt) / 1000) * RANK_BADGE_ORBIT_SPEED_PX_PER_SECOND
  const orbitProgress = (traveledDistance % maxPerimeter) / maxPerimeter

  badgeMetrics.forEach(({ badge, rect, radius, perimeter }) => {
    const badgeDistance = orbitProgress * perimeter
    const point = getRoundedRectPoint(badgeDistance, rect.width, rect.height, radius)
    const oppositePoint = getRoundedRectPoint(
      badgeDistance + perimeter / 2,
      rect.width,
      rect.height,
      radius
    )

    badge.style.setProperty('--rank-orbit-x', `${point.x}px`)
    badge.style.setProperty('--rank-orbit-y', `${point.y}px`)
    badge.style.setProperty('--rank-orbit-secondary-x', `${oppositePoint.x}px`)
    badge.style.setProperty('--rank-orbit-secondary-y', `${oppositePoint.y}px`)
  })
}

function getRoundedRectPerimeter(width: number, height: number, radius: number) {
  const safeWidth = Math.max(0, width)
  const safeHeight = Math.max(0, height)

  if (!safeWidth || !safeHeight) {
    return 0
  }

  if (radius <= 0) {
    return getRectPerimeter(safeWidth, safeHeight)
  }

  const straightWidth = Math.max(0, safeWidth - radius * 2)
  const straightHeight = Math.max(0, safeHeight - radius * 2)
  const arcLength = (Math.PI * radius) / 2
  return straightWidth * 2 + straightHeight * 2 + arcLength * 4
}

function getRoundedRectPoint(distance: number, width: number, height: number, radius: number) {
  const safeWidth = Math.max(0, width)
  const safeHeight = Math.max(0, height)

  if (!safeWidth || !safeHeight) {
    return { x: 0, y: 0 }
  }

  if (radius <= 0) {
    return getRectPoint(distance, safeWidth, safeHeight)
  }

  const straightWidth = Math.max(0, safeWidth - radius * 2)
  const straightHeight = Math.max(0, safeHeight - radius * 2)
  const arcLength = (Math.PI * radius) / 2
  const perimeter = straightWidth * 2 + straightHeight * 2 + arcLength * 4
  let current = distance % perimeter

  if (current <= straightWidth) {
    return { x: radius + current, y: 0 }
  }
  current -= straightWidth

  if (current <= arcLength) {
    const angle = -Math.PI / 2 + current / radius
    return {
      x: safeWidth - radius + Math.cos(angle) * radius,
      y: radius + Math.sin(angle) * radius
    }
  }
  current -= arcLength

  if (current <= straightHeight) {
    return { x: safeWidth, y: radius + current }
  }
  current -= straightHeight

  if (current <= arcLength) {
    const angle = current / radius
    return {
      x: safeWidth - radius + Math.cos(angle) * radius,
      y: safeHeight - radius + Math.sin(angle) * radius
    }
  }
  current -= arcLength

  if (current <= straightWidth) {
    return { x: safeWidth - radius - current, y: safeHeight }
  }
  current -= straightWidth

  if (current <= arcLength) {
    const angle = Math.PI / 2 + current / radius
    return {
      x: radius + Math.cos(angle) * radius,
      y: safeHeight - radius + Math.sin(angle) * radius
    }
  }
  current -= arcLength

  if (current <= straightHeight) {
    return { x: 0, y: safeHeight - radius - current }
  }
  current -= straightHeight

  const angle = Math.PI + current / radius
  return {
    x: radius + Math.cos(angle) * radius,
    y: radius + Math.sin(angle) * radius
  }
}

function getRectPoint(distance: number, width: number, height: number) {
  const perimeter = getRectPerimeter(width, height)
  let current = distance % perimeter

  if (current <= width) {
    return { x: current, y: 0 }
  }
  current -= width

  if (current <= height) {
    return { x: width, y: current }
  }
  current -= height

  if (current <= width) {
    return { x: width - current, y: height }
  }
  current -= width

  return { x: 0, y: height - current }
}

function getRectPerimeter(width: number, height: number) {
  return width * 2 + height * 2
}

function formatRankTierPart(rank: QueueInfo | null): string {
  if (!rank || isUnrankedTier(rank.tier)) {
    return '未定级'
  }

  return formatTierCn(rank)
}

function formatRankDivisionPart(rank: QueueInfo | null): string {
  if (!rank || isUnrankedTier(rank.tier)) {
    return ''
  }

  return formatDivision(rank)
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
              <span class="rank-orbit-dot" aria-hidden="true"></span>
              <span class="rank-orbit-dot secondary" aria-hidden="true"></span>
              <span class="rank-emblem" aria-hidden="true"></span>
              <span class="rank-label">
                <span class="rank-queue">{{ t('home.soloQueue') }}：</span>
                <span class="rank-tier">{{ formatRankTierPart(soloRank) }}</span>
                <span v-if="formatRankDivisionPart(soloRank)" class="rank-division">{{ formatRankDivisionPart(soloRank) }}</span>
              </span>
            </span>
            <span class="rank-badge" :style="rankBadgeStyle(flexRank)">
              <span class="rank-orbit-dot" aria-hidden="true"></span>
              <span class="rank-orbit-dot secondary" aria-hidden="true"></span>
              <span class="rank-emblem" aria-hidden="true"></span>
              <span class="rank-label">
                <span class="rank-queue">{{ t('home.flexQueue') }}：</span>
                <span class="rank-tier">{{ formatRankTierPart(flexRank) }}</span>
                <span v-if="formatRankDivisionPart(flexRank)" class="rank-division">{{ formatRankDivisionPart(flexRank) }}</span>
              </span>
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
  --rank-text: #e0e0e0;
  --switch-track-off: rgba(255, 255, 255, 0.14);
  --switch-track-on: var(--accent-color);
  --switch-track-border: rgba(255, 255, 255, 0.06);
  --switch-thumb-color: #d7d9de;
  --switch-thumb-active: #ffffff;
  --switch-thumb-shadow: 0 1px 3px rgba(0, 0, 0, 0.28);
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
  --rank-border: #8a94a3;
  --rank-border-light: #6b7280;
  --rank-hover-border: #a2acbb;
  --rank-hover-border-light: #7a8494;
  --rank-fill-rgb: 122, 132, 148;
  --rank-inner-outline: 0 0 0 rgba(0, 0, 0, 0);
  --rank-comet: var(--rank-border);
  --rank-orbit-x: 0px;
  --rank-orbit-y: 0px;
  --rank-orbit-secondary-x: 0px;
  --rank-orbit-secondary-y: 0px;
  position: relative;
  isolation: isolate;
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 10px;
  border: 1px solid var(--rank-border);
  border-radius: 8px;
  background: rgba(var(--rank-fill-rgb), 0.12);
  color: var(--rank-text);
  box-shadow:
    inset -1px -1px 2px rgba(0, 0, 0, 0.15),
    var(--rank-inner-outline);
  overflow: visible;
  text-shadow: none;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.rank-orbit-dot {
  position: absolute;
  top: var(--rank-orbit-y);
  left: var(--rank-orbit-x);
  z-index: 3;
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background:
    radial-gradient(circle at center, rgba(255, 255, 255, 0.92) 0 18%, var(--rank-comet) 36%, rgba(212, 175, 55, 0) 72%);
  box-shadow:
    0 0 6px var(--rank-comet),
    0 0 12px rgba(var(--rank-fill-rgb), 0.38);
  opacity: 0.74;
  pointer-events: none;
  transform: translate(-50%, -50%);
  animation: rank-orbit-breathe 1.8s ease-in-out infinite;
}

.rank-orbit-dot.secondary {
  top: var(--rank-orbit-secondary-y);
  left: var(--rank-orbit-secondary-x);
}

.rank-badge:hover {
  border-color: var(--rank-hover-border);
  background: rgba(var(--rank-fill-rgb), 0.16);
  box-shadow:
    inset -1px -1px 2px rgba(0, 0, 0, 0.15),
    var(--rank-inner-outline),
    0 2px 4px rgba(0, 0, 0, 0.12);
}

.rank-emblem {
  position: relative;
  z-index: 1;
  width: 9px;
  height: 9px;
  flex: 0 0 auto;
  transform: rotate(45deg);
  border: 1px solid var(--rank-border);
  border-radius: 2px;
  background: rgba(var(--rank-fill-rgb), 0.22);
  box-shadow: inset -1px -1px 1px rgba(0, 0, 0, 0.16);
}

.rank-emblem::after {
  content: '';
  position: absolute;
  inset: 2px;
  border-radius: 1px;
  background: var(--rank-border);
  opacity: 0.72;
}

.rank-label {
  position: relative;
  z-index: 1;
  white-space: nowrap;
}

.rank-queue {
  font-weight: 700;
}

.rank-tier {
  font-weight: 500;
  letter-spacing: 0.5px;
}

.rank-division {
  margin-left: 2px;
  font-family: 'Times New Roman', serif;
  font-weight: 400;
  color: inherit;
  opacity: 1;
}

.coach-report-grid {
  --fortune-column-width: 286px;
  --coach-grid-gap: 16px;
  --coach-report-height: 318px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) var(--fortune-column-width);
  align-items: start;
  gap: var(--coach-grid-gap);
  overflow: visible;
}

.coach-report-panel {
  min-width: 0;
  min-height: var(--coach-report-height);
  height: var(--coach-report-height);
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
  min-height: var(--coach-report-height);
  height: var(--coach-report-height);
  box-sizing: border-box;
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
  border: 1px solid var(--switch-track-border);
  border-radius: 999px;
  background: var(--switch-track-off);
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.18);
  transition: background 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.switch-thumb {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: var(--switch-thumb-color);
  box-shadow: var(--switch-thumb-shadow);
  transition: transform 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.auto-analysis-switch.active {
  border-color: rgba(var(--accent-rgb), 0.72);
}

.auto-analysis-switch.active .switch-track {
  background: var(--switch-track-on);
}

.auto-analysis-switch.active .switch-thumb {
  transform: translateX(24px);
  background: var(--switch-thumb-active);
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
  --coach-slide-bg: linear-gradient(135deg, rgba(20, 22, 28, 0.88), rgba(12, 14, 18, 0.78));
  --coach-slide-tile-bg: rgba(255, 255, 255, 0.055);
  --coach-slide-tile-border: rgba(212, 175, 55, 0.24);
  --coach-slide-muted: rgba(232, 221, 186, 0.68);
}

.coach-report-panel :deep(.ai-coach-cards) {
  min-height: var(--coach-report-height);
  height: var(--coach-report-height);
  padding-top: 0;
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
  top: 0;
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
  position: fixed;
  inset: 62px 24px 24px 276px;
  z-index: 9999;
  width: auto;
  padding: 0;
  display: flex;
  align-items: stretch;
  background: rgba(6, 7, 10, 0.34);
  backdrop-filter: blur(10px);
  transition: opacity 0.35s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.coach-report-panel :deep(.coach-expanded-card) {
  width: 100%;
  min-height: 0;
  height: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(280px, 0.92fr);
  grid-template-rows: auto minmax(0, 1fr) minmax(128px, auto);
  align-content: stretch;
  gap: 22px;
  padding: 40px 48px;
  border: 1px solid rgba(212, 175, 55, 0.38);
  border-radius: 24px;
  background: var(--coach-slide-bg);
  backdrop-filter: blur(20px);
  box-shadow:
    0 0 0 1px rgba(212, 175, 55, 0.12),
    0 0 44px rgba(212, 175, 55, 0.2);
  overflow: hidden;
  transform-origin: center;
  transition:
    transform 0.35s cubic-bezier(0.22, 0.61, 0.36, 1),
    opacity 0.35s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.coach-report-panel :deep(.coach-expanded-card h3) {
  grid-column: 1 / -1;
  margin: 0;
  padding-bottom: 20px;
  border-bottom: 1px solid rgba(212, 175, 55, 0.4);
  color: var(--coach-title-color);
  font-size: 0;
  line-height: 1.35;
}

.coach-report-panel :deep(.coach-expanded-card h3)::before {
  content: 'AI 电子教练 · 综合报告';
  display: block;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', SimSun, PMingLiU, 'Times New Roman', serif;
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.coach-report-panel :deep(.coach-expanded-card p),
.coach-report-panel :deep(.coach-expanded-card strong),
.coach-report-panel :deep(.coach-expanded-card)::after {
  min-height: 0;
  margin: 0;
  border: 1px solid var(--coach-slide-tile-border);
  border-radius: 18px;
  background: var(--coach-slide-tile-bg);
  color: var(--coach-body-color);
  box-shadow: inset -1px -1px 2px rgba(0, 0, 0, 0.1);
}

.coach-report-panel :deep(.coach-expanded-card p) {
  grid-column: 1 / 2;
  grid-row: 2 / 3;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  font-size: 0;
}

.coach-report-panel :deep(.coach-expanded-card p)::before {
  content: '数据总览';
  color: var(--coach-title-color);
  font-size: 1.25rem;
  font-weight: 700;
}

.coach-report-panel :deep(.coach-expanded-card p)::after {
  content: '近期KDA趋势\A 英雄池分布\A 高光时刻';
  flex: 1;
  display: grid;
  align-content: center;
  gap: 14px;
  padding: 22px;
  border-radius: 14px;
  background:
    linear-gradient(135deg, rgba(212, 175, 55, 0.12), transparent),
    rgba(255, 255, 255, 0.045);
  color: var(--coach-slide-muted);
  font-size: 1rem;
  line-height: 2.6;
  white-space: pre-line;
}

.coach-report-panel :deep(.coach-expanded-card strong) {
  grid-column: 2 / 3;
  grid-row: 2 / 3;
  display: flex;
  align-items: stretch;
  padding: 24px;
  font-size: 0;
}

.coach-report-panel :deep(.coach-expanded-card strong)::before {
  content: '智能建议\A\A 对线策略建议\A 团战定位建议\A 资源交换建议';
  width: 100%;
  color: var(--coach-body-color);
  font-size: 1rem;
  font-weight: 600;
  line-height: 2.05;
  white-space: pre-line;
}

.coach-report-panel :deep(.coach-expanded-card)::after {
  content: '赛后复盘\A 详细复盘报告即将上线';
  grid-column: 1 / -1;
  grid-row: 3 / 4;
  display: flex;
  align-items: center;
  padding: 24px 28px;
  color: var(--coach-slide-muted);
  font-size: 1.02rem;
  font-weight: 600;
  line-height: 1.8;
  white-space: pre-line;
}

.coach-report-panel :deep(.coach-close) {
  top: 22px;
  right: 24px;
  z-index: 2;
  border-color: rgba(212, 175, 55, 0.46);
  background: rgba(255, 255, 255, 0.08);
  color: rgba(238, 205, 112, 0.96);
}

.coach-report-panel :deep(.coach-expand-enter-active),
.coach-report-panel :deep(.coach-expand-leave-active) {
  transition: opacity 0.35s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.coach-report-panel :deep(.coach-expand-enter-active .coach-expanded-card),
.coach-report-panel :deep(.coach-expand-leave-active .coach-expanded-card) {
  transition:
    transform 0.35s cubic-bezier(0.22, 0.61, 0.36, 1),
    opacity 0.35s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.coach-report-panel :deep(.coach-expand-enter-from .coach-expanded-card),
.coach-report-panel :deep(.coach-expand-leave-to .coach-expanded-card) {
  opacity: 0;
  transform: scale(0.95);
}

:global([data-theme="light"] .coach-report-panel) {
  --coach-title-color: #2f2918;
  --coach-body-color: #4b4638;
  --coach-placeholder-color: #6f5b19;
  --coach-slide-bg: linear-gradient(135deg, rgba(245, 245, 250, 0.9), rgba(242, 236, 222, 0.82));
  --coach-slide-tile-bg: rgba(255, 255, 255, 0.52);
  --coach-slide-tile-border: rgba(180, 180, 190, 0.5);
  --coach-slide-muted: #62583e;
}

:global([data-theme="light"] .rank-badge) {
  --rank-comet: var(--rank-border-light);
  border-color: var(--rank-border-light);
  color: var(--rank-text);
}

:global([data-theme="light"] .rank-queue),
:global([data-theme="light"] .rank-tier) {
  color: #2c2c2c;
}

:global([data-theme="light"] .rank-division) {
  color: #2c2c2c;
  opacity: 1;
}

:global([data-theme="light"] .rank-badge:hover) {
  border-color: var(--rank-hover-border-light);
}

:global([data-theme="light"] .rank-emblem) {
  border-color: var(--rank-border-light);
}

:global([data-theme="light"] .rank-emblem::after) {
  background: var(--rank-border-light);
}

:global([data-theme="light"] .coach-report-panel .coach-stack-card),
:global([data-theme="light"] .coach-report-panel .coach-expanded-card) {
  border-color: rgba(180, 180, 190, 0.5);
  background: linear-gradient(135deg, rgba(238, 240, 246, 0.82), rgba(244, 241, 232, 0.74));
  color: #2c2c2c;
  backdrop-filter: blur(12px);
  box-shadow:
    inset -1px -1px 2px rgba(0, 0, 0, 0.08),
    0 0 14px rgba(212, 175, 55, 0.08);
}

:global([data-theme="light"] .coach-report-panel .coach-stack-card::after) {
  border-color: rgba(212, 175, 55, 0.18);
  box-shadow: 0 0 12px rgba(212, 175, 55, 0.08);
}

:global([data-theme="light"] .coach-report-panel .coach-stack-card:hover),
:global([data-theme="light"] .coach-report-panel .coach-stack-card:focus-visible) {
  border-color: rgba(100, 116, 139, 0.38);
  box-shadow:
    inset -1px -1px 2px rgba(0, 0, 0, 0.08),
    0 2px 8px rgba(100, 116, 139, 0.16);
}

:global([data-theme="light"] .coach-report-panel .coach-stack-card:hover::after),
:global([data-theme="light"] .coach-report-panel .coach-stack-card:focus-visible::after) {
  border-color: rgba(212, 175, 55, 0.24);
  box-shadow: 0 0 12px rgba(212, 175, 55, 0.1);
}

:global([data-theme="light"] .coach-report-panel .coach-expanded-layer) {
  background: rgba(248, 248, 250, 0.38);
}

:global([data-theme="light"] .coach-report-panel .coach-expanded-card) {
  background: var(--coach-slide-bg);
  backdrop-filter: blur(20px);
  box-shadow:
    0 0 0 1px rgba(180, 180, 190, 0.24),
    0 10px 34px rgba(100, 116, 139, 0.18);
}

:global([data-theme="light"] .coach-report-panel .coach-expanded-card p),
:global([data-theme="light"] .coach-report-panel .coach-expanded-card strong),
:global([data-theme="light"] .coach-report-panel .coach-expanded-card::after) {
  background: var(--coach-slide-tile-bg);
  border-color: var(--coach-slide-tile-border);
}

:global([data-theme="light"] .home-view) {
  --home-theme-glow: 0 0 0 1px rgba(100, 116, 139, 0.18), 0 0 16px rgba(100, 116, 139, 0.18);
  --home-theme-glow-strong: 0 0 0 1px rgba(100, 116, 139, 0.24), 0 0 20px rgba(100, 116, 139, 0.25);
  --home-glow-border: rgba(100, 116, 139, 0.28);
  --rank-text: #2c2c2c;
  --switch-track-off: #d0d0d6;
  --switch-track-on: #a3c9a3;
  --switch-track-border: rgba(100, 116, 139, 0.28);
  --switch-thumb-color: #f8f8fa;
  --switch-thumb-active: #ffffff;
  --switch-thumb-shadow: 0 1px 3px rgba(15, 23, 42, 0.24);
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
  min-height: 0;
  height: calc(100% - 34px);
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

@keyframes rank-orbit-breathe {
  0%,
  100% {
    opacity: 0.58;
    filter: brightness(0.96);
    box-shadow:
      0 0 5px var(--rank-comet),
      0 0 10px rgba(var(--rank-fill-rgb), 0.3);
  }

  50% {
    opacity: 0.92;
    filter: brightness(1.18);
    box-shadow:
      0 0 8px var(--rank-comet),
      0 0 16px rgba(var(--rank-fill-rgb), 0.5);
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
    width: auto;
  }
}

@media (max-width: 760px) {
  .coach-report-panel :deep(.coach-expanded-layer) {
    inset: 52px 14px 14px 110px;
  }

  .coach-report-panel :deep(.coach-expanded-card) {
    grid-template-columns: 1fr;
    grid-template-rows: auto minmax(160px, 1fr) minmax(160px, 1fr) auto;
    padding: 30px 28px;
  }

  .coach-report-panel :deep(.coach-expanded-card strong) {
    grid-column: 1 / -1;
    grid-row: 3 / 4;
  }

  .coach-report-panel :deep(.coach-expanded-card)::after {
    grid-row: 4 / 5;
  }
}
</style>
