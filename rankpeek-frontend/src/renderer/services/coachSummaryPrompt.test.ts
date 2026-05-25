import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'

const promptModuleUrl = new URL('./coachSummaryPrompt.ts', import.meta.url)

function makeSnapshot() {
  return {
    schemaVersion: 'coach_summary.v1',
    analysisType: 'coach_summary',
    inputHash: 'coach-input-hash',
    analysisBrief: {
      schemaVersion: 'coach_summary_analysis_brief.v1',
      language: 'zh-CN',
      text: [
        '当前snapshot时间：2026-05-25T12:00:00.000Z。',
        '最近20局走势：12胜8负，平均KDA 3.2，15分钟对位经济平均+88。',
        'm01：Karma 辅助 胜 2/1/18，15分钟经济+420，资源前120秒内死亡0次。'
      ].join('\n'),
      overviewFacts: [],
      trendFacts: [],
      championFacts: [],
      roleFacts: [],
      matchFacts: [],
      dataQualityFacts: []
    },
    dataQuality: {
      confidence: 'high',
      hasAllTimelines: true,
      hasAllParticipantDetails: true,
      hasAllRuneData: true,
      hasAllItemData: true,
      hasAnyRuneOrItemData: true,
      missingTimelineMatchRefs: [],
      missingParticipantDetailMatchRefs: [],
      missingRuneMatchRefs: [],
      missingItemMatchRefs: [],
      missingRuneOrItemMatchRefs: [],
      missingLaneOpponentMatchRefs: [],
      missingEconomyDiffMatchRefs: [],
      missingDataReasons: []
    },
    metadata: {
      generatedInputAt: '2026-05-25T12:00:00.000Z',
      matchRefs: ['m01'],
      anchorMatchRefs: ['m01'],
      source: 'local_sqlite'
    }
  }
}

test('coach summary prompt wraps the natural-language snapshot with long-term coaching rules', async () => {
  assert.equal(existsSync(promptModuleUrl), true)
  const { buildCoachSummaryPromptPayload, COACH_SUMMARY_PROMPT_VERSION } = await import('./coachSummaryPrompt.ts')

  const payload = buildCoachSummaryPromptPayload({
    snapshot: makeSnapshot(),
    historicalCoachContext: '上次报告：资源团前死亡偏多，本轮需要观察是否改善。'
  })

  assert.equal(COACH_SUMMARY_PROMPT_VERSION, 'coach_summary.prompt.v2')
  assert.equal(payload.promptVersion, 'coach_summary.prompt.v2')
  assert.match(payload.systemPrompt, /长期电子教练/)
  assert.match(payload.systemPrompt, /只输出 JSON/)
  assert.match(payload.systemPrompt, /coach_summary_report\.v1/)
  assert.match(payload.systemPrompt, /顶层必须包含这些字段：title、summary、verdict/)
  assert.match(payload.systemPrompt, /不要把报告包在 report\/result\/data 字段里/)
  assert.match(payload.systemPrompt, /先判断 currentSnapshotMeta\.overallState/)
  assert.doesNotMatch(payload.systemPrompt, /资源/)
  assert.match(payload.systemPrompt, /不要逐局流水账/)
  assert.doesNotMatch(payload.systemPrompt, /不超过 \d+ 个中文字符/)
  assert.doesNotMatch(payload.systemPrompt, /\d+-\d+ 个中文字符/)
  assert.match(payload.systemPrompt, /m01/)
  assert.match(payload.systemPrompt, /不要输出真实 matchId、gameId、puuid、summonerName、gameName、tagLine/)
  assert.match(payload.systemPrompt, /没有历史上下文时/)
  assert.match(payload.systemPrompt, /historicalCoachContext 只用于趋势对比/)
  assert.match(payload.systemPrompt, /不要把历史报告里的旧问题直接当成本轮问题复述/)
  assert.match(payload.systemPrompt, /title、cardTitle、shortTitle 必须是正向主结论/)
  assert.match(payload.systemPrompt, /不能出现“但是”“但”“需提升”“短板”“仅”/)
  assert.match(payload.systemPrompt, /分路\/英雄低胜率样本只能作为观察项/)
  assert.match(payload.systemPrompt, /keyFindings 优先 2 条/)
  assert.match(payload.systemPrompt, /不要写成编号清单、小标题清单或模板化体检报告/)
  assert.match(payload.systemPrompt, /trainingPlan 2-3 条/)
  assert.match(payload.systemPrompt, /championAdvice 2-5 条/)

  assert.match(payload.userPrompt, /currentSnapshotText/)
  assert.match(payload.userPrompt, /最近20局走势：12胜8负/)
  assert.match(payload.userPrompt, /currentSnapshotMeta/)
  assert.match(payload.userPrompt, /coach-input-hash/)
  assert.match(payload.userPrompt, /historicalCoachContext/)
  assert.match(payload.userPrompt, /资源团前死亡偏多/)
})

test('coach summary prompt uses an explicit empty historical context for first reports', async () => {
  assert.equal(existsSync(promptModuleUrl), true)
  const { buildCoachSummaryPromptPayload } = await import('./coachSummaryPrompt.ts')

  const payload = buildCoachSummaryPromptPayload({
    snapshot: makeSnapshot()
  })

  assert.match(payload.userPrompt, /historicalCoachContext/)
  assert.match(payload.userPrompt, /无历史电子教练报告/)
  assert.doesNotMatch(payload.userPrompt, /undefined|null/)
})
