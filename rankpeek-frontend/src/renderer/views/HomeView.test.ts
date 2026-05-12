import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function extractRule(source: string, selector: string) {
  const normalizedSource = source.replace(/\r\n/g, '\n')
  const start = normalizedSource.indexOf(selector)
  assert.notEqual(start, -1, `${selector} should exist`)

  const open = normalizedSource.indexOf('{', start)
  assert.notEqual(open, -1, `${selector} should have a body`)

  let depth = 0
  for (let index = open; index < normalizedSource.length; index += 1) {
    if (normalizedSource[index] === '{') {
      depth += 1
    }

    if (normalizedSource[index] === '}') {
      depth -= 1
      if (depth === 0) {
        return normalizedSource.slice(open + 1, index)
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

test('home analyze button prepares coach summary input without creating a fake AI report', () => {
  const source = readFileSync(new URL('./HomeView.vue', import.meta.url), 'utf8')
  const runAnalysisFunction = source.match(/async function runAnalysis\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(source, /import \{ prepareCoachSummaryGeneration \} from '@\/services\/coachSummaryInputSnapshot'/)
  assert.match(source, /const coachAnalysisBusy = ref\(false\)/)
  assert.match(source, /async function runAnalysis\(\)/)
  assert.match(source, /prepareCoachSummaryGeneration\(\{[\s\S]*accountPuuid: puuid[\s\S]*\}\)/)
  assert.match(source, /AI_COACH_PREPARING_NOTICE/)
  assert.match(source, /AI_COACH_PARTIAL_TIMELINE_NOTICE/)
  assert.match(source, /AI_COACH_SNAPSHOT_INTEGRITY_FAILED_NOTICE/)
  assert.match(source, /result\.status === 'snapshot_integrity_failed'/)
  assert.match(source, /onHydrationProgress: \(progress\) =>/)
  assert.match(source, /setCoachProgressNotice\(`正在补全第 \$\{progress\.current\}\/\$\{progress\.total\} 局对局详情\.\.\.`\)/)
  assert.match(source, /console\.info\('RankPeek coach_summary input snapshot ready:', result\.snapshot\)/)
  assert.match(source, /showCoachNotice\(result\.message\)/)
  assert.match(source, /AI_COACH_ACCOUNT_MISSING_NOTICE/)
  assert.match(source, /:disabled="coachAnalysisBusy"/)
  assert.doesNotMatch(runAnalysisFunction, /saveAnalysisResult|saveServerAiFinalResultToLocal|router\.push|name: 'ai-analysis'|openCoachReportModal/)
})

test('home coach report cards load local coach summaries and open report modal by id', () => {
  const source = readFileSync(new URL('./HomeView.vue', import.meta.url), 'utf8')
  const openFunction = source.match(/async function openCoachReport\([\s\S]*?\n\}/)?.[0] || ''

  assert.match(source, /import CoachSummaryReportModal from '@\/components\/CoachSummaryReportModal\.vue'/)
  assert.match(source, /loadLocalAiAnalysisResults/)
  assert.match(source, /analysisType:\s*'coach_summary'/)
  assert.match(source, /limit:\s*6/)
  assert.match(source, /<AICoachCards[\s\S]*:reports="coachReports"[\s\S]*@open-report="openCoachReport"/)
  assert.match(source, /const coachReportModalOpen = ref\(false\)/)
  assert.match(source, /const activeCoachReport = ref<CoachSummaryReportV1 \| null>\(null\)/)
  assert.match(source, /async function openCoachReport\(/)
  assert.match(source, /getAnalysisResultById\(Number\(report\.id\)\)/)
  assert.match(source, /parseCoachSummaryReportOutput\(result\.data\.outputJson\)/)
  assert.match(source, /<CoachSummaryReportModal[\s\S]*:open="coachReportModalOpen"[\s\S]*:report="activeCoachReport"[\s\S]*@close="closeCoachReportModal"/)
  assert.doesNotMatch(openFunction, /router\.push|name:\s*'CoachSummaryReport'|\/reports/)
  assert.doesNotMatch(source, /listMatchRecordsByAccount|getMatchDetail|getGameDetail|getGameTimeline|findAnalysisByInputHash/)
})

test('home wires coach report modal navigation to the current local reports list', () => {
  const source = readFileSync(new URL('./HomeView.vue', import.meta.url), 'utf8')

  assert.match(source, /const activeCoachReportIndex = ref\(-1\)/)
  assert.match(source, /async function openCoachReport\(report: HomeCoachReport \| null, index: number\)/)
  assert.match(source, /activeCoachReportIndex\.value = index/)
  assert.match(source, /async function openCoachReportAtIndex\(index: number\)/)
  assert.match(source, /function navigateCoachReport\(delta: number\)/)
  assert.match(source, /coachReports\.value\.length/)
  assert.match(source, /openCoachReportAtIndex\(nextIndex\)/)
  assert.match(source, /:can-navigate="coachReports\.length > 1 && !coachReportPreview"/)
  assert.match(source, /:active-index="activeCoachReportIndex"/)
  assert.match(source, /:report-count="coachReports\.length"/)
  assert.match(source, /@previous="navigateCoachReport\(-1\)"/)
  assert.match(source, /@next="navigateCoachReport\(1\)"/)
})

test('home dev placeholder opens the dev report preview without saving a fake report', () => {
  const source = readFileSync(new URL('./HomeView.vue', import.meta.url), 'utf8')
  const openFunction = source.match(/async function openCoachReport\([\s\S]*?\n\}/)?.[0] || ''

  assert.match(source, /import \{ DEV_COACH_SUMMARY_REPORT_PREVIEW \} from '@\/services\/coachSummaryReportPreview'/)
  assert.match(openFunction, /if \(!report\?\.id\) \{[\s\S]*if \(import\.meta\.env\.DEV\) \{[\s\S]*openCoachReportModal\(DEV_COACH_SUMMARY_REPORT_PREVIEW, \{ preview: true \}\)/)
  assert.match(source, /showCoachNotice\(\)/)
  assert.match(source, /function closeCoachReportModal\(\)/)
  assert.match(source, /coachReportModalOpen\.value = false/)
  assert.doesNotMatch(openFunction, /saveAnalysisResult|saveServerAiFinalResultToLocal|router\.push/)
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

test('home fortune draw uses the stable local-date daily fortune key', () => {
  const source = readFileSync(new URL('./HomeView.vue', import.meta.url), 'utf8')
  const drawFunction = source.match(/function drawFortune\(\) \{[\s\S]*?\n\}/)?.[0] || ''

  assert.doesNotMatch(drawFunction, /TEMP: disable daily fortune limit/)
  assert.doesNotMatch(drawFunction, /iterationKey/)
  assert.doesNotMatch(drawFunction, /new Date\(\)\.toISOString\(\)/)
  assert.match(drawFunction, /drawDailyFortune\(fortuneRecord\.value\)/)
  assert.match(source, /:disabled="fortuneRolling \|\| fortuneAlreadyDrawn"/)
  assert.match(source, /t\('home\.fortuneOnceDaily'\)[\s\S]*t\('home\.fortuneDisclaimer'\)/)
})
