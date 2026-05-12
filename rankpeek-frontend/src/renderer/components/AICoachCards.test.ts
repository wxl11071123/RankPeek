import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('coach cards use report headline fallback fields while preserving open-report emit', () => {
  const source = readFileSync(new URL('./AICoachCards.vue', import.meta.url), 'utf8')

  assert.match(source, /import \{ getCoachReportHeadline \} from '@\/services\/localAiAnalysis'/)
  assert.match(source, /id\?: number \| string/)
  assert.match(source, /headline\?: string/)
  assert.match(source, /cardTitle\?: string/)
  assert.match(source, /shortTitle\?: string/)
  assert.match(source, /getCoachReportHeadline\(\{ report \}\)/)
  assert.match(source, /emit\('open-report', activeReportForEmit\.value, activeIndex\.value\)/)
  assert.match(source, /activeReport\.value\?\.isDevPlaceholder \? null : activeReport\.value/)
  assert.doesNotMatch(source, /dev-preview|CoachSummaryReport/)
})
