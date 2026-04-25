import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('subscribes to backend cache update events and exposes unsubscribe handler', () => {
  const source = readFileSync(new URL('./websocketClient.ts', import.meta.url), 'utf8')

  assert.match(source, /import type \{ GameState, CacheUpdateEvent \} from '@\/types\/api'/)
  assert.match(source, /type CacheUpdateCallback = \(event: CacheUpdateEvent\) => void/)
  assert.match(source, /private cacheUpdateCallbacks: Set<CacheUpdateCallback> = new Set\(\)/)
  assert.match(source, /this\.client\.subscribe\('\/topic\/cache-updates', \(message: IMessage\) => \{/)
  assert.match(source, /JSON\.parse\(message\.body\) as CacheUpdateEvent/)
  assert.match(source, /for \(const callback of this\.cacheUpdateCallbacks\)/)
  assert.match(source, /onCacheUpdate\(callback: CacheUpdateCallback\): \(\) => void/)
  assert.match(source, /this\.cacheUpdateCallbacks\.add\(callback\)/)
  assert.match(source, /return \(\) => this\.cacheUpdateCallbacks\.delete\(callback\)/)
})

test('cache update subscription guards parse and callback failures', () => {
  const source = readFileSync(new URL('./websocketClient.ts', import.meta.url), 'utf8')
  const cacheSubscription = source.match(/this\.client\.subscribe\('\/topic\/cache-updates'[\s\S]*?\n    \}\)/)?.[0] || ''

  assert.match(cacheSubscription, /try \{/)
  assert.match(cacheSubscription, /catch \(e\) \{/)
  assert.match(cacheSubscription, /for \(const callback of this\.cacheUpdateCallbacks\)/)
  assert.match(cacheSubscription, /try \{\s*callback\(data\)/)
  assert.match(cacheSubscription, /console\.warn\('Failed to parse cache update message'/)
  assert.match(cacheSubscription, /console\.warn\('Cache update callback failed'/)
})
