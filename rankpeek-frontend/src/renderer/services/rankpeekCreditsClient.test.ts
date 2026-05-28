import test from 'node:test'
import assert from 'node:assert/strict'

import {
  getRankPeekCreditBalance,
  getRankPeekCreditLedger,
  RANKPEEK_CREDITS_BALANCE_ENDPOINT,
  RANKPEEK_CREDITS_LEDGER_ENDPOINT
} from './rankpeekCreditsClient.ts'

test('fetches RankPeek credit balance with a bearer token', async () => {
  const previousFetch = globalThis.fetch
  let requestUrl = ''
  let requestHeaders: HeadersInit | undefined

  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    requestUrl = String(input)
    requestHeaders = init?.headers
    return new Response(JSON.stringify({
      success: true,
      data: {
        userId: 1,
        balance: 100
      },
      error: null
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const result = await getRankPeekCreditBalance('access-token-1')

    assert.equal(result.ok, true)
    assert.equal(result.ok ? result.balance : -1, 100)
    assert.match(requestUrl, new RegExp(`${RANKPEEK_CREDITS_BALANCE_ENDPOINT}$`))
    assert.deepEqual(requestHeaders, {
      Accept: 'application/json',
      Authorization: 'Bearer access-token-1'
    })
  } finally {
    globalThis.fetch = previousFetch
  }
})

test('reports login required when fetching balance without a token', async () => {
  const result = await getRankPeekCreditBalance('')

  assert.equal(result.ok, false)
  assert.match(result.ok ? '' : result.message, /login/i)
})

test('fetches RankPeek credit ledger with a bearer token', async () => {
  const previousFetch = globalThis.fetch
  let requestUrl = ''
  let requestHeaders: HeadersInit | undefined

  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    requestUrl = String(input)
    requestHeaders = init?.headers
    return new Response(JSON.stringify({
      success: true,
      data: {
        entries: [
          {
            id: 2,
            type: 'AI_CHARGE',
            amount: -1,
            balanceAfter: 99,
            referenceType: 'ai_analysis_run',
            referenceId: '7',
            reason: 'AI analysis charge',
            createdAt: '2026-05-28T08:00:00Z'
          },
          {
            id: 1,
            type: 'ADMIN_ADJUSTMENT',
            amount: 100,
            balanceAfter: 100,
            referenceType: 'admin',
            referenceId: '1',
            reason: 'manual top-up',
            createdAt: '2026-05-28T07:00:00Z'
          }
        ]
      },
      error: null
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const result = await getRankPeekCreditLedger('access-token-1')

    assert.equal(result.ok, true)
    assert.equal(result.ok ? result.entries.length : -1, 2)
    assert.deepEqual(result.ok ? result.entries[0] : null, {
      id: 2,
      type: 'AI_CHARGE',
      amount: -1,
      balanceAfter: 99,
      referenceType: 'ai_analysis_run',
      referenceId: '7',
      reason: 'AI analysis charge',
      createdAt: '2026-05-28T08:00:00Z'
    })
    assert.match(requestUrl, new RegExp(`${RANKPEEK_CREDITS_LEDGER_ENDPOINT}$`))
    assert.deepEqual(requestHeaders, {
      Accept: 'application/json',
      Authorization: 'Bearer access-token-1'
    })
  } finally {
    globalThis.fetch = previousFetch
  }
})

test('reports login required when fetching ledger without a token', async () => {
  const result = await getRankPeekCreditLedger(null)

  assert.equal(result.ok, false)
  assert.match(result.ok ? '' : result.message, /login/i)
})
