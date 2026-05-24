import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('standalone routes keep title bar but hide sidebar and skip main-window auto navigation', () => {
  const source = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.match(source, /const isStandaloneRoute = computed\(\(\) => isStandaloneRuntimeRoute\(\)\)/)
  assert.match(source, /function isStandaloneRuntimeRoute\(\)[\s\S]*router\.currentRoute\.value\.meta\.standalone === true[\s\S]*window\.location\.hash\.startsWith\('#\/opgg'\)/)
  assert.match(source, /if \(!isStandaloneRuntimeRoute\(\)\) \{[\s\S]*void gameStore\.initConnection\(\)/)
  assert.match(source, /if \(isStandaloneRoute\.value\) \{[\s\S]*void gameStore\.checkConnection\(\)[\s\S]*standaloneConnectionTimer = setInterval/)
  assert.match(source, /if \(!isStandaloneRoute\.value\) \{[\s\S]*createGameflowAutoNavigator\(router\)/)
  assert.match(source, /clearInterval\(standaloneConnectionTimer\)/)
  assert.match(source, /<TitleBar \/>/)
  assert.match(source, /<Sidebar v-if="!isStandaloneRoute" \/>/)
  assert.match(source, /:class="\{ 'main-content-standalone': isStandaloneRoute \}"/)
})
