import type {
  GameDetail,
  GameParticipant,
  GameParticipantIdentity,
  MatchHistory,
  MatchTimeline,
  ParticipantFrame,
  TeamObjectiveSummary,
  TimelineEvent,
  TimelineFrame
} from '../types/api.ts'
import {
  createTimelineChartModel,
  resolveParticipantLane,
  type GoldDiffMetricKey
} from './matchTimelineChart.ts'
import { stableStringify } from './aiAnalysisInputSnapshot.ts'

export type PostgameAiMode = 'review' | 'praise'
export type PostgameAiAnalysisType = 'postgame_review' | 'postgame_praise'
export type PostgameAiSide = 'blue' | 'red'

export interface PostgameAiInputSnapshot {
  schemaVersion: 'postgame_ai_input_snapshot.v1'
  analysisType: PostgameAiAnalysisType
  mode: PostgameAiMode
  builtAt: string
  inputHash: string
  match: {
    matchIdHash?: string
    gameIdHash?: string
    queueId: number | null
    queueName?: string
    gameMode?: string
    gameVersion?: string | null
    gameCreation?: number | null
    durationSeconds?: number | null
    isRanked: boolean
    isAram: boolean
    isArena: boolean
  }
  currentPlayerKey: string
  teams: PostgameAiTeamSnapshot[]
  players: PostgameAiPlayerSnapshot[]
  timeline?: PostgameAiTimelineSnapshot
  dataQuality: {
    hasMatchHistory: boolean
    hasGameDetail: boolean
    hasTimeline: boolean
    participantCount: number
    teamCount: number
    hasRankedTimelineMetrics: boolean
    hasArenaAugments: boolean
    warnings: string[]
  }
}

export interface PostgameAiTeamSnapshot {
  side: PostgameAiSide
  teamId: 100 | 200
  win: boolean | null
  totals: {
    kills: number
    deaths: number
    assists: number
    goldEarned: number
    totalDamageDealtToChampions: number
    totalDamageTaken: number
    visionScore: number
  }
  objectives?: {
    dragons: number | null
    barons: number | null
    heralds: number | null
    grubs: number | null
    towers: number | null
    inhibitors: number | null
    turretPlates: number | null
  }
}

export interface PostgameAiPlayerSnapshot {
  playerKey: string
  side: PostgameAiSide
  teamId: number
  participantId: number
  isCurrentPlayer: boolean
  championId: number | null
  championName?: string
  role?: string
  lane?: string
  position?: string
  level?: number
  stats: {
    win: boolean | null
    kills: number
    deaths: number
    assists: number
    kda: number | null
    killParticipation: number | null
    goldEarned: number
    goldShare: number | null
    totalDamageDealtToChampions: number
    damageShare: number | null
    totalDamageTaken: number
    damageTakenShare: number | null
    damageToGoldRatio: number | null
    visionScore: number
    cs: number
    csPerMinute: number | null
  }
  loadout: {
    spellIds: number[]
    itemIds: number[]
    runeIds: number[]
    augmentIds: number[]
  }
  rankedMetrics?: {
    laneGoldDiffAt15?: number
    teamGoldDiffAt15?: number
    turretPlatesTaken?: number
  }
}

export interface PostgameAiTimelineSnapshot {
  hasTimeline: boolean
  durationSeconds?: number
  goldDiffPoints?: Array<{
    minute: number
    teamGoldDiff?: number
    topGoldDiff?: number
    jungleGoldDiff?: number
    middleGoldDiff?: number
    bottomGoldDiff?: number
    supportGoldDiff?: number
  }>
  objectiveEvents?: Array<{
    timeSeconds: number
    type: string
    teamId?: number
    side?: PostgameAiSide
    isCurrentPlayerAlive?: boolean
  }>
  deathEvents?: Array<{
    timeSeconds: number
    playerKey: string
    championId?: number
    teamId: number
    side: PostgameAiSide
    teamGoldDiffAtDeath?: number
    secondsBeforeObjective?: number
  }>
}

interface BuildPostgameAiInputSnapshotParams {
  mode: PostgameAiMode
  matchHistory: MatchHistory
  gameDetail: GameDetail | null
  timeline: MatchTimeline | null
  currentPuuid: string
  currentSummonerName: string
}

type SnapshotParticipant = GameParticipant

const POSTGAME_AI_SCHEMA_VERSION = 'postgame_ai_input_snapshot.v1'
const BLUE_TEAM_ID = 100
const RED_TEAM_ID = 200
const RANKED_QUEUE_IDS = new Set([420, 440])
const ARAM_QUEUE_IDS = new Set([450])
const ARENA_QUEUE_IDS = new Set([1700, 1710])
const LANE_METRICS: Array<Exclude<GoldDiffMetricKey, 'teamAverage'>> = [
  'top',
  'jungle',
  'middle',
  'bottom',
  'support'
]

export function buildPostgameAiInputSnapshot(
  params: BuildPostgameAiInputSnapshotParams
): PostgameAiInputSnapshot {
  const detail = params.gameDetail?.participants?.length
    ? params.gameDetail
    : toGameDetailFromMatchHistory(params.matchHistory)
  const participants = detail?.participants ?? []
  const identities = detail?.participantIdentities ?? []
  const teamIds = new Set(participants.map(participant => toTeamId(participant.teamId)).filter((teamId): teamId is 100 | 200 => teamId !== null))
  const warnings: string[] = []
  const match = buildMatchSnapshot(params.matchHistory, detail)
  const playerKeyByParticipantId = new Map<number, string>()
  participants.forEach(participant => {
    playerKeyByParticipantId.set(participant.participantId, createPlayerKey(participant))
  })
  const currentParticipantId = findCurrentParticipantId(identities, params.currentPuuid, params.currentSummonerName)
  const currentPlayerKey = currentParticipantId !== null
    ? playerKeyByParticipantId.get(currentParticipantId) ?? `player:${currentParticipantId}`
    : playerKeyByParticipantId.get(participants[0]?.participantId ?? 0) ?? 'player:unknown'

  if (!participants.length) {
    warnings.push('match participants are unavailable')
  }
  if (!params.gameDetail?.participants?.length) {
    warnings.push('game detail is unavailable; using match history summary only')
  }
  if (!hasTimeline(params.timeline)) {
    warnings.push('timeline is unavailable')
  }
  if (currentParticipantId === null) {
    warnings.push('current player could not be matched to a participant')
  }

  const teamTotals = createTeamTotals(participants)
  const teamGoldDiffAt15 = getTeamGoldDiffAtMinute(params.timeline, participants, 15)
  const laneGoldDiffAt15 = createLaneGoldDiffAtMinuteMap(params.timeline, participants, 15)
  const players = participants.map(participant => toPlayerSnapshot(
    participant,
    identities,
    currentParticipantId,
    teamTotals,
    match.durationSeconds ?? null,
    teamGoldDiffAt15,
    laneGoldDiffAt15
  ))
  const timelineSnapshot = createTimelineSnapshot(params.timeline, detail, playerKeyByParticipantId)
  const hasRankedTimelineMetrics = match.isRanked
    && teamGoldDiffAt15 !== null
    && laneGoldDiffAt15.size > 0
  const hasArenaAugments = players.some(player => player.loadout.augmentIds.length > 0)
  const snapshotWithoutHash: PostgameAiInputSnapshot = {
    schemaVersion: POSTGAME_AI_SCHEMA_VERSION,
    analysisType: params.mode === 'praise' ? 'postgame_praise' : 'postgame_review',
    mode: params.mode,
    builtAt: new Date().toISOString(),
    inputHash: '',
    match,
    currentPlayerKey,
    teams: [BLUE_TEAM_ID, RED_TEAM_ID]
      .filter(teamId => teamIds.has(teamId))
      .map(teamId => toTeamSnapshot(teamId, participants, teamTotals, detail?.teamObjectives)),
    players,
    timeline: timelineSnapshot,
    dataQuality: {
      hasMatchHistory: Boolean(params.matchHistory),
      hasGameDetail: Boolean(params.gameDetail?.participants?.length),
      hasTimeline: hasTimeline(params.timeline),
      participantCount: participants.length,
      teamCount: teamIds.size,
      hasRankedTimelineMetrics,
      hasArenaAugments,
      warnings
    }
  }

  return {
    ...snapshotWithoutHash,
    inputHash: createPostgameAiInputHash(snapshotWithoutHash)
  }
}

export function createPostgameAiInputHash(snapshot: PostgameAiInputSnapshot): string {
  const hashInput: Record<string, unknown> = { ...snapshot }
  delete hashInput.builtAt
  delete hashInput.inputHash
  return hashText(stableStringify(hashInput))
}

function buildMatchSnapshot(matchHistory: MatchHistory, detail: GameDetail | null): PostgameAiInputSnapshot['match'] {
  const queueId = firstNumber(detail?.queueId, matchHistory.queueId)
  const gameMode = firstString(detail?.gameMode, matchHistory.gameMode)
  const gameVersion = firstString(readRecordValue(detail, 'gameVersion'), readRecordValue(matchHistory, 'gameVersion'))
  const gameId = firstNumber(detail?.gameId, matchHistory.gameId)
  const matchId = firstString(readRecordValue(matchHistory, 'matchId'), readRecordValue(detail, 'matchId'))

  return {
    ...(matchId ? { matchIdHash: hashText(`match:${matchId}`) } : {}),
    ...(gameId !== null ? { gameIdHash: hashText(`game:${gameId}`) } : {}),
    queueId,
    ...(firstString(matchHistory.queueName) ? { queueName: firstString(matchHistory.queueName) ?? undefined } : {}),
    ...(gameMode ? { gameMode } : {}),
    gameVersion,
    gameCreation: firstNumber(detail?.gameCreation, matchHistory.gameCreation),
    durationSeconds: firstNumber(detail?.gameDuration, matchHistory.gameDuration),
    isRanked: isRankedQueue(queueId, matchHistory.queueName, gameMode),
    isAram: isAramQueue(queueId, matchHistory.queueName, gameMode),
    isArena: isArenaQueue(queueId, matchHistory.queueName, gameMode)
  }
}

function toTeamSnapshot(
  teamId: 100 | 200,
  participants: SnapshotParticipant[],
  totalsByTeamId: Map<number, PostgameAiTeamSnapshot['totals']>,
  objectiveSummaries: TeamObjectiveSummary[] | undefined
): PostgameAiTeamSnapshot {
  const teamPlayers = participants.filter(participant => participant.teamId === teamId)
  const objectiveSummary = objectiveSummaries?.find(summary => summary.teamId === teamId)
  return {
    side: teamIdToSide(teamId),
    teamId,
    win: readTeamWin(teamPlayers),
    totals: totalsByTeamId.get(teamId) ?? emptyTeamTotals(),
    ...(objectiveSummary ? { objectives: toObjectiveSnapshot(objectiveSummary) } : {})
  }
}

function toPlayerSnapshot(
  participant: SnapshotParticipant,
  identities: GameParticipantIdentity[],
  currentParticipantId: number | null,
  totalsByTeamId: Map<number, PostgameAiTeamSnapshot['totals']>,
  durationSeconds: number | null,
  teamGoldDiffAt15: number | null,
  laneGoldDiffAt15: Map<GoldDiffMetricKey, number>,
  idPrefix = 'player'
): PostgameAiPlayerSnapshot {
  const stats = participant.stats
  const teamId = participant.teamId
  const teamTotals = totalsByTeamId.get(teamId) ?? emptyTeamTotals()
  const kills = finiteNumberOrZero(stats.kills)
  const deaths = finiteNumberOrZero(stats.deaths)
  const assists = finiteNumberOrZero(stats.assists)
  const goldEarned = finiteNumberOrZero(stats.goldEarned)
  const damage = finiteNumberOrZero(stats.totalDamageDealtToChampions)
  const damageTaken = finiteNumberOrZero(stats.totalDamageTaken)
  const visionScore = finiteNumberOrZero(stats.visionScore)
  const cs = getCreepScore(stats)
  const lane = resolveParticipantLane(participant)
  const rankedMetrics = buildRankedMetrics(
    participant,
    lane,
    teamGoldDiffAt15,
    laneGoldDiffAt15
  )
  const championName = firstString(
    readRecordValue(participant, 'championName'),
    readRecordValue(participant, 'championNameCn')
  )

  return {
    playerKey: `${idPrefix}:${participant.participantId}`,
    side: teamIdToSide(teamId),
    teamId,
    participantId: participant.participantId,
    isCurrentPlayer: currentParticipantId === participant.participantId,
    championId: positiveInteger(participant.championId),
    ...(championName ? { championName } : {}),
    ...(firstString(participant.timeline?.role, readRecordValue(participant, 'role')) ? { role: firstString(participant.timeline?.role, readRecordValue(participant, 'role')) ?? undefined } : {}),
    ...(firstString(participant.timeline?.lane, readRecordValue(participant, 'lane')) ? { lane: firstString(participant.timeline?.lane, readRecordValue(participant, 'lane')) ?? undefined } : {}),
    ...(firstString(participant.teamPosition, participant.individualPosition, participant.selectedPosition, participant.timeline?.teamPosition) ? { position: firstString(participant.teamPosition, participant.individualPosition, participant.selectedPosition, participant.timeline?.teamPosition) ?? undefined } : {}),
    ...(readStatNumber(stats, 'champLevel') !== null ? { level: readStatNumber(stats, 'champLevel') as number } : {}),
    stats: {
      win: typeof stats.win === 'boolean' ? stats.win : null,
      kills,
      deaths,
      assists,
      kda: calculateKda(kills, deaths, assists),
      killParticipation: calculateRate(kills + assists, teamTotals.kills),
      goldEarned,
      goldShare: calculateRate(goldEarned, teamTotals.goldEarned),
      totalDamageDealtToChampions: damage,
      damageShare: calculateRate(damage, teamTotals.totalDamageDealtToChampions),
      totalDamageTaken: damageTaken,
      damageTakenShare: calculateRate(damageTaken, teamTotals.totalDamageTaken),
      damageToGoldRatio: calculateRate(damage, goldEarned),
      visionScore,
      cs,
      csPerMinute: durationSeconds && durationSeconds > 0 ? roundMetric(cs / (durationSeconds / 60)) : null
    },
    loadout: {
      spellIds: [
        positiveInteger(participant.spell1Id),
        positiveInteger(participant.spell2Id)
      ].filter((value): value is number => value !== null),
      itemIds: readPositiveIds(stats, ['item0', 'item1', 'item2', 'item3', 'item4', 'item5', 'item6']),
      runeIds: readPositiveIds(stats, ['perk0', 'perk1', 'perk2', 'perk3', 'perk4', 'perk5', 'perkPrimaryStyle', 'perkSubStyle']),
      augmentIds: readPositiveIds(stats, ['playerAugment1', 'playerAugment2', 'playerAugment3', 'playerAugment4', 'playerAugment5', 'playerAugment6'])
    },
    ...(rankedMetrics ? { rankedMetrics } : {})
  }
}

function buildRankedMetrics(
  participant: SnapshotParticipant,
  lane: GoldDiffMetricKey | null,
  teamGoldDiffAt15: number | null,
  laneGoldDiffAt15: Map<GoldDiffMetricKey, number>
): PostgameAiPlayerSnapshot['rankedMetrics'] | null {
  const metrics: NonNullable<PostgameAiPlayerSnapshot['rankedMetrics']> = {}
  if (teamGoldDiffAt15 !== null) {
    metrics.teamGoldDiffAt15 = participant.teamId === BLUE_TEAM_ID ? teamGoldDiffAt15 : -teamGoldDiffAt15
  }
  if (lane !== null) {
    const laneDiff = laneGoldDiffAt15.get(lane)
    if (laneDiff !== undefined) {
      metrics.laneGoldDiffAt15 = participant.teamId === BLUE_TEAM_ID ? laneDiff : -laneDiff
    }
  }
  const turretPlatesTaken = readStatNumber(participant.stats, 'turretPlatesTaken')
  if (turretPlatesTaken !== null) {
    metrics.turretPlatesTaken = turretPlatesTaken
  }

  return Object.keys(metrics).length ? metrics : null
}

function createTimelineSnapshot(
  timeline: MatchTimeline | null,
  gameDetail: GameDetail | null,
  playerKeyByParticipantId: Map<number, string>
): PostgameAiTimelineSnapshot {
  if (!hasTimeline(timeline)) {
    return { hasTimeline: false }
  }

  const model = createTimelineChartModel(timeline, gameDetail)
  const objectiveEvents = model.eventMarkers
    .filter(marker => marker.type !== 'kill')
    .map(marker => ({
      timeSeconds: toSeconds(marker.timestamp),
      type: marker.type,
      ...(marker.teamId !== null ? { teamId: marker.teamId } : {}),
      ...(teamIdToNullableSide(marker.teamId) ? { side: teamIdToNullableSide(marker.teamId) as PostgameAiSide } : {})
    }))
  const deathEvents = createDeathEvents(
    timeline,
    gameDetail?.participants ?? [],
    model.eventMarkers.filter(marker => marker.type !== 'kill').map(marker => marker.timestamp),
    playerKeyByParticipantId
  )
  const goldDiffPoints = createGoldDiffPoints(model.seriesByMetric)
  const durationSeconds = getTimelineDurationSeconds(timeline)

  return {
    hasTimeline: true,
    ...(durationSeconds !== null ? { durationSeconds } : {}),
    ...(goldDiffPoints.length ? { goldDiffPoints } : {}),
    ...(objectiveEvents.length ? { objectiveEvents } : {}),
    ...(deathEvents.length ? { deathEvents } : {})
  }
}

function createGoldDiffPoints(
  seriesByMetric: ReturnType<typeof createTimelineChartModel>['seriesByMetric']
): NonNullable<PostgameAiTimelineSnapshot['goldDiffPoints']> {
  const pointsByMinute = new Map<number, NonNullable<PostgameAiTimelineSnapshot['goldDiffPoints']>[number]>()

  for (const [metric, series] of Object.entries(seriesByMetric) as Array<[GoldDiffMetricKey, typeof seriesByMetric[GoldDiffMetricKey]]>) {
    for (const point of series.points) {
      const minute = Math.round(point.timestamp / 60_000)
      const existing = pointsByMinute.get(minute) ?? { minute }
      const key = metricToGoldDiffKey(metric)
      existing[key] = Math.round(point.diff)
      pointsByMinute.set(minute, existing)
    }
  }

  return [...pointsByMinute.values()].sort((left, right) => left.minute - right.minute)
}

function createDeathEvents(
  timeline: MatchTimeline,
  participants: SnapshotParticipant[],
  objectiveTimestamps: number[],
  playerKeyByParticipantId: Map<number, string>
): NonNullable<PostgameAiTimelineSnapshot['deathEvents']> {
  const participantsById = new Map(participants.map(participant => [participant.participantId, participant]))
  return getTimelineEvents(timeline)
    .filter(event => normalizeText(event.eventType) === 'CHAMPION_KILL')
    .flatMap(event => {
      const victimId = toFiniteNumber(event.victimId)
      const timestamp = toFiniteNumber(event.timestamp)
      if (victimId === null || timestamp === null) {
        return []
      }
      const participant = participantsById.get(victimId)
      if (!participant) {
        return []
      }
      const teamGoldDiff = getTeamGoldDiffAtTimestamp(timeline, participants, timestamp)
      const secondsBeforeObjective = getSecondsBeforeNextObjective(timestamp, objectiveTimestamps)
      return [{
        timeSeconds: toSeconds(timestamp),
        playerKey: playerKeyByParticipantId.get(victimId) ?? `player:${victimId}`,
        ...(positiveInteger(participant.championId) !== null ? { championId: positiveInteger(participant.championId) as number } : {}),
        teamId: participant.teamId,
        side: teamIdToSide(participant.teamId),
        ...(teamGoldDiff !== null ? { teamGoldDiffAtDeath: participant.teamId === BLUE_TEAM_ID ? teamGoldDiff : -teamGoldDiff } : {}),
        ...(secondsBeforeObjective !== null ? { secondsBeforeObjective } : {})
      }]
    })
}

function getSecondsBeforeNextObjective(timestamp: number, objectiveTimestamps: number[]): number | null {
  const nextObjective = objectiveTimestamps.find(objectiveTimestamp => objectiveTimestamp >= timestamp)
  if (nextObjective === undefined) {
    return null
  }
  return Math.max(0, Math.round((nextObjective - timestamp) / 1000))
}

function createTeamTotals(participants: SnapshotParticipant[]): Map<number, PostgameAiTeamSnapshot['totals']> {
  const totals = new Map<number, PostgameAiTeamSnapshot['totals']>()
  for (const participant of participants) {
    const existing = totals.get(participant.teamId) ?? emptyTeamTotals()
    existing.kills += finiteNumberOrZero(participant.stats.kills)
    existing.deaths += finiteNumberOrZero(participant.stats.deaths)
    existing.assists += finiteNumberOrZero(participant.stats.assists)
    existing.goldEarned += finiteNumberOrZero(participant.stats.goldEarned)
    existing.totalDamageDealtToChampions += finiteNumberOrZero(participant.stats.totalDamageDealtToChampions)
    existing.totalDamageTaken += finiteNumberOrZero(participant.stats.totalDamageTaken)
    existing.visionScore += finiteNumberOrZero(participant.stats.visionScore)
    totals.set(participant.teamId, existing)
  }
  return totals
}

function emptyTeamTotals(): PostgameAiTeamSnapshot['totals'] {
  return {
    kills: 0,
    deaths: 0,
    assists: 0,
    goldEarned: 0,
    totalDamageDealtToChampions: 0,
    totalDamageTaken: 0,
    visionScore: 0
  }
}

function toObjectiveSnapshot(summary: TeamObjectiveSummary): NonNullable<PostgameAiTeamSnapshot['objectives']> {
  return {
    dragons: firstNumber(summary.dragonKills),
    barons: firstNumber(summary.baronKills),
    heralds: firstNumber(summary.heraldKills),
    grubs: firstNumber(summary.voidGrubKills),
    towers: firstNumber(summary.turretKills),
    inhibitors: firstNumber(summary.inhibitorKills),
    turretPlates: firstNumber(summary.turretPlatesTaken, summary.turretPlateKills)
  }
}

function getTeamGoldDiffAtMinute(
  timeline: MatchTimeline | null,
  participants: SnapshotParticipant[],
  minute: number
): number | null {
  if (!hasTimeline(timeline)) {
    return null
  }
  const frame = findNearestFrame(timeline.frames ?? [], minute * 60_000)
  return frame ? calculateTeamGoldDiff(frame, participants) : null
}

function createLaneGoldDiffAtMinuteMap(
  timeline: MatchTimeline | null,
  participants: SnapshotParticipant[],
  minute: number
): Map<GoldDiffMetricKey, number> {
  const result = new Map<GoldDiffMetricKey, number>()
  if (!hasTimeline(timeline)) {
    return result
  }
  const frame = findNearestFrame(timeline.frames ?? [], minute * 60_000)
  if (!frame) {
    return result
  }

  for (const lane of LANE_METRICS) {
    const blueParticipant = participants.find(participant => participant.teamId === BLUE_TEAM_ID && resolveParticipantLane(participant) === lane)
    const redParticipant = participants.find(participant => participant.teamId === RED_TEAM_ID && resolveParticipantLane(participant) === lane)
    const blueGold = readFrameTotalGold(frame, blueParticipant?.participantId)
    const redGold = readFrameTotalGold(frame, redParticipant?.participantId)
    if (blueGold !== null && redGold !== null) {
      result.set(lane, blueGold - redGold)
    }
  }
  return result
}

function getTeamGoldDiffAtTimestamp(
  timeline: MatchTimeline,
  participants: SnapshotParticipant[],
  timestamp: number
): number | null {
  const frame = findNearestFrame(timeline.frames ?? [], timestamp)
  return frame ? calculateTeamGoldDiff(frame, participants) : null
}

function calculateTeamGoldDiff(frame: TimelineFrame, participants: SnapshotParticipant[]): number | null {
  let blueGold = 0
  let redGold = 0
  let blueCount = 0
  let redCount = 0
  for (const participant of participants) {
    const gold = readFrameTotalGold(frame, participant.participantId)
    if (gold === null) {
      continue
    }
    if (participant.teamId === BLUE_TEAM_ID) {
      blueGold += gold
      blueCount += 1
    } else if (participant.teamId === RED_TEAM_ID) {
      redGold += gold
      redCount += 1
    }
  }
  return blueCount > 0 && redCount > 0 ? blueGold - redGold : null
}

function findNearestFrame(frames: TimelineFrame[], targetTimestamp: number): TimelineFrame | null {
  const usable = frames.filter(frame => toFiniteNumber(frame.timestamp) !== null)
  if (!usable.length) {
    return null
  }
  return usable.reduce((nearest, frame) => {
    const nearestDistance = Math.abs((toFiniteNumber(nearest.timestamp) ?? 0) - targetTimestamp)
    const frameDistance = Math.abs((toFiniteNumber(frame.timestamp) ?? 0) - targetTimestamp)
    return frameDistance < nearestDistance ? frame : nearest
  }, usable[0] as TimelineFrame)
}

function readFrameTotalGold(frame: TimelineFrame, participantId: number | null | undefined): number | null {
  if (participantId == null) {
    return null
  }
  const directFrame = frame.participantFrames?.[String(participantId)]
  const participantFrame = directFrame ?? Object.values(frame.participantFrames ?? {}).find(candidate => candidate?.participantId === participantId)
  return readParticipantFrameNumber(participantFrame, 'totalGold')
}

function readParticipantFrameNumber(frame: ParticipantFrame | undefined, key: keyof ParticipantFrame): number | null {
  return toFiniteNumber(frame?.[key])
}

function getTimelineDurationSeconds(timeline: MatchTimeline): number | null {
  const timestamps = [
    ...(timeline.frames ?? []).map(frame => toFiniteNumber(frame.timestamp)),
    ...getTimelineEvents(timeline).map(event => toFiniteNumber(event.timestamp))
  ].filter((value): value is number => value !== null)
  if (!timestamps.length) {
    return null
  }
  return Math.round(Math.max(...timestamps) / 1000)
}

function getTimelineEvents(timeline: MatchTimeline): TimelineEvent[] {
  const rootEvents = timeline.events ?? []
  if (rootEvents.length) {
    return rootEvents
  }
  return (timeline.frames ?? []).flatMap(frame => frame.events ?? [])
}

function findCurrentParticipantId(
  identities: GameParticipantIdentity[],
  currentPuuid: string,
  currentSummonerName: string
): number | null {
  const normalizedPuuid = currentPuuid.trim()
  if (normalizedPuuid) {
    const identity = identities.find(item => item.player?.puuid === normalizedPuuid)
    if (identity) {
      return identity.participantId
    }
  }

  const normalizedName = currentSummonerName.trim().toLowerCase()
  if (!normalizedName) {
    return null
  }
  const identity = identities.find(item => formatIdentityName(item).toLowerCase() === normalizedName)
  return identity?.participantId ?? null
}

function formatIdentityName(identity: GameParticipantIdentity): string {
  const gameName = identity.player?.gameName?.trim()
  const tagLine = identity.player?.tagLine?.trim()
  if (gameName) {
    return tagLine ? `${gameName}#${tagLine}` : gameName
  }
  return identity.player?.summonerName?.trim() ?? ''
}

function createPlayerKey(participant: Pick<GameParticipant, 'participantId'>): string {
  return `player:${participant.participantId}`
}

function toGameDetailFromMatchHistory(match: MatchHistory): GameDetail {
  return {
    gameId: match.gameId,
    gameMode: match.gameMode,
    gameType: match.gameType,
    mapId: 0,
    queueId: match.queueId,
    gameDuration: match.gameDuration,
    gameCreation: match.gameCreation,
    participantIdentities: match.participantIdentities.map(identity => ({
      participantId: identity.participantId,
      player: {
        accountId: identity.player?.accountId ?? 0,
        puuid: identity.player?.puuid ?? '',
        platformId: identity.player?.platformId ?? '',
        summonerName: identity.player?.summonerName ?? '',
        gameName: identity.player?.gameName ?? '',
        tagLine: identity.player?.tagLine ?? '',
        summonerId: identity.player?.summonerId ?? 0
      }
    })),
    participants: match.participants.map(participant => ({
      participantId: participant.participantId,
      teamId: participant.teamId,
      championId: participant.championId,
      spell1Id: participant.spell1Id,
      spell2Id: participant.spell2Id,
      teamPosition: participant.teamPosition,
      individualPosition: participant.individualPosition,
      selectedPosition: participant.selectedPosition,
      stats: {
        ...participant.stats,
        totalHeal: participant.stats.totalHeal ?? 0,
        visionWardsBoughtInGame: 0,
        wardsPlaced: 0,
        wardsKilled: 0,
        largestMultiKill: 0,
        doubleKills: participant.stats.doubleKills ?? 0,
        tripleKills: participant.stats.tripleKills ?? 0,
        quadraKills: participant.stats.quadraKills ?? 0,
        pentaKills: participant.stats.pentaKills ?? 0
      },
      timeline: {
        lane: participant.teamPosition || participant.lane || participant.individualPosition || '',
        role: participant.role || '',
        teamPosition: participant.teamPosition,
        rawLane: participant.lane,
        rawRole: participant.role
      }
    })),
    teamObjectives: match.teamObjectives,
    teamBans: match.teamBans
  }
}

function getCreepScore(stats: GameParticipant['stats']): number {
  return (readStatNumber(stats, 'minionsKilled') ?? finiteNumberOrZero(stats.totalMinionsKilled))
    + finiteNumberOrZero(stats.neutralMinionsKilled)
}

function readPositiveIds(stats: GameParticipant['stats'], keys: string[]): number[] {
  return keys
    .map(key => positiveInteger(readStatNumber(stats, key)))
    .filter((value): value is number => value !== null)
}

function readStatNumber(stats: GameParticipant['stats'], key: string): number | null {
  const statsRecord = stats as unknown as Record<string, unknown>
  const extraFields = isRecord(statsRecord.extraFields) ? statsRecord.extraFields : null
  const challenges = isRecord(statsRecord.challenges) ? statsRecord.challenges : null
  return firstNumber(statsRecord[key], extraFields?.[key], challenges?.[key])
}

function metricToGoldDiffKey(metric: GoldDiffMetricKey): keyof NonNullable<PostgameAiTimelineSnapshot['goldDiffPoints']>[number] {
  switch (metric) {
    case 'teamAverage':
      return 'teamGoldDiff'
    case 'top':
      return 'topGoldDiff'
    case 'jungle':
      return 'jungleGoldDiff'
    case 'middle':
      return 'middleGoldDiff'
    case 'bottom':
      return 'bottomGoldDiff'
    case 'support':
      return 'supportGoldDiff'
  }
}

function teamIdToSide(teamId: number): PostgameAiSide {
  return teamId === RED_TEAM_ID ? 'red' : 'blue'
}

function teamIdToNullableSide(teamId: number | null): PostgameAiSide | null {
  if (teamId === BLUE_TEAM_ID || teamId === RED_TEAM_ID) {
    return teamIdToSide(teamId)
  }
  return null
}

function toTeamId(value: unknown): 100 | 200 | null {
  return value === BLUE_TEAM_ID || value === RED_TEAM_ID ? value : null
}

function readTeamWin(players: SnapshotParticipant[]): boolean | null {
  const value = players.find(player => typeof player.stats?.win === 'boolean')?.stats.win
  return typeof value === 'boolean' ? value : null
}

function isRankedQueue(queueId: number | null, queueName?: string, gameMode?: string): boolean {
  return (queueId !== null && RANKED_QUEUE_IDS.has(queueId)) || containsAny(queueName, ['RANKED', '排位', '单排', '双排', '灵活'])
    || containsAny(gameMode, ['RANKED'])
}

function isAramQueue(queueId: number | null, queueName?: string, gameMode?: string): boolean {
  return (queueId !== null && ARAM_QUEUE_IDS.has(queueId)) || containsAny(queueName, ['ARAM', '大乱斗', '极地'])
    || containsAny(gameMode, ['ARAM'])
}

function isArenaQueue(queueId: number | null, queueName?: string, gameMode?: string): boolean {
  return (queueId !== null && ARENA_QUEUE_IDS.has(queueId)) || containsAny(queueName, ['ARENA', 'CHERRY', '斗魂', '竞技场'])
    || containsAny(gameMode, ['CHERRY'])
}

function containsAny(value: string | undefined, keywords: string[]): boolean {
  if (!value) {
    return false
  }
  const upper = value.toUpperCase()
  return keywords.some(keyword => upper.includes(keyword.toUpperCase()))
}

function hasTimeline(timeline: MatchTimeline | null): timeline is MatchTimeline {
  return Boolean(timeline?.frames?.length || timeline?.events?.length)
}

function calculateKda(kills: number, deaths: number, assists: number): number | null {
  if (deaths === 0) {
    return roundMetric(kills + assists)
  }
  return roundMetric((kills + assists) / deaths)
}

function calculateRate(value: number, total: number): number | null {
  if (total <= 0) {
    return null
  }
  return roundMetric(value / total)
}

function toSeconds(timestamp: number): number {
  return Math.round(timestamp / 1000)
}

function roundMetric(value: number): number {
  return Number(value.toFixed(2))
}

function positiveInteger(value: unknown): number | null {
  const numberValue = toFiniteNumber(value)
  return numberValue !== null && Number.isInteger(numberValue) && numberValue > 0 ? numberValue : null
}

function finiteNumberOrZero(value: unknown): number {
  return toFiniteNumber(value) ?? 0
}

function firstNumber(...values: unknown[]): number | null {
  for (const value of values) {
    const numberValue = toFiniteNumber(value)
    if (numberValue !== null) {
      return numberValue
    }
  }
  return null
}

function toFiniteNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function firstString(...values: unknown[]): string | null {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }
  return null
}

function normalizeText(value: unknown): string {
  return typeof value === 'string' ? value.trim().toUpperCase() : ''
}

function readRecordValue(source: unknown, key: string): unknown {
  return isRecord(source) ? source[key] : undefined
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function hashText(source: string): string {
  let hash = 0x811c9dc5
  for (let index = 0; index < source.length; index += 1) {
    hash ^= source.charCodeAt(index)
    hash = Math.imul(hash, 0x01000193) >>> 0
  }
  return hash.toString(16).padStart(8, '0')
}
