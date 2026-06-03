import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function extractRule(source: string, selector: string) {
  const normalizedSource = source.replace(/\r\n/g, '\n')
  const start = normalizedSource.indexOf(selector)
  assert.notEqual(start, -1, `${selector} should exist`)

  const open = normalizedSource.indexOf('{', start)
  assert.notEqual(open, -1, `${selector} should have a body`)

  let depth = 0
  for (let index = open; index < normalizedSource.length; index += 1) {
    if (normalizedSource[index] === '{') {
      depth += 1
    }

    if (normalizedSource[index] === '}') {
      depth -= 1
      if (depth === 0) {
        return normalizedSource.slice(open + 1, index)
      }
    }
  }

  assert.fail(`${selector} should close`)
}

test('home chart uses reliable match history instead of the legacy filtered history chain', () => {
  const source = readFileSync(new URL('./HomeChart.vue', import.meta.url), 'utf8')
  const homeView = readFileSync(new URL('../views/HomeView.vue', import.meta.url), 'utf8')

  assert.match(source, /loadReliableMatchHistory/)
  assert.match(source, /createHomeChartEntries/)
  assert.match(source, /runWithConcurrencyLimit/)
  assert.doesNotMatch(source, /getFilteredMatchHistory/)
  assert.doesNotMatch(source, /Promise\.allSettled/)
  assert.doesNotMatch(source, /stats\.kills\s*\|\|\s*0/)
  assert.match(homeView, /<HomeChart[^>]*:summoner="currentSummoner"/)
})

test('home chart uses RP index as the default trend metric', () => {
  const source = readFileSync(new URL('./HomeChart.vue', import.meta.url), 'utf8')

  assert.match(source, /type MetricKey =[\s\S]*\| 'rpIndex'/)
  assert.match(source, /const selectedMetric = ref<MetricKey>\('rpIndex'\)/)
  assert.match(source, /METRIC_OPTIONS:[\s\S]*\{ value: 'rpIndex', label: 'RP 指数' \}/)
  assert.match(source, /function getMetricValue\(entry: ChartEntry, metric: MetricKey\): number \| null \{[\s\S]*metric === 'rpIndex'[\s\S]*entry\.rpIndex/)
  assert.match(source, /function formatMetricValue\(value: number \| null, metric = selectedMetric\.value\): string \{[\s\S]*metric === 'rpIndex'[\s\S]*value\.toFixed\(1\)/)
  assert.match(source, /function formatAxisValue\(value: number, metric: MetricKey\): string \{[\s\S]*metric === 'rpIndex'[\s\S]*value\.toFixed\(1\)/)
  assert.doesNotMatch(source, /selectedMetric = ref<MetricKey>\('kda'\)/)
  assert.doesNotMatch(source, /\{ value: 'kda', label: 'KDA 比率' \}/)
})

test('home chart passes match platform id to SGP detail and timeline hydration for RP index', () => {
  const source = readFileSync(new URL('./HomeChart.vue', import.meta.url), 'utf8')

  assert.match(source, /apiClient\.getGameDetail\(match\.gameId,\s*\{\s*source:\s*'sgp',\s*sgpServerId:\s*match\.platformId\s*\}\)/)
  assert.match(source, /apiClient\.getGameTimeline\(gameId,\s*\{\s*source:\s*'sgp',\s*sgpServerId\s*\}\)/)
  assert.match(source, /async function loadMatchTimelineForRpIndex\(gameId: number,\s*sgpServerId: string\)/)
})

test('home chart card only applies the shared outer glow on hover or focus', () => {
  const source = readFileSync(new URL('./HomeChart.vue', import.meta.url), 'utf8')
  const baseRule = extractRule(source, '.home-chart-card')
  const hoverRule = extractRule(source, '.home-chart-card:hover,\n.home-chart-card:focus-within')
  const lightRule = extractRule(source, ':global([data-theme="light"] .home-chart-card)')

  assert.match(baseRule, /--chart-module-hover-border:\s*var\(--home-module-hover-border, rgba\(96, 176, 255, 0\.48\)\)/)
  assert.match(baseRule, /--chart-module-hover-shadow:\s*var\(\s*--home-module-hover-shadow,/)
  assert.match(baseRule, /border:\s*1px solid var\(--border-color\)/)
  assert.match(baseRule, /box-shadow:\s*none/)
  assert.match(hoverRule, /border-color:\s*var\(--chart-module-hover-border\)/)
  assert.match(hoverRule, /box-shadow:\s*var\(--chart-module-hover-shadow\)/)
  assert.match(lightRule, /--chart-module-hover-border:\s*var\(--home-module-hover-border, rgba\(86, 109, 134, 0\.42\)\)/)
  assert.match(lightRule, /--chart-module-hover-shadow:\s*var\(\s*--home-module-hover-shadow,/)
})

test('chart select near glow paints a full border ring without changing its fill', () => {
  const source = readFileSync(new URL('./HomeChart.vue', import.meta.url), 'utf8')
  const queuePseudoRule = extractRule(source, '.queue-tabs.control-glow::before,\n.chart-select.control-glow::before')
  const selectNearRule = extractRule(source, ".chart-select.control-glow[data-near-glow='true']:not(:hover):not(:focus)")
  const queueNearRule = extractRule(source, ".queue-tabs.control-glow[data-near-glow='true']:not(:hover):not(:focus-visible)")

  assert.match(queuePseudoRule, /content:\s*none/)
  assert.match(selectNearRule, /border-color:\s*transparent/)
  assert.match(selectNearRule, /linear-gradient\(var\(--chart-control-bg\), var\(--chart-control-bg\)\) padding-box/)
  assert.match(selectNearRule, /radial-gradient\(/)
  assert.match(selectNearRule, /circle at var\(--control-glow-x\) var\(--control-glow-y\)/)
  assert.match(selectNearRule, /var\(--chart-control-border-local-glow\) 0%/)
  assert.match(selectNearRule, /var\(--chart-control-border-local-glow-fade\) 36%/)
  assert.match(selectNearRule, /var\(--chart-control-border\) 72%/)
  assert.match(selectNearRule, /\) border-box/)
  assert.match(selectNearRule, /box-shadow:\s*none/)
  assert.doesNotMatch(selectNearRule, /--chart-control-edge-shadow/)
  assert.doesNotMatch(selectNearRule, /--chart-control-bg-hover|--chart-control-active-bg|--chart-control-focus/)
  assert.match(queueNearRule, /border-color:\s*transparent/)
  assert.match(queueNearRule, /linear-gradient\(var\(--chart-tab-shell-bg\), var\(--chart-tab-shell-bg\)\) padding-box/)
  assert.match(queueNearRule, /radial-gradient\(/)
  assert.match(queueNearRule, /circle at var\(--control-glow-x\) var\(--control-glow-y\)/)
  assert.match(queueNearRule, /\) border-box/)
  assert.match(queueNearRule, /box-shadow:\s*none/)
  assert.doesNotMatch(queueNearRule, /--chart-control-edge-shadow|--chart-control-bg-hover|--chart-control-active-bg|--chart-control-focus/)
})

test('chart toolbar hover stacks full border glow with local proximity glow', () => {
  const source = readFileSync(new URL('./HomeChart.vue', import.meta.url), 'utf8')
  const baseRule = extractRule(source, '.home-chart-card')
  const queueHoverRule = extractRule(source, '.queue-tabs:hover')
  const selectHoverRule = extractRule(source, '.chart-select:hover,\n.chart-select:focus')

  assert.match(baseRule, /--chart-control-bg-hover-local:[\s\S]*var\(--chart-control-border-hover\) 72%[\s\S]*\) border-box/)
  assert.match(baseRule, /--chart-control-active-bg-hover-local:[\s\S]*var\(--chart-control-border-hover\) 72%[\s\S]*\) border-box/)
  assert.match(queueHoverRule, /background:\s*var\(--chart-control-bg-hover-local\)/)
  assert.match(queueHoverRule, /box-shadow:\s*var\(--chart-control-focus\), var\(--chart-control-edge-shadow\)/)
  assert.match(selectHoverRule, /background:\s*var\(--chart-control-bg-hover-local\)/)
  assert.match(selectHoverRule, /box-shadow:\s*var\(--chart-control-focus\), var\(--chart-control-edge-shadow\)/)
})
