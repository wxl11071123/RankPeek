import test from 'node:test'
import assert from 'node:assert/strict'
import type { PostgameAiInputSnapshot } from './postgameAiInputSnapshot.ts'
import {
  createPostgameAiStreamRequest,
  RANKPEEK_SERVER_POSTGAME_STREAM_ENDPOINT,
  streamPostgameAiAnalysis
} from './postgameAiServerStream.ts'

function createSnapshot(): PostgameAiInputSnapshot {
  return {
    schemaVersion: 'postgame_ai_input_snapshot.v1',
    analysisType: 'postgame_review',
    mode: 'review',
    builtAt: '2026-05-13T00:00:00.000Z',
    inputHash: 'hash-1',
    match: {
      gameIdHash: 'game-hash',
      queueId: 420,
      queueName: 'Ranked Solo',
      gameMode: 'CLASSIC',
      gameVersion: null,
      gameCreation: 1710000000000,
      durationSeconds: 1800,
      isRanked: true,
      isAram: false,
      isArena: false
    },
    currentPlayerKey: 'player:1',
    teams: [],
    players: [],
    timeline: { hasTimeline: false },
    dataQuality: {
      hasMatchHistory: true,
      hasGameDetail: true,
      hasTimeline: false,
      participantCount: 10,
      teamCount: 2,
      hasRankedTimelineMetrics: false,
      hasArenaAugments: false,
      warnings: ['timeline missing']
    }
  }
}

test('creates a postgame stream request that wraps mode, schema, and snapshot', () => {
  const snapshot = createSnapshot()
  const request = createPostgameAiStreamRequest(snapshot)

  assert.equal(request.mode, 'review')
  assert.equal(request.snapshotSchemaVersion, 'postgame_ai_input_snapshot.v1')
  assert.equal(request.snapshot, snapshot)
})

test('streams postgame AI analysis events from an SSE response', async () => {
  const request = createPostgameAiStreamRequest(createSnapshot())
  const events: string[] = []
  const deltas: string[] = []
  const sections: string[] = []
  const originalFetch = globalThis.fetch

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    assert.equal(String(url), `http://127.0.0.1:18080${RANKPEEK_SERVER_POSTGAME_STREAM_ENDPOINT}`)
    assert.equal(init?.method, 'POST')
    assert.deepEqual(init?.headers, { 'Content-Type': 'application/json' })
    assert.equal(JSON.parse(String(init?.body)).snapshotSchemaVersion, 'postgame_ai_input_snapshot.v1')

    const encoder = new TextEncoder()
    return new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('event: start\ndata: {"title":"mock"}\n\n'))
        controller.enqueue(encoder.encode('event: section\ndata: {"title":"Data"}\n\n'))
        controller.enqueue(encoder.encode('event: delta\ndata: {"text":"accepted"}\n\n'))
        controller.enqueue(encoder.encode('event: done\ndata: done\n\n'))
        controller.close()
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' }
    })
  }) as typeof fetch

  try {
    const result = await streamPostgameAiAnalysis(request, {
      onEvent: event => events.push(event.type),
      onSection: title => sections.push(title),
      onDelta: text => deltas.push(text)
    })

    assert.deepEqual(result, { ok: true })
    assert.deepEqual(events, ['start', 'section', 'delta', 'done'])
    assert.deepEqual(sections, ['Data'])
    assert.deepEqual(deltas, ['accepted'])
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('streams postgame AI analysis events from an NDJSON response including errors', async () => {
  const request = createPostgameAiStreamRequest({ ...createSnapshot(), mode: 'praise', analysisType: 'postgame_praise' })
  const events: string[] = []
  const errors: string[] = []
  const originalFetch = globalThis.fetch

  globalThis.fetch = (async () => {
    const encoder = new TextEncoder()
    return new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('{"type":"start","title":"mock"}\n'))
        controller.enqueue(encoder.encode('{"type":"section","title":"Quality"}\n'))
        controller.enqueue(encoder.encode('{"type":"delta","text":"quality accepted"}\n'))
        controller.enqueue(encoder.encode('{"type":"error","message":"mock warning"}\n'))
        controller.close()
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/x-ndjson' }
    })
  }) as typeof fetch

  try {
    const result = await streamPostgameAiAnalysis(request, {
      onEvent: event => events.push(event.type),
      onError: message => errors.push(message)
    })

    assert.deepEqual(result, { ok: true })
    assert.deepEqual(events, ['start', 'section', 'delta', 'error'])
    assert.deepEqual(errors, ['mock warning'])
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('returns a failed result instead of throwing when rankpeek-server is unavailable', async () => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async () => {
    throw new TypeError('fetch failed')
  }) as typeof fetch

  try {
    const result = await streamPostgameAiAnalysis(createPostgameAiStreamRequest(createSnapshot()), {})

    assert.equal(result.ok, false)
    assert.match(result.ok ? '' : result.message, /rankpeek-server/)
  } finally {
    globalThis.fetch = originalFetch
  }
})
