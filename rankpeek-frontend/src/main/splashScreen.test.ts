import test from 'node:test'
import assert from 'node:assert/strict'

import { buildSplashHtml, getSplashPalette } from './splashScreen.ts'

test('dark-mode splash uses the black eye logo on a light surface', () => {
  assert.deepEqual(getSplashPalette(true), {
    logoFile: 'rankpeek-eye-black.png',
    surfaceColor: '#f5f5f0',
    glowColor: 'rgba(10, 16, 30, 0.16)',
    labelColor: '#111827'
  })
})

test('light-mode splash uses the white eye logo on a dark surface', () => {
  assert.deepEqual(getSplashPalette(false), {
    logoFile: 'rankpeek-eye-white.png',
    surfaceColor: '#04060d',
    glowColor: 'rgba(255, 255, 255, 0.18)',
    labelColor: '#f8fafc'
  })
})

test('splash markup centers the logo and applies a blinking animation', () => {
  const html = buildSplashHtml({
    logoUrl: 'file:///branding/rankpeek-eye-black.png',
    surfaceColor: '#f5f5f0',
    glowColor: 'rgba(10, 16, 30, 0.16)',
    labelColor: '#111827'
  })

  assert.match(html, /class="splash-logo"/)
  assert.match(html, /justify-content:\s*center/)
  assert.match(html, /@keyframes blink/)
  assert.match(html, /animation:\s*blink 1\.6s ease-in-out infinite/)
  assert.match(html, /等待 RankPeek 启动中/)
})
