import test from 'node:test'
import assert from 'node:assert/strict'
import { handleRequest } from '../src/handlers.mjs'

class FakeStatement {
  constructor(db, sql) {
    this.db = db
    this.sql = sql
    this.params = []
  }

  bind(...params) {
    this.params = params
    return this
  }

  async first() {
    if (this.sql.includes('count(*) as count')) {
      return { count: this.db.recentFeedbackCount }
    }
    return null
  }

  async run() {
    if (this.sql.includes('insert into feedback_messages')) {
      this.db.feedbackRows.push(this.params)
      return { success: true }
    }
    return { success: true }
  }

  async all() {
    if (this.sql.includes('from announcements')) {
      return { results: this.db.announcements }
    }
    return { results: [] }
  }
}

class FakeD1 {
  constructor() {
    this.feedbackRows = []
    this.announcements = []
    this.recentFeedbackCount = 0
  }

  prepare(sql) {
    return new FakeStatement(this, sql)
  }
}

function makeEnv(overrides = {}) {
  return {
    DB: new FakeD1(),
    EMAIL: {
      sent: [],
      async send(message) {
        this.sent.push(message)
      }
    },
    FEEDBACK_RECIPIENT_EMAIL: '619123277@qq.com',
    FEEDBACK_FROM_EMAIL: 'notify@rankpeek.cn',
    IP_HASH_SALT: 'test-salt',
    ...overrides
  }
}

async function readJson(response) {
  return response.json()
}

test('feedback endpoint validates message body before writing to D1', async () => {
  const env = makeEnv()
  const response = await handleRequest(new Request('https://api.rankpeek.cn/app/feedback', {
    method: 'POST',
    body: JSON.stringify({
      message: 'too short',
      contact: 'player@example.com',
      appVersion: '1.0.0',
      platform: 'win32',
      installationId: 'rp-installation-test'
    })
  }), env)

  assert.equal(response.status, 400)
  assert.equal(env.DB.feedbackRows.length, 0)
  const payload = await readJson(response)
  assert.equal(payload.success, false)
  assert.equal(payload.error.code, 'VALIDATION_ERROR')
})

test('feedback endpoint stores safe fields and sends email notification without logs', async () => {
  const env = makeEnv()
  const response = await handleRequest(new Request('https://api.rankpeek.cn/app/feedback', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'cf-connecting-ip': '203.0.113.9',
      'user-agent': 'RankPeek/1.0.0'
    },
    body: JSON.stringify({
      message: '希望公告能够在客户端里展示，并且反馈不要上传日志。',
      contact: 'player@example.com',
      category: 'suggestion',
      appVersion: '1.0.0',
      platform: 'win32',
      locale: 'zh-CN',
      installationId: 'rp-installation-test',
      logs: 'must not be persisted'
    })
  }), env)

  assert.equal(response.status, 200)
  const payload = await readJson(response)
  assert.equal(payload.success, true)
  assert.equal(payload.data.notificationQueued, true)
  assert.equal(env.DB.feedbackRows.length, 1)
  assert.equal(env.EMAIL.sent.length, 1)

  const storedParams = env.DB.feedbackRows[0].join('\n')
  assert.match(storedParams, /希望公告/)
  assert.match(storedParams, /player@example\.com/)
  assert.match(storedParams, /rp-installation-test/)
  assert.doesNotMatch(storedParams, /must not be persisted/)
  assert.equal(env.EMAIL.sent[0].to, '619123277@qq.com')
  assert.equal(env.EMAIL.sent[0].from, 'notify@rankpeek.cn')
})

test('feedback endpoint rate limits repeated submissions for one installation', async () => {
  const env = makeEnv()
  env.DB.recentFeedbackCount = 5

  const response = await handleRequest(new Request('https://api.rankpeek.cn/app/feedback', {
    method: 'POST',
    body: JSON.stringify({
      message: '这是一个足够长的反馈内容，用来触发频率限制路径。',
      appVersion: '1.0.0',
      platform: 'win32',
      installationId: 'rp-installation-test'
    })
  }), env)

  assert.equal(response.status, 429)
  assert.equal(env.DB.feedbackRows.length, 0)
})

test('announcements endpoint filters by active window, channel, platform, and app version', async () => {
  const env = makeEnv()
  env.DB.announcements = [
    {
      id: 'active-1',
      title: 'RankPeek 公告',
      body: '新版下载源已迁移。',
      level: 'info',
      link_url: 'https://rankpeek.cn',
      min_version: '1.0.0',
      max_version: null,
      platforms: 'win32,windows',
      locales: 'zh-CN',
      channels: 'stable',
      starts_at: '2026-01-01T00:00:00.000Z',
      ends_at: '2026-12-31T23:59:59.000Z',
      enabled: 1,
      created_at: '2026-06-03T00:00:00.000Z'
    },
    {
      id: 'future-1',
      title: 'Future',
      body: 'Not yet',
      level: 'info',
      link_url: null,
      min_version: null,
      max_version: null,
      platforms: 'all',
      locales: 'all',
      channels: 'stable',
      starts_at: '2030-01-01T00:00:00.000Z',
      ends_at: null,
      enabled: 1,
      created_at: '2026-06-03T00:00:00.000Z'
    },
    {
      id: 'old-version',
      title: 'Old',
      body: 'Old clients only',
      level: 'warning',
      link_url: null,
      min_version: null,
      max_version: '0.9.0',
      platforms: 'all',
      locales: 'all',
      channels: 'stable',
      starts_at: null,
      ends_at: null,
      enabled: 1,
      created_at: '2026-06-03T00:00:00.000Z'
    }
  ]

  const response = await handleRequest(new Request(
    'https://api.rankpeek.cn/app/announcements?version=1.0.0&platform=win32&locale=zh-CN&channel=stable'
  ), env, { now: new Date('2026-06-03T00:00:00.000Z') })

  assert.equal(response.status, 200)
  const payload = await readJson(response)
  assert.deepEqual(payload.data.map(item => item.id), ['active-1'])
  assert.equal(payload.data[0].linkUrl, 'https://rankpeek.cn')
})
