import test from 'node:test'
import assert from 'node:assert/strict'

import {
  clearStoredRankPeekAuthSession,
  getStoredRankPeekAuthSession,
  storeRankPeekAuthSession
} from './rankpeekAuthClient.ts'
import {
  getRankPeekCreditBalance,
  getRankPeekCreditLedger,
  RANKPEEK_CREDITS_BALANCE_ENDPOINT,
  RANKPEEK_CREDITS_LEDGER_ENDPOINT
} from './rankpeekCreditsClient.ts'
import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'

class MemoryStorage {
  private values = new Map<string, string>()

  getItem(key: string) {
    return this.values.get(key) ?? null
  }

  setItem(key: string, value: string) {
    this.values.set(key, value)
  }

  removeItem(key: string) {
    this.values.delete(key)
  }
}

const storedSession = {
  user: {
    id: 1,
    email: 'player@rankpeek.local',
    displayName: 'Player',
    role: 'USER',
    status: 'ACTIVE'
  },
  accessToken: 'expired-access-token',
  refreshToken: 'refresh-token',
  expiresInSeconds: 3600
}

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

test('refreshes the stored auth session and retries balance after an expired token response', async () => {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })
  storeRankPeekAuthSession(storedSession)

  const previousFetch = globalThis.fetch
  const calls: Array<{ url: string; init?: RequestInit }> = []
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: String(input), init })

    if (calls.length === 1) {
      return new Response(JSON.stringify({
        success: false,
        data: null,
        error: { code: 'ACCESS_TOKEN_INVALID', message: 'Invalid or expired access token' }
      }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      })
    }

    if (String(input).endsWith('/api/auth/refresh')) {
      return new Response(JSON.stringify({
        success: true,
        data: {
          accessToken: 'rotated-access-token',
          refreshToken: 'rotated-refresh-token',
          expiresInSeconds: 3600
        },
        error: null
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    }

    return new Response(JSON.stringify({
      success: true,
      data: { userId: 1, balance: 29 },
      error: null
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const result = await getRankPeekCreditBalance('expired-access-token')

    assert.equal(result.ok, true)
    assert.equal(result.ok ? result.balance : -1, 29)
    assert.equal(calls[0]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_CREDITS_BALANCE_ENDPOINT}`)
    assert.equal(calls[0]?.init?.headers?.['Authorization' as keyof HeadersInit], 'Bearer expired-access-token')
    assert.match(calls[1]?.url || '', /\/api\/auth\/refresh$/)
    assert.equal(calls[2]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_CREDITS_BALANCE_ENDPOINT}`)
    assert.equal(calls[2]?.init?.headers?.['Authorization' as keyof HeadersInit], 'Bearer rotated-access-token')
    assert.equal(getStoredRankPeekAuthSession()?.accessToken, 'rotated-access-token')
  } finally {
    globalThis.fetch = previousFetch
    clearStoredRankPeekAuthSession()
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
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

test('refreshes the stored auth session and retries ledger after an expired token response', async () => {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })
  storeRankPeekAuthSession(storedSession)

  const previousFetch = globalThis.fetch
  const calls: Array<{ url: string; init?: RequestInit }> = []
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: String(input), init })

    if (calls.length === 1) {
      return new Response(JSON.stringify({
        success: false,
        data: null,
        error: { code: 'ACCESS_TOKEN_INVALID', message: 'Invalid or expired access token' }
      }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      })
    }

    if (String(input).endsWith('/api/auth/refresh')) {
      return new Response(JSON.stringify({
        success: true,
        data: {
          accessToken: 'rotated-access-token',
          refreshToken: 'rotated-refresh-token',
          expiresInSeconds: 3600
        },
        error: null
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    }

    return new Response(JSON.stringify({
      success: true,
      data: {
        entries: [
          {
            type: 'AI_REFUND',
            amount: 1,
            balanceAfter: 29
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
    const result = await getRankPeekCreditLedger('expired-access-token')

    assert.equal(result.ok, true)
    assert.equal(result.ok ? result.entries.length : -1, 1)
    assert.equal(calls[2]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_CREDITS_LEDGER_ENDPOINT}`)
    assert.equal(calls[2]?.init?.headers?.['Authorization' as keyof HeadersInit], 'Bearer rotated-access-token')
  } finally {
    globalThis.fetch = previousFetch
    clearStoredRankPeekAuthSession()
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
})
