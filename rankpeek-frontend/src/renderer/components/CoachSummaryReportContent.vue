<script setup lang="ts">
import { computed } from 'vue'
import CoachSummaryChartBlock from '@/components/CoachSummaryChartBlock.vue'
import { getChampionIconUrl, markAssetLoadFailed } from '@/utils/gameAssetUrls'
import type {
  CoachSummaryHeroStat,
  CoachSummaryReportV1
} from '@/types/coachSummaryReport'

const MAX_REPORT_CHARTS = 3

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
const findings = computed(() => (props.report?.keyFindings || []).slice(0, 5))
const championAdvice = computed(() => (props.report?.championAdvice || []).slice(0, 3))
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

function formatNumber(value?: number): string {
  if (value === undefined) {
    return '-'
  }
  return Number.isInteger(value) ? String(value) : String(Number(value.toFixed(2)))
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
  return getChampionIconUrl(hero.championId)
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
                    <p>{{ formatRoleLabel(hero.role) }} · {{ hero.games }} 场</p>
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
                    :aria-label="`${hero.championDisplayName} ${formatPercent(hero.winRate)}`"
                  >
                    <div class="hero-win-bar-plot">
                      <div class="hero-win-bar-fill"></div>
                      <span class="hero-win-tooltip">{{ formatPercent(hero.winRate) }}</span>
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
          <span>{{ findings.length }} 条重点</span>
        </div>

        <ol class="finding-list report-list">
          <li v-for="finding in findings" :key="finding.id" class="finding-item">
            <strong class="ai-report-prose">{{ finding.claim }}</strong>
            <p class="ai-report-prose">{{ finding.advice || finding.reasoning || finding.evidence }}</p>
          </li>
        </ol>

        <div v-if="championAdvice.length" class="advice-list">
          <section v-for="advice in championAdvice" :key="`${advice.championName}-${advice.role}`" class="advice-item">
            <span>{{ advice.role }}</span>
            <strong>{{ advice.championName }}</strong>
            <p class="ai-report-prose">{{ advice.reason }}</p>
          </section>
        </div>

      </section>

      <div class="paper-divider" aria-hidden="true"></div>

      <section class="report-section summary-section">
        <div class="section-heading">
          <h2>数据总结</h2>
          <span>{{ report.verdict.label }}</span>
        </div>

        <blockquote class="verdict-paper">
          <strong>{{ report.verdict.label }}</strong>
          <span>{{ formatNumber(report.verdict.score) }}</span>
          <p class="ai-report-prose">{{ report.verdict.summary }}</p>
        </blockquote>

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
  font-size: 21px;
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
  font-size: 14px;
  line-height: 1.6;
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
  gap: 22px;
  align-items: stretch;
  margin-top: 16px;
  padding-bottom: var(--overview-bottom-gap);
}

.overview-text-column {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: var(--overview-bottom-gap);
}

.overview-facts {
  display: grid;
  gap: 12px;
  margin: 0;
}

.overview-fact-row {
  min-width: 0;
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  padding: 10px 0 10px 12px;
  border-left: 2px solid rgba(var(--accent-rgb), 0.22);
}

.overview-fact-row-split {
  justify-content: space-between;
}

.fact-main {
  display: block;
  color: var(--text-primary);
  font-size: 21px;
  font-weight: 800;
  line-height: 1.25;
}

.fact-sub,
.hero-main p,
.hero-metrics span,
.finding-item p,
.advice-item p,
.advice-item span,
.verdict-paper p {
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
.advice-list,
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
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 7px 8px;
  border-radius: 8px;
  background: rgba(var(--accent-rgb), 0.045);
}

.hero-avatar {
  width: 38px;
  height: 38px;
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

.hero-main h3,
.finding-item strong,
.advice-item strong,
.verdict-paper strong {
  display: block;
  margin: 0;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 750;
  line-height: 1.35;
}

.hero-main p {
  margin: 3px 0 0;
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
  min-height: 260px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 10px;
}

.hero-win-rate-y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 0 0 42px;
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
  inset: 0 0 42px;
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
  grid-template-rows: minmax(0, 1fr) 36px;
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
  opacity: 0;
  visibility: hidden;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 850;
  line-height: 1;
  pointer-events: none;
  transition: opacity 0.14s ease, visibility 0.14s ease;
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
  align-items: center;
  justify-content: center;
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

.chart-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.report-list {
  grid-template-columns: 1fr;
  padding: 0;
  list-style: none;
  counter-reset: report-item;
}

.finding-item {
  position: relative;
  min-width: 0;
  padding: 0 0 13px 40px;
  border-bottom: 1px solid var(--border-subtle);
  counter-increment: report-item;
}

.finding-item::before {
  content: counter(report-item, decimal-leading-zero);
  position: absolute;
  top: 0;
  left: 0;
  color: rgba(var(--accent-rgb), 0.86);
  font-size: 12px;
  font-weight: 850;
  line-height: 1.35;
}

.finding-item p,
.advice-item p {
  margin: 6px 0 0;
}

.advice-list {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.advice-item {
  padding-top: 10px;
}

.advice-item {
  border-top: 1px solid var(--border-subtle);
}

.advice-item span {
  display: block;
  margin-top: 6px;
  color: var(--text-tertiary);
  font-weight: 700;
}

.verdict-paper {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 6px 12px;
  padding: 2px 0 2px 14px;
  margin-top: 4px;
  border-left: 3px solid rgba(var(--accent-rgb), 0.52);
}

.verdict-paper span {
  color: var(--accent-color);
  font-size: 24px;
  font-weight: 800;
  line-height: 1;
}

.verdict-paper p {
  grid-column: 1 / -1;
  margin: 0;
}

.final-summary {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--border-subtle);
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
  .chart-grid,
  .advice-list {
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
