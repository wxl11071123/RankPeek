import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('asset hover tooltip renders through body teleport with fixed viewport positioning', () => {
  const source = readFileSync(new URL('./AssetHoverTooltip.vue', import.meta.url), 'utf8')

  assert.match(source, /details:\s*GameAssetTooltipDetails/)
  assert.match(source, /<slot/)
  assert.match(source, /<Teleport\s+to="body"/)
  assert.match(source, /position:\s*fixed/)
  assert.match(source, /getBoundingClientRect\(\)/)
  assert.match(source, /window\.innerWidth/)
  assert.match(source, /window\.innerHeight/)
  assert.match(source, /pointer-events:\s*none/)
})

test('asset hover tooltip supports mouse and keyboard activation without browser title dependency', () => {
  const source = readFileSync(new URL('./AssetHoverTooltip.vue', import.meta.url), 'utf8')

  assert.match(source, /@mouseenter="scheduleShow"/)
  assert.match(source, /@focusin="scheduleShow"/)
  assert.match(source, /@mouseleave="scheduleHide"/)
  assert.match(source, /@focusout="scheduleHide"/)
  assert.match(source, /details\.description/)
  assert.match(source, /暂无详细说明/)
  assert.doesNotMatch(source, /\btitle=/)
})
