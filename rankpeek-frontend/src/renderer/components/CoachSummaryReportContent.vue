<script setup lang="ts">
import { computed } from 'vue'
import CoachSummaryChartBlock from '@/components/CoachSummaryChartBlock.vue'
import { getChampionIconUrl, markAssetLoadFailed } from '@/utils/gameAssetUrls'
import type {
  CoachSummaryHeroStat,
  CoachSummaryReportV1
} from '@/types/coachSummaryReport'

const MAX_REPORT_CHARTS = 3
const MAX_SUMMARY_CHARTS = 1

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
const mainRoleLabel = computed(() => {
  const role = overview.value?.primaryRoles?.[0]?.role || overview.value?.roleStats?.[0]?.role
  return role || '暂无'
})
const chartBlocks = computed(() => props.report?.chartBlocks || [])
const overviewCharts = computed(() => (
  chartBlocks.value
    .filter(chart => chart.placement === 'overview')
    .slice(0, Math.min(2, MAX_REPORT_CHARTS))
))
const analysisCharts = computed(() => {
  const remaining = Math.max(MAX_REPORT_CHARTS - overviewCharts.value.length, 0)
  return chartBlocks.value
    .filter(chart => chart.placement === 'analysis')
    .slice(0, remaining)
})
const summaryCharts = computed(() => {
  const remaining = Math.max(MAX_REPORT_CHARTS - overviewCharts.value.length - analysisCharts.value.length, 0)
  return chartBlocks.value
    .filter(chart => chart.placement === 'summary')
    .slice(0, Math.min(MAX_SUMMARY_CHARTS, remaining))
})
const findings = computed(() => (props.report?.keyFindings || []).slice(0, 5))
const championAdvice = computed(() => (props.report?.championAdvice || []).slice(0, 3))
const trainingPlan = computed(() => (props.report?.trainingPlan || []).slice(0, 3))
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

        <p class="section-summary">{{ overviewSummary }}</p>

        <div class="overview-stats">
          <div>
            <strong>{{ overview?.totalMatches || 20 }}</strong>
            <span>总场次</span>
          </div>
          <div>
            <strong>{{ formatPercent(overview?.winRate) }}</strong>
            <span>胜率</span>
          </div>
          <div>
            <strong>{{ mainRoleLabel }}</strong>
            <span>主位置</span>
          </div>
        </div>

        <div v-if="heroStats.length" class="hero-grid">
          <article v-for="hero in heroStats" :key="`${hero.championDisplayName}-${hero.role}`" class="hero-card">
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
              <p>{{ hero.role }} · {{ hero.games }} 场</p>
            </div>
            <div class="hero-metrics">
              <span>{{ formatPercent(hero.winRate) }}</span>
              <span>{{ hero.kda || formatNumber(hero.averageKda) }}</span>
            </div>
          </article>
        </div>

        <div v-if="overviewCharts.length" class="chart-grid">
          <CoachSummaryChartBlock
            v-for="chart in overviewCharts"
            :key="chart.id"
            :chart="chart"
          />
        </div>
      </section>

      <section class="report-section analysis-section">
        <div class="section-heading">
          <h2>AI 分析内容</h2>
          <span>{{ findings.length }} 条重点</span>
        </div>

        <div class="finding-list">
          <article v-for="finding in findings" :key="finding.id" class="finding-card">
            <strong>{{ finding.claim }}</strong>
            <p>{{ finding.advice || finding.reasoning || finding.evidence }}</p>
          </article>
        </div>

        <div v-if="championAdvice.length" class="advice-grid">
          <article v-for="advice in championAdvice" :key="`${advice.championName}-${advice.role}`" class="advice-card">
            <span>{{ advice.role }}</span>
            <strong>{{ advice.championName }}</strong>
            <p>{{ advice.reason }}</p>
          </article>
        </div>

        <div v-if="analysisCharts.length" class="chart-grid">
          <CoachSummaryChartBlock
            v-for="chart in analysisCharts"
            :key="chart.id"
            :chart="chart"
          />
        </div>
      </section>

      <section class="report-section summary-section">
        <div class="section-heading">
          <h2>AI 总结</h2>
          <span>{{ report.verdict.label }}</span>
        </div>

        <div class="verdict-card">
          <strong>{{ report.verdict.label }}</strong>
          <span>{{ formatNumber(report.verdict.score) }}</span>
          <p>{{ report.verdict.summary }}</p>
        </div>

        <div class="training-list">
          <article v-for="item in trainingPlan" :key="`${item.focus}-${item.metricToTrack}`" class="training-card">
            <strong>{{ item.focus }}</strong>
            <p>{{ item.task }}</p>
            <span>{{ item.target }}</span>
          </article>
        </div>

        <div v-if="summaryCharts.length" class="chart-grid summary-chart-grid">
          <CoachSummaryChartBlock
            v-for="chart in summaryCharts"
            :key="chart.id"
            :chart="chart"
          />
        </div>

        <p class="final-summary">{{ closingSummary }}</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.coach-report-content {
  min-width: 0;
}

.state-card,
.report-section {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(circle at 10% 0%, rgba(var(--accent-rgb), 0.065), transparent 34%),
    var(--bg-secondary);
  box-shadow:
    0 14px 32px rgba(0, 0, 0, 0.16),
    0 0 0 1px rgba(var(--accent-rgb), 0.025);
}

.state-card {
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
  padding: 20px;
}

.report-section + .report-section {
  margin-top: 16px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-heading h2 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 20px;
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

.overview-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.overview-stats div,
.hero-card,
.finding-card,
.advice-card,
.training-card,
.verdict-card {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-tertiary);
}

.overview-stats div {
  padding: 12px;
}

.overview-stats strong {
  display: block;
  color: var(--text-primary);
  font-size: 20px;
  font-weight: 800;
  line-height: 1.2;
}

.overview-stats span,
.hero-main p,
.hero-metrics span,
.finding-card p,
.advice-card p,
.advice-card span,
.training-card p,
.training-card span,
.verdict-card p {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.overview-stats span {
  display: block;
  margin-top: 5px;
  color: var(--text-tertiary);
  font-weight: 700;
}

.hero-grid,
.finding-list,
.advice-grid,
.training-list,
.chart-grid {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.hero-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.hero-card {
  min-width: 0;
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
}

.hero-avatar {
  width: 44px;
  height: 44px;
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
.finding-card strong,
.advice-card strong,
.training-card strong,
.verdict-card strong {
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

.chart-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.finding-list {
  grid-template-columns: 1fr;
}

.finding-card,
.advice-card,
.training-card {
  padding: 13px;
}

.finding-card p,
.advice-card p,
.training-card p {
  margin: 6px 0 0;
}

.advice-grid,
.training-list {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.advice-card span,
.training-card span {
  display: block;
  margin-top: 6px;
  color: var(--text-tertiary);
  font-weight: 700;
}

.verdict-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 6px 12px;
  padding: 14px;
  margin-top: 4px;
}

.verdict-card span {
  color: var(--accent-color);
  font-size: 24px;
  font-weight: 800;
  line-height: 1;
}

.verdict-card p {
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

@media (max-width: 860px) {
  .hero-grid,
  .chart-grid,
  .advice-grid,
  .training-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .report-section {
    padding: 16px;
  }

  .overview-stats {
    grid-template-columns: 1fr;
  }

  .hero-card {
    grid-template-columns: 40px minmax(0, 1fr);
  }

  .hero-metrics {
    grid-column: 2;
    align-items: flex-start;
    flex-direction: row;
  }
}
</style>
