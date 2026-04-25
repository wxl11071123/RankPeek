import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('renders the showcase as centered logo over animated background copy', () => {
  const source = readFileSync(new URL('./SettingsView.vue', import.meta.url), 'utf8')

  assert.match(source, /import brandSymbolBlack from "@\/assets\/branding\/rankpeek-symbol-black\.png"/)
  assert.match(source, /import brandSymbolWhite from "@\/assets\/branding\/rankpeek-symbol-white\.png"/)
  assert.match(source, /import brandEyeBlack from "@\/assets\/branding\/rankpeek-eye-black\.png"/)
  assert.match(source, /import brandEyeWhite from "@\/assets\/branding\/rankpeek-eye-white\.png"/)
  assert.match(
    source,
    /const aboutLogoSrc = computed\(\(\) =>\s*themeStore\.theme === "dark" \? brandSymbolBlack : brandSymbolWhite,\s*\)/
  )
  assert.match(
    source,
    /const aboutShowcaseSrc = computed\(\(\) =>\s*themeStore\.theme === "dark" \? brandEyeBlack : brandEyeWhite,\s*\)/
  )
  assert.match(source, /const showcaseBackgroundLines = computed\(\(\) => \[/)
  assert.match(source, /class="showcase-backdrop"/)
  assert.match(source, /class="showcase-track"/)
  assert.match(source, /class="showcase-center-mark"/)
  assert.match(source, /@keyframes showcase-scroll-left/)
  assert.match(source, /@keyframes showcase-scroll-right/)
  assert.doesNotMatch(source, /showcase-copy/)
  assert.doesNotMatch(source, /showcase-pill/)
  assert.doesNotMatch(source, /brandGlow/)
})

test('renders local cache diagnostics and guarded clear actions', () => {
  const source = readFileSync(new URL('./SettingsView.vue', import.meta.url), 'utf8')

  assert.match(source, /CacheClearScope/)
  assert.match(source, /CacheStatus/)
  assert.match(source, /const cacheStatus = ref<CacheStatus \| null>\(null\)/)
  assert.match(source, /const cacheStats = computed\(\(\) => \[/)
  assert.match(source, /apiClient\.getCacheStatus\(\)/)
  assert.match(source, /async function loadCacheStatus\(\)/)
  assert.match(source, /async function clearLocalCache\(scope: CacheClearScope\)/)
  assert.match(source, /window\.confirm\(/)
  assert.match(source, /apiClient\.clearCache\(scope\)/)
  assert.match(source, /await loadCacheStatus\(\)/)
  assert.match(source, /@click="loadCacheStatus"/)
  assert.match(source, /@click="clearLocalCache\('memory'\)"/)
  assert.match(source, /@click="clearLocalCache\('localDb'\)"/)
  assert.match(source, /@click="clearLocalCache\('all'\)"/)

  for (const label of [
    '本地缓存',
    '刷新状态',
    '清理内存缓存',
    '清理本地数据库缓存',
    '清理全部缓存',
    'enabled',
    'databaseSizeBytes',
    'summonerCount',
    'rankCount',
    'matchCount',
    'gameDetailCount',
    'participantCount',
    'trackedPlayerCount',
    'latestMatchCreation'
  ]) {
    assert.ok(source.includes(label), `missing ${label}`)
  }
})
