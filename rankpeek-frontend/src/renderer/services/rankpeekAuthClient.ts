import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'

export const RANKPEEK_AUTH_LOGIN_ENDPOINT = '/api/auth/login'
export const RANKPEEK_AUTH_REGISTER_ENDPOINT = '/api/auth/register'
export const RANKPEEK_AUTH_LOGOUT_ENDPOINT = '/api/auth/logout'
export const RANKPEEK_AUTH_ME_ENDPOINT = '/api/auth/me'

const RANKPEEK_AUTH_STORAGE_KEY = 'rankpeek.auth.session'
const AUTH_UNAVAILABLE_MESSAGE = 'rankpeek-server auth is unavailable'
const INVALID_CREDENTIALS_MESSAGE = '邮箱或密码不正确'

export interface RankPeekAuthUser {
  id: number
  email: string
  displayName: string | null
  role: string
  status: string
}

export interface RankPeekAuthSession {
  user: RankPeekAuthUser
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
}

interface AuthApiResponse<T> {
  success?: boolean
  data?: T
  error?: {
    code?: string
    message?: string
  } | null
}

type AuthResult =
  | {
    ok: true
    session: RankPeekAuthSession
  }
  | {
    ok: false
    message: string
  }

type LogoutResult =
  | {
    ok: true
  }
  | {
    ok: false
    message: string
  }

export async function loginRankPeekAccount(input: {
  email: string
  password: string
}): Promise<AuthResult> {
  return submitAuthRequest(RANKPEEK_AUTH_LOGIN_ENDPOINT, {
    email: normalizeEmail(input.email),
    password: input.password
  }, 'login')
}

export async function registerRankPeekAccount(input: {
  email: string
  password: string
  displayName: string
}): Promise<AuthResult> {
  return submitAuthRequest(RANKPEEK_AUTH_REGISTER_ENDPOINT, {
    email: normalizeEmail(input.email),
    password: input.password,
    displayName: input.displayName.trim() || null
  }, 'register')
}

export async function logoutRankPeekAccount(refreshToken: string | null | undefined): Promise<LogoutResult> {
  if (!refreshToken) {
    return { ok: true }
  }

  try {
    const response = await fetch(`${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_AUTH_LOGOUT_ENDPOINT}`, {
      method: 'POST',
      headers: jsonHeaders(),
      body: JSON.stringify({ refreshToken })
    })
    if (!response.ok) {
      return { ok: false, message: AUTH_UNAVAILABLE_MESSAGE }
    }
    return { ok: true }
  } catch {
    return { ok: false, message: AUTH_UNAVAILABLE_MESSAGE }
  }
}

export function getStoredRankPeekAuthSession(): RankPeekAuthSession | null {
  if (typeof localStorage === 'undefined') {
    return null
  }

  try {
    const raw = localStorage.getItem(RANKPEEK_AUTH_STORAGE_KEY)
    if (!raw) {
      return null
    }
    const session = JSON.parse(raw) as Partial<RankPeekAuthSession>
    return isAuthSession(session) ? session : null
  } catch {
    return null
  }
}

export function storeRankPeekAuthSession(session: RankPeekAuthSession): void {
  if (typeof localStorage === 'undefined') {
    return
  }

  localStorage.setItem(RANKPEEK_AUTH_STORAGE_KEY, JSON.stringify(session))
}

export function clearStoredRankPeekAuthSession(): void {
  if (typeof localStorage === 'undefined') {
    return
  }

  localStorage.removeItem(RANKPEEK_AUTH_STORAGE_KEY)
}

async function submitAuthRequest(
  endpoint: string,
  body: Record<string, string | null>,
  action: 'login' | 'register'
): Promise<AuthResult> {
  try {
    const response = await fetch(`${RANKPEEK_SERVER_BASE_URL}${endpoint}`, {
      method: 'POST',
      headers: jsonHeaders(),
      body: JSON.stringify(body)
    })
    const payload = await parseAuthResponse(response)

    if (!response.ok || payload.success === false) {
      return {
        ok: false,
        message: authFailureMessage(payload.error?.code, payload.error?.message, action)
      }
    }
    if (payload.success !== true || !isAuthSession(payload.data)) {
      return { ok: false, message: AUTH_UNAVAILABLE_MESSAGE }
    }

    return { ok: true, session: payload.data }
  } catch {
    return { ok: false, message: AUTH_UNAVAILABLE_MESSAGE }
  }
}

async function parseAuthResponse(response: Response): Promise<AuthApiResponse<RankPeekAuthSession>> {
  try {
    return await response.json() as AuthApiResponse<RankPeekAuthSession>
  } catch {
    return {}
  }
}

function authFailureMessage(code: string | undefined, message: string | undefined, action: 'login' | 'register'): string {
  if (code === 'INVALID_CREDENTIALS') {
    return INVALID_CREDENTIALS_MESSAGE
  }
  if (code === 'EMAIL_ALREADY_REGISTERED') {
    return '这个邮箱已经注册'
  }
  if (message) {
    return message
  }
  return action === 'login' ? '登录失败' : '注册失败'
}

function normalizeEmail(email: string): string {
  return email.trim().toLowerCase()
}

function jsonHeaders(): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    Accept: 'application/json'
  }
}

function isAuthSession(value: unknown): value is RankPeekAuthSession {
  const candidate = value as Partial<RankPeekAuthSession> | null
  return Boolean(
    candidate
    && candidate.user
    && typeof candidate.user.email === 'string'
    && typeof candidate.user.role === 'string'
    && typeof candidate.accessToken === 'string'
    && typeof candidate.refreshToken === 'string'
    && typeof candidate.expiresInSeconds === 'number'
  )
}
