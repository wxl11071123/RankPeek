import test from 'node:test'
import assert from 'node:assert/strict'
import type { AiAnalysisResult, LocalDatabaseAPI } from '../types/localDatabase.ts'
import {
  formatAnalysisTime,
  formatAnalysisType,
  loadLocalAiAnalysisResults,
  parseAiAnalysisOutput
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
