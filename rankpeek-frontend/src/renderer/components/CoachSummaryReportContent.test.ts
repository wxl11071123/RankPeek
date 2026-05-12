import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

const contentUrl = new URL('./CoachSummaryReportContent.vue', import.meta.url)

test('coach summary report content owns the three report sections and chart caps', () => {
  assert.equal(existsSync(contentUrl), true)
  const source = readFileSync(contentUrl, 'utf8')

  assert.match(source, /近 20 局概览/)
  assert.match(source, /AI 分析内容/)
  assert.match(source, /AI 总结/)
  assert.equal((source.match(/<section[\s\S]*?class="report-section/g) || []).length, 3)
  assert.match(source, /MAX_REPORT_CHARTS\s*=\s*3/)
  assert.match(source, /MAX_SUMMARY_CHARTS\s*=\s*1/)
  assert.match(source, /placement === 'overview'/)
  assert.match(source, /placement === 'analysis'/)
  assert.match(source, /placement === 'summary'/)
  assert.match(source, /CoachSummaryChartBlock/)
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
