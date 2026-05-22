import type { GamingAiInputPlayer, GamingAiInputSnapshot, GamingAiTeamSnapshot } from './gamingAiInputSnapshot.ts'
import { RANKPEEK_SERVER_BASE_URL } from './rankpeekServerClient.ts'

export const RANKPEEK_SERVER_PREGAME_MOCK_ENDPOINT = '/api/analysis/pregame/mock'

export interface PregameMockRequest {
  patchKey?: string
  queueId?: number
  championId?: number
  role?: string
  allyTeamTags: string[]
  enemyTeamTags: string[]
  snapshotSchemaVersion?: string
  snapshot?: GamingAiInputSnapshot
}

interface PregameMockResponse {
  success?: boolean
  data?: {
    summary?: string
  }
  error?: {
    message?: string
  }
}

export function flattenGamingAiSnapshotTags(snapshot: GamingAiInputSnapshot): {
  allyTeamTags: string[]
  enemyTeamTags: string[]
} {
  if (snapshot.mode === 'opponent') {
    return {
      allyTeamTags: [],
      enemyTeamTags: [formatTeamSnapshotText(snapshot.opponentSnapshot)]
    }
  }

  return {
    allyTeamTags: [formatTeamSnapshotText(snapshot.teammateSnapshot)],
    enemyTeamTags: []
  }
}

export function createPregameMockRequestFromSnapshot(snapshot: GamingAiInputSnapshot): PregameMockRequest {
  const flattened = flattenGamingAiSnapshotTags(snapshot)

  return {
    queueId: snapshot.queueId,
    allyTeamTags: flattened.allyTeamTags,
    enemyTeamTags: flattened.enemyTeamTags,
    snapshotSchemaVersion: snapshot.schemaVersion,
    snapshot
  }
}

export async function submitGamingAiInputSnapshotToServer(
  snapshot: GamingAiInputSnapshot,
  options: { signal?: AbortSignal } = {}
): Promise<
  | { ok: true; summary?: string }
  | { ok: false; message: string }
> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 8000)
  const abortFromOuterSignal = () => controller.abort()

  if (options.signal?.aborted) {
    clearTimeout(timeout)
    return { ok: false, message: '请求已取消' }
  }

  options.signal?.addEventListener('abort', abortFromOuterSignal, { once: true })

  try {
    const response = await fetch(`${RANKPEEK_SERVER_BASE_URL}${RANKPEEK_SERVER_PREGAME_MOCK_ENDPOINT}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(createPregameMockRequestFromSnapshot(snapshot)),
      signal: controller.signal
    })

    if (!response.ok) {
      return { ok: false, message: `rankpeek-server mock 请求失败：HTTP ${response.status}` }
    }

    const payload = await parsePregameMockResponse(response)
    if (payload.success === false) {
      return { ok: false, message: payload.error?.message || 'rankpeek-server mock 请求失败' }
    }

    return {
      ok: true,
      ...(payload.data?.summary ? { summary: payload.data.summary } : {})
    }
  } catch {
    return { ok: false, message: 'rankpeek-server 暂不可用' }
  } finally {
    clearTimeout(timeout)
    options.signal?.removeEventListener('abort', abortFromOuterSignal)
  }
}

function formatPlayerTagLine(player: GamingAiInputPlayer): string {
  return player.summaryLine || player.key
}

function formatTeamSnapshotText(snapshot: GamingAiTeamSnapshot): string {
  return snapshot.text || snapshot.players.map(player => formatPlayerTagLine(player)).join('\n\n')
}

async function parsePregameMockResponse(response: Response): Promise<PregameMockResponse> {
  try {
    return await response.json() as PregameMockResponse
  } catch {
    return {}
  }
}
