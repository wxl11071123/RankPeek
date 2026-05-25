<script setup lang="ts">
import { computed } from 'vue'
import CoachSummaryChartBlock from '@/components/CoachSummaryChartBlock.vue'
import { getChampionIconUrl, markAssetLoadFailed } from '@/utils/gameAssetUrls'
import type {
  CoachSummaryKeyFinding,
  CoachSummaryHeroStat,
  CoachSummaryReportV1
} from '@/types/coachSummaryReport'

const MAX_REPORT_CHARTS = 3
const COACH_SUMMARY_HERO_ICON_FALLBACK_IDS = buildHeroIconFallbackIds([
  [43, ['Karma', '卡尔玛']],
  [59, ['Jarvan IV', '嘉文四世']],
  [76, ['Nidalee', '奈德丽']],
  [89, ['Leona', '蕾欧娜']],
  [102, ['Shyvana', '希瓦娜']],
  [103, ['Ahri', '阿狸']],
  [117, ['Lulu', '璐璐']],
  [133, ['Quinn', '奎因']],
  [141, ['Kayn', '凯隐']],
  [200, ["Bel'Veth", '卑尔维斯']],
  [233, ['Briar', '贝蕾亚']],
  [234, ['Viego', '佛耶戈']],
  [350, ['Yuumi', '悠米']],
  [517, ['Sylas', '塞拉斯']],
  [888, ['Renata Glasc', '烈娜塔·戈拉斯克']],
  [897, ["K'Sante", '奎桑提']],
  [950, ['Naafiri', '纳亚菲利']]
])

type ReportLoadState = 'loading' | 'ready' | 'missing' | 'unsupported' | 'invalid' | 'error'

const props = withDefaults(defineProps<{
  report: CoachSummaryReportV1 | null
  reportLoadState?: ReportLoadState
  errorMessage?: string
  createdAt?: string | null
  mode?: 'modal' | 'page'
}>(), {
  reportLoadState: 'ready',
  errorMessage: '',
  createdAt: null,
  mode: 'page'
})

const overview = computed(() => props.report?.overview ?? null)
const overviewSummary = computed(() => overview.value?.summary || props.report?.summary || '')
const heroStats = computed(() => (overview.value?.heroStats || []).slice(0, 5))
const heroWinRateStats = computed(() => (
  heroStats.value
    .filter(hero => typeof hero.winRate === 'number')
    .slice(0, 5)
))
const winLossLabel = computed(() => {
  const wins = overview.value?.wins
  const losses = overview.value?.losses
  if (wins !== undefined && losses !== undefined) {
    return `${wins}W / ${losses}L`
  }
  const totalMatches = overview.value?.totalMatches
  return totalMatches ? `${totalMatches} 场` : '暂无样本'
})
const roleSummary = computed(() => {
  const roleStats = overview.value?.roleStats || []
  const primaryRoles = overview.value?.primaryRoles || []
  const roles = roleStats.length
    ? roleStats.map(role => ({ role: role.role, count: role.games }))
    : primaryRoles.map(role => ({ role: role.role, count: role.count }))
  return roles.length
    ? roles.map(role => `${formatRoleLabel(role.role)} ${role.count}`).join(' / ')
    : '暂无'
})
const chartBlocks = computed(() => props.report?.chartBlocks || [])
const overviewCharts = computed(() => (
  heroWinRateStats.value.length
    ? []
    : chartBlocks.value
      .filter(chart => chart.placement === 'overview')
      .slice(0, Math.min(2, MAX_REPORT_CHARTS))
))
const findings = computed(() => (props.report?.keyFindings || []).slice(0, 2))
const closingSummary = computed(() =>
  props.report?.finalSummary ||
  props.report?.verdict.summary ||
  props.report?.summary ||
  ''
)
const createdAtLabel = computed(() =>
  props.createdAt ? new Date(props.createdAt).toLocaleDateString('zh-CN') : ''
)

function formatPercent(value?: number): string {
  if (value === undefined) {
    return '-'
  }
  return `${Number.isInteger(value) ? value : Number(value.toFixed(1))}%`
}

function formatRoleLabel(role: string): string {
  const normalized = role.trim().toUpperCase()
  const labels: Record<string, string> = {
    TOP: '上路',
    JUNGLE: '打野',
    MID: '中路',
    MIDDLE: '中路',
    ADC: '下路',
    BOTTOM: '下路',
    SUPPORT: '辅助',
    UTILITY: '辅助',
    UNKNOWN: '未知'
  }
  return labels[normalized] || role || '未知'
}

function clampPercent(value?: number): number {
  if (value === undefined) {
    return 0
  }
  return Math.max(0, Math.min(100, value))
}

function heroIcon(hero: CoachSummaryHeroStat): string {
  return getChampionIconUrl(heroIconChampionId(hero))
}

function heroIconChampionId(hero: CoachSummaryHeroStat): number | null {
  return hero.championId ?? readHeroIconFallbackId(hero)
}

function formatHeroChartLabel(hero: CoachSummaryHeroStat): string {
  return `${hero.championDisplayName}，${hero.games} 场，胜率 ${formatPercent(hero.winRate)}`
}

function heroDetailLine(hero: CoachSummaryHeroStat): string {
  return [
    formatRoleLabel(hero.role),
    `${hero.games} 场`,
    heroRecordText(hero),
    heroAverageKdaText(hero)
  ]
    .filter(Boolean)
    .join(' · ')
}

function heroRecordText(hero: CoachSummaryHeroStat): string {
  if (hero.wins !== undefined && hero.losses !== undefined) {
    return `${hero.wins}胜${hero.losses}负`
  }
  return ''
}

function heroAverageKdaText(hero: CoachSummaryHeroStat): string {
  if (hero.averageKda !== undefined) {
    return `KDA ${formatCompactNumber(hero.averageKda)}`
  }
  return hero.kda ? `KDA ${hero.kda}` : ''
}

function formatCompactNumber(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}

function findingText(finding: CoachSummaryKeyFinding): string {
  return [
    finding.claim,
    finding.evidence,
    finding.reasoning,
    finding.advice
  ]
    .filter(Boolean)
    .join(' ')
}

function readHeroIconFallbackId(hero: CoachSummaryHeroStat): number | null {
  for (const name of [hero.championCanonicalName, hero.championDisplayName]) {
    const fallbackId = COACH_SUMMARY_HERO_ICON_FALLBACK_IDS.get(normalizeHeroIconLookupKey(name))
    if (fallbackId !== undefined) {
      return fallbackId
    }
  }
  return null
}

function buildHeroIconFallbackIds(entries: Array<[number, string[]]>): Map<string, number> {
  const map = new Map<string, number>()
  for (const [championId, names] of entries) {
    for (const name of names) {
      map.set(normalizeHeroIconLookupKey(name), championId)
    }
  }
  return map
}

function normalizeHeroIconLookupKey(value: string | null | undefined): string {
  return (value || '').trim().toLowerCase().replace(/\s+/g, ' ')
}
</script>

<template>
  <div class="coach-report-content" :class="`content-mode-${mode}`">
    <div v-if="reportLoadState === 'loading'" class="state-card">正在读取报告...</div>

    <div v-else-if="reportLoadState !== 'ready'" class="state-card warning">
      <h2>{{ reportLoadState === 'unsupported' ? '暂不支持该报告类型' : '报告内容暂时无法解析' }}</h2>
      <p>{{ errorMessage || '这份记录不是 coach_summary_report.v1，或报告 JSON 不完整。' }}</p>
    </div>

    <template v-else-if="report">
      <section class="report-section overview-section">
        <div class="section-heading">
          <h2>近 20 局概览</h2>
          <span>{{ createdAtLabel }}</span>
        </div>

        <p class="section-summary ai-report-prose">{{ overviewSummary }}</p>

        <div class="overview-layout">
          <div class="overview-text-column">
            <div class="overview-facts" aria-label="概览关键数据">
              <div class="overview-fact-row overview-fact-row-split" aria-label="胜率">
                <strong class="fact-main">{{ formatPercent(overview?.winRate) }}</strong>
                <span class="fact-sub">{{ winLossLabel }}</span>
              </div>

              <div class="overview-fact-row" aria-label="游玩位置">
                <strong class="fact-main">{{ roleSummary }}</strong>
              </div>
            </div>

            <div v-if="heroStats.length" class="overview-hero-list" aria-label="最近游玩英雄">
              <ul class="hero-chip-list">
                <li v-for="hero in heroStats" :key="`${hero.championDisplayName}-${hero.role}`" class="hero-chip">
                  <div class="hero-avatar">
                    <img
                      v-if="heroIcon(hero)"
                      :src="heroIcon(hero)"
                      :alt="hero.championDisplayName"
                      @error="markAssetLoadFailed"
                    />
                    <span v-else>{{ hero.championDisplayName.slice(0, 1) }}</span>
                  </div>
                  <div class="hero-main">
                    <h3>{{ hero.championDisplayName }}</h3>
                    <p>{{ heroDetailLine(hero) }}</p>
                  </div>
                  <div class="hero-metrics">
                    <span>{{ formatPercent(hero.winRate) }}</span>
                  </div>
                </li>
              </ul>
            </div>
          </div>

          <figure v-if="heroWinRateStats.length" class="hero-win-rate-figure" aria-label="主玩英雄胜率">
            <div class="hero-win-rate-chart" role="img" aria-label="主玩英雄胜率柱状图">
              <div class="hero-win-rate-y-axis" aria-hidden="true">
                <span>100%</span>
                <span>75%</span>
                <span>50%</span>
                <span>25%</span>
                <span>0%</span>
              </div>
              <div class="hero-win-rate-plot">
                <div class="hero-win-rate-grid" aria-hidden="true">
                  <span></span>
                  <span></span>
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
                <div class="hero-win-rate-bars">
                  <div
                    v-for="hero in heroWinRateStats"
                    :key="`chart-${hero.championDisplayName}-${hero.role}`"
                    class="hero-win-rate-column"
                    :style="{ '--hero-win-rate': `${clampPercent(hero.winRate)}%` }"
                    tabindex="0"
                    :aria-label="formatHeroChartLabel(hero)"
                    :title="formatHeroChartLabel(hero)"
                  >
                    <div class="hero-win-bar-plot">
                      <div class="hero-win-bar-fill"></div>
                      <span class="hero-win-tooltip">
                        <strong>{{ formatPercent(hero.winRate) }}</strong>
                        <em>{{ hero.games }} 场</em>
                      </span>
                    </div>
                    <div class="hero-win-rate-x-axis">
                      <div class="hero-win-rate-avatar" :title="hero.championDisplayName">
                        <img
                          v-if="heroIcon(hero)"
                          :src="heroIcon(hero)"
                          :alt="hero.championDisplayName"
                          @error="markAssetLoadFailed"
                        />
                        <span v-else>{{ hero.championDisplayName.slice(0, 1) }}</span>
                      </div>
                      <span class="hero-win-rate-games">{{ hero.games }} 场</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </figure>
        </div>

        <div v-if="overviewCharts.length" class="chart-grid">
          <CoachSummaryChartBlock
            v-for="chart in overviewCharts"
            :key="chart.id"
            :chart="chart"
          />
        </div>
      </section>

      <div class="paper-divider" aria-hidden="true"></div>

      <section class="report-section analysis-section">
        <div class="section-heading">
          <h2>数据分析</h2>
        </div>

        <ol class="finding-list report-list">
          <li v-for="finding in findings" :key="finding.id" class="finding-item">
            <p class="finding-copy ai-report-prose">{{ findingText(finding) }}</p>
          </li>
        </ol>
      </section>

      <div class="paper-divider" aria-hidden="true"></div>

      <section class="report-section summary-section">
        <div class="section-heading">
          <h2>数据总结</h2>
        </div>

        <p class="final-summary ai-report-prose">{{ closingSummary }}</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.coach-report-content {
  min-width: 0;
}

.state-card {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(circle at 10% 0%, rgba(var(--accent-rgb), 0.065), transparent 34%),
    var(--bg-secondary);
  box-shadow:
    0 14px 32px rgba(0, 0, 0, 0.16),
    0 0 0 1px rgba(var(--accent-rgb), 0.025);
  padding: 22px;
  color: var(--text-secondary);
}

.state-card.warning {
  border-color: rgba(255, 159, 10, 0.22);
}

.state-card h2 {
  margin: 0 0 6px;
  color: var(--text-primary);
  font-size: 17px;
}

.state-card p {
  margin: 0;
  color: var(--text-secondary);
}

.report-section {
  padding: 8px 2px 4px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.section-heading h2 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: 0;
}

.section-heading span {
  color: var(--text-tertiary);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.5;
}

.section-summary,
.final-summary {
  margin: 0;
  color: var(--text-secondary);
  font-size: 24px;
  line-height: 1.65;
}

.section-summary {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.paper-divider {
  position: relative;
  height: 18px;
  margin: 28px 0 24px;
}

.paper-divider::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(148, 163, 184, 0.08) 16%,
    rgba(148, 163, 184, 0.22) 38%,
    rgba(148, 163, 184, 0.34) 50%,
    rgba(148, 163, 184, 0.22) 62%,
    rgba(148, 163, 184, 0.08) 84%,
    transparent 100%
  );
}

.paper-divider::after {
  content: '';
  position: absolute;
  left: 50%;
  top: calc(50% - 1px);
  width: min(220px, 28%);
  height: 2px;
  transform: translateX(-50%);
  border-radius: 999px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(var(--accent-rgb), 0.12) 18%,
    rgba(var(--accent-rgb), 0.42) 50%,
    rgba(var(--accent-rgb), 0.12) 82%,
    transparent 100%
  );
}

.overview-layout {
  --overview-bottom-gap: 8px;
  display: grid;
  grid-template-columns: minmax(260px, 0.85fr) minmax(340px, 1.15fr);
  gap: 18px;
  align-items: stretch;
  margin-top: 16px;
  padding-bottom: var(--overview-bottom-gap);
}

.overview-text-column {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: var(--overview-bottom-gap);
}

.overview-facts {
  display: grid;
  gap: 10px;
  margin: 0;
}

.overview-fact-row {
  min-width: 0;
  min-height: 54px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  padding: 8px 0 8px 12px;
  border-left: 2px solid rgba(var(--accent-rgb), 0.22);
}

.overview-fact-row-split {
  justify-content: space-between;
}

.fact-main {
  display: block;
  color: var(--text-primary);
  font-size: 23px;
  font-weight: 800;
  line-height: 1.25;
}

.fact-sub,
.hero-main p,
.hero-metrics span {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.fact-sub {
  color: var(--text-tertiary);
  font-size: 13px;
  font-weight: 700;
  text-align: right;
  white-space: nowrap;
}

.overview-hero-list {
  min-width: 0;
  margin-top: auto;
}

.hero-chip-list,
.finding-list,
.chart-grid {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.hero-chip-list {
  grid-template-columns: 1fr;
  padding: 0;
  list-style: none;
}

.hero-chip {
  min-width: 0;
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 8px 9px;
  border-radius: 8px;
  background: rgba(var(--accent-rgb), 0.045);
}

.hero-avatar {
  width: 40px;
  height: 40px;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(var(--accent-rgb), 0.2);
  border-radius: 10px;
  background: rgba(var(--accent-rgb), 0.1);
  color: var(--text-primary);
  font-weight: 800;
}

.hero-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-main {
  min-width: 0;
}

.hero-main h3 {
  display: block;
  margin: 0;
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 750;
  line-height: 1.35;
}

.hero-main p {
  margin: 3px 0 0;
  font-size: 13px;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero-metrics {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
}

.hero-metrics span {
  font-size: 13px;
  line-height: 1.2;
}

.hero-metrics span:first-child {
  color: var(--accent-color);
  font-weight: 800;
}

.hero-win-rate-figure {
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  margin: 0;
  padding-left: 18px;
  padding-bottom: var(--overview-bottom-gap);
  border-left: 1px solid var(--border-subtle);
}

.hero-win-rate-chart {
  flex: 1;
  height: 100%;
  min-height: 240px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 10px;
}

.hero-win-rate-y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 0 0 52px;
  color: var(--text-tertiary);
  font-size: 11px;
  font-weight: 750;
  text-align: right;
}

.hero-win-rate-plot {
  position: relative;
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: grid;
}

.hero-win-rate-grid {
  position: absolute;
  inset: 0 0 52px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
}

.hero-win-rate-grid span {
  height: 1px;
  background: var(--border-subtle);
}

.hero-win-rate-bars {
  position: relative;
  z-index: 1;
  min-width: 0;
  height: 100%;
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(52px, 1fr);
  gap: 10px;
  align-items: end;
}

.hero-win-rate-column {
  min-width: 0;
  height: 100%;
  display: grid;
  grid-template-rows: minmax(0, 1fr) 48px;
  align-items: end;
  justify-items: center;
  gap: 6px;
  outline: none;
}

.hero-win-rate-column:focus-visible .hero-win-rate-avatar {
  border-color: rgba(var(--accent-rgb), 0.62);
  box-shadow: 0 0 0 3px rgba(var(--accent-rgb), 0.16);
}

.hero-win-bar-plot {
  width: min(42px, 70%);
  height: 100%;
  position: relative;
  overflow: visible;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  border-radius: 0;
}

.hero-win-bar-fill {
  width: 100%;
  height: var(--hero-win-rate);
  min-height: 4px;
  border-radius: 0;
  background: #4d9dff;
}

.hero-win-tooltip {
  position: absolute;
  left: 50%;
  bottom: min(calc(var(--hero-win-rate) + 8px), calc(100% - 22px));
  transform: translateX(-50%);
  min-width: 58px;
  display: grid;
  gap: 3px;
  opacity: 0;
  visibility: hidden;
  color: var(--text-primary);
  text-align: center;
  pointer-events: none;
  transition: opacity 0.14s ease, visibility 0.14s ease;
}

.hero-win-tooltip strong {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 850;
  line-height: 1;
}

.hero-win-tooltip em {
  color: var(--text-tertiary);
  font-size: 11px;
  font-style: normal;
  font-weight: 750;
  line-height: 1;
}

.hero-win-rate-column:hover .hero-win-tooltip,
.hero-win-rate-column:focus .hero-win-tooltip,
.hero-win-rate-column:focus-within .hero-win-tooltip {
  opacity: 1;
  visibility: visible;
}

.hero-win-rate-x-axis {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.hero-win-rate-avatar {
  width: 34px;
  height: 34px;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(212, 175, 55, 0.32);
  border-radius: 50%;
  background: rgba(var(--accent-rgb), 0.12);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 850;
}

.hero-win-rate-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-win-rate-games {
  color: var(--text-tertiary);
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
}

.chart-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.report-list {
  grid-template-columns: 1fr;
  padding: 0;
  list-style: none;
}

.finding-item {
  position: relative;
  min-width: 0;
  padding: 0 0 13px;
  border-bottom: 1px solid var(--border-subtle);
}

.finding-item p {
  margin: 0;
}

.finding-copy {
  color: var(--text-primary);
  font-size: 24px;
  font-weight: 650;
  line-height: 1.65;
}

.final-summary {
  margin-top: 0;
  color: var(--text-primary);
  font-weight: 650;
}

.ai-report-prose {
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", "SimSun", serif;
  font-weight: 500;
  line-height: 1.65;
}

.finding-item strong.ai-report-prose,
.final-summary.ai-report-prose {
  font-weight: 600;
}

@media (max-width: 860px) {
  .overview-layout,
  .hero-chip-list,
  .chart-grid {
    grid-template-columns: 1fr;
  }

  .hero-win-rate-figure {
    padding-left: 0;
    border-left: 0;
  }
}

@media (max-width: 620px) {
  .report-section {
    padding: 6px 0;
  }

  .overview-fact-row {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .hero-chip {
    grid-template-columns: 40px minmax(0, 1fr);
  }

  .hero-metrics {
    grid-column: 2;
    align-items: flex-start;
    flex-direction: row;
  }
}
</style>
