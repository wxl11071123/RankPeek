import type { MatchHistory, Participant } from '../types/api.ts'
import { formatDuration } from './matchDetailMetrics.ts'

export type RecentMatchResult = 'win' | 'loss' | 'unknown'

export interface ParticipantRecentMatchItem {
  key: string
  gameId: number
  championId: number | null
  result: RecentMatchResult
  resultText: string
  kdaText: string
  queueText: string
  timeText: string
  durationText: string
}

export function buildParticipantRecentMatchItems(
  matches: ReadonlyArray<MatchHistory | null | undefined> | null | undefined,
  puuid: string | null | undefined
): ParticipantRecentMatchItem[] {
  if (!matches?.length) {
    return []
  }

  const normalizedPuuid = normalizeText(puuid)
  return matches
    .map((match, index) => ({ match, index }))
    .filter((entry): entry is { match: MatchHistory, index: number } => Boolean(entry.match))
    .sort((left, right) => compareRecentMatches(left.match, right.match, left.index, right.index))
    .slice(0, 20)
    .map(({ match, index }) => buildRecentMatchItem(match, normalizedPuuid, index))
}

function buildRecentMatchItem(match: MatchHistory, puuid: string, index: number): ParticipantRecentMatchItem {
  const participant = findParticipantForPlayer(match, puuid)
  const championId = normalizePositiveInteger(participant?.championId)
  const result = getResult(participant)

  return {
    key: `${match.gameId || 'match'}-${index}`,
    gameId: match.gameId,
    championId,
    result,
    resultText: getResultText(result),
    kdaText: formatKda(participant),
    queueText: formatQueue(match),
    timeText: formatShortDate(match.gameCreation),
    durationText: formatMatchDuration(match.gameDuration)
  }
}

function findParticipantForPlayer(match: MatchHistory, puuid: string): Participant | null {
  const participantId = findParticipantId(match, puuid)
  if (participantId !== null) {
    return match.participants?.find(participant => participant?.participantId === participantId) ?? null
  }

  if (match.participants?.length === 1) {
    return match.participants[0] ?? null
  }

  return null
}

function findParticipantId(match: MatchHistory, puuid: string): number | null {
  if (!puuid || !match.participantIdentities?.length) {
    return null
  }

  const identity = match.participantIdentities.find(item => item?.player?.puuid === puuid)
  return normalizeFiniteNumber(identity?.participantId)
}

function getResult(participant: Participant | null): RecentMatchResult {
  const win = participant?.stats?.win
  if (win === true) return 'win'
  if (win === false) return 'loss'
  return 'unknown'
}

function getResultText(result: RecentMatchResult): string {
  if (result === 'win') return '胜利'
  if (result === 'loss') return '失败'
  return '--'
}

function formatKda(participant: Participant | null): string {
  const stats = participant?.stats
  const kills = normalizeFiniteNumber(stats?.kills)
  const deaths = normalizeFiniteNumber(stats?.deaths)
  const assists = normalizeFiniteNumber(stats?.assists)

  if (kills === null || deaths === null || assists === null) {
    return '--'
  }

  return `${kills} / ${deaths} / ${assists}`
}

function formatQueue(match: MatchHistory): string {
  const queueId = normalizePositiveInteger(match.queueId)
  if (queueId === 420) {
    return '单双排'
  }
  if (queueId === 440) {
    return '灵活排位'
  }

  const queueText = mapRankedQueueText(match.queueName) || mapRankedQueueText(match.gameMode)
  return queueText || '--'
}

function mapRankedQueueText(value: unknown): string {
  const text = normalizeText(value)
  if (!text) {
    return ''
  }

  const upperText = text.toUpperCase()
  if (upperText === 'RANKED_SOLO_5X5' || /单双|单\/双|SOLO/.test(upperText)) {
    return '单双排'
  }
  if (upperText === 'RANKED_FLEX_SR' || /灵活|FLEX/.test(upperText)) {
    return '灵活排位'
  }
  return ''
}

function formatShortDate(timestamp: unknown): string {
  const safeTimestamp = normalizeFiniteNumber(timestamp)
  if (safeTimestamp === null || safeTimestamp <= 0) {
    return '--'
  }

  const date = new Date(safeTimestamp)
  if (Number.isNaN(date.getTime())) {
    return '--'
  }

  return `${date.getMonth() + 1}/${date.getDate()}`
}

function formatMatchDuration(seconds: unknown): string {
  const safeSeconds = normalizeFiniteNumber(seconds)
  return safeSeconds !== null && safeSeconds > 0 ? formatDuration(safeSeconds) : '--'
}

function compareRecentMatches(left: MatchHistory, right: MatchHistory, leftIndex: number, rightIndex: number): number {
  const leftTime = normalizeFiniteNumber(left.gameCreation)
  const rightTime = normalizeFiniteNumber(right.gameCreation)

  if (leftTime !== null && rightTime !== null && leftTime !== rightTime) {
    return rightTime - leftTime
  }

  if (leftTime !== null && rightTime === null) {
    return -1
  }

  if (leftTime === null && rightTime !== null) {
    return 1
  }

  return leftIndex - rightIndex
}

function normalizeText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizePositiveInteger(value: unknown): number | null {
  const normalized = normalizeFiniteNumber(value)
  return normalized !== null && Number.isInteger(normalized) && normalized > 0 ? normalized : null
}

function normalizeFiniteNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}
