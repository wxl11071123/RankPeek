import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('keeps best teammates and troublesome opponents columns at equal height', () => {
  const componentSource = readFileSync(new URL('./SummonerOverviewPanel.vue', import.meta.url), 'utf8')

  assert.match(componentSource, /\.relationship-section\s*\{[\s\S]*align-items:\s*stretch;/)
  assert.match(componentSource, /\.relationship-col\s*\{[\s\S]*height:\s*100%;/)
  assert.match(componentSource, /\.relationship-list\s*\{[\s\S]*flex:\s*1;/)
})

test('aligns relationship section height with the left summoner card in embedded layout', () => {
  const componentSource = readFileSync(new URL('./SummonerOverviewPanel.vue', import.meta.url), 'utf8')

  assert.match(componentSource, /\.overview-panel\.embedded\s*\{[\s\S]*align-items:\s*stretch;/)
  assert.match(componentSource, /\.overview-panel\.embedded\s*>\s*\.user-card,\s*[\s\S]*\.overview-panel\.embedded\s*>\s*\.relationship-section\s*\{[\s\S]*height:\s*100%;/)
})

test('stretches rank cards to match recent status height in embedded overview', () => {
  const componentSource = readFileSync(new URL('./SummonerOverviewPanel.vue', import.meta.url), 'utf8')

  assert.match(componentSource, /\.rank-cards\s*\{[\s\S]*align-self:\s*stretch;/)
  assert.match(componentSource, /\.rank-card\s*\{[\s\S]*height:\s*100%;/)
})

test('formats tier labels with Chinese rank names', () => {
  const componentSource = readFileSync(new URL('./SummonerOverviewPanel.vue', import.meta.url), 'utf8')

  assert.match(componentSource, /MASTER:\s*'超凡大师'/)
  assert.match(componentSource, /GRANDMASTER:\s*'傲世宗师'/)
  assert.match(componentSource, /CHALLENGER:\s*'最强王者'/)
  assert.match(componentSource, /EMERALD:\s*'翡翠'/)
})

test('shows duo queue label and uses larger rank card typography', () => {
  const componentSource = readFileSync(new URL('./SummonerOverviewPanel.vue', import.meta.url), 'utf8')

  assert.match(componentSource, /<span class="rank-label">单双排<\/span>/)
  assert.match(componentSource, /\.rank-label\s*\{[\s\S]*font-size:\s*16px;/)
  assert.match(componentSource, /\.rank-tier\s*\{[\s\S]*font-size:\s*16px;/)
  assert.match(componentSource, /\.rank-img\s*\{[\s\S]*width:\s*72px;[\s\S]*height:\s*72px;/)
})

test('stacks rank card content in the requested vertical order with unified text sizing', () => {
  const componentSource = readFileSync(new URL('./SummonerOverviewPanel.vue', import.meta.url), 'utf8')

  assert.match(componentSource, /<div class="rank-card">\s*<span class="rank-label">单双排<\/span>\s*<img class="rank-img"[\s\S]*?<div class="rank-tier">[\s\S]*?<\/div>\s*<div class="win-rate-badge">[\s\S]*?<\/div>\s*<div class="rank-wl">/)
  assert.match(componentSource, /<div class="rank-card">\s*<span class="rank-label">灵活组排<\/span>\s*<img class="rank-img"[\s\S]*?<div class="rank-tier">[\s\S]*?<\/div>\s*<div class="win-rate-badge">[\s\S]*?<\/div>\s*<div class="rank-wl">/)
  assert.match(componentSource, /\.rank-card\s*\{[\s\S]*display:\s*flex;[\s\S]*flex-direction:\s*column;[\s\S]*align-items:\s*flex-start;/)
  assert.match(componentSource, /\.win-rate-badge\s*\{[\s\S]*font-size:\s*16px;/)
  assert.match(componentSource, /\.rank-wl\s*\{[\s\S]*font-size:\s*16px;/)
})

test('uses larger teammate and opponent card visuals', () => {
  const componentSource = readFileSync(new URL('./SummonerOverviewPanel.vue', import.meta.url), 'utf8')

  assert.match(componentSource, /\.section-header\s*\{[\s\S]*font-size:\s*16px;/)
  assert.match(componentSource, /\.relationship-avatar\s*\{[\s\S]*width:\s*36px;[\s\S]*height:\s*36px;/)
  assert.match(componentSource, /\.relationship-name\s*\{[\s\S]*font-size:\s*16px;/)
  assert.match(componentSource, /\.relationship-rate\s*\{[\s\S]*font-size:\s*16px;/)
  assert.match(componentSource, /\.relationship-list\s*>\s*\.empty-text\s*\{[\s\S]*font-size:\s*15px;/)
})
