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

test('coach cards build a vertical decorative deck behind the active report', () => {
  const source = readFileSync(new URL('./AICoachCards.vue', import.meta.url), 'utf8')

  assert.match(source, /const deckDecorativeLayers = computed/)
  assert.match(source, /Math\.min\(displayCount\.value - 1, 2\)/)
  assert.match(source, /v-for="layer in deckDecorativeLayers"/)
  assert.match(source, /class="record-stack-card"/)
  assert.match(source, /deck-layer-\$\{layer\}/)
  assert.doesNotMatch(source, /record-preview-title/)
  assert.doesNotMatch(source, /record-preview-meta/)
})

test('coach cards keep wheel navigation cyclic for the deck', () => {
  const source = readFileSync(new URL('./AICoachCards.vue', import.meta.url), 'utf8')

  assert.match(source, /const nextIndex = \(\(index % count\) \+ count\) % count/)
  assert.match(source, /selectReport\(activeIndex\.value \+ \(direction === 'next' \? 1 : -1\), direction\)/)
})
