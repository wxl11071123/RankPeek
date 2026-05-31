import test from 'node:test'
import assert from 'node:assert/strict'
import {
  getLocalAiSettings,
  saveLocalAiSettings
} from './localAiProviderClient.ts'
import { RANKPEEK_LOCAL_SERVICE_BASE_URL } from './rankpeekLocalServiceClient.ts'

test('getLocalAiSettings calls local AI settings endpoint', async () => {
  const originalFetch = globalThis.fetch
  const calls: string[] = []

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push(String(url))
    assert.equal(init, undefined)
    return new Response(JSON.stringify({
      success: true,
      data: {
        enabled: true,
        providerId: 'deepseek',
        baseUrl: 'https://api.deepseek.com',
        model: 'deepseek-v4-flash',
        apiKeySaved: true,
        apiKeyMasked: 'sk-...test',
        temperature: 0.4,
        maxTokens: 4096,
        pricing: {
          currency: 'CNY',
          inputCacheHitCnyPerMillionTokens: 0.02,
          inputCacheMissCnyPerMillionTokens: 1,
          outputCnyPerMillionTokens: 2
        }
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const settings = await getLocalAiSettings()

    assert.equal(calls[0], `${RANKPEEK_LOCAL_SERVICE_BASE_URL}/api/v1/ai/settings`)
    assert.equal(settings.providerId, 'deepseek')
    assert.equal(settings.apiKeySaved, true)
    assert.equal(settings.pricing?.currency, 'CNY')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('saveLocalAiSettings uses PUT on local AI settings endpoint', async () => {
  const originalFetch = globalThis.fetch
  const calls: Array<{ url: string; init?: RequestInit }> = []

  globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), init })
    return new Response(JSON.stringify({
      success: true,
      data: {
        enabled: true,
        providerId: 'custom-openai-compatible',
        baseUrl: 'https://provider.example/v1',
        model: 'free-model',
        apiKeySaved: false,
        apiKeyMasked: null,
        temperature: 0.2,
        maxTokens: 2048,
        pricing: null
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  }) as typeof fetch

  try {
    const settings = await saveLocalAiSettings({
      enabled: true,
      providerId: 'custom-openai-compatible',
      baseUrl: 'https://provider.example/v1',
      model: 'free-model',
      apiKey: '',
      saveApiKey: false,
      temperature: 0.2,
      maxTokens: 2048,
      pricing: null
    })

    assert.equal(calls[0]?.url, `${RANKPEEK_LOCAL_SERVICE_BASE_URL}/api/v1/ai/settings`)
    assert.equal(calls[0]?.init?.method, 'PUT')
    assert.deepEqual(calls[0]?.init?.headers, { 'Content-Type': 'application/json' })
    assert.equal(JSON.parse(String(calls[0]?.init?.body)).providerId, 'custom-openai-compatible')
    assert.equal(settings.model, 'free-model')
  } finally {
    globalThis.fetch = originalFetch
  }
})
