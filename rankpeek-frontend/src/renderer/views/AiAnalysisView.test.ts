import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

const viewUrl = new URL('./AiAnalysisView.vue', import.meta.url)

function readRendererFile(path: string) {
  return readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')
}

function readViewSource() {
  assert.equal(existsSync(viewUrl), true)
  return readFileSync(viewUrl, 'utf8')
}

test('AI report center uses local provider and cost clients instead of account credits', () => {
  const source = readViewSource()

  assert.match(source, /getLocalAiSettings/)
  assert.match(source, /getLocalCostSummary/)
  assert.match(source, /getLocalCostEvents/)
  assert.match(source, /localAiSettings/)
  assert.match(source, /localCostSummary/)
  assert.match(source, /recentAiCostEvents/)
  assert.match(source, /refreshLocalAiOverview/)
  assert.match(source, /aiAnalysis\.providerStatusTitle/)
  assert.match(source, /aiAnalysis\.costSummaryTitle/)
  assert.match(source, /aiAnalysis\.recentRunsTitle/)
  assert.match(source, /aiAnalysis\.manualCostShortcut/)

  assert.doesNotMatch(source, /rankpeekAuthClient|rankpeekCreditsClient/)
  assert.doesNotMatch(source, /RankPeekAuthSession|RankPeekCreditLedgerEntry/)
  assert.doesNotMatch(source, /getRankPeekCreditBalance|getRankPeekCreditLedger/)
  assert.doesNotMatch(source, /pointsBalance|pointsAction|billingPointsDelta/)
})

test('AI report center keeps account-scoped local history and read-only postgame detail', () => {
  const source = readViewSource()
  const localAiAnalysis = readRendererFile('services/localAiAnalysis.ts')

  assert.match(source, /loadLocalAiAnalysisResults/)
  assert.match(source, /buildAccountAnalysisInputSnapshot/)
  assert.match(source, /gameStore\.currentSummoner/)
  assert.match(source, /accountPuuid/)
  assert.match(source, /reportTypeTabs = computed/)
  assert.match(source, /filteredAnalysisResults = computed/)
  assert.match(source, /PostgameAiAnalysisModal/)
  assert.match(source, /selectedPostgameReplayText/)
  assert.match(source, /startSavedPostgameReplay/)
  assert.match(source, /show-start-button="false"/)
  assert.match(localAiAnalysis, /postgameRun\?: PostgameAiRunOutputV1/)
})

test('AI report center local cost copy exists in both locales', () => {
  const zhCN = readRendererFile('i18n/locales/zh-CN.ts')
  const enUS = readRendererFile('i18n/locales/en-US.ts')

  for (const key of [
    'aiAnalysis.providerStatusTitle',
    'aiAnalysis.providerEnabled',
    'aiAnalysis.providerDisabled',
    'aiAnalysis.providerConfigureAction',
    'aiAnalysis.costSummaryTitle',
    'aiAnalysis.costToday',
    'aiAnalysis.costMonth',
    'aiAnalysis.costAi',
    'aiAnalysis.costManual',
    'aiAnalysis.costTotal',
    'aiAnalysis.recentRunsTitle',
    'aiAnalysis.recentRunsEmpty',
    'aiAnalysis.manualCostShortcut',
    'aiAnalysis.localCostUnavailable'
  ]) {
    assert.ok(zhCN.includes(`'${key}'`), `zh-CN should include ${key}`)
    assert.ok(enUS.includes(`'${key}'`), `en-US should include ${key}`)
  }
})
