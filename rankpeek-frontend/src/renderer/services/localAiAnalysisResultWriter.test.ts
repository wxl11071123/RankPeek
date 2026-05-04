import test from 'node:test'
import assert from 'node:assert/strict'
import type { AiAnalysisResult, AiAnalysisResultInput } from '../types/localDatabase.ts'
import type { ServerAiAnalysisResult } from '../types/serverAiAnalysis.ts'
import * as writerModule from './localAiAnalysisResultWriter.ts'
import { saveServerAiFinalResultToLocal } from './localAiAnalysisResultWriter.ts'

const finalResult: ServerAiAnalysisResult = {
  analysisType: 'postgame',
  title: 'Post-game Review',
  summary: 'A clean final result from the server.',
  verdict: 'Keep tempo around objectives.',
  confidence: 0.9,
  sections: [
    {
      title: 'Objective setup',
      body: 'Reset before dragon and arrive with vision.',
      severity: 'warning',
      bullets: ['Push mid first', 'Ward river entrance']
    }
  ],
  metadata: {
    modelName: 'rankpeek-server-model',
    promptVersion: 'server-prompt-v1',
    gameVersion: '15.8',
    versionContextHash: 'version-context-hash',
    inputHash: 'input-hash-1',
    generatedAt: '2026-04-29T12:00:00.000Z'
  }
}

function savedAnalysis(input: AiAnalysisResultInput, id = 42): AiAnalysisResult {
  return {
    id,
    accountPuuid: input.accountPuuid,
    matchId: input.matchId ?? null,
    analysisType: input.analysisType,
    subjectKey: input.subjectKey ?? null,
    gameVersion: input.gameVersion ?? null,
    modelName: input.modelName ?? null,
    promptVersion: input.promptVersion ?? null,
    inputHash: input.inputHash ?? null,
    outputJson: typeof input.outputJson === 'string' ? input.outputJson : JSON.stringify(input.outputJson),
    createdAt: '2026-04-29T12:01:00.000Z',
    updatedAt: '2026-04-29T12:01:00.000Z'
  }
}

test('final server result converts and calls local saveAnalysisResult', async () => {
  const savedPayloads: AiAnalysisResultInput[] = []
  const result = await saveServerAiFinalResultToLocal({
    result: finalResult,
    accountPuuid: ' account-puuid ',
    subjectKey: 'job:abc',
    database: {
      saveAnalysisResult: async payload => {
        savedPayloads.push(payload)
        return {
          success: true,
          data: savedAnalysis(payload, 77)
        }
      }
    }
  })

  assert.deepEqual(result, {
    success: true,
    id: 77
  })
  assert.equal(savedPayloads.length, 1)
  assert.deepEqual(savedPayloads[0], {
    accountPuuid: 'account-puuid',
    analysisType: 'postgame',
    subjectKey: 'job:abc',
    gameVersion: '15.8',
    modelName: 'rankpeek-server-model',
    promptVersion: 'server-prompt-v1',
    inputHash: 'input-hash-1',
    outputJson: JSON.stringify(finalResult)
  })
})

test('missing accountPuuid is rejected without saving', async () => {
  let called = false
  const result = await saveServerAiFinalResultToLocal({
    result: finalResult,
    accountPuuid: '   ',
    database: {
      saveAnalysisResult: async payload => {
        called = true
        return {
          success: true,
          data: savedAnalysis(payload)
        }
      }
    }
  })

  assert.deepEqual(result, {
    success: false,
    error: 'Missing accountPuuid'
  })
  assert.equal(called, false)
})

test('missing metadata inputHash is rejected without saving', async () => {
  let called = false
  const resultWithoutHash = {
    ...finalResult,
    metadata: {
      ...finalResult.metadata,
      inputHash: ''
    }
  }

  const result = await saveServerAiFinalResultToLocal({
    result: resultWithoutHash,
    accountPuuid: 'account-puuid',
    database: {
      saveAnalysisResult: async payload => {
        called = true
        return {
          success: true,
          data: savedAnalysis(payload)
        }
      }
    }
  })

  assert.deepEqual(result, {
    success: false,
    error: 'Missing inputHash'
  })
  assert.equal(called, false)
})

test('expected inputHash mismatch is rejected without saving', async () => {
  let called = false
  const result = await saveServerAiFinalResultToLocal({
    result: finalResult,
    accountPuuid: 'account-puuid',
    expectedInputHash: 'different-hash',
    database: {
      saveAnalysisResult: async payload => {
        called = true
        return {
          success: true,
          data: savedAnalysis(payload)
        }
      }
    }
  })

  assert.deepEqual(result, {
    success: false,
    error: 'Input hash mismatch'
  })
  assert.equal(called, false)
})

test('matching expected inputHash allows saving', async () => {
  const savedPayloads: AiAnalysisResultInput[] = []
  const result = await saveServerAiFinalResultToLocal({
    result: finalResult,
    accountPuuid: 'account-puuid',
    expectedInputHash: 'input-hash-1',
    database: {
      saveAnalysisResult: async payload => {
        savedPayloads.push(payload)
        return {
          success: true,
          data: savedAnalysis(payload, 88)
        }
      }
    }
  })

  assert.deepEqual(result, {
    success: true,
    id: 88
  })
  assert.equal(savedPayloads.length, 1)
  assert.equal(savedPayloads[0].inputHash, 'input-hash-1')
})

test('save failures return a safe error result', async () => {
  const failedResult = await saveServerAiFinalResultToLocal({
    result: finalResult,
    accountPuuid: 'account-puuid',
    database: {
      saveAnalysisResult: async () => ({
        success: false,
        error: 'database locked'
      })
    }
  })

  assert.deepEqual(failedResult, {
    success: false,
    error: 'database locked'
  })

  const thrownResult = await saveServerAiFinalResultToLocal({
    result: finalResult,
    accountPuuid: 'account-puuid',
    database: {
      saveAnalysisResult: async () => {
        throw new Error('ipc unavailable')
      }
    }
  })

  assert.deepEqual(thrownResult, {
    success: false,
    error: 'ipc unavailable'
  })
})

test('outputJson stores the complete ServerAiAnalysisResult JSON', async () => {
  const savedPayloads: AiAnalysisResultInput[] = []
  await saveServerAiFinalResultToLocal({
    result: finalResult,
    accountPuuid: 'account-puuid',
    database: {
      saveAnalysisResult: async payload => {
        savedPayloads.push(payload)
        return {
          success: true,
          data: savedAnalysis(payload)
        }
      }
    }
  })

  assert.equal(savedPayloads.length, 1)
  assert.deepEqual(JSON.parse(savedPayloads[0].outputJson as string), finalResult)
})

test('stream delta events have no local save entrypoint', () => {
  assert.equal('saveServerAiStreamDeltaToLocal' in writerModule, false)
  assert.equal('saveServerAiStreamEventToLocal' in writerModule, false)
})

test('subjectKey can preserve job or match identity', async () => {
  const savedSubjectKeys: Array<string | null | undefined> = []

  await saveServerAiFinalResultToLocal({
    result: finalResult,
    accountPuuid: 'account-puuid',
    subjectKey: 'match:NA1_998877',
    database: {
      saveAnalysisResult: async payload => {
        savedSubjectKeys.push(payload.subjectKey)
        return {
          success: true,
          data: savedAnalysis(payload)
        }
      }
    }
  })

  await saveServerAiFinalResultToLocal({
    result: finalResult,
    accountPuuid: 'account-puuid',
    subjectKey: 'job:rankpeek-ai-1',
    database: {
      saveAnalysisResult: async payload => {
        savedSubjectKeys.push(payload.subjectKey)
        return {
          success: true,
          data: savedAnalysis(payload)
        }
      }
    }
  })

  assert.deepEqual(savedSubjectKeys, ['match:NA1_998877', 'job:rankpeek-ai-1'])
})
