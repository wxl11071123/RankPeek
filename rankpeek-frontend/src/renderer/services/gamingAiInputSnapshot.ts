import type { RecordStatus, SessionData, SessionSummoner, Summoner } from '@/types/api'
import { formatGamingAiRankText } from './gamingAiAnalysisPreview.ts'

export type GamingAiInputMode = 'teammate' | 'opponent'

export interface GamingAiInputSnapshot {
  schemaVersion: 'gaming_ai_input_snapshot.v1'
  mode: GamingAiInputMode
  generatedAt: string
  phase: string
  queueId: number
  queueName: string
  matchId?: string
  roundIndex?: number
  currentSummoner?: {
    puuid?: string
    gameName?: string
    tagLine?: string
  }
  allyTeam: GamingAiInputPlayer[]
  enemyTeam: GamingAiInputPlayer[]
  selectedPlayers: GamingAiInputPlayer[]
  dataQuality: {
    allyCount: number
    enemyCount: number
    selectedCount: number
    normalRecordCount: number
    hiddenRecordCount: number
    emptyRecordCount: number
    errorRecordCount: number
  }
}

export interface GamingAiInputPlayer {
  side: 'ally' | 'enemy'
  puuid?: string
  gameName: string
  tagLine?: string
  displayName: string
  championId?: number
  championKey?: string
  rankText: string
  recordStatus: 'NORMAL' | 'PRIVATE' | 'EMPTY' | 'ERROR'
  tags: Array<{
    name: string
    good?: boolean | null
    desc?: string
  }>
  metrics: {
    sample: number
    wins: number
    losses: number
    winRate: number | null
    kda: number | null
    kills: number | null
    deaths: number | null
    assists: number | null
    averageGold: number | null
    averageDamageDealtToChampions: number | null
    damageRate: number | null
    goldRate: number | null
    groupRate: number | null
    friendsRate: number | null
    disputeRate: number | null
  }
  preGroupMarker?: {
    name?: string
    type?: string
  }
}

export function buildGamingAiInputSnapshot(input: {
  mode: GamingAiInputMode
  sessionData: SessionData
  selectedPlayers: SessionSummoner[]
  currentSummonerPuuid?: string
}): GamingAiInputSnapshot {
  const allyPlayers = pickPreferredTeam(input.sessionData.teammates, input.sessionData.teamOne)
  const enemyPlayers = pickPreferredTeam(input.sessionData.opponents, input.sessionData.teamTwo)
  const defaultSelectedPlayers = input.mode === 'teammate' ? allyPlayers : enemyPlayers
  const selectedPlayers = input.selectedPlayers?.length ? input.selectedPlayers : defaultSelectedPlayers

  const allyTeam = allyPlayers.map(player => toInputPlayer(player, 'ally'))
  const enemyTeam = enemyPlayers.map(player => toInputPlayer(player, 'enemy'))
  const normalizedByKey = new Map<string, GamingAiInputPlayer>()
  allyPlayers.forEach((player, index) => normalizedByKey.set(getPlayerSnapshotKey(player), allyTeam[index]))
  enemyPlayers.forEach((player, index) => normalizedByKey.set(getPlayerSnapshotKey(player), enemyTeam[index]))
  const selectedFallbackSide = input.mode === 'teammate' ? 'ally' : 'enemy'
  const normalizedSelectedPlayers = selectedPlayers.map(player => (
    normalizedByKey.get(getPlayerSnapshotKey(player)) ?? toInputPlayer(player, selectedFallbackSide)
  ))
  const allPlayers = [...allyTeam, ...enemyTeam]

  return {
    schemaVersion: 'gaming_ai_input_snapshot.v1',
    mode: input.mode,
    generatedAt: new Date().toISOString(),
    phase: input.sessionData.phase || '',
    queueId: finiteNumberOrZero(input.sessionData.queueId),
    queueName: readNonEmptyString(input.sessionData.typeCn) || readNonEmptyString(input.sessionData.queueType) || 'Unknown',
    ...(readNonEmptyString(input.sessionData.matchId) ? { matchId: input.sessionData.matchId.trim() } : {}),
    ...(toFiniteNumber(input.sessionData.roundIndex) != null ? { roundIndex: toFiniteNumber(input.sessionData.roundIndex) as number } : {}),
    ...(normalizeCurrentSummoner(input.sessionData.currentSummoner, input.currentSummonerPuuid) ? {
      currentSummoner: normalizeCurrentSummoner(input.sessionData.currentSummoner, input.currentSummonerPuuid)
    } : {}),
    allyTeam,
    enemyTeam,
    selectedPlayers: normalizedSelectedPlayers,
    dataQuality: {
      allyCount: allyTeam.length,
      enemyCount: enemyTeam.length,
      selectedCount: normalizedSelectedPlayers.length,
      normalRecordCount: countRecordStatus(allPlayers, 'NORMAL'),
      hiddenRecordCount: countRecordStatus(allPlayers, 'PRIVATE'),
      emptyRecordCount: countRecordStatus(allPlayers, 'EMPTY'),
      errorRecordCount: countRecordStatus(allPlayers, 'ERROR')
    }
  }
}

function pickPreferredTeam(preferred: SessionSummoner[] | undefined, fallback: SessionSummoner[] | undefined): SessionSummoner[] {
  return preferred?.length ? preferred : (fallback ?? [])
}

function toInputPlayer(player: SessionSummoner, side: 'ally' | 'enemy'): GamingAiInputPlayer {
  const recentData = player.userTag?.recentData
  const wins = finiteNumberOrZero(recentData?.selectWins)
  const losses = finiteNumberOrZero(recentData?.selectLosses)
  const sample = wins + losses
  const averageGold = toFiniteNumber(recentData?.averageGold)
  const averageDamage = toFiniteNumber(recentData?.averageDamageDealtToChampions)
  const damageRate = averageDamage != null && averageGold != null && averageDamage > 0 && averageGold > 0
    ? (averageDamage / averageGold) * 100
    : null
  const preGroupMarker = normalizePreGroupMarker(player.preGroupMarkers)
  const championId = toFiniteNumber(player.championId)

  return {
    side,
    ...(readNonEmptyString(player.summoner?.puuid) ? { puuid: player.summoner.puuid.trim() } : {}),
    gameName: readNonEmptyString(player.summoner?.gameName) || 'Unknown player',
    ...(readNonEmptyString(player.summoner?.tagLine) ? { tagLine: player.summoner.tagLine.trim() } : {}),
    displayName: formatDisplayName(player.summoner),
    ...(championId != null && championId > 0 ? { championId } : {}),
    ...(readNonEmptyString(player.championKey) ? { championKey: player.championKey.trim() } : {}),
    rankText: formatGamingAiRankText(player),
    recordStatus: normalizeRecordStatus(player.userTag?.recordStatus),
    tags: (player.userTag?.tag ?? [])
      .filter(tag => readNonEmptyString(tag.tagName))
      .map(tag => ({
        name: tag.tagName.trim(),
        ...(tag.good !== undefined ? { good: tag.good } : {}),
        ...(readNonEmptyString(tag.tagDesc) ? { desc: tag.tagDesc.trim() } : {})
      })),
    metrics: {
      sample,
      wins,
      losses,
      winRate: sample > 0 ? (wins / sample) * 100 : null,
      kda: toFiniteNumber(recentData?.kda),
      kills: toFiniteNumber(recentData?.kills),
      deaths: toFiniteNumber(recentData?.deaths),
      assists: toFiniteNumber(recentData?.assists),
      averageGold,
      averageDamageDealtToChampions: averageDamage,
      damageRate,
      goldRate: toFiniteNumber(recentData?.goldRate),
      groupRate: toFiniteNumber(recentData?.groupRate),
      friendsRate: toFiniteNumber(recentData?.friendAndDispute?.friendsRate),
      disputeRate: toFiniteNumber(recentData?.friendAndDispute?.disputeRate)
    },
    ...(preGroupMarker ? { preGroupMarker } : {})
  }
}

function normalizeCurrentSummoner(summoner: Summoner | undefined, currentSummonerPuuid?: string) {
  const puuid = readNonEmptyString(currentSummonerPuuid) || readNonEmptyString(summoner?.puuid)
  const gameName = readNonEmptyString(summoner?.gameName)
  const tagLine = readNonEmptyString(summoner?.tagLine)

  if (!puuid && !gameName && !tagLine) {
    return null
  }

  return {
    ...(puuid ? { puuid } : {}),
    ...(gameName ? { gameName } : {}),
    ...(tagLine ? { tagLine } : {})
  }
}

function normalizePreGroupMarker(marker: SessionSummoner['preGroupMarkers'] | undefined): GamingAiInputPlayer['preGroupMarker'] | null {
  const name = readNonEmptyString(marker?.name)
  const type = readNonEmptyString(marker?.type)

  if (!name && !type) {
    return null
  }

  return {
    ...(name ? { name } : {}),
    ...(type ? { type } : {})
  }
}

function normalizeRecordStatus(status: RecordStatus | undefined): RecordStatus {
  if (status === 'PRIVATE' || status === 'EMPTY' || status === 'ERROR') {
    return status
  }
  return 'NORMAL'
}

function countRecordStatus(players: GamingAiInputPlayer[], status: GamingAiInputPlayer['recordStatus']): number {
  return players.filter(player => player.recordStatus === status).length
}

function formatDisplayName(summoner: Summoner | undefined): string {
  const gameName = readNonEmptyString(summoner?.gameName) || 'Unknown player'
  const tagLine = readNonEmptyString(summoner?.tagLine)
  return tagLine ? `${gameName}#${tagLine}` : gameName
}

function getPlayerSnapshotKey(player: SessionSummoner): string {
  const puuid = readNonEmptyString(player.summoner?.puuid)
  if (puuid) {
    return `puuid:${puuid}`
  }

  const summonerId = toFiniteNumber(player.summoner?.summonerId)
  if (summonerId != null) {
    return `summoner:${summonerId}`
  }

  return `name:${formatDisplayName(player.summoner)}:${finiteNumberOrZero(player.championId)}`
}

function readNonEmptyString(value: unknown): string {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : ''
}

function toFiniteNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function finiteNumberOrZero(value: unknown): number {
  return toFiniteNumber(value) ?? 0
}
