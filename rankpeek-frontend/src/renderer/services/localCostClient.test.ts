import test from 'node:test'
import assert from 'node:assert/strict'
import { getLocalCostSummary } from './localCostClient.ts'
import { RANKPEEK_LOCAL_SERVICE_BASE_URL } from './rankpeekLocalServiceClient.ts'

test('getLocalCostSummary calls local cost summary endpoint', async () => {
  const originalFetch = globalThis.fetch
  const calls: string[] = []

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push(String(url))
    assert.equal(init, undefined)
    return new Response(JSON.stringify({
      success: true,
      data: {
        from: '2026-05-01',
        to: '2026-05-31',
        aiCostCny: 0.12,
        manualCostCny: 9.9,
        totalCostCny: 10.02,
        eventCount: 3,
        manualItemCount: 1
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const summary = await getLocalCostSummary()

    assert.equal(calls[0], `${RANKPEEK_LOCAL_SERVICE_BASE_URL}/api/v1/costs/summary`)
    assert.equal(summary.totalCostCny, 10.02)
  } finally {
    globalThis.fetch = originalFetch
  }
})
