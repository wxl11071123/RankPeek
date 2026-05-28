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

function readViewSource() {
  assert.equal(existsSync(viewUrl), true)
  return readFileSync(viewUrl, 'utf8')
}

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

test('AI report center keeps account-scoped local results and local-snapshot logic without model calls', () => {
  const source = readViewSource()
  const localAiAnalysis = readRendererFile('services/localAiAnalysis.ts')
  const localDatabaseTypes = readRendererFile('types/localDatabase.ts')

  assert.match(source, /import \{ useGameStore \} from '@\/stores\/game'/)
  assert.match(source, /loadLocalAiAnalysisResults/)
  assert.match(source, /buildAccountAnalysisInputSnapshot/)
  assert.match(source, /prepareAnalysisInputSnapshot/)
  assert.match(source, /gameStore\.currentSummoner/)
  assert.match(source, /accountPuuid/)
  assert.match(source, /limit:\s*20/)
  assert.match(source, /offset:\s*0/)
  assert.match(source, /preparedSnapshot\.value = snapshot/)
  assert.match(source, /snapshot\.inputHash/)
  assert.match(localAiAnalysis, /listAnalysisResultsByAccount\(trimmedPuuid,\s*queryOptions\)/)
  assert.match(localDatabaseTypes, /interface AiAnalysisResult[\s\S]*accountPuuid: string[\s\S]*matchId: string \| null/)
  assert.doesNotMatch(source, /DeepSeek|Kimi|OpenAI|fetch\(|axios|\/api\/.*ai|streamPostgameAiAnalysis|createPostgameAiStreamRequest/i)
  assert.doesNotMatch(source, /saveAnalysisResult|findAnalysisByInputHash|upsertAnalysis/)
  assert.doesNotMatch(source, /SummonerMatchHistoryPanel|MatchDetailModal/)
  assert.doesNotMatch(source, /listAnalysisResultsByAccount\(['"]all|allAccounts|selectedAccountScope/)
})

test('AI report center product copy, points copy, sidebar entry, and icon are wired', () => {
  const sidebar = readRendererFile('components/layout/Sidebar.vue')
  const zhCN = readRendererFile('i18n/locales/zh-CN.ts')
  const enUS = readRendererFile('i18n/locales/en-US.ts')
  const iconPath = resolve(rendererRoot, 'assets/icons/nav-ai-spark.svg')

  assert.match(sidebar, /import aiAnalysisIconSvg from '@\/assets\/icons\/nav-ai-spark\.svg\?raw'/)
  assert.match(sidebar, /path:\s*'\/match-history'[\s\S]*path:\s*'\/ai-analysis'[\s\S]*path:\s*'\/settings'/)
  assert.match(sidebar, /\{ path: '\/ai-analysis', iconSvg: aiAnalysisIconSvg, labelKey: 'nav\.aiAnalysis' \}/)
  assert.match(zhCN, /'aiAnalysis\.title': 'AI \u62a5\u544a\u4e2d\u5fc3'/u)
  assert.match(zhCN, /'aiAnalysis\.subtitle': '\u6240\u6709\u62a5\u544a\u90fd\u4f1a\u6309\u8d26\u53f7\u4fdd\u5b58\u5728\u8fd9\u91cc\u3002'/u)
  assert.match(zhCN, /'aiAnalysis\.rankpeekAccountGuest': '\u672a\u767b\u5f55'/u)
  assert.match(zhCN, /'aiAnalysis\.pointsAction': '\u70b9\u6570'/u)
  assert.match(zhCN, /'aiAnalysis\.pointsBalance': '\{count\} \u70b9'/u)
  assert.match(zhCN, /'aiAnalysis\.featurePreGame': '\u8d5b\u524d\u5206\u6790'/u)
  assert.match(zhCN, /'aiAnalysis\.featurePostGame': '\u8d5b\u540e\u590d\u76d8'/u)
  assert.match(zhCN, /'aiAnalysis\.featurePraise': '\u5938\u5938\u673a'/u)
  assert.match(zhCN, /'aiAnalysis\.featureCoach': '\u7535\u5b50\u6559\u7ec3'/u)
  assert.match(zhCN, /'aiAnalysis\.preGamePositioning': '\u5f00\u5c40\u524d\u7684\u961f\u53cb\u4e0e\u5bf9\u624b\u98ce\u9669\u901f\u89c8\u3002'/u)
  assert.match(zhCN, /'aiAnalysis\.postGamePositioning': '\u5355\u5c40\u7ed3\u675f\u540e\u7684\u8868\u73b0\u590d\u76d8\u3002'/u)
  assert.match(zhCN, /'aiAnalysis\.praisePositioning': '\u6253\u5b8c\u5148\u628a\u4f60\u5938\u8212\u670d\u3002'/u)
  assert.match(zhCN, /'aiAnalysis\.coachPositioning': '\u9636\u6bb5\u6027\u7684\u957f\u671f\u8868\u73b0\u603b\u7ed3\u3002'/u)
  assert.match(zhCN, /'aiAnalysis\.historyTitle': '\u5386\u53f2\u62a5\u544a'/u)
  assert.match(zhCN, /'aiAnalysis\.emptyHistoryTitle': '\u6682\u65e0 AI \u62a5\u544a'/u)
  assert.match(enUS, /'aiAnalysis\.title': 'AI Report Center'/)
  assert.match(enUS, /'aiAnalysis\.subtitle': 'All reports are saved here by account.'/)
  assert.match(enUS, /'aiAnalysis\.pointsAction': 'Points'/)
  assert.match(enUS, /'aiAnalysis\.pointsBalance': '\{count\} points'/)
  assert.match(enUS, /'aiAnalysis\.featurePraise': 'Praise Machine'/)
  assert.match(enUS, /'aiAnalysis\.preGamePositioning': 'A fast risk read on teammates and opponents before the match.'/)
  assert.match(enUS, /'aiAnalysis\.postGamePositioning': 'A single-match performance review after the game.'/)
  assert.match(enUS, /'aiAnalysis\.praisePositioning': 'Get praised first after a match.'/)
  assert.match(enUS, /'aiAnalysis\.coachPositioning': 'A periodic summary of long-term performance.'/)
  assert.doesNotMatch(zhCN, /'aiAnalysis\.featureTitle': '\u0041\u0049 \u529f\u80fd\u5165\u53e3'/u)
  assert.doesNotMatch(zhCN, /'aiAnalysis\.dataPrepTitle':|'aiAnalysis\.serverAiTitle':|'aiAnalysis\.serverAiPhase':/)
  assert.doesNotMatch(zhCN, /'aiAnalysis\.localMatches':|'aiAnalysis\.localDetails':|'aiAnalysis\.checkLocalData':|'aiAnalysis\.inputHash':/)
  assert.doesNotMatch(zhCN, /'aiAnalysis\.preGameDescription':|'aiAnalysis\.postGameDescription':|'aiAnalysis\.praiseDescription':|'aiAnalysis\.coachDescription':/)
  assert.doesNotMatch(zhCN, /'aiAnalysis\.tagShareable':|'aiAnalysis\.tagWeeklyMonthly':/)
  assert.doesNotMatch(zhCN, /\u62a5\u544a\u6309\u82f1\u96c4\u8054\u76df\u8d26\u53f7\u4fdd\u5b58/u)
  assert.doesNotMatch(zhCN, /\u8fd9\u91cc\u8bf4\u660e\u6bcf\u7c7b\u62a5\u544a\u662f\u4ec0\u4e48/u)
  assert.doesNotMatch(zhCN, /\u5df2\u63a5\u5165\u5165\u53e3|\u672c\u5730\u9636\u6bb5|\u89c4\u5212\u4e2d|\u4f7f\u7528\u4f4d\u7f6e/u)
  assert.doesNotMatch(zhCN, /'aiAnalysis\.[^']+': '[^']*(?:\u6570\u636e\u72b6\u6001|\u672c\u5730\u6218\u7ee9|\u5bf9\u5c40\u8be6\u60c5|\u6570\u636e\u8be6\u60c5|\u68c0\u67e5\u672c\u5730\u6570\u636e)/u)
  assert.doesNotMatch(zhCN, /'aiAnalysis\.[^']+': '[^']*(?:AI \u670d\u52a1|\u5f53\u524d\u9636\u6bb5|\u672a\u63a5\u5165\u771f\u5b9e\u6a21\u578b)/u)
  assert.doesNotMatch(zhCN, /\u53ef\u5206\u4eab|\u5468\u62a5\u6708\u62a5/u)
  assert.doesNotMatch(zhCN, /\u5386\u53f2\u62a5\u544a\u4e2d\u5fc3/u)
  assert.equal(existsSync(iconPath), true)

  const svg = readFileSync(iconPath, 'utf8')
  assert.match(svg, /fill="none"/)
  assert.match(svg, /stroke="currentColor"/)
  assert.match(svg, /stroke-linecap="round"/)
  assert.match(svg, /stroke-linejoin="round"/)
})

test('AI report center removes the old feature placeholder cards', () => {
  const source = readViewSource()

  assert.doesNotMatch(source, /interface FeatureCard/)
  assert.doesNotMatch(source, /featureCards = computed<FeatureCard\[\]>/)
  assert.doesNotMatch(source, /class="feature-section"|class="feature-grid"|class="feature-card"/)
  assert.doesNotMatch(source, /feature-positioning|feature-title-row|tag-list/)
  assert.doesNotMatch(source, /v-for="card in featureCards"/)
  assert.doesNotMatch(source, /description:|card\.description/)
  assert.doesNotMatch(source, /t\('aiAnalysis\.preGameDescription'\)|t\('aiAnalysis\.postGameDescription'\)|t\('aiAnalysis\.praiseDescription'\)|t\('aiAnalysis\.coachDescription'\)/)
  assert.doesNotMatch(source, /t\('aiAnalysis\.tagShareable'\)|t\('aiAnalysis\.tagWeeklyMonthly'\)/)
  assert.doesNotMatch(source, /t\('aiAnalysis\.featureTitle'\)|AI 功能入口|AI Feature Entrances/)
  assert.doesNotMatch(source, /feature-status|statusLabel|feature-usage|usageLabel/)
  assert.doesNotMatch(source, /openFeatureCard|routeName/)
  assert.doesNotMatch(source, /goMatchHistory|goLiveGame|viewReports/)
})

test('AI report center hero keeps RankPeek login, game account, and points separated', () => {
  const source = readViewSource()

  assert.doesNotMatch(source, /centerEyebrow|class="eyebrow"|\u5386\u53f2\u62a5\u544a\u4e2d\u5fc3/u)
  assert.match(source, /import \{[\s\S]*getStoredRankPeekAuthSession[\s\S]*type RankPeekAuthSession[\s\S]*\} from '@\/services\/rankpeekAuthClient'/)
  assert.match(source, /import \{[\s\S]*getRankPeekCreditBalance[\s\S]*getRankPeekCreditLedger[\s\S]*type RankPeekCreditLedgerEntry[\s\S]*\} from '@\/services\/rankpeekCreditsClient'/)
  assert.match(source, /account-showcase-card/)
  assert.match(source, /rankpeek-account-value/)
  assert.match(source, /league-account-showcase/)
  assert.match(source, /balance-showcase/)
  assert.match(source, /balance-row/)
  assert.match(source, /balance-recharge-button/)
  assert.match(source, /rankpeekAccountLabel/)
  assert.match(source, /rankpeekBalanceLabel/)
  assert.match(source, /rankpeekCreditLedgerEntries/)
  assert.match(source, /t\('aiAnalysis\.pointsAction'\)/)
  assert.match(source, /currentSummonerProfileIconUrl/)
  assert.match(source, /getProfileIconUrl\(summoner\.profileIconId\)/)
  assert.match(source, /@error="markAssetLoadFailed"/)
  assert.match(source, /accountStatusLabel = computed\(\(\) => currentSummonerName\.value \|\| t\('aiAnalysis\.noAccountStatus'\)\)/)
  assert.match(source, /rankpeekAuthSession = ref<RankPeekAuthSession \| null>\(getStoredRankPeekAuthSession\(\)\)/)
  assert.match(source, /rankpeekAccountLabel = computed\(\(\) => rankpeekAuthSession\.value\?\.user\.email \?\? t\('aiAnalysis\.rankpeekAccountGuest'\)\)/)
  assert.match(source, /rankpeekBalanceLabel = computed\(\(\) => t\('aiAnalysis\.pointsBalance', \{ count: rankpeekCreditBalance\.value \?\? 0 \}\)\)/)
  assert.match(source, /getRankPeekCreditBalance\(session\.accessToken\)/)
  assert.match(source, /getRankPeekCreditLedger\(session\.accessToken\)/)
  assert.doesNotMatch(source, /\uffe5|0\.00|t\('aiAnalysis\.recharge'\)/u)
  assert.doesNotMatch(source, /pay|payment|stripe|checkout|invoice|rechargeApi/i)
  assert.doesNotMatch(source, /account-report-note|accountReportHint|noAccountReportHint/)
})

test('AI report center history section keeps account tabs and report cards without the fun filter tab', () => {
  const source = readViewSource()

  assert.match(source, /selectedReportType = ref<ReportTypeFilter>\('all'\)/)
  assert.match(source, /reportTypeTabs = computed/)
  assert.match(source, /filteredAnalysisResults = computed/)
  assert.match(source, /getReportCategory/)
  assert.match(source, /getReportCategoryLabel/)
  assert.match(source, /getReportTitle/)
  assert.match(source, /getReportScopeLabel/)
  assert.doesNotMatch(source, /t\('aiAnalysis\.historyScopeHint'\)|localReportCountLabel/)
  assert.match(source, /t\('aiAnalysis\.filterAll'\)/)
  assert.match(source, /t\('aiAnalysis\.featurePreGame'\)/)
  assert.match(source, /t\('aiAnalysis\.featurePostGame'\)/)
  assert.match(source, /t\('aiAnalysis\.featurePraise'\)/)
  assert.match(source, /t\('aiAnalysis\.featureCoach'\)/)
  assert.doesNotMatch(source, /\{\s*key:\s*'fun'[\s\S]{0,120}label:\s*t\('aiAnalysis\.featureFun'\)/)
  assert.match(source, /case 'fun':\s*return t\('aiAnalysis\.featureFun'\)/)
  assert.match(source, /v-for="tab in reportTypeTabs"/)
  assert.match(source, /filteredAnalysisResults/)
  assert.doesNotMatch(source, /result\.output\.highlights\.slice\(0,\s*3\)/)
  assert.doesNotMatch(source, /class="report-highlights"/)
  assert.match(source, /getReportTitle\(result\)/)
  assert.match(source, /getReportScopeLabel\(result\)/)
  assert.match(source, /currentSummonerName \|\| t\('aiAnalysis\.currentAccountFallback'\)/)
  assert.match(source, /result\.matchId \? t\('aiAnalysis\.singleMatchReport'\) : t\('aiAnalysis\.accountReport'\)/)
  assert.match(source, /result\.createdAtLabel/)
})

test('AI report center opens saved postgame runs with shared read-only modal rendering', () => {
  const source = readViewSource()
  const localAiAnalysis = readRendererFile('services/localAiAnalysis.ts')

  assert.match(source, /import PostgameAiAnalysisModal from '@\/components\/match-history\/PostgameAiAnalysisModal\.vue'/)
  assert.match(source, /selectedPostgameResult/)
  assert.match(source, /selectedPostgameRun/)
  assert.match(source, /function openReportDetail\(result: LocalAiAnalysisDisplayResult\)/)
  assert.match(source, /function closeReportDetail\(\)/)
  assert.match(source, /selectedPostgameReplayText/)
  assert.match(source, /selectedPostgameReplayState/)
  assert.match(source, /function startSavedPostgameReplay\(rawText: string\)/)
  assert.match(source, /function stopSavedPostgameReplay\(\)/)
  assert.match(source, /SAVED_POSTGAME_REPLAY_TARGET_DURATION_MS = 5200/)
  assert.match(source, /SAVED_POSTGAME_REPLAY_SENTENCE_DELAY_MS = 180/)
  assert.match(source, /SAVED_POSTGAME_REPLAY_COMMA_DELAY_MS = 80/)
  assert.match(source, /window\.setTimeout/)
  assert.match(source, /window\.clearTimeout/)
  assert.match(source, /startSavedPostgameReplay\(postgameRun\.rawOutputText\)/)
  assert.doesNotMatch(source, /function getReportUsageLabel\(result: LocalAiAnalysisDisplayResult\)/)
  assert.match(source, /selectedPostgameChampionIdByName/)
  assert.match(source, /function hydrateSelectedPostgameChampionNames\(\)/)
  assert.match(source, /apiClient\.getChampionOptions\(\)/)
  assert.doesNotMatch(source, /getGameDetail|buildPostgameAiReviewRosterPlayersFromGameDetail|hydrateSelectedPostgameRoster/)
  assert.match(source, /@click="openReportDetail\(result\)"/)
  assert.match(source, /@keydown\.enter="openReportDetail\(result\)"/)
  assert.doesNotMatch(source, /class="report-usage"/)
  assert.doesNotMatch(source, /getReportUsageLabel\(result\)/)
  assert.match(source, /<PostgameAiAnalysisModal[\s\S]*:open="Boolean\(selectedPostgameRun\)"[\s\S]*:mode="selectedPostgameRunMode"[\s\S]*:stream-state="selectedPostgameReplayState"[\s\S]*:stream-text="selectedPostgameReplayText"[\s\S]*:roster-players="selectedPostgameModalRosterPlayers"[\s\S]*:champion-id-by-name="selectedPostgameChampionIdByName"[\s\S]*:show-start-button="false"[\s\S]*@close="closeReportDetail"/)
  assert.doesNotMatch(source, /stream-state="completed"[\s\S]*:stream-text="selectedPostgameRun\.rawOutputText"/)
  assert.doesNotMatch(source, /:stream-usage="selectedPostgameRun\.usage"/)
  assert.match(localAiAnalysis, /postgameRun\?: PostgameAiRunOutputV1/)
  assert.match(localAiAnalysis, /normalizePostgameAiRunOutput/)
})

test('AI report center refreshes history when a local postgame run is saved', () => {
  const source = readViewSource()
  const persistence = readRendererFile('services/postgameAiRunPersistence.ts')

  assert.match(source, /onMounted/)
  assert.match(source, /onBeforeUnmount/)
  assert.match(source, /rankpeek:ai-analysis-result-saved/)
  assert.match(source, /function handleLocalAiAnalysisResultSaved\(\)[\s\S]*refreshRankPeekAccountState\(\)[\s\S]*refreshLocalAnalysisResults\(\)/)
  assert.match(persistence, /dispatchEvent\(new Event\('rankpeek:ai-analysis-result-saved'\)\)/)
})

test('postgame analysis modal supports read-only saved-result rendering', () => {
  const source = readRendererFile('components/match-history/PostgameAiAnalysisModal.vue')

  assert.match(source, /showStartButton\?: boolean/)
  assert.match(source, /showStartButton: true/)
  assert.match(source, /v-if="showStartButton"[\s\S]*class="postgame-ai-analysis-start"/)
})

test('AI report center exposes AI billing ledger instead of AI memory internals', () => {
  const source = readViewSource()
  const creditsClient = readRendererFile('services/rankpeekCreditsClient.ts')
  const zhCN = readRendererFile('i18n/locales/zh-CN.ts')
  const enUS = readRendererFile('i18n/locales/en-US.ts')

  assert.match(creditsClient, /RANKPEEK_CREDITS_LEDGER_ENDPOINT = '\/api\/credits\/ledger'/)
  assert.match(creditsClient, /export interface RankPeekCreditLedgerEntry/)
  assert.match(creditsClient, /export async function getRankPeekCreditLedger/)

  assert.match(source, /rankpeekCreditLedgerEntries = ref<RankPeekCreditLedgerEntry\[\]>\(\[\]\)/)
  assert.match(source, /rankpeekCreditLedgerLoading/)
  assert.match(source, /rankpeekCreditLedgerError/)
  assert.match(source, /loadRankPeekCreditLedger/)
  assert.match(source, /getRankPeekCreditLedger\(session\.accessToken\)/)
  assert.match(source, /class="ai-billing-section"/)
  assert.match(source, /class="billing-card"/)
  assert.match(source, /class="billing-ledger-list"/)
  assert.match(source, /getCreditLedgerEntryTitle\(entry\)/)
  assert.match(source, /formatCreditLedgerAmount\(entry\.amount\)/)
  assert.match(source, /formatCreditLedgerDate\(entry\.createdAt\)/)
  assert.match(source, /t\('aiAnalysis\.billingTitle'\)/)
  assert.match(source, /t\('aiAnalysis\.billingBalanceAfter'/)
  assert.doesNotMatch(source, /aiMemory|loadAiMemoryStats|exportAiMemory|getAiMemoryStats|ai-memory-section|memoryTypeDistribution/)

  for (const key of [
    'aiAnalysis.billingTitle',
    'aiAnalysis.billingDescription',
    'aiAnalysis.billingCurrentBalance',
    'aiAnalysis.billingAccount',
    'aiAnalysis.billingLoading',
    'aiAnalysis.billingLoginRequired',
    'aiAnalysis.billingUnavailable',
    'aiAnalysis.billingEmpty',
    'aiAnalysis.billingBalanceAfter',
    'aiAnalysis.billingAiCharge',
    'aiAnalysis.billingAiRefund',
    'aiAnalysis.billingAdminCredit',
    'aiAnalysis.billingAdminDebit',
    'aiAnalysis.billingAdjustment',
    'aiAnalysis.billingPointsDelta'
  ]) {
    assert.ok(zhCN.includes(`'${key}'`), `zh-CN should include ${key}`)
    assert.ok(enUS.includes(`'${key}'`), `en-US should include ${key}`)
  }

  assert.doesNotMatch(zhCN, /AI 记忆|导出 AI 记忆/)
  assert.doesNotMatch(enUS, /AI Memory|Export AI Memory/)
})

test('AI report center no longer renders data status or AI service support cards', () => {
  const source = readViewSource()

  assert.doesNotMatch(source, /support-grid|data-status-card|service-status-card/)
  assert.doesNotMatch(source, /t\('aiAnalysis\.dataPrepTitle'\)|t\('aiAnalysis\.localMatches'\)|t\('aiAnalysis\.localDetails'\)|t\('aiAnalysis\.dataPrepStatus'\)/)
  assert.doesNotMatch(source, /t\('aiAnalysis\.checkLocalData'\)|t\('aiAnalysis\.checkingData'\)|t\('aiAnalysis\.inputHash'\)/)
  assert.doesNotMatch(source, /t\('aiAnalysis\.serverAiTitle'\)|t\('aiAnalysis\.serverAiPhase'\)|serverAiStatusLabel|serverAiPhaseLabel|isServerAiEnabled/)
  assert.doesNotMatch(source, /class="secondary-action prep-action"|class="data-detail-line"|service-stage|prep-grid|prep-body/)
})

test('AI report center follows the shared module shell and hover glow contract', () => {
  const source = readViewSource()
  const variablesRule = extractRule(source, '.ai-analysis-view')
  const lightVariablesRule = extractRule(source, ':global([data-theme="light"] .ai-analysis-view)')
  const moduleBaseRule = extractRule(source, '.hero-panel,')
  const moduleHoverRule = extractRule(source, '.hero-panel:hover,')
  const tabHoverRule = extractRule(source, '.report-type-tab:hover,')
  const rechargeHoverRule = extractRule(source, '.balance-recharge-button:hover,')

  assert.match(variablesRule, /--ai-analysis-module-hover-rgb:\s*96,\s*176,\s*255/)
  assert.match(variablesRule, /--ai-analysis-module-hover-border:\s*rgba\(var\(--ai-analysis-module-hover-rgb\),\s*0\.48\)/)
  assert.match(variablesRule, /--ai-analysis-module-hover-shadow:/)
  assert.match(lightVariablesRule, /--ai-analysis-module-hover-rgb:\s*86,\s*109,\s*134/)
  assert.match(lightVariablesRule, /--ai-analysis-module-hover-border:\s*rgba\(var\(--ai-analysis-module-hover-rgb\),\s*0\.42\)/)

  assert.match(moduleBaseRule, /background:\s*var\(--bg-secondary\)/)
  assert.match(moduleBaseRule, /border:\s*1px solid var\(--border-color\)/)
  assert.match(moduleBaseRule, /border-radius:\s*12px/)
  assert.match(moduleBaseRule, /box-shadow:\s*none/)
  assert.doesNotMatch(moduleBaseRule, /linear-gradient|rgba\(var\(--accent-rgb\)|rgba\(41,\s*151,\s*255/)

  assert.match(moduleHoverRule, /border-color:\s*var\(--ai-analysis-module-hover-border\)/)
  assert.match(moduleHoverRule, /box-shadow:\s*var\(--ai-analysis-module-hover-shadow\)/)
  assert.doesNotMatch(moduleHoverRule, /inset|animation:/)

  assert.match(tabHoverRule, /border-color:\s*var\(--ai-analysis-control-hover-border\)/)
  assert.match(tabHoverRule, /box-shadow:\s*var\(--ai-analysis-control-hover-shadow\)/)
  assert.doesNotMatch(tabHoverRule, /linear-gradient/)
  assert.match(rechargeHoverRule, /border-color:\s*var\(--ai-analysis-control-hover-border\)/)
  assert.match(rechargeHoverRule, /box-shadow:\s*var\(--ai-analysis-control-hover-shadow\)/)
})

test('AI analysis history renders typed index cards without report body copy', () => {
  const source = readViewSource()
  const variablesRule = extractRule(source, '.ai-analysis-view')
  const lightVariablesRule = extractRule(source, ':global([data-theme="light"] .ai-analysis-view)')
  const reportCardRule = extractRule(source, '.report-card {')

  assert.match(source, /t\('aiAnalysis\.emptyHistoryTitle'\)/)
  assert.match(source, /t\('aiAnalysis\.emptyHistoryBody'\)/)
  assert.match(source, /report-card/)
  assert.match(source, /class="report-header"/)
  assert.match(source, /class="report-title-block"/)
  assert.match(source, /class="report-meta"/)
  assert.match(source, /getReportDisplayTitle\(result\)/)
  assert.match(source, /function getReportDisplayTitle\(result: LocalAiAnalysisDisplayResult\)[\s\S]*result\.output\.postgamePraise\?\.headline \|\| getReportTitle\(result\)/)
  assert.match(source, /result\.createdAtLabel/)
  assert.match(source, /getReportCategoryLabel\(result\)/)
  assert.match(source, /review: isPostgameReviewResult\(result\)/)
  assert.match(source, /praise: isPraiseReport\(result\)/)
  assert.match(source, /<time>{{ result\.createdAtLabel }}<\/time>[\s\S]*<span class="report-type-pill">{{ getReportCategoryLabel\(result\) }}<\/span>/)
  assert.doesNotMatch(source, /result\.output\.summary/)
  assert.doesNotMatch(source, /result\.output\.postgamePraise\.body/)
  assert.doesNotMatch(source, /class="report-praise-card"/)
  assert.doesNotMatch(source, /class="report-praise-body"/)
  assert.doesNotMatch(source, /result\.subjectKey/)
  assert.doesNotMatch(source, /result\.gameVersion/)
  assert.doesNotMatch(source, /result\.modelName/)
  assert.doesNotMatch(source, /formatTokenCount|formatCny/)
  assert.doesNotMatch(source, /result\.inputHash/)
  assert.doesNotMatch(source, /class="type-code"/)
  assert.ok(source.indexOf('report-main') < source.indexOf('report-context'))
  assert.match(variablesRule, /--ai-record-review-font:[\s\S]*"Times New Roman"/)
  assert.match(variablesRule, /--ai-record-praise-font:[\s\S]*YouYuan[\s\S]*"Trebuchet MS"/)
  assert.match(variablesRule, /--ai-record-review-bg:/)
  assert.match(variablesRule, /--ai-record-praise-bg:/)
  assert.match(lightVariablesRule, /--ai-record-review-bg:/)
  assert.match(lightVariablesRule, /--ai-record-praise-bg:/)
  assert.match(reportCardRule, /background:\s*var\(--ai-record-bg\)/)
})

test('AI analysis history gives review and praise records distinct index-card typography', () => {
  const source = readViewSource()
  const localAiAnalysis = readRendererFile('services/localAiAnalysis.ts')
  const reviewTitleRule = extractRule(source, '.report-card.review .report-title')
  const praiseTitleRule = extractRule(source, '.report-card.praise .report-title')
  const reviewCardRule = extractRule(source, '.report-card.review')
  const praiseCardRule = extractRule(source, '.report-card.praise')

  assert.match(localAiAnalysis, /postgamePraise\?: PostgameAiPraiseResult/)
  assert.match(source, /result\.output\.postgamePraise/)
  assert.match(source, /function isPraiseReport\(result: LocalAiAnalysisDisplayResult\)[\s\S]*Boolean\(result\.output\.postgamePraise\)/)
  assert.match(source, /function isPostgameReviewResult\(result: LocalAiAnalysisDisplayResult\)[\s\S]*result\.output\.postgameRun\?\.mode === 'review'/)
  assert.match(reviewTitleRule, /font-family:\s*var\(--ai-record-review-font\)/)
  assert.match(reviewTitleRule, /font-size:\s*20px/)
  assert.match(reviewTitleRule, /line-height:\s*1\.35/)
  assert.match(praiseTitleRule, /font-family:\s*var\(--ai-record-praise-font\)/)
  assert.match(praiseTitleRule, /font-size:\s*21px/)
  assert.match(praiseTitleRule, /line-height:\s*1\.34/)
  assert.match(reviewCardRule, /background:\s*var\(--ai-record-review-bg\)/)
  assert.match(reviewCardRule, /border-color:\s*var\(--ai-record-review-border\)/)
  assert.match(praiseCardRule, /background:\s*var\(--ai-record-praise-bg\)/)
  assert.match(praiseCardRule, /border-color:\s*var\(--ai-record-praise-border\)/)
})

test('AI analysis history links saved postgame records back to match history details', () => {
  const source = readViewSource()

  assert.match(source, /import \{ useRouter \} from 'vue-router'/)
  assert.match(source, /function getPostgameMatchMetaText\(result: LocalAiAnalysisDisplayResult\)/)
  assert.match(source, /function formatPostgameKda\(result: LocalAiAnalysisDisplayResult\)/)
  assert.match(source, /function getPostgameChampionIcon\(result: LocalAiAnalysisDisplayResult\)/)
  assert.match(source, /class="report-match-link"/)
  assert.match(source, /@click="openMatchHistoryForReport\(result, \$event\)"/)
  assert.match(source, /router\.push\(\{[\s\S]*name: 'MatchHistory'[\s\S]*openMatchId: matchId/)
  assert.match(source, /\['INVALID', 'NONE', 'UNKNOWN'\]\.includes\(normalized\)/)
  assert.match(source, /return '胜利'[\s\S]*return '失败'/)
  assert.match(source, /return '单双排'[\s\S]*return '打野'/)
})
