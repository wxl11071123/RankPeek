import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

const modalUrl = new URL('./PostgameAiAnalysisModal.vue', import.meta.url)

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
  assert.match(source, /props\.streamState === 'failed'/)
  assert.match(source, /class="postgame-ai-analysis-error"[\s\S]*streamError/)
  assert.match(source, /props\.streamState === 'preparing' \|\| props\.streamState === 'streaming'/)
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
