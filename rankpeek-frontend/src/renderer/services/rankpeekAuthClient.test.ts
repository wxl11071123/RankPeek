import test from 'node:test'
import assert from 'node:assert/strict'
import {
  clearStoredRankPeekAuthSession,
  getStoredRankPeekAuthSession,
  loginRankPeekAccount,
  logoutRankPeekAccount,
  RANKPEEK_AUTH_LOGIN_ENDPOINT,
  RANKPEEK_AUTH_LOGOUT_ENDPOINT,
  RANKPEEK_AUTH_REGISTER_ENDPOINT,
  registerRankPeekAccount,
  storeRankPeekAuthSession
} from './rankpeekAuthClient.ts'
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

const authPayload = {
  success: true,
  data: {
    user: {
      id: 1,
      email: 'admin@rankpeek.local',
      displayName: 'RankPeek Admin',
      role: 'ADMIN',
      status: 'ACTIVE'
    },
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    expiresInSeconds: 3600
  },
  error: null
}

test('logs in through rankpeek-server auth endpoint and normalizes the session', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), init })
    return new Response(JSON.stringify(authPayload), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const result = await loginRankPeekAccount({
      email: ' ADMIN@RANKPEEK.LOCAL ',
      password: 'Secret123!'
    })

    assert.equal(result.ok, true)
    assert.equal(result.ok ? result.session.user.email : '', 'admin@rankpeek.local')
    assert.equal(result.ok ? result.session.user.role : '', 'ADMIN')
    assert.equal(calls[0]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_AUTH_LOGIN_ENDPOINT}`)
    assert.equal(calls[0]?.init?.method, 'POST')
    assert.equal(calls[0]?.init?.headers?.['Content-Type' as keyof HeadersInit], 'application/json')
    assert.deepEqual(JSON.parse(String(calls[0]?.init?.body)), {
      email: 'admin@rankpeek.local',
      password: 'Secret123!'
    })
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('registers through rankpeek-server auth endpoint with a display name', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), init })
    return new Response(JSON.stringify(authPayload), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const result = await registerRankPeekAccount({
      email: 'admin@rankpeek.local',
      password: 'Secret123!',
      displayName: ' RankPeek Admin '
    })

    assert.equal(result.ok, true)
    assert.equal(calls[0]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_AUTH_REGISTER_ENDPOINT}`)
    assert.deepEqual(JSON.parse(String(calls[0]?.init?.body)), {
      email: 'admin@rankpeek.local',
      password: 'Secret123!',
      displayName: 'RankPeek Admin'
    })
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('auth calls return user-facing failures instead of throwing', async () => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async () => new Response(JSON.stringify({
    success: false,
    data: null,
    error: {
      code: 'INVALID_CREDENTIALS',
      message: 'Invalid email or password'
    }
  }), {
    status: 401,
    headers: { 'Content-Type': 'application/json' }
  })) as typeof fetch

  try {
    const result = await loginRankPeekAccount({
      email: 'admin@rankpeek.local',
      password: 'Wrong123!'
    })

    assert.equal(result.ok, false)
    assert.match(result.ok ? '' : result.message, /邮箱|密码|email|password/i)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('stores, reads, clears, and logs out rankpeek auth sessions locally', async () => {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })

  const originalFetch = globalThis.fetch
  const calls: Array<{ url: string; init?: RequestInit }> = []
  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), init })
    return new Response(JSON.stringify({
      success: true,
      data: { revoked: true },
      error: null
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    storeRankPeekAuthSession(authPayload.data)
    assert.equal(getStoredRankPeekAuthSession()?.user.email, 'admin@rankpeek.local')

    const logoutResult = await logoutRankPeekAccount('refresh-token')
    assert.equal(logoutResult.ok, true)
    assert.equal(calls[0]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_AUTH_LOGOUT_ENDPOINT}`)
    assert.deepEqual(JSON.parse(String(calls[0]?.init?.body)), { refreshToken: 'refresh-token' })

    clearStoredRankPeekAuthSession()
    assert.equal(getStoredRankPeekAuthSession(), null)
  } finally {
    globalThis.fetch = originalFetch
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
})
