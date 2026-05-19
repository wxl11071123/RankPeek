import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./index.html', import.meta.url), 'utf8')

test('content security policy allows local rankpeek-server requests', () => {
  assert.match(source, /connect-src[^"]*http:\/\/127\.0\.0\.1:18080/)
})
