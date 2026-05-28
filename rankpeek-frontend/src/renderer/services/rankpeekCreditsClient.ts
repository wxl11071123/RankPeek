import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'
import { refreshStoredRankPeekAuthSession } from './rankpeekAuthClient.ts'

export const RANKPEEK_CREDITS_BALANCE_ENDPOINT = '/api/credits/balance'
export const RANKPEEK_CREDITS_LEDGER_ENDPOINT = '/api/credits/ledger'

const CREDIT_BALANCE_UNAVAILABLE_MESSAGE = 'RankPeek credit balance is unavailable'
const CREDIT_LEDGER_UNAVAILABLE_MESSAGE = 'RankPeek credit ledger is unavailable'
const CREDIT_BALANCE_LOGIN_REQUIRED_MESSAGE = 'RankPeek account login is required'

interface CreditBalanceApiResponse {
  success?: boolean
  data?: {
    userId?: number
    balance?: number
  } | null
  error?: {
    code?: string
    message?: string
  } | null
}

interface CreditLedgerApiResponse {
  success?: boolean
  data?: {
    entries?: RankPeekCreditLedgerEntry[]
  } | null
  error?: {
    code?: string
    message?: string
  } | null
}

type CreditBalanceResult =
  | {
    ok: true
    balance: number
  }
  | {
    ok: false
    message: string
  }

export interface RankPeekCreditLedgerEntry {
  id?: number
  type: string
  amount: number
  balanceAfter: number
  referenceType?: string | null
  referenceId?: string | null
  reason?: string | null
  createdAt?: string | null
}

type CreditLedgerResult =
  | {
    ok: true
    entries: RankPeekCreditLedgerEntry[]
  }
  | {
    ok: false
    message: string
  }

export async function getRankPeekCreditBalance(accessToken: string | null | undefined): Promise<CreditBalanceResult> {
  if (!accessToken) {
    return { ok: false, message: CREDIT_BALANCE_LOGIN_REQUIRED_MESSAGE }
  }

  try {
    let response = await fetchCreditRequest(RANKPEEK_CREDITS_BALANCE_ENDPOINT, accessToken)
    if (response.status === 401) {
      const refreshResult = await refreshStoredRankPeekAuthSession()
      if (!refreshResult.ok) {
        return { ok: false, message: refreshResult.message }
      }
      response = await fetchCreditRequest(RANKPEEK_CREDITS_BALANCE_ENDPOINT, refreshResult.session.accessToken)
    }
    const payload = await parseCreditBalanceResponse(response)

    if (!response.ok || payload.success === false) {
      return {
        ok: false,
        message: payload.error?.message || payload.error?.code || CREDIT_BALANCE_UNAVAILABLE_MESSAGE
      }
    }
    if (payload.success !== true || typeof payload.data?.balance !== 'number') {
      return { ok: false, message: CREDIT_BALANCE_UNAVAILABLE_MESSAGE }
    }

    return { ok: true, balance: payload.data.balance }
  } catch {
    return { ok: false, message: CREDIT_BALANCE_UNAVAILABLE_MESSAGE }
  }
}

export async function getRankPeekCreditLedger(accessToken: string | null | undefined): Promise<CreditLedgerResult> {
  if (!accessToken) {
    return { ok: false, message: CREDIT_BALANCE_LOGIN_REQUIRED_MESSAGE }
  }

  try {
    let response = await fetchCreditRequest(RANKPEEK_CREDITS_LEDGER_ENDPOINT, accessToken)
    if (response.status === 401) {
      const refreshResult = await refreshStoredRankPeekAuthSession()
      if (!refreshResult.ok) {
        return { ok: false, message: refreshResult.message }
      }
      response = await fetchCreditRequest(RANKPEEK_CREDITS_LEDGER_ENDPOINT, refreshResult.session.accessToken)
    }
    const payload = await parseCreditLedgerResponse(response)

    if (!response.ok || payload.success === false) {
      return {
        ok: false,
        message: payload.error?.message || payload.error?.code || CREDIT_LEDGER_UNAVAILABLE_MESSAGE
      }
    }
    if (payload.success !== true || !Array.isArray(payload.data?.entries)) {
      return { ok: false, message: CREDIT_LEDGER_UNAVAILABLE_MESSAGE }
    }

    return { ok: true, entries: payload.data.entries.filter(isCreditLedgerEntry) }
  } catch {
    return { ok: false, message: CREDIT_LEDGER_UNAVAILABLE_MESSAGE }
  }
}

async function fetchCreditRequest(endpoint: string, accessToken: string): Promise<Response> {
  return fetch(`${RANKPEEK_SERVER_BASE_URL}${endpoint}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`
    }
  })
}

async function parseCreditBalanceResponse(response: Response): Promise<CreditBalanceApiResponse> {
  try {
    return await response.json() as CreditBalanceApiResponse
  } catch {
    return {}
  }
}

async function parseCreditLedgerResponse(response: Response): Promise<CreditLedgerApiResponse> {
  try {
    return await response.json() as CreditLedgerApiResponse
  } catch {
    return {}
  }
}

function isCreditLedgerEntry(entry: RankPeekCreditLedgerEntry): entry is RankPeekCreditLedgerEntry {
  return typeof entry?.type === 'string'
    && typeof entry.amount === 'number'
    && typeof entry.balanceAfter === 'number'
}
