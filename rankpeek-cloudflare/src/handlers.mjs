const JSON_HEADERS = {
  'content-type': 'application/json; charset=utf-8',
  'access-control-allow-origin': '*',
  'access-control-allow-methods': 'GET,POST,OPTIONS',
  'access-control-allow-headers': 'content-type'
}

const FEEDBACK_MAX_PER_HOUR = 5
const FEEDBACK_CATEGORIES = new Set(['bug', 'suggestion', 'other'])

export async function handleRequest(request, env, options = {}) {
  if (request.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers: JSON_HEADERS })
  }

  const url = new URL(request.url)
  if (url.pathname === '/app/feedback' && request.method === 'POST') {
    return handleFeedback(request, env, options)
  }

  if (url.pathname === '/app/announcements' && request.method === 'GET') {
    return handleAnnouncements(url, env, options)
  }

  return jsonResponse({ success: false, error: { code: 'NOT_FOUND', message: 'Not found' } }, 404)
}

async function handleFeedback(request, env, options) {
  if (!env.DB) {
    return jsonResponse({ success: false, error: { code: 'DB_UNAVAILABLE', message: 'Database is not configured' } }, 503)
  }

  const body = await readJsonBody(request)
  if (!body.ok) {
    return validationError('Request body must be valid JSON')
  }

  const payload = normalizeFeedbackPayload(body.value)
  if (!payload.ok) {
    return validationError(payload.message)
  }

  const feedback = payload.value
  const createdAt = (options.now ?? new Date()).toISOString()
  const ipHash = await hashRequestIp(request, env)
  const userAgent = truncate(String(request.headers.get('user-agent') ?? ''), 300)
  const limited = await isRateLimited(env.DB, {
    installationId: feedback.installationId,
    ipHash,
    now: options.now ?? new Date()
  })

  if (limited) {
    return jsonResponse({
      success: false,
      error: { code: 'RATE_LIMITED', message: 'Too many feedback submissions. Try again later.' }
    }, 429)
  }

  const id = crypto.randomUUID()
  await env.DB.prepare(`
    insert into feedback_messages (
      id, category, contact, message, app_version, platform, locale,
      installation_id, ip_hash, user_agent, notification_status, created_at
    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).bind(
    id,
    feedback.category,
    feedback.contact,
    feedback.message,
    feedback.appVersion,
    feedback.platform,
    feedback.locale,
    feedback.installationId,
    ipHash,
    userAgent,
    'pending',
    createdAt
  ).run()

  const notificationQueued = await notifyFeedback(env, { id, ...feedback, createdAt })
  await env.DB.prepare(`
    update feedback_messages
    set notification_status = ?
    where id = ?
  `).bind(notificationQueued ? 'sent' : 'failed', id).run()

  return jsonResponse({
    success: true,
    data: {
      id,
      notificationQueued
    }
  })
}

async function handleAnnouncements(url, env, options) {
  if (!env.DB) {
    return jsonResponse({ success: false, error: { code: 'DB_UNAVAILABLE', message: 'Database is not configured' } }, 503)
  }

  const version = normalizeQueryValue(url.searchParams.get('version')) || '0.0.0'
  const platform = normalizeQueryValue(url.searchParams.get('platform')) || 'unknown'
  const locale = normalizeQueryValue(url.searchParams.get('locale')) || 'zh-CN'
  const channel = normalizeQueryValue(url.searchParams.get('channel')) || 'stable'
  const now = options.now ?? new Date()

  const result = await env.DB.prepare(`
    select
      id, title, body, level, link_url, min_version, max_version,
      platforms, locales, channels, starts_at, ends_at, enabled, created_at
    from announcements
    where enabled = 1
    order by created_at desc
    limit 50
  `).all()

  const announcements = (result.results ?? [])
    .filter(row => isAnnouncementActiveFor(row, { version, platform, locale, channel, now }))
    .map(mapAnnouncementRow)

  return jsonResponse({ success: true, data: announcements })
}

async function readJsonBody(request) {
  try {
    const value = await request.json()
    return { ok: true, value }
  } catch {
    return { ok: false }
  }
}

function normalizeFeedbackPayload(value) {
  if (!isRecord(value)) {
    return { ok: false, message: 'Feedback payload must be an object' }
  }

  const message = normalizeString(value.message, 2000)
  if (message.length < 10) {
    return { ok: false, message: 'Feedback message must be at least 10 characters' }
  }

  const contact = normalizeString(value.contact, 180)
  const category = normalizeCategory(value.category)
  const appVersion = normalizeString(value.appVersion, 40) || 'unknown'
  const platform = normalizeString(value.platform, 40) || 'unknown'
  const locale = normalizeString(value.locale, 20) || 'zh-CN'
  const installationId = normalizeString(value.installationId, 80)
  if (!installationId) {
    return { ok: false, message: 'Installation id is required' }
  }

  return {
    ok: true,
    value: {
      message,
      contact,
      category,
      appVersion,
      platform,
      locale,
      installationId
    }
  }
}

function normalizeCategory(value) {
  const category = normalizeString(value, 30)
  return FEEDBACK_CATEGORIES.has(category) ? category : 'other'
}

function normalizeString(value, maxLength) {
  if (typeof value !== 'string') {
    return ''
  }
  return truncate(value.trim(), maxLength)
}

function truncate(value, maxLength) {
  return value.length > maxLength ? value.slice(0, maxLength) : value
}

async function isRateLimited(db, { installationId, ipHash, now }) {
  const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000).toISOString()
  const row = await db.prepare(`
    select count(*) as count
    from feedback_messages
    where created_at >= ?
      and (installation_id = ? or ip_hash = ?)
  `).bind(oneHourAgo, installationId, ipHash).first()

  return Number(row?.count ?? 0) >= FEEDBACK_MAX_PER_HOUR
}

async function hashRequestIp(request, env) {
  const ip = request.headers.get('cf-connecting-ip') ?? request.headers.get('x-forwarded-for') ?? 'unknown'
  const salt = env.IP_HASH_SALT || 'rankpeek-feedback'
  const encoded = new TextEncoder().encode(`${salt}:${ip}`)
  const digest = await crypto.subtle.digest('SHA-256', encoded)
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
}

async function notifyFeedback(env, feedback) {
  if (!env.EMAIL?.send || !env.FEEDBACK_RECIPIENT_EMAIL || !env.FEEDBACK_FROM_EMAIL) {
    return false
  }

  const lines = [
    `Feedback id: ${feedback.id}`,
    `Category: ${feedback.category}`,
    `Contact: ${feedback.contact || 'not provided'}`,
    `Version: ${feedback.appVersion}`,
    `Platform: ${feedback.platform}`,
    `Locale: ${feedback.locale}`,
    `Installation: ${feedback.installationId}`,
    `Created: ${feedback.createdAt}`,
    '',
    feedback.message
  ]

  try {
    await env.EMAIL.send({
      from: env.FEEDBACK_FROM_EMAIL,
      to: env.FEEDBACK_RECIPIENT_EMAIL,
      subject: `[RankPeek] New feedback ${feedback.id.slice(0, 8)}`,
      text: lines.join('\n')
    })
    return true
  } catch {
    return false
  }
}

function validationError(message) {
  return jsonResponse({
    success: false,
    error: {
      code: 'VALIDATION_ERROR',
      message
    }
  }, 400)
}

function isAnnouncementActiveFor(row, request) {
  return (
    isWithinTimeWindow(row, request.now) &&
    matchesCsv(row.channels, request.channel) &&
    matchesCsv(row.locales, request.locale) &&
    matchesPlatform(row.platforms, request.platform) &&
    matchesVersionRange(request.version, row.min_version, row.max_version)
  )
}

function isWithinTimeWindow(row, now) {
  const time = now.getTime()
  if (row.starts_at && Date.parse(row.starts_at) > time) {
    return false
  }
  if (row.ends_at && Date.parse(row.ends_at) <= time) {
    return false
  }
  return true
}

function matchesCsv(csv, value) {
  const entries = splitCsv(csv)
  return entries.length === 0 || entries.includes('all') || entries.includes(value.toLowerCase())
}

function matchesPlatform(csv, platform) {
  const normalizedPlatform = platform.toLowerCase()
  const aliases = new Set([normalizedPlatform])
  if (normalizedPlatform === 'win32' || normalizedPlatform === 'windows') {
    aliases.add('win32')
    aliases.add('windows')
  }
  const entries = splitCsv(csv)
  return entries.length === 0 || entries.includes('all') || entries.some(entry => aliases.has(entry))
}

function splitCsv(value) {
  if (typeof value !== 'string') {
    return []
  }
  return value
    .split(',')
    .map(item => item.trim().toLowerCase())
    .filter(Boolean)
}

function matchesVersionRange(version, minVersion, maxVersion) {
  if (minVersion && compareVersions(version, minVersion) < 0) {
    return false
  }
  if (maxVersion && compareVersions(version, maxVersion) > 0) {
    return false
  }
  return true
}

function compareVersions(left, right) {
  const leftParts = parseVersion(left)
  const rightParts = parseVersion(right)
  const length = Math.max(leftParts.length, rightParts.length)
  for (let index = 0; index < length; index += 1) {
    const difference = (leftParts[index] ?? 0) - (rightParts[index] ?? 0)
    if (difference !== 0) {
      return difference > 0 ? 1 : -1
    }
  }
  return 0
}

function parseVersion(value) {
  return String(value ?? '')
    .split(/[.-]/)
    .map(part => Number.parseInt(part, 10))
    .filter(part => Number.isFinite(part))
}

function mapAnnouncementRow(row) {
  return {
    id: row.id,
    title: row.title,
    body: row.body,
    level: row.level || 'info',
    linkUrl: row.link_url || null,
    startsAt: row.starts_at || null,
    endsAt: row.ends_at || null
  }
}

function normalizeQueryValue(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function jsonResponse(payload, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: JSON_HEADERS
  })
}

function isRecord(value) {
  return typeof value === 'object' && value !== null
}
