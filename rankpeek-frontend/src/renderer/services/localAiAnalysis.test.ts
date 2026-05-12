import test from 'node:test'
import assert from 'node:assert/strict'
import type { AiAnalysisResult, LocalDatabaseAPI } from '../types/localDatabase.ts'
import {
  formatAnalysisTime,
  formatAnalysisType,
  getCoachReportFinalSentence,
  getCoachReportHeadline,
  loadLocalAiAnalysisResults,
  normalizeCoachChartBlocks,
  parseAiAnalysisOutput,
  parseCoachSummaryReportOutput
} from './localAiAnalysis.ts'

async function withMutedWarnings<T>(operation: () => T | Promise<T>): Promise<T> {
  const originalWarn = console.warn
  console.warn = () => undefined
  try {
    return await operation()
  } finally {
    console.warn = originalWarn
  }
}

const savedResult: AiAnalysisResult = {
  id: 7,
  accountPuuid: 'account-puuid',
  matchId: '998877',
  analysisType: 'post_game',
  subjectKey: 'match:998877',
  gameVersion: '15.8',
  modelName: 'local-placeholder',
  promptVersion: 'v1',
  inputHash: 'hash-1',
  outputJson: JSON.stringify({
    title: 'Post-game review',
    summary: 'Review summary',
    verdict: 'Keep farming before dragon fights',
    sections: [
      { title: 'Deaths', summary: 'Two deaths before objectives' },
      'Vision setup needs a wider timing window'
    ]
  }),
  createdAt: '2026-04-29T08:30:00.000Z',
  updatedAt: '2026-04-29T08:30:00.000Z'
}

test('normal JSON output parses preferred AI analysis fields', () => {
  const parsed = parseAiAnalysisOutput(savedResult.outputJson)

  assert.equal(parsed.status, 'parsed')
  assert.equal(parsed.title, 'Post-game review')
  assert.equal(parsed.summary, 'Review summary')
  assert.deepEqual(parsed.highlights, [
    'Keep farming before dragon fights',
    'Deaths: Two deaths before objectives',
    'Vision setup needs a wider timing window'
  ])
})

test('non-standard JSON output falls back to a compact short summary', () => {
  const parsed = parseAiAnalysisOutput(JSON.stringify({
    score: 82,
    tags: ['macro', 'tempo'],
    nested: { turn: 'baron' }
  }))

  assert.equal(parsed.status, 'parsed')
  assert.equal(parsed.title, null)
  assert.match(parsed.summary, /"score":82/)
  assert.ok(parsed.summary.length <= 160)
})

test('malformed output JSON does not throw and returns an invalid summary', async () => {
  const parsed = await withMutedWarnings(() => parseAiAnalysisOutput('{bad-json'))

  assert.equal(parsed.status, 'invalid')
  assert.equal(parsed.title, null)
  assert.equal(parsed.summary, '无法解析的分析结果')
  assert.deepEqual(parsed.highlights, [])
})

test('analysis type and timestamp are formatted for display', () => {
  assert.equal(formatAnalysisType('pre_game'), '赛前分析')
  assert.equal(formatAnalysisType('POST_GAME_REVIEW'), '赛后复盘')
  assert.equal(formatAnalysisType('coach_weekly'), '电子教练')
  assert.equal(formatAnalysisType('fun_mode'), '娱乐分析')
  assert.equal(formatAnalysisType('custom_drill'), 'Custom Drill')
  assert.match(formatAnalysisTime('2026-04-29T08:30:00.000Z'), /2026/)
})

test('loading local AI analysis results skips the database when account puuid is missing', async () => {
  let called = false
  const database = {
    listAnalysisResultsByAccount: async () => {
      called = true
      return { success: true, data: [] }
    }
  } as Pick<LocalDatabaseAPI, 'listAnalysisResultsByAccount'>

  const result = await loadLocalAiAnalysisResults('', {
    database
  })

  assert.equal(called, false)
  assert.deepEqual(result, {
    results: [],
    unavailable: false,
    error: null
  })
})

test('loading local AI analysis results maps database records and preserves query options', async () => {
  const calls: unknown[] = []
  const database = {
    listAnalysisResultsByAccount: async (accountPuuid: string, options: unknown) => {
      calls.push({ accountPuuid, options })
      return { success: true, data: [savedResult] }
    }
  } as Pick<LocalDatabaseAPI, 'listAnalysisResultsByAccount'>

  const result = await loadLocalAiAnalysisResults('account-puuid', {
    limit: 20,
    offset: 0,
    database
  })

  assert.equal(result.unavailable, false)
  assert.equal(result.error, null)
  assert.equal(result.results.length, 1)
  assert.equal(result.results[0]?.analysisTypeLabel, '赛后复盘')
  assert.equal(result.results[0]?.output.title, 'Post-game review')
  assert.deepEqual(calls, [
    {
      accountPuuid: 'account-puuid',
      options: {
        limit: 20,
        offset: 0
      }
    }
  ])
})

const coachSummaryReport = {
  schemaVersion: 'coach_summary_report.v1',
  analysisType: 'coach_summary',
  inputHash: 'hash-coach',
  headline: '贝蕾亚波动偏高',
  title: '近20场排位电子教练简报',
  summary: '近20局中野节奏有起伏，死亡集中在资源刷新前。',
  overview: {
    totalMatches: 20,
    wins: 11,
    losses: 9,
    winRate: 55,
    summary: '主玩打野，贝蕾亚和凯隐占比最高。',
    primaryRoles: [{ role: 'JUNGLE', count: 16 }],
    heroStats: [
      {
        championId: 233,
        championCanonicalName: 'Briar',
        championDisplayName: '贝蕾亚',
        role: 'JUNGLE',
        games: 8,
        wins: 4,
        losses: 4,
        winRate: 50,
        kda: '7.1 / 6.0 / 8.4',
        averageKda: 2.58
      }
    ],
    roleStats: [
      { role: 'JUNGLE', games: 16, wins: 9, losses: 7, winRate: 56.25 }
    ]
  },
  verdict: {
    label: '中期死亡拖慢节奏',
    score: 72,
    confidence: 'medium',
    summary: '优势局需要提前处理资源团前站位。'
  },
  keyFindings: [
    {
      id: 'death-before-objective',
      priority: 'high',
      category: 'death',
      claim: '资源刷新前死亡偏多',
      evidence: '20局中有多次资源前120秒死亡。',
      reasoning: '死亡窗口让队伍失去布控和先手。',
      advice: '资源刷新前先推线再进河道。',
      confidence: 'medium',
      evidenceRefs: ['aggregate.objectiveDeaths']
    }
  ],
  trainingPlan: [
    {
      focus: '资源前站位',
      why: '减少资源团前掉点。',
      nextGames: 5,
      task: '资源刷新前45秒避免单人脸探。',
      metricToTrack: 'objective_deaths_before_120s',
      target: '5局内不超过1次',
      priority: 'high'
    }
  ],
  championAdvice: [
    {
      championName: 'Briar',
      role: 'JUNGLE',
      recommendation: 'practice',
      reason: '样本最多但死亡波动偏高。',
      confidence: 'medium'
    }
  ],
  chartBlocks: [
    {
      id: 'hero-winrate',
      title: '主玩英雄胜率',
      kind: 'bar',
      placement: 'overview',
      data: [
        { champion: '贝蕾亚', games: 8, winRate: 50 },
        { champion: '凯隐', games: 6, winRate: 67 }
      ],
      labelKey: 'champion',
      valueKey: 'winRate',
      intent: '对比主玩英雄胜率',
      interpretation: '凯隐更稳定。',
      evidenceRefs: ['overview.heroStats']
    },
    {
      id: 'kda-trend',
      title: 'KDA 趋势',
      kind: 'line',
      placement: 'analysis',
      data: [
        { match: 1, kda: 2.1 },
        { match: 2, kda: 3.4 }
      ],
      xKey: 'match',
      yKeys: ['kda'],
      intent: '观察近期 KDA 波动',
      evidenceRefs: ['aggregate.kdaTrend']
    },
    {
      id: 'future-chart',
      title: '经济差趋势',
      kind: 'line',
      placement: 'analysis',
      dataRef: 'aggregate.goldDiffTrend',
      intent: '后续由确定性聚合提供曲线',
      evidenceRefs: ['aggregate.goldDiffTrend']
    }
  ],
  warnings: [],
  finalSummary: '接下来一周先把资源前死亡压下来。',
  metadata: {
    modelName: 'deepseek-placeholder',
    promptVersion: 'coach_summary.prompt.v1',
    generatedAt: '2026-05-12T00:00:00Z',
    snapshotSchemaVersion: 'coach_summary.v1',
    dataQualityConfidence: 'medium'
  }
}

test('coach summary report v1 output parses structured overview and chart blocks', () => {
  const parsed = parseCoachSummaryReportOutput(JSON.stringify(coachSummaryReport))

  assert.equal(parsed.status, 'parsed')
  assert.equal(parsed.report?.schemaVersion, 'coach_summary_report.v1')
  assert.equal(parsed.report?.overview?.heroStats?.[0]?.championDisplayName, '贝蕾亚')
  assert.equal(parsed.report?.overview?.roleStats?.[0]?.role, 'JUNGLE')
  assert.equal(parsed.report?.chartBlocks?.length, 3)
  assert.equal(parsed.report?.chartBlocks?.[2]?.dataRef, 'aggregate.goldDiffTrend')
})

test('coach report headline uses product fallback order and truncates long template title', () => {
  assert.equal(getCoachReportHeadline({ report: coachSummaryReport }), '贝蕾亚波动偏高')
  assert.equal(getCoachReportHeadline({ report: { ...coachSummaryReport, headline: '', cardTitle: '凯隐纳亚菲利更稳' } }), '凯隐纳亚菲利更稳')
  assert.equal(getCoachReportHeadline({ report: { ...coachSummaryReport, headline: '', cardTitle: '', shortTitle: '中期死亡拖慢节奏' } }), '中期死亡拖慢节奏')
  assert.equal(getCoachReportHeadline({ report: { ...coachSummaryReport, headline: '', cardTitle: '', shortTitle: '', verdict: { ...coachSummaryReport.verdict, label: '资源团前掉点偏多' } } }), '资源团前掉点偏多')

  const fallbackTitle = getCoachReportHeadline({
    report: {
      ...coachSummaryReport,
      headline: '',
      cardTitle: '',
      shortTitle: '',
      verdict: { ...coachSummaryReport.verdict, label: '' },
      title: '近20场排位电子教练简报：这是一段非常长的模板标题，首页不应该完整展示'
    }
  })
  assert.ok(fallbackTitle.length <= 18)
  assert.match(fallbackTitle, /\.\.\.$/)
})

test('coach report final sentence uses finalSummary first sentence for modal header', () => {
  assert.equal(
    getCoachReportFinalSentence({
      ...coachSummaryReport,
      finalSummary: '中期团战筑造优势。后续先压低团前死亡。',
      verdict: { ...coachSummaryReport.verdict, summary: '不应该优先显示这句。' }
    }),
    '中期团战筑造优势。'
  )
})

test('coach report final sentence falls back to verdict summary first sentence', () => {
  assert.equal(
    getCoachReportFinalSentence({
      ...coachSummaryReport,
      finalSummary: '',
      verdict: { ...coachSummaryReport.verdict, summary: '资源团前先站稳！第二句不展示。' }
    }),
    '资源团前先站稳！'
  )
})

test('coach report final sentence trims long text and appends punctuation when needed', () => {
  const sentence = getCoachReportFinalSentence({
    ...coachSummaryReport,
    finalSummary: '中期资源团站位和视野联动已经能稳定筑造优势但还需要控制追击成本'
  })

  assert.ok(sentence.length <= 25)
  assert.match(sentence, /…$/)
  assert.doesNotMatch(sentence, /开发环境|DEV|预览说明/)

  assert.equal(
    getCoachReportFinalSentence({
      ...coachSummaryReport,
      finalSummary: '',
      verdict: { ...coachSummaryReport.verdict, label: '', summary: '' },
      cardTitle: '',
      shortTitle: '',
      headline: '中期团战筑造优势'
    }),
    '中期团战筑造优势。'
  )
})

test('coach chart blocks normalize malformed values without throwing', () => {
  const blocks = normalizeCoachChartBlocks([
    coachSummaryReport.chartBlocks[0],
    { id: 'bad-kind', title: '坏图', kind: 'pie', placement: 'overview', data: [{ x: 1 }], evidenceRefs: [] },
    { id: 'bad-placement', title: '坏位置', kind: 'bar', placement: 'sidecar', data: [{ x: 1 }], evidenceRefs: [] },
    { id: 'bad-data', title: '坏数据', kind: 'bar', placement: 'analysis', data: ['nope'], evidenceRefs: [42] },
    { id: 'legacy', title: '旧字段图表', type: 'gold_curve', dataRef: 'matches[*].economyTimeline', description: '旧 schema 图表', highlight: '先保留 dataRef' }
  ])

  assert.equal(blocks.length, 3)
  assert.equal(blocks[0]?.kind, 'bar')
  assert.equal(blocks[1]?.kind, 'bar')
  assert.equal(blocks[1]?.data, undefined)
  assert.equal(blocks[1]?.evidenceRefs.length, 0)
  assert.equal(blocks[2]?.placement, 'analysis')
  assert.equal(blocks[2]?.dataRef, 'matches[*].economyTimeline')
})

test('unsupported or malformed coach summary JSON does not throw', async () => {
  const malformed = await withMutedWarnings(() => parseCoachSummaryReportOutput('{bad-json'))
  const unsupported = parseCoachSummaryReportOutput(JSON.stringify({ schemaVersion: 'other', analysisType: 'coach_summary' }))

  assert.equal(malformed.status, 'invalid')
  assert.equal(unsupported.status, 'unsupported')
  assert.equal(malformed.report, null)
  assert.equal(unsupported.report, null)
})
