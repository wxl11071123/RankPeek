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

test('light-mode AI report glow assertions use the current coach card selectors', () => {
  const source = readFileSync(new URL('./HomeView.vue', import.meta.url), 'utf8')
  const coachCards = readFileSync(new URL('../components/AICoachCards.vue', import.meta.url), 'utf8')
  const homeCoachRule = extractRule(source, ':global([data-theme="light"] .home-view .coach-report-panel .ai-coach-cards)')
  const coachLightRule = extractRule(coachCards, ':global([data-theme="light"] .ai-coach-cards)')

  assert.match(coachCards, /\.ai-coach-cards:hover \.record-main-card:not\(\.record-main-card-leaving\)/)
  assert.match(coachCards, /\.ai-coach-cards:hover \.record-stack-card/)
  assert.match(homeCoachRule, /--record-card-border-hover:\s*rgba\(41, 151, 255, 0\.42\)/)
  assert.match(coachLightRule, /--record-card-hover-shadow:/)
  assert.doesNotMatch(source, /coach-stack-card|coach-expanded-card/)
  assert.doesNotMatch(homeCoachRule, /rgba\(100,\s*116,\s*139/)
})

test('coach report panel uses the same outer hover strength as adjacent home modules', () => {
  const source = readFileSync(new URL('./HomeView.vue', import.meta.url), 'utf8')
  const baseRule = extractRule(source, '.account-panel,\n.ai-analysis-card,\n.fortune-card,\n.coach-report-panel')
  const hoverRule = extractRule(source, '.ai-analysis-card:hover,\n.fortune-card:hover,\n.coach-report-panel:hover')

  assert.match(baseRule, /background:\s*var\(--bg-secondary\)/)
  assert.match(baseRule, /border:\s*1px solid var\(--border-color\)/)
  assert.match(baseRule, /border-radius:\s*12px/)
  assert.match(baseRule, /transition:\s*background 0\.3s ease, border-color 0\.3s ease, box-shadow 0\.3s ease/)
  assert.match(hoverRule, /background:\s*var\(--home-ai-hover-bg\)/)
  assert.match(hoverRule, /border-color:\s*var\(--home-ai-hover-border\)/)
  assert.match(hoverRule, /box-shadow:\s*var\(--home-ai-hover-shadow\)/)
  assert.match(hoverRule, /animation:\s*home-ai-breathe 2\.6s ease-in-out infinite/)
})

test('coach report panel keeps its outer hover glow above the embedded coach cards', () => {
  const source = readFileSync(new URL('./HomeView.vue', import.meta.url), 'utf8')
  const glowOverlayRule = extractRule(source, '.coach-report-panel.surface-glow::before')
  const coachCardsRule = extractRule(source, '.coach-report-panel :deep(.ai-coach-cards)')

  assert.match(glowOverlayRule, /z-index:\s*2/)
  assert.match(coachCardsRule, /position:\s*relative/)
  assert.match(coachCardsRule, /z-index:\s*1/)
})

test('home refresh account button uses the shared refresh icon button', () => {
  const source = readFileSync(new URL('./HomeView.vue', import.meta.url), 'utf8')
  const refreshFunction = source.match(/async function handleRefreshAccount\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(source, /import RefreshIconButton from '@\/components\/common\/RefreshIconButton\.vue'/)
  assert.match(source, /const accountRefreshBusy = ref\(false\)/)
  assert.match(source, /<RefreshIconButton[\s\S]*:aria-label="accountRefreshBusy \? t\('common\.refreshing'\) : t\('home\.refreshAccount'\)"[\s\S]*:loading="accountRefreshBusy"[\s\S]*@click="handleRefreshAccount"/)
  assert.doesNotMatch(source, /@click="gameStore\.refreshSummoner"/)
  assert.match(refreshFunction, /if \(accountRefreshBusy\.value\) \{[\s\S]*return[\s\S]*\}/)
  assert.match(refreshFunction, /accountRefreshBusy\.value = true[\s\S]*await gameStore\.refreshSummoner\(\)[\s\S]*finally[\s\S]*accountRefreshBusy\.value = false/)
})

test('home rank badges show loading or failure instead of immediate unranked fallback', () => {
  const source = readFileSync(new URL('./HomeView.vue', import.meta.url), 'utf8')

  assert.match(source, /const accountRankStatus = computed<RankLoadStatus>/)
  assert.match(source, /gameStore\.rankLoading[\s\S]*return 'loading'/)
  assert.match(source, /gameStore\.rankError \? 'error' : 'loaded'/)
  assert.match(source, /formatRankTierPart\(soloRank, accountRankStatus\)/)
  assert.match(source, /formatRankTierPart\(flexRank, accountRankStatus\)/)
  assert.match(source, /status === 'loading'[\s\S]*t\('overview\.rankLoading'\)/)
  assert.match(source, /status === 'error'[\s\S]*t\('overview\.rankFailed'\)/)
})
