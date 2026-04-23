import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('uses theme-specific eye artwork instead of the old abstract glow showcase', () => {
  const source = readFileSync(new URL('./SettingsView.vue', import.meta.url), 'utf8')

  assert.match(source, /import brandEyeBlack from "@\/assets\/branding\/rankpeek-eye-black\.png"/)
  assert.match(source, /import brandEyeWhite from "@\/assets\/branding\/rankpeek-eye-white\.png"/)
  assert.match(
    source,
    /const aboutLogoSrc = computed\(\(\) =>\s*themeStore\.theme === "dark" \? brandEyeBlack : brandEyeWhite,\s*\)/
  )
  assert.match(
    source,
    /const aboutShowcaseSrc = computed\(\(\) =>\s*themeStore\.theme === "dark" \? brandEyeBlack : brandEyeWhite,\s*\)/
  )
  assert.doesNotMatch(source, /brandGlow/)
})
