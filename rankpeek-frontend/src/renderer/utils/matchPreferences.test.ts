import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('defaults analysis to solo queue data and recognizes supported queue modes', () => {
  const source = readFileSync(new URL('./matchPreferences.ts', import.meta.url), 'utf8')

  assert.match(source, /DEFAULT_ANALYSIS_QUEUE_MODE\s*=\s*QUEUE_ID\.SOLO_5X5/)
  assert.match(source, /VALID_MATCH_QUEUE_MODES[\s\S]*QUEUE_ID\.SOLO_5X5/)
  assert.match(source, /VALID_MATCH_QUEUE_MODES[\s\S]*QUEUE_ID\.FLEX_SR/)
  assert.match(source, /VALID_MATCH_QUEUE_MODES[\s\S]*QUEUE_ID\.ARAM/)
  assert.match(source, /return VALID_MATCH_QUEUE_MODES\.has\(rawValue\) \? rawValue : 0/)
})

test('caches normalized queue mode values when settings are saved', () => {
  const source = readFileSync(new URL('./matchPreferences.ts', import.meta.url), 'utf8')

  assert.match(source, /export function setCachedDefaultMatchQueueMode\(value: unknown\): number/)
  assert.match(source, /cachedDefaultMatchQueueMode = normalizeMatchQueueMode\(value\)/)
  assert.match(source, /return cachedDefaultMatchQueueMode/)
})
