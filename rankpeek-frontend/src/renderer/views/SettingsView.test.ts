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
  assert.match(source, /const showcaseBackgroundLines = \[/)
  assert.match(source, /class="showcase-backdrop"/)
  assert.match(source, /class="showcase-track"/)
  assert.match(source, /class="showcase-center-mark"/)
  assert.match(source, /@keyframes showcase-scroll-left/)
  assert.match(source, /@keyframes showcase-scroll-right/)
  assert.doesNotMatch(source, /showcase-copy/)
  assert.doesNotMatch(source, /showcase-pill/)
  assert.doesNotMatch(source, /brandGlow/)
})
