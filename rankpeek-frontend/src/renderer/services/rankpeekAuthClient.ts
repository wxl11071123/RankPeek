import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'

export const RANKPEEK_AUTH_LOGIN_ENDPOINT = '/api/auth/login'
export const RANKPEEK_AUTH_REGISTER_ENDPOINT = '/api/auth/register'
export const RANKPEEK_AUTH_REGISTER_EMAIL_CODE_ENDPOINT = '/api/auth/register/email-code'
export const RANKPEEK_AUTH_PASSWORD_RESET_REQUEST_ENDPOINT = '/api/auth/password-reset/request'
export const RANKPEEK_AUTH_REFRESH_ENDPOINT = '/api/auth/refresh'
export const RANKPEEK_AUTH_LOGOUT_ENDPOINT = '/api/auth/logout'
export const RANKPEEK_AUTH_ME_ENDPOINT = '/api/auth/me'

const RANKPEEK_AUTH_STORAGE_KEY = 'rankpeek.auth.session'
const AUTH_UNAVAILABLE_MESSAGE = 'RankPeek 账号服务暂时不可用，请稍后再试。'
const AUTH_LOGIN_REQUIRED_MESSAGE = '请先登录 RankPeek 账号后再试。'
const AUTH_LOGIN_EXPIRED_MESSAGE = '登录状态已失效，请重新登录后再试。'
const INVALID_CREDENTIALS_MESSAGE = '邮箱或密码不正确'
let inFlightRefreshSession: Promise<AuthResult> | null = null

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

interface RefreshTokenApiResponseData {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
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

type PasswordResetRequestResult =
  | {
    ok: true
    accepted: boolean
  }
  | {
    ok: false
    message: string
  }

type EmailCodeRequestResult =
  | {
    ok: true
    accepted: boolean
    expiresInSeconds: number
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
  verificationCode?: string
}): Promise<AuthResult> {
  const email = normalizeEmail(input.email)
  const verificationCode = input.verificationCode?.trim()
  const body: Record<string, string | null> = {
    email,
    password: input.password,
    displayName: deriveRankPeekDisplayNameFromEmail(email)
  }
  if (verificationCode) {
    body.verificationCode = verificationCode
  }
  return submitAuthRequest(RANKPEEK_AUTH_REGISTER_ENDPOINT, body, 'register')
}

export async function requestRankPeekRegisterEmailCode(input: {
  email: string
}): Promise<EmailCodeRequestResult> {
  try {
    const response = await fetch(`${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_AUTH_REGISTER_EMAIL_CODE_ENDPOINT}`, {
      method: 'POST',
      headers: jsonHeaders(),
      body: JSON.stringify({ email: normalizeEmail(input.email) })
    })
    const payload = await parseAuthResponse<{ accepted?: boolean; expiresInSeconds?: number }>(response)

    if (!response.ok || payload.success === false) {
      return {
        ok: false,
        message: authFailureMessage(payload.error?.code, payload.error?.message, 'register')
      }
    }

    return {
      ok: true,
      accepted: payload.data?.accepted === true,
      expiresInSeconds: typeof payload.data?.expiresInSeconds === 'number'
        ? payload.data.expiresInSeconds
        : 0
    }
  } catch {
    return { ok: false, message: AUTH_UNAVAILABLE_MESSAGE }
  }
}

export async function requestRankPeekPasswordReset(input: {
  email: string
}): Promise<PasswordResetRequestResult> {
  try {
    const response = await fetch(`${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_AUTH_PASSWORD_RESET_REQUEST_ENDPOINT}`, {
      method: 'POST',
      headers: jsonHeaders(),
      body: JSON.stringify({ email: normalizeEmail(input.email) })
    })
    const payload = await parseAuthResponse<{ accepted?: boolean }>(response)

    if (!response.ok || payload.success === false) {
      return { ok: false, message: payload.error?.message || AUTH_UNAVAILABLE_MESSAGE }
    }

    return { ok: true, accepted: payload.data?.accepted === true }
  } catch {
    return { ok: false, message: AUTH_UNAVAILABLE_MESSAGE }
  }
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

export async function refreshStoredRankPeekAuthSession(): Promise<AuthResult> {
  if (inFlightRefreshSession) {
    return inFlightRefreshSession
  }

  inFlightRefreshSession = refreshStoredRankPeekAuthSessionOnce()
  try {
    return await inFlightRefreshSession
  } finally {
    inFlightRefreshSession = null
  }
}

async function refreshStoredRankPeekAuthSessionOnce(): Promise<AuthResult> {
  const currentSession = getStoredRankPeekAuthSession()
  if (!currentSession?.refreshToken) {
    return { ok: false, message: AUTH_LOGIN_REQUIRED_MESSAGE }
  }

  try {
    const response = await fetch(`${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_AUTH_REFRESH_ENDPOINT}`, {
      method: 'POST',
      headers: jsonHeaders(),
      body: JSON.stringify({ refreshToken: currentSession.refreshToken })
    })
    const payload = await parseAuthResponse<RefreshTokenApiResponseData>(response)

    if (!response.ok || payload.success === false) {
      if (payload.error?.code === 'REFRESH_TOKEN_INVALID') {
        clearStoredRankPeekAuthSession()
      }
      return {
        ok: false,
        message: payload.error?.code === 'REFRESH_TOKEN_INVALID'
          ? AUTH_LOGIN_EXPIRED_MESSAGE
          : AUTH_UNAVAILABLE_MESSAGE
      }
    }
    if (payload.success !== true || !isRefreshTokenResponse(payload.data)) {
      return { ok: false, message: AUTH_UNAVAILABLE_MESSAGE }
    }

    const session: RankPeekAuthSession = {
      ...currentSession,
      accessToken: payload.data.accessToken,
      refreshToken: payload.data.refreshToken,
      expiresInSeconds: payload.data.expiresInSeconds
    }
    storeRankPeekAuthSession(session)
    return { ok: true, session }
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
    const payload = await parseAuthResponse<RankPeekAuthSession>(response)

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

async function parseAuthResponse<T>(response: Response): Promise<AuthApiResponse<T>> {
  try {
    return await response.json() as AuthApiResponse<T>
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
  if (code === 'EMAIL_VERIFICATION_CODE_INVALID') {
    return '验证码无效或已过期'
  }
  if (code === 'EMAIL_VERIFICATION_CODE_TOO_FREQUENT') {
    return '验证码发送太频繁，请稍后再试'
  }
  if (message) {
    return message
  }
  return action === 'login' ? '登录失败' : '注册失败'
}

function normalizeEmail(email: string): string {
  return email.trim().toLowerCase()
}

function deriveRankPeekDisplayNameFromEmail(email: string): string {
  const localPart = email.split('@')[0]?.trim()
  return localPart || email
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

function isRefreshTokenResponse(value: unknown): value is RefreshTokenApiResponseData {
  const candidate = value as Partial<RefreshTokenApiResponseData> | null
  return Boolean(
    candidate
    && typeof candidate.accessToken === 'string'
    && typeof candidate.refreshToken === 'string'
    && typeof candidate.expiresInSeconds === 'number'
  )
}
