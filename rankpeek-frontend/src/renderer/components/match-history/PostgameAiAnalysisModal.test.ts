import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

const modalUrl = new URL('./PostgameAiAnalysisModal.vue', import.meta.url)

test('postgame AI analysis modal compiles partial review JSON instead of showing raw JSON while streaming', () => {
  assert.equal(existsSync(modalUrl), true)
  const source = readFileSync(modalUrl, 'utf8')

  assert.match(source, /parsePartialPostgameAiStructuredResult/)
  assert.match(source, /displayedPostgameReview/)
  assert.match(source, /partialPostgameReview/)
  assert.match(source, /v-if="displayedPostgameReview"/)
  assert.match(source, /v-if="structuredPostgameReview"[\s\S]*class="postgame-ai-analysis-share"/)
  assert.match(source, /v-else-if="hasStreamOutput && mode === 'review'"/)
  assert.match(source, /v-else-if="hasStreamOutput && streamText && \(mode !== 'review' \|\| props\.streamState === 'completed'\)"/)
})

test('postgame AI analysis modal exposes review and praise copy with clickable start button', () => {
  assert.equal(existsSync(modalUrl), true)
  const source = readFileSync(modalUrl, 'utf8')

  assert.match(source, /type PostgameAiAnalysisMode = 'review' \| 'praise'/)
  assert.match(source, /type PostgameAiStreamState = 'idle' \| 'preparing' \| 'streaming' \| 'completed' \| 'failed'/)
  assert.match(source, /open: boolean/)
  assert.match(source, /mode\?: PostgameAiAnalysisMode/)
  assert.match(source, /streamState\?: PostgameAiStreamState/)
  assert.match(source, /streamText\?: string/)
  assert.match(source, /streamError\?: string/)
  assert.match(source, /streamUsage\?: PostgameAiTokenUsage \| null/)
  assert.match(source, /championIdByName\?: Record<string, number>/)
  assert.match(source, /mode: 'review'/)
  assert.match(source, /streamState: 'idle'/)
  assert.match(source, /const modalTitle = computed/)
  assert.match(source, /const modalDescription = computed/)
  assert.match(source, /const primaryButtonText = computed/)
  assert.match(source, /emit\('start-analysis'\)/)
  assert.match(source, /class="postgame-ai-analysis-start"[\s\S]*:disabled="isBusy"[\s\S]*@click="emitStartAnalysis"[\s\S]*\{\{ primaryButtonText \}\}/)
  assert.doesNotMatch(source, /apiClient|fetch\(|axios|DeepSeek|deepseek|build.*Snapshot|streamGaming|sqlite|localMatchCache/i)
})

test('postgame AI analysis modal shows streaming text and failed errors', () => {
  assert.equal(existsSync(modalUrl), true)
  const source = readFileSync(modalUrl, 'utf8')

  assert.match(source, /props\.streamState === 'streaming' \|\| props\.streamState === 'completed'/)
  assert.match(source, /class="postgame-ai-analysis-stream-text"[\s\S]*streamText/)
  assert.match(source, /class="postgame-ai-token-usage"/)
  assert.match(source, /Token 用量/)
  assert.match(source, /缓存命中/)
  assert.match(source, /估算成本/)
  assert.match(source, /大陆 API 人民币价格/)
  assert.match(source, /props\.streamState === 'failed'/)
  assert.match(source, /class="postgame-ai-analysis-error"[\s\S]*streamError/)
  assert.match(source, /props\.streamState === 'preparing' \|\| props\.streamState === 'streaming'/)
  assert.match(source, /正在接收 rankpeek-server stream/)
  assert.doesNotMatch(source, /mock stream/)
})

test('postgame AI analysis modal provides overlay, escape, close button, and cancel dismissal', () => {
  assert.equal(existsSync(modalUrl), true)
  const source = readFileSync(modalUrl, 'utf8')

  assert.match(source, /role="dialog"/)
  assert.match(source, /aria-modal="true"/)
  assert.match(source, /aria-labelledby="postgame-ai-analysis-title"/)
  assert.match(source, /emit\('close'\)/)
  assert.match(source, /emit\('cancel-analysis'\)/)
  assert.match(source, /@click\.self="requestClose"/)
  assert.match(source, /event\.key === 'Escape'/)
  assert.match(source, /requestClose\(\)/)
  assert.match(source, /document\.addEventListener\('keydown', handleKeydown\)/)
  assert.match(source, /document\.removeEventListener\('keydown', handleKeydown\)/)
  assert.match(source, /aria-label=/)
})

test('postgame AI analysis ladu chart keeps champion names and phrases visually compact', () => {
  assert.equal(existsSync(modalUrl), true)
  const source = readFileSync(modalUrl, 'utf8')

  const playerRule = source.match(/\.postgame-ladu-player \{[\s\S]*?\}/)?.[0] ?? ''
  const playersRule = source.match(/\.postgame-ladu-players \{[\s\S]*?\}/)?.[0] ?? ''
  const phraseRule = [...source.matchAll(/\.postgame-ladu-player span:last-child \{[\s\S]*?\}/g)]
    .map(match => match[0])
    .find(rule => rule.includes('line-height')) ?? ''

  assert.match(playersRule, /align-content:\s*center/)
  assert.match(playersRule, /align-items:\s*center/)
  assert.match(playerRule, /align-content:\s*center/)
  assert.match(playerRule, /align-items:\s*center/)
  assert.match(playerRule, /gap:\s*[01]px 7px/)
  assert.match(phraseRule, /line-height:\s*1\.5/)
  assert.match(phraseRule, /white-space:\s*normal/)
  assert.match(phraseRule, /overflow:\s*visible/)
  assert.doesNotMatch(phraseRule, /-webkit-line-clamp/)
})

test('postgame AI analysis modal renders structured ladu chart and image export', () => {
  assert.equal(existsSync(modalUrl), true)
  const source = readFileSync(modalUrl, 'utf8')

  assert.match(source, /parsePostgameAiStructuredResult/)
  assert.match(source, /downloadPostgameReviewImage/)
  assert.match(source, /resolvePostgameReviewPlayerIconUrl\(player, props\.rosterPlayers, getChampionIconUrl, props\.championIdByName\)/)
  assert.match(source, /championIdByName: props\.championIdByName/)
  assert.match(source, /structuredPostgameReview/)
  assert.match(source, /class="postgame-ladu-chart"/)
  assert.match(source, /class="postgame-ladu-label"/)
  assert.match(source, /class="postgame-ladu-summary"/)
  assert.match(source, /class="postgame-ai-analysis-share"/)
  assert.match(source, /postgameReviewRows/)
  assert.match(source, /row\.label/)
  assert.match(source, /生成图片/)
  assert.match(source, /从夯到拉/)
})
