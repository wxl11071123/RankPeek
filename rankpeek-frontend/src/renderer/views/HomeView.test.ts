import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function extractRule(source: string, selector: string) {
  const start = source.indexOf(selector)
  assert.notEqual(start, -1, `${selector} should exist`)

  const open = source.indexOf('{', start)
  assert.notEqual(open, -1, `${selector} should have a body`)

  let depth = 0
  for (let index = open; index < source.length; index += 1) {
    if (source[index] === '{') {
      depth += 1
    }

    if (source[index] === '}') {
      depth -= 1
      if (depth === 0) {
        return source.slice(open + 1, index)
      }
    }
  }

  assert.fail(`${selector} should close`)
}

test('light-mode AI report glow remains warm gold', () => {
  const source = readFileSync(new URL('./HomeView.vue', import.meta.url), 'utf8')
  const hoverRule = extractRule(source, ':global([data-theme="light"] .coach-report-panel .coach-stack-card:hover)')
  const expandedRule = extractRule(source, ':global([data-theme="light"] .coach-report-panel .coach-expanded-card)')

  assert.match(hoverRule, /box-shadow:\s*0 0 12px rgba\(212, 175, 55, 0\.2\);/)
  assert.doesNotMatch(hoverRule, /rgba\(100,\s*116,\s*139/)
  assert.match(expandedRule, /0 0 12px rgba\(212, 175, 55, 0\.2\)/)
  assert.doesNotMatch(expandedRule, /rgba\(100,\s*116,\s*139/)
})
