import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

const modalUrl = new URL('./GamingAiAnalysisModal.vue', import.meta.url)

test('gaming AI analysis modal exposes accessible dialog structure and close controls', () => {
  assert.equal(existsSync(modalUrl), true)
  const source = readFileSync(modalUrl, 'utf8')

  assert.match(source, /role="dialog"/)
  assert.match(source, /aria-modal="true"/)
  assert.match(source, /aria-labelledby="gaming-ai-analysis-title"/)
  assert.match(source, /emit\('close'\)/)
  assert.match(source, /@click\.self="emitClose"/)
  assert.match(source, /event\.key === 'Escape'/)
  assert.match(source, /document\.addEventListener\('keydown', handleKeydown\)/)
  assert.match(source, /document\.removeEventListener\('keydown', handleKeydown\)/)
})

test('gaming AI analysis modal renders mode title, preview notice, and player insight list', () => {
  const source = readFileSync(modalUrl, 'utf8')

  assert.match(source, /preview: GamingAiAnalysisPreview \| null/)
  assert.match(source, /serverSyncState\?: 'idle' \| 'syncing' \| 'synced' \| 'failed'/)
  assert.match(source, /serverSyncMessage\?: string/)
  assert.match(source, /\{\{ preview\.title \}\}/)
  assert.match(source, /\{\{ preview\.subtitle \}\}/)
  assert.match(source, /AI 占位/)
  assert.match(source, /本地规则预览/)
  assert.match(source, /v-for="player in preview\.players"/)
  assert.match(source, /player\.verdict/)
  assert.match(source, /player\.kdaText/)
  assert.match(source, /player\.winRateText/)
  assert.match(source, /player\.damageRateText/)
  assert.match(source, /player\.sampleText/)
  assert.match(source, /preview\.laneAdvice/)
})

test('gaming AI analysis modal shows local preview and lightweight server sync status', () => {
  const source = readFileSync(modalUrl, 'utf8')

  assert.match(source, /const serverSyncText = computed/)
  assert.match(source, /正在整理并发送临时数据/)
  assert.match(source, /临时数据已发送到本地服务器 mock/)
  assert.match(source, /服务器暂不可用，当前展示本地规则预览/)
  assert.match(source, /正式 AI 结果/)
  assert.match(source, /class="gaming-ai-analysis-sync"/)
  assert.match(source, /serverSyncText/)
})

test('gaming AI analysis modal has empty state and lightweight scrollable report styling', () => {
  const source = readFileSync(modalUrl, 'utf8')

  assert.match(source, /当前还没有可用玩家数据/)
  assert.match(source, /\.gaming-ai-analysis-overlay[\s\S]*position:\s*fixed/)
  assert.match(source, /backdrop-filter:\s*blur/)
  assert.match(source, /width:\s*min\(920px, calc\(100vw - 64px\)\)/)
  assert.match(source, /max-height:\s*calc\(100vh - 64px\)/)
  assert.match(source, /\.gaming-ai-analysis-body[\s\S]*overflow-y:\s*auto/)
  assert.match(source, /--bg-secondary/)
  assert.match(source, /--bg-tertiary/)
  assert.match(source, /--border-color/)
  assert.match(source, /--text-primary/)
  assert.match(source, /--text-secondary/)
  assert.match(source, /--accent-rgb/)
})

test('gaming AI analysis modal stays local-only without AI provider calls', () => {
  const source = readFileSync(modalUrl, 'utf8')

  assert.doesNotMatch(source, /DeepSeek|deepseek|OpenAI|fetch\(|axios|apiClient|serverAi/i)
  assert.doesNotMatch(source, /chart\.js|echarts/i)
})
