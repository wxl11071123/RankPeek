import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

const contentUrl = new URL('./CoachSummaryReportContent.vue', import.meta.url)

test('coach summary report content owns the three report sections and chart caps', () => {
  assert.equal(existsSync(contentUrl), true)
  const source = readFileSync(contentUrl, 'utf8')

  assert.match(source, /近 20 局概览/)
  assert.match(source, /数据分析/)
  assert.match(source, /数据总结/)
  assert.doesNotMatch(source, /AI 分析内容/)
  assert.doesNotMatch(source, /AI 总结/)
  assert.doesNotMatch(source, /下一阶段训练重点/)
  assert.equal((source.match(/<section[\s\S]*?class="report-section/g) || []).length, 3)
  assert.match(source, /MAX_REPORT_CHARTS\s*=\s*3/)
  assert.match(source, /placement === 'overview'/)
  assert.match(source, /CoachSummaryChartBlock/)
  assert.doesNotMatch(source, /analysisCharts|summaryCharts/)
  assert.doesNotMatch(source, /placement === 'analysis'|placement === 'summary'/)
})

test('coach summary report analysis section hides champion advice cards', () => {
  const source = readFileSync(contentUrl, 'utf8')

  assert.match(source, /analysis-section/)
  assert.match(source, /finding-list/)
  assert.match(source, /finding-item/)
  assert.doesNotMatch(source, /<span>\{\{ findings\.length \}\}[^<]*<\/span>/)
  assert.doesNotMatch(source, /championAdvice/)
  assert.doesNotMatch(source, /advice-list/)
  assert.doesNotMatch(source, /advice-item/)
  assert.doesNotMatch(source, /advice\.championName|advice\.role|advice\.reason/)
})

test('coach summary report summary section hides verdict label and score paper', () => {
  const source = readFileSync(contentUrl, 'utf8')

  assert.match(source, /summary-section/)
  assert.match(source, /closingSummary/)
  assert.match(source, /<p class="final-summary ai-report-prose">\{\{ closingSummary \}\}<\/p>/)
  assert.doesNotMatch(source, /verdict-paper/)
  assert.doesNotMatch(source, /report\.verdict\.label/)
  assert.doesNotMatch(source, /formatNumber\(report\.verdict\.score\)/)
  assert.doesNotMatch(source, /<span>\{\{ report\.verdict\.label \}\}<\/span>/)
})

test('coach summary report content uses paper sections instead of nested dashboard cards', () => {
  const source = readFileSync(contentUrl, 'utf8')

  assert.match(source, /report-divider|paper-divider|section-divider/)
  assert.match(source, /<section[\s\S]*?class="report-section/)
  assert.doesNotMatch(source, /overview-summary-panel|hero-win-rate-panel/)
  assert.doesNotMatch(source, /finding-card|advice-card|training-card|verdict-card|verdict-paper/)
  assert.doesNotMatch(source, /\.report-section\s*\{[^}]*border:/)
  assert.doesNotMatch(source, /\.report-section\s*\{[^}]*box-shadow:/)
})

test('coach summary report overview keeps facts and hero win-rate figure', () => {
  const source = readFileSync(contentUrl, 'utf8')

  assert.match(source, /roleSummary/)
  assert.match(source, /formatRoleLabel/)
  assert.match(source, /heroWinRateStats/)
  assert.match(source, /overview-facts/)
  assert.match(source, /overview-fact-row/)
  assert.match(source, /fact-main/)
  assert.match(source, /fact-sub/)
  assert.match(source, /hero-win-rate-figure/)
  assert.match(source, /hero-win-rate-chart/)
  assert.match(source, /hero-win-rate-y-axis/)
  assert.match(source, />100%<\/span>/)
  assert.match(source, />50%<\/span>/)
  assert.match(source, />0%<\/span>/)
  assert.match(source, /hero-win-rate-x-axis/)
  assert.match(source, /hero-win-rate-avatar/)
  assert.match(source, /:src="heroIcon\(hero\)"/)
  assert.doesNotMatch(source, /hero\.kda|averageKda/)
  assert.doesNotMatch(source, />\s*胜率\s*</)
  assert.doesNotMatch(source, />\s*游玩位置\s*</)
  assert.doesNotMatch(source, />\s*最近游玩英雄\s*</)
  assert.doesNotMatch(source, />\s*主玩英雄胜率\s*</)
  assert.doesNotMatch(source, /柱高对应英雄胜率，颜色按 0-100% 全局刻度映射。/)
})

test('coach summary report hero win-rate axis uses quarter ticks and grid lines', () => {
  const source = readFileSync(contentUrl, 'utf8')
  const yAxis = source.match(/<div class="hero-win-rate-y-axis"[\s\S]*?<\/div>/)?.[0] || ''
  const grid = source.match(/<div class="hero-win-rate-grid"[\s\S]*?<\/div>/)?.[0] || ''

  for (const tick of ['100', '75', '50', '25', '0']) {
    assert.match(yAxis, new RegExp(`>${tick}%<\\/span>`))
  }
  assert.equal((yAxis.match(/<span>/g) || []).length, 5)
  assert.equal((grid.match(/<span><\/span>/g) || []).length, 5)
  assert.doesNotMatch(source, /echarts|chart\.js/i)
})

test('coach summary report overview aligns fact rows and chart bottoms', () => {
  const source = readFileSync(contentUrl, 'utf8')

  assert.match(source, /--overview-bottom-gap/)
  assert.match(source, /\.overview-layout\s*\{[\s\S]*align-items:\s*stretch/)
  assert.match(source, /\.overview-layout\s*\{[\s\S]*padding-bottom:\s*var\(--overview-bottom-gap\)/)
  assert.match(source, /\.overview-text-column\s*\{[\s\S]*padding-bottom:\s*var\(--overview-bottom-gap\)/)
  assert.match(source, /\.hero-win-rate-figure\s*\{[\s\S]*height:\s*100%/)
  assert.match(source, /\.hero-win-rate-figure\s*\{[\s\S]*display:\s*flex/)
  assert.match(source, /\.hero-win-rate-figure\s*\{[\s\S]*flex-direction:\s*column/)
  assert.match(source, /\.hero-win-rate-figure\s*\{[\s\S]*padding-bottom:\s*var\(--overview-bottom-gap\)/)
  assert.match(source, /\.hero-win-rate-chart\s*\{[\s\S]*flex:\s*1/)
  assert.match(source, /\.hero-win-rate-chart\s*\{[\s\S]*height:\s*100%/)
  assert.match(source, /\.hero-win-rate-plot\s*\{[\s\S]*height:\s*100%/)
  assert.match(source, /\.hero-win-rate-x-axis\s*\{[\s\S]*margin-top:\s*auto/)
  assert.match(source, /\.overview-fact-row\s*\{[^}]*min-height:/)
  assert.match(source, /\.overview-fact-row\s*\{[^}]*align-items:\s*center/)
  assert.match(source, /\.overview-fact-row\s*\{[^}]*padding:/)
})

test('coach summary report hero win-rate bars are rectangular and reveal values on hover or focus only', () => {
  const source = readFileSync(contentUrl, 'utf8')

  assert.match(source, /hero-win-bar-plot/)
  assert.match(source, /hero-win-bar-fill/)
  assert.match(source, /hero-win-tooltip/)
  assert.match(source, /class="hero-win-rate-column"[\s\S]*:style="\{ '--hero-win-rate': `\$\{clampPercent\(hero\.winRate\)\}%` \}"/)
  assert.match(source, /\.hero-win-bar-fill\s*\{[\s\S]*height:\s*var\(--hero-win-rate\)/)
  assert.match(source, /\.hero-win-bar-fill\s*\{[\s\S]*border-radius:\s*0/)
  assert.match(source, /\.hero-win-bar-fill\s*\{[\s\S]*background:\s*(?:#[0-9a-fA-F]{3,6}|rgb|rgba|linear-gradient)/)
  assert.match(source, /\.hero-win-tooltip\s*\{[\s\S]*opacity:\s*0/)
  assert.match(source, /\.hero-win-tooltip\s*\{[\s\S]*visibility:\s*hidden/)
  assert.match(source, /\.hero-win-tooltip\s*\{[\s\S]*bottom:\s*(?:min\()?calc\(var\(--hero-win-rate\)/)
  assert.match(source, /\.hero-win-rate-column:hover\s+\.hero-win-tooltip/)
  assert.match(source, /\.hero-win-rate-column:focus(?:-within)?\s+\.hero-win-tooltip/)
  assert.match(source, /tabindex="0"/)
  assert.match(source, /<div class="hero-win-bar-plot">[\s\S]*<span class="hero-win-tooltip">\{\{ formatPercent\(hero\.winRate\) \}\}<\/span>[\s\S]*<\/div>\s*<div class="hero-win-rate-x-axis">/)
  assert.doesNotMatch(source, /<div class="hero-win-rate-x-axis">[\s\S]*hero-win-tooltip[\s\S]*<\/div>/)
  assert.doesNotMatch(source, /hero-win-rate-value/)
  assert.doesNotMatch(source, /hero-win-bar-gradient/)
  assert.doesNotMatch(source, /--hero-win-rate-percent/)
  assert.doesNotMatch(source, /clip-path:\s*inset/)
  assert.doesNotMatch(source, /\.hero-win-bar-plot\s*\{[^}]*border:/)
  assert.doesNotMatch(source, /\.hero-win-bar-plot\s*\{[^}]*background:/)
  assert.doesNotMatch(source, /\.hero-win-rate-track\s*\{[\s\S]*border:/)
  assert.doesNotMatch(source, /\.hero-win-rate-track\s*\{[\s\S]*background:/)
})

test('coach summary report divider fades smoothly without a hard center block', () => {
  const source = readFileSync(contentUrl, 'utf8')

  assert.match(source, /\.paper-divider\s*\{[\s\S]*height:\s*18px/)
  assert.match(source, /\.paper-divider::before\s*\{[\s\S]*rgba\(148, 163, 184, 0\.34\) 50%/)
  assert.match(source, /\.paper-divider::after\s*\{[\s\S]*width:\s*min\(220px, 28%\)/)
  assert.match(source, /\.paper-divider::after\s*\{[\s\S]*rgba\(var\(--accent-rgb\), 0\.42\) 50%/)
  assert.doesNotMatch(source, /\.paper-divider\s*\{[^}]*background:\s*linear-gradient/)
  assert.doesNotMatch(source, /rgba\(248, 192, 74/)
})

test('coach summary report applies serif prose only to AI generated copy', () => {
  const source = readFileSync(contentUrl, 'utf8')

  assert.match(source, /ai-report-prose/)
  assert.match(source, /\.ai-report-prose\s*\{[\s\S]*"Noto Serif SC"/)
  assert.match(source, /\.ai-report-prose\s*\{[\s\S]*"Source Han Serif SC"/)
  assert.match(source, /\.ai-report-prose\s*\{[\s\S]*"Songti SC"/)
  assert.match(source, /\.ai-report-prose\s*\{[\s\S]*"SimSun"/)
  assert.match(source, /\.ai-report-prose\s*\{[\s\S]*serif/)
  assert.match(source, /<p class="section-summary ai-report-prose">\{\{ overviewSummary \}\}<\/p>/)
  assert.match(source, /<strong class="ai-report-prose">\{\{ finding\.claim \}\}<\/strong>/)
  assert.match(source, /<p class="ai-report-prose">\{\{ finding\.advice \|\| finding\.reasoning \|\| finding\.evidence \}\}<\/p>/)
  assert.match(source, /<p class="final-summary ai-report-prose">\{\{ closingSummary \}\}<\/p>/)
  assert.doesNotMatch(source, /advice\.(championName|role|reason)/)
  assert.doesNotMatch(source, /report\.verdict\.label|report\.verdict\.score/)
  assert.doesNotMatch(source, /<h2 class="ai-report-prose">/)
  assert.doesNotMatch(source, /class="fact-main ai-report-prose"/)
  assert.doesNotMatch(source, /class="hero-win-tooltip ai-report-prose"/)
})

test('coach summary report hides analysis charts and training plan UI', () => {
  const source = readFileSync(contentUrl, 'utf8')

  assert.doesNotMatch(source, /analysisCharts|summaryCharts/)
  assert.doesNotMatch(source, /placement === 'analysis'|placement === 'summary'/)
  assert.doesNotMatch(source, /v-if="analysisCharts\.length"|v-if="summaryCharts\.length"/)
  assert.doesNotMatch(source, /v-for="chart in analysisCharts"|v-for="chart in summaryCharts"/)
  assert.doesNotMatch(source, /trainingPlan|training-list|training-item/)
  assert.doesNotMatch(source, /item\.task|item\.metricToTrack|item\.target|nextGames/)
  assert.doesNotMatch(source, /下一阶段训练重点|接下来/)
})

test('coach summary report content keeps fallbacks while chart block handles empty dataRef and unsupported kinds', () => {
  const source = readFileSync(contentUrl, 'utf8')
  const chart = readFileSync(new URL('./CoachSummaryChartBlock.vue', import.meta.url), 'utf8')

  assert.match(source, /暂不支持该报告类型/)
  assert.match(source, /报告内容暂时无法解析/)
  assert.match(source, /reportLoadState/)
  assert.match(chart, /图表数据待接入/)
  assert.match(chart, /unsupportedChart/)
  assert.doesNotMatch(source + chart, /echarts|chart\.js|highcharts|d3/i)
})
