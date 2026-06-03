import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('standalone routes keep title bar but hide sidebar and skip main-window auto navigation', () => {
  const source = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.match(source, /import AppAnnouncements from '@\/components\/AppAnnouncements\.vue'/)
  assert.match(source, /const isStandaloneRoute = computed\(\(\) => isStandaloneRuntimeRoute\(\)\)/)
  assert.match(source, /function isStandaloneRuntimeRoute\(\)[\s\S]*router\.currentRoute\.value\.meta\.standalone === true[\s\S]*window\.location\.hash\.startsWith\('#\/opgg'\)/)
  assert.match(source, /if \(!isStandaloneRuntimeRoute\(\)\) \{[\s\S]*void gameStore\.initConnection\(\)/)
  assert.match(source, /if \(isStandaloneRoute\.value\) \{[\s\S]*void gameStore\.checkConnection\(\)[\s\S]*standaloneConnectionTimer = setInterval/)
  assert.match(source, /if \(!isStandaloneRoute\.value\) \{[\s\S]*createGameflowAutoNavigator\(router\)/)
  assert.match(source, /clearInterval\(standaloneConnectionTimer\)/)
  assert.match(source, /<TitleBar \/>/)
  assert.match(source, /<Sidebar v-if="!isStandaloneRoute" \/>/)
  assert.match(source, /<AppAnnouncements v-if="!isStandaloneRoute" \/>/)
  assert.match(source, /:class="\{ 'main-content-standalone': isStandaloneRoute \}"/)
})

test('standalone routes keep zero page padding even at narrow widths', () => {
  const source = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.match(source, /\.main-content-standalone \{[\s\S]*padding:\s*0/)
  assert.match(source, /@media \(max-width: 760px\) \{[\s\S]*\.main-content:not\(\.main-content-standalone\) \{[\s\S]*padding:\s*14px/)
  assert.doesNotMatch(source, /@media \(max-width: 760px\) \{[\s\S]*\.main-content \{[\s\S]*padding:\s*14px/)
})
