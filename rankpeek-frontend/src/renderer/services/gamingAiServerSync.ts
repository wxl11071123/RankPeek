import type { GamingAiInputPlayer, GamingAiInputSnapshot } from './gamingAiInputSnapshot.ts'

export const RANKPEEK_SERVER_BASE_URL = 'http://127.0.0.1:18080'
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
  return {
    allyTeamTags: snapshot.allyTeam.map(player => formatPlayerTagLine(player)),
    enemyTeamTags: snapshot.enemyTeam.map(player => formatPlayerTagLine(player))
  }
}

export function createPregameMockRequestFromSnapshot(snapshot: GamingAiInputSnapshot): PregameMockRequest {
  const flattened = flattenGamingAiSnapshotTags(snapshot)
  const focusPlayer = snapshot.selectedPlayers[0] ?? snapshot.allyTeam[0] ?? snapshot.enemyTeam[0]

  return {
    queueId: snapshot.queueId,
    ...(focusPlayer?.championId ? { championId: focusPlayer.championId } : {}),
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
  const parts = [
    player.side,
    player.displayName
  ]

  if (player.isSelf) {
    parts.push('self=true')
  }

  parts.push(
    `champion=${player.championId ?? 'unknown'}`,
    `rank=${player.rankText || 'unknown'}`,
    `status=${player.recordStatus}`,
    `sample=${player.metrics.sample}`,
    `winRate=${formatPercent(player.metrics.winRate)}`,
    `kda=${formatNumber(player.metrics.kda)}`,
    `damageRate=${formatPercent(player.metrics.damageRate)}`
  )
  const tagNames = player.tags.map(tag => tag.name).filter(Boolean)

  if (tagNames.length) {
    parts.push(`tags=${tagNames.join(', ')}`)
  }

  return parts.join(' | ')
}

function formatPercent(value: number | null): string {
  return Number.isFinite(value) && value != null ? `${value.toFixed(1)}%` : '--'
}

function formatNumber(value: number | null): string {
  return Number.isFinite(value) && value != null ? value.toFixed(1) : '--'
}

async function parsePregameMockResponse(response: Response): Promise<PregameMockResponse> {
  try {
    return await response.json() as PregameMockResponse
  } catch {
    return {}
  }
}
