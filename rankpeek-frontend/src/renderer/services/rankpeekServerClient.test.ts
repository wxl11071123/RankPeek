import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  checkRankPeekServerDiagnostics,
  RANKPEEK_SERVER_BASE_URL,
  RANKPEEK_SERVER_DIAGNOSTICS_ENDPOINT
} from './rankpeekServerClient.ts'

test('checks rankpeek-server diagnostics through the local server endpoint', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), init })
    return new Response(JSON.stringify({
      success: true,
      data: {
        status: 'ok',
        service: 'rankpeek-server',
        mode: 'prod',
        version: '0.1.0',
        database: {
          status: 'ok',
          productName: 'PostgreSQL',
          productVersion: '16.13'
        },
        flyway: {
          status: 'ok',
          currentVersion: '4',
          appliedCount: 4
        }
      },
      error: null
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const result = await checkRankPeekServerDiagnostics()

    assert.deepEqual(result, {
      available: true,
      service: 'rankpeek-server',
      mode: 'prod',
      version: '0.1.0'
    })
    assert.equal(calls.length, 1)
    assert.equal(calls[0]?.url, `${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_DIAGNOSTICS_ENDPOINT}`)
    assert.equal(calls[0]?.init?.method, 'GET')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('diagnostics check returns unavailable instead of throwing', async () => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async () => {
    throw new TypeError('fetch failed')
  }) as typeof fetch

  try {
    const result = await checkRankPeekServerDiagnostics()

    assert.equal(result.available, false)
    assert.match(result.available ? '' : result.message, /rankpeek-server/)
    assert.match(result.available ? '' : result.message, /Ubuntu\/WSL/)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('diagnostics check treats invalid diagnostics payloads as unavailable', async () => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async () => new Response('not-json', {
    status: 200,
    headers: { 'Content-Type': 'text/plain' }
  })) as typeof fetch

  try {
    const result = await checkRankPeekServerDiagnostics()

    assert.equal(result.available, false)
    assert.match(result.available ? '' : result.message, /rankpeek-server/)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('server AI services use the shared rankpeek-server base URL', () => {
  for (const relativePath of [
    './gamingAiServerSync.ts',
    './gamingAiServerStream.ts',
    './postgameAiServerStream.ts'
  ]) {
    const source = readFileSync(new URL(relativePath, import.meta.url), 'utf8')
    assert.match(source, /from '\.\/rankpeekServerClient\.ts'/)
    assert.doesNotMatch(source, /export const RANKPEEK_SERVER_BASE_URL =/)
  }
})
