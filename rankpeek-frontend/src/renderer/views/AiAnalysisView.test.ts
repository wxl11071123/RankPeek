import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const viewUrl = new URL('./AiAnalysisView.vue', import.meta.url)
const rendererRoot = fileURLToPath(new URL('..', import.meta.url))

function readRendererFile(path: string) {
  return readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')
}

test('AI analysis view is a local-results shell without model or backend calls', () => {
  assert.equal(existsSync(viewUrl), true)

  const source = readFileSync(viewUrl, 'utf8')

  assert.match(source, /import \{ useGameStore \} from '@\/stores\/game'/)
  assert.match(source, /loadLocalAiAnalysisResults/)
  assert.match(source, /buildAccountAnalysisInputSnapshot/)
  assert.match(source, /isServerAiEnabled/)
  assert.match(source, /prepareAnalysisInputSnapshot/)
  assert.match(source, /gameStore\.currentSummoner/)
  assert.match(source, /accountPuuid/)
  assert.match(source, /limit:\s*20/)
  assert.match(source, /offset:\s*0/)
  assert.match(source, /preparedSnapshot\.source\.matchRecordCount/)
  assert.match(source, /preparedSnapshot\.source\.matchDetailCount/)
  assert.match(source, /preparedSnapshot\.source\.hasEnoughData/)
  assert.match(source, /preparedSnapshot\.inputHash/)
  assert.match(source, /aiAnalysis\.featurePreGame/)
  assert.match(source, /aiAnalysis\.featurePostGame/)
  assert.match(source, /aiAnalysis\.featureCoach/)
  assert.match(source, /aiAnalysis\.featureFun/)
  assert.match(source, /aiAnalysis\.dataPrepTitle/)
  assert.match(source, /aiAnalysis\.prepareInput/)
  assert.match(source, /aiAnalysis\.serverAiTitle/)
  assert.match(source, /aiAnalysis\.serverAiStreamPlan/)
  assert.match(source, /aiAnalysis\.serverAiAsyncPlan/)
  assert.doesNotMatch(source, /apiClient|DeepSeek|Kimi|OpenAI|fetch\(|axios|\/api\/.*ai/i)
  assert.doesNotMatch(source, /saveAnalysisResult|findAnalysisByInputHash|upsertAnalysis/)
  assert.doesNotMatch(source, /SummonerMatchHistoryPanel|MatchDetailModal/)
})

test('AI analysis route, sidebar entry, icon, and locale keys are wired', () => {
  const router = readRendererFile('router/index.ts')
  const sidebar = readRendererFile('components/layout/Sidebar.vue')
  const zhCN = readRendererFile('i18n/locales/zh-CN.ts')
  const enUS = readRendererFile('i18n/locales/en-US.ts')
  const iconPath = resolve(rendererRoot, 'assets/icons/nav-ai-spark.svg')

  assert.match(router, /path:\s*'\/ai-analysis'[\s\S]*name:\s*'AiAnalysis'[\s\S]*import\('@\/views\/AiAnalysisView\.vue'\)[\s\S]*titleKey:\s*'nav\.aiAnalysis'/)
  assert.match(sidebar, /import aiAnalysisIconSvg from '@\/assets\/icons\/nav-ai-spark\.svg\?raw'/)
  assert.match(sidebar, /path:\s*'\/match-history'[\s\S]*path:\s*'\/ai-analysis'[\s\S]*path:\s*'\/settings'/)
  assert.match(sidebar, /\{ path: '\/ai-analysis', iconSvg: aiAnalysisIconSvg, labelKey: 'nav\.aiAnalysis' \}/)
  assert.match(zhCN, /'nav\.aiAnalysis': 'AI 分析'/)
  assert.match(enUS, /'nav\.aiAnalysis': 'AI Analysis'/)
  assert.match(zhCN, /'aiAnalysis\.dataPrepTitle':/)
  assert.match(enUS, /'aiAnalysis\.dataPrepTitle': 'Data Preparation Status'/)
  assert.match(zhCN, /'aiAnalysis\.prepareInput':/)
  assert.match(enUS, /'aiAnalysis\.prepareInput': 'Prepare Analysis Input'/)
  assert.match(zhCN, /'aiAnalysis\.serverAiTitle':/)
  assert.match(enUS, /'aiAnalysis\.serverAiTitle': 'AI Service Status'/)
  assert.match(enUS, /'aiAnalysis\.serverAiStreamPlan':/)
  assert.match(enUS, /'aiAnalysis\.serverAiAsyncPlan':/)
  assert.equal(existsSync(iconPath), true)

  const svg = readFileSync(iconPath, 'utf8')
  assert.match(svg, /fill="none"/)
  assert.match(svg, /stroke="currentColor"/)
  assert.match(svg, /stroke-linecap="round"/)
  assert.match(svg, /stroke-linejoin="round"/)
})
