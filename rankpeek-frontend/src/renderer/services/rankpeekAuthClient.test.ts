import test from 'node:test'
import assert from 'node:assert/strict'
import {
  clearStoredRankPeekAuthSession,
  getStoredRankPeekAuthSession,
  loginRankPeekAccount,
  logoutRankPeekAccount,
  RANKPEEK_AUTH_LOGIN_ENDPOINT,
  RANKPEEK_AUTH_LOGOUT_ENDPOINT,
  RANKPEEK_AUTH_PASSWORD_RESET_REQUEST_ENDPOINT,
  RANKPEEK_AUTH_REFRESH_ENDPOINT,
  RANKPEEK_AUTH_REGISTER_EMAIL_CODE_ENDPOINT,
  RANKPEEK_AUTH_REGISTER_ENDPOINT,
  registerRankPeekAccount,
  requestRankPeekRegisterEmailCode,
  requestRankPeekPasswordReset,
  refreshStoredRankPeekAuthSession,
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

test('registers through rankpeek-server auth endpoint with display name derived from email', async () => {
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
      email: ' Admin.User@RANKPEEK.LOCAL ',
      password: 'Secret123!'
    })

    assert.equal(result.ok, true)
    assert.equal(calls[0]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_AUTH_REGISTER_ENDPOINT}`)
    assert.deepEqual(JSON.parse(String(calls[0]?.init?.body)), {
      email: 'admin.user@rankpeek.local',
      password: 'Secret123!',
      displayName: 'admin.user'
    })
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('registers with the email verification code required by production signup', async () => {
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
      email: ' Admin.User@RANKPEEK.LOCAL ',
      password: 'Secret123!',
      verificationCode: ' 839204 '
    })

    assert.equal(result.ok, true)
    assert.equal(calls[0]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_AUTH_REGISTER_ENDPOINT}`)
    assert.deepEqual(JSON.parse(String(calls[0]?.init?.body)), {
      email: 'admin.user@rankpeek.local',
      password: 'Secret123!',
      displayName: 'admin.user',
      verificationCode: '839204'
    })
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('requests a register email verification code through rankpeek-server auth endpoint', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), init })
    return new Response(JSON.stringify({
      success: true,
      data: { accepted: true, expiresInSeconds: 900 },
      error: null
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const result = await requestRankPeekRegisterEmailCode({
      email: ' Admin.User@RANKPEEK.LOCAL '
    })

    assert.equal(result.ok, true)
    assert.equal(result.ok ? result.accepted : false, true)
    assert.equal(result.ok ? result.expiresInSeconds : 0, 900)
    assert.equal(calls[0]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_AUTH_REGISTER_EMAIL_CODE_ENDPOINT}`)
    assert.equal(calls[0]?.init?.method, 'POST')
    assert.deepEqual(JSON.parse(String(calls[0]?.init?.body)), {
      email: 'admin.user@rankpeek.local'
    })
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('requests password reset through rankpeek-server auth endpoint', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), init })
    return new Response(JSON.stringify({
      success: true,
      data: { accepted: true },
      error: null
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const result = await requestRankPeekPasswordReset({
      email: ' Admin.User@RANKPEEK.LOCAL '
    })

    assert.equal(result.ok, true)
    assert.equal(result.ok ? result.accepted : false, true)
    assert.equal(calls[0]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_AUTH_PASSWORD_RESET_REQUEST_ENDPOINT}`)
    assert.equal(calls[0]?.init?.method, 'POST')
    assert.deepEqual(JSON.parse(String(calls[0]?.init?.body)), {
      email: 'admin.user@rankpeek.local'
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

test('reports a user-facing login prompt when refreshing without a stored session', async () => {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })

  try {
    const result = await refreshStoredRankPeekAuthSession()

    assert.equal(result.ok, false)
    assert.equal(result.ok ? '' : result.message, '请先登录 RankPeek 账号后再试。')
  } finally {
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
})

test('refreshes a stored rankpeek auth session with rotated refresh tokens', async () => {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })
  storeRankPeekAuthSession(authPayload.data)

  const originalFetch = globalThis.fetch
  const calls: Array<{ url: string; init?: RequestInit }> = []
  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), init })
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
  }) as typeof fetch

  try {
    const result = await refreshStoredRankPeekAuthSession()

    assert.equal(result.ok, true)
    assert.equal(result.ok ? result.session.accessToken : '', 'rotated-access-token')
    assert.equal(result.ok ? result.session.refreshToken : '', 'rotated-refresh-token')
    assert.equal(getStoredRankPeekAuthSession()?.accessToken, 'rotated-access-token')
    assert.equal(getStoredRankPeekAuthSession()?.refreshToken, 'rotated-refresh-token')
    assert.equal(calls[0]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_AUTH_REFRESH_ENDPOINT}`)
    assert.deepEqual(JSON.parse(String(calls[0]?.init?.body)), { refreshToken: 'refresh-token' })
  } finally {
    globalThis.fetch = originalFetch
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
})

test('deduplicates concurrent stored session refresh attempts', async () => {
  const originalLocalStorage = globalThis.localStorage
  Object.defineProperty(globalThis, 'localStorage', {
    value: new MemoryStorage(),
    configurable: true
  })
  storeRankPeekAuthSession(authPayload.data)

  const originalFetch = globalThis.fetch
  let refreshCalls = 0
  globalThis.fetch = (async () => {
    refreshCalls += 1
    await new Promise(resolve => setTimeout(resolve, 10))
    return new Response(JSON.stringify({
      success: true,
      data: {
        accessToken: 'single-flight-access-token',
        refreshToken: 'single-flight-refresh-token',
        expiresInSeconds: 3600
      },
      error: null
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const [first, second] = await Promise.all([
      refreshStoredRankPeekAuthSession(),
      refreshStoredRankPeekAuthSession()
    ])

    assert.equal(first.ok, true)
    assert.equal(second.ok, true)
    assert.equal(first.ok ? first.session.accessToken : '', 'single-flight-access-token')
    assert.equal(second.ok ? second.session.accessToken : '', 'single-flight-access-token')
    assert.equal(refreshCalls, 1)
  } finally {
    globalThis.fetch = originalFetch
    Object.defineProperty(globalThis, 'localStorage', {
      value: originalLocalStorage,
      configurable: true
    })
  }
})
