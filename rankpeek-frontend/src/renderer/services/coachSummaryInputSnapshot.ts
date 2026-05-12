import type {
  AiAnalysisResult,
  LocalDatabaseAPI,
  MatchDetail,
  MatchDetailInput,
  MatchRecord,
  MatchRecordListOptions
} from '../types/localDatabase'
import type { GameDetail, MatchTimeline, MatchTimelineFetchResult } from '../types/api'
import { stableStringify } from './aiAnalysisInputSnapshot.ts'

export const COACH_SUMMARY_SCHEMA_VERSION = 'coach_summary.v1' as const
export const COACH_SUMMARY_ANALYSIS_TYPE = 'coach_summary' as const
export const COACH_SUMMARY_REQUIRED_RANKED_MATCHES = 20
export const COACH_SUMMARY_RANKED_QUEUE_IDS = [420, 440] as const
export const COACH_SUMMARY_SGP_HYDRATION_DELAY_MS = 1200
export const COACH_SUMMARY_SGP_HYDRATION_RETRY_COUNT = 1
export const COACH_SUMMARY_SGP_SUMMARY_PAGE_SIZE = 100

const RANKED_MATCH_QUERY_LIMIT_PER_QUEUE = 80
const OBJECTIVE_LOOKAHEAD_SECONDS = 120
const BLUE_TEAM_ID = 100
const RED_TEAM_ID = 200
const COACH_SUMMARY_SGP_RETRY_BACKOFF_MS = 2400
const SNAPSHOT_INTEGRITY_FAILED_MESSAGE = '电子教练数据校验失败，请刷新战绩后重试' as const

export const COACH_SUMMARY_DEEPSEEK_PROMPT_GUARDRAILS = [
  'Do not infer champion names from championId. Use championCanonicalName from the snapshot.',
  'If championId and championCanonicalName disagree, emit warnings and do not analyze that champion.',
  'evidenceRefs must reference existing matchRef, eventRef, or dataRef values from the snapshot.',
  'Never output raw matchId, gameId, puuid, summonerName, gameName, or tagLine.',
  'Never output a champion that does not exist in the snapshot championDictionary or matches.'
] as const

const STABLE_CHAMPION_DICTIONARY: Record<number, CoachSummaryChampionDictionaryEntry> = {
  59: { canonicalName: 'Jarvan IV', displayName: '嘉文四世' },
  76: { canonicalName: 'Nidalee', displayName: '奈德丽' },
  102: { canonicalName: 'Shyvana', displayName: '希瓦娜' },
  103: { canonicalName: 'Ahri', displayName: '阿狸' },
  141: { canonicalName: 'Kayn', displayName: '凯隐' },
  233: { canonicalName: 'Briar', displayName: '贝蕾亚' },
  350: { canonicalName: 'Yuumi', displayName: '悠米' },
  950: { canonicalName: 'Naafiri', displayName: '纳亚菲利' }
}

type RankedQueueId = typeof COACH_SUMMARY_RANKED_QUEUE_IDS[number]
type CoachSummaryDatabase = Pick<
  LocalDatabaseAPI,
  'listMatchRecordsByAccount' | 'getMatchDetail' | 'listAnalysisResultsByAccount'
> & Partial<Pick<LocalDatabaseAPI, 'upsertMatchDetail'>>
type ParticipantLane = 'top' | 'jungle' | 'middle' | 'bottom' | 'support'
type CoachSummaryObjectiveType = 'dragon' | 'baron' | 'herald' | 'grub' | 'riftHerald' | string

export interface CoachSummarySgpHydrationClient {
  fetchGameDetailFromSgpOnly(gameId: number): Promise<GameDetail | Record<string, unknown> | null>
  fetchGameTimelineFromSgpOnly(gameId: number): Promise<MatchTimelineFetchResult | Record<string, unknown> | null>
  fetchRecentMatchSummariesFromSgpOnly?(
    accountPuuid: string,
    pageSize: number
  ): Promise<unknown[] | { matches?: unknown[] } | null>
}

export interface CoachSummaryHydrationProgress {
  stage: 'preparing' | 'hydrating_match' | 'partial_failure'
  current?: number
  total?: number
  matchId?: string
  message?: string
}

export interface PrepareCoachSummaryGenerationParams {
  accountPuuid: string
  database?: CoachSummaryDatabase | null
  sgpHydrationClient?: CoachSummarySgpHydrationClient | null
  hydrationDelayMs?: number
  hydrationRetryCount?: number
  hydrationDelay?: (delayMs: number) => Promise<void>
  onHydrationProgress?: (progress: CoachSummaryHydrationProgress) => void
}

export type CoachSummaryGenerationResult =
  | CoachSummaryReadyResult
  | CoachSummaryInsufficientRankedMatchesResult
  | CoachSummaryNotEnoughNewRankedMatchesResult
  | CoachSummarySnapshotIntegrityFailedResult

export interface CoachSummaryReadyResult {
  status: 'ready'
  message: '电子教练数据已准备完成，等待 AI 服务接入。'
  snapshot: CoachSummaryInputSnapshot
}

export interface CoachSummaryInsufficientRankedMatchesResult {
  status: 'insufficient_ranked_matches'
  message: '最近排位不足 20 局，暂时无法生成电子教练报告。'
  currentRankedMatchCount: number
  requiredRankedMatchCount: 20
}

export interface CoachSummaryNotEnoughNewRankedMatchesResult {
  status: 'not_enough_new_ranked_matches'
  message: '距离上次电子教练报告还不足 20 局排位，继续多打几局后再来。'
  newRankedMatchCountSinceLastReport: number
  requiredNewRankedMatchCount: 20
  lastGeneratedAt: string
}

export interface CoachSummarySnapshotIntegrityFailedResult {
  status: 'snapshot_integrity_failed'
  message: '电子教练数据校验失败，请刷新战绩后重试'
  errors: string[]
  warnings: string[]
  audit: CoachSummarySnapshotAudit
}

export interface CoachSummaryInputSnapshot {
  schemaVersion: 'coach_summary.v1'
  analysisType: 'coach_summary'
  accountPuuidHash: string
  inputHash: string
  eligibility: {
    status: 'ready'
    rankedMatchCount: 20
    queueIds: number[]
    latestMatchTimestamp?: number
    oldestMatchTimestamp?: number
  }
  sample: {
    matchCount: 20
    queues: Array<{
      queueId: number
      count: number
    }>
    dateRange: {
      from?: string
      to?: string
    }
  }
  matches: CoachSummaryMatchDigest[]
  championDictionary: Record<string, CoachSummaryChampionDictionaryEntry>
  dataQuality: CoachSummaryDataQuality
  metadata: {
    generatedInputAt: string
    latestMatchTimestamp?: number
    latestMatchRef?: string
    matchRefs: string[]
    anchorMatchRefs: string[]
    source: 'local_sqlite'
  }
}

export interface CoachSummaryChampionDictionaryEntry {
  canonicalName: string
  displayName: string
}

export interface CoachSummaryDataQuality {
  hasAllTimelines: boolean
  hasAllParticipantDetails: boolean
  hasAllRuneData: boolean
  hasAllItemData: boolean
  hasAnyRuneOrItemData: boolean
  missingTimelineMatchRefs: string[]
  missingParticipantDetailMatchRefs: string[]
  missingRuneMatchRefs: string[]
  missingItemMatchRefs: string[]
  missingRuneOrItemMatchRefs: string[]
  missingLaneOpponentMatchRefs: string[]
  missingEconomyDiffMatchRefs: string[]
  sgpHydration?: CoachSummarySgpHydrationSummary
  missingDataReasons: Array<{
    matchRef: string
    reasons: Array<
      | 'timeline_missing'
      | 'participant_detail_missing'
      | 'rune_data_missing'
      | 'item_data_missing'
      | 'lane_opponent_unmatched'
      | 'economy_diff_unavailable'
    >
  }>
  confidence: 'high' | 'medium' | 'low'
}

export interface CoachSummarySgpHydrationSummary {
  attempted: boolean
  totalMatches: number
  detailFetchedCount: number
  timelineFetchedCount: number
  detailFailedMatchRefs: string[]
  timelineFailedMatchRefs: string[]
  skippedBecauseAlreadyCompleteCount: number
  delayMs: number
  retryCount: number
  errors: Array<{
    matchRef: string
    matchIdHash: string
    stage: 'detail' | 'timeline'
    message: string
  }>
}

export interface CoachSummaryMatchDigest {
  matchRef: string
  matchIdHash: string
  queueId: number
  gameCreation?: number
  gameStartTimestamp?: number
  gameEndTimestamp?: number
  durationSeconds?: number
  result: 'win' | 'loss' | 'unknown'
  self: {
    championId?: number
    championName?: string
    championCanonicalName?: string
    championDisplayName?: string
    championNameSource?: 'local_metadata' | 'raw_participant' | 'unknown'
    identityCheck?: CoachSummaryIdentityCheck
    teamId?: number
    role?: string
    lane?: string
    position?: string
    kills?: number
    deaths?: number
    assists?: number
    kdaText?: string
    items: Array<{
      slot: number
      itemId: number
      name?: string
    }>
    summonerSpells?: Array<{
      spellId: number
      name?: string
    }>
    runes?: {
      primaryStyleId?: number
      primaryStyleName?: string
      subStyleId?: number
      subStyleName?: string
      keystoneId?: number
      keystoneName?: string
      selectedPerkIds: number[]
    }
    stats?: {
      totalMinionsKilled?: number
      neutralMinionsKilled?: number
      csPerMin?: number
      goldEarned?: number
      goldPerMin?: number
      totalDamageDealtToChampions?: number
      damageShare?: number
      visionScore?: number
      killParticipation?: number
    }
  }
  laneOpponent?: {
    puuidHash?: string
    championId?: number
    championName?: string
    role?: string
    lane?: string
    position?: string
  }
  laneDiff?: {
    goldDiffAt10?: number
    goldDiffAt15?: number
    goldDiffAt20?: number
    csDiffAt10?: number
    csDiffAt15?: number
    xpDiffAt10?: number
    xpDiffAt15?: number
  }
  economyTimeline?: {
    teamGoldDiffAt10?: number
    teamGoldDiffAt15?: number
    teamGoldDiffAt20?: number
    selfGoldDiffAt10?: number
    selfGoldDiffAt15?: number
    selfGoldDiffAt20?: number
    points?: Array<{
      minute: number
      selfGold?: number
      laneOpponentGold?: number
      selfGoldDiff?: number
      selfCs?: number
      laneOpponentCs?: number
      csDiff?: number
      selfXp?: number
      laneOpponentXp?: number
      xpDiff?: number
      allyTeamGold?: number
      enemyTeamGold?: number
      teamGoldDiff?: number
    }>
  }
  events: {
    kills: Array<{
      eventRef: string
      timeSeconds: number
      killerIsSelf?: boolean
      victimIsSelf?: boolean
      assisterIsSelf?: boolean
      killerChampionName?: string
      victimChampionName?: string
      position?: {
        x: number
        y: number
      }
    }>
    deaths: Array<{
      eventRef: string
      timeSeconds: number
      killerChampionName?: string
      assistingChampionNames?: string[]
      teamGoldDiffAtDeath?: number
      nearestUpcomingObjective?: {
        type: 'dragon' | 'baron' | 'herald' | 'grub'
        spawnOrEventTimeSeconds: number
        secondsBeforeObjective: number
      }
      position?: {
        x: number
        y: number
      }
    }>
    buildings: Array<{
      eventRef: string
      timeSeconds: number
      type?: 'tower' | 'inhibitor' | 'nexus' | string
      teamId?: number
      killerTeamId?: number
      laneType?: string
      position?: {
        x: number
        y: number
      }
    }>
    objectives: Array<{
      eventRef: string
      timeSeconds: number
      type: CoachSummaryObjectiveType
      killerTeamId?: number
      isAllyObjective?: boolean
      teamGoldDiffAtObjective?: number
      selfAlive?: boolean
    }>
  }
}

export interface CoachSummaryIdentityCheck {
  matchedByAccountPuuid: boolean
  matchedParticipantIndex?: number
  matchedParticipantPuuidHash?: string
  source: 'detail_puuid' | 'summary_puuid' | 'summary_anchor' | 'unconfirmed'
  warnings?: string[]
}

export interface CoachSummarySnapshotIntegrityResult {
  valid: boolean
  errors: string[]
  warnings: string[]
}

export interface CoachSummarySnapshotAudit {
  inputHash: string
  matchCount: number
  dataQuality: CoachSummaryDataQuality
  topChampions: Array<{
    championId: number
    championCanonicalName: string
    championDisplayName: string
    count: number
  }>
  matches: Array<{
    matchRef: string
    matchIdHash: string
    rawMatchIdPresent: boolean
    queueId: number
    gameStartTimestamp?: number
    result: CoachSummaryMatchDigest['result']
    self: {
      championId?: number
      championName?: string
      championCanonicalName?: string
      championDisplayName?: string
      role?: string
      position?: string
      kdaText?: string
    }
    identityCheck: CoachSummaryIdentityCheck
    championMappingCheck: {
      idFromSnapshot?: number
      nameFromSnapshot?: string
      nameFromLocalMetadata?: string
      isConsistent: boolean
    }
  }>
}

interface CoachSummaryInputHashPayload {
  analysisType: 'coach_summary'
  schemaVersion: 'coach_summary.v1'
  accountPuuid: string
  matchIdList: string[]
}

interface BuildCoachSummaryInputSnapshotParams {
  accountPuuid: string
  rankedRecords: MatchRecord[]
  database: Pick<LocalDatabaseAPI, 'getMatchDetail'>
  sgpHydration?: CoachSummarySgpHydrationSummary
}

interface ParsedMatchData {
  summary: Record<string, unknown> | null
  detail: Record<string, unknown> | null
  normalizedDetail: Record<string, unknown> | null
  timeline: Record<string, unknown> | null
  participants: Record<string, unknown>[]
  identities: Record<string, unknown>[]
  summaryParticipants: Record<string, unknown>[]
  summaryIdentities: Record<string, unknown>[]
  trustedParticipants: Record<string, unknown>[]
  trustedIdentities: Record<string, unknown>[]
}

interface ParticipantContext {
  participant: Record<string, unknown>
  participantId: number | null
  identity: Record<string, unknown> | null
  stats: Record<string, unknown> | null
  timeline: Record<string, unknown> | null
  identityCheck?: CoachSummaryIdentityCheck
}

interface MatchDigestBuildResult {
  digest: CoachSummaryMatchDigest
  hasTimeline: boolean
  hasParticipantDetail: boolean
  hasLaneOpponent: boolean
  hasEconomyDiff: boolean
  hasRuneData: boolean
  hasItemData: boolean
  hasRuneOrItemData: boolean
  hasConfirmedSelfIdentity: boolean
}

interface TimelineFramePoint {
  minute: number
  selfGold?: number
  laneOpponentGold?: number
  selfGoldDiff?: number
  selfCs?: number
  laneOpponentCs?: number
  csDiff?: number
  selfXp?: number
  laneOpponentXp?: number
  xpDiff?: number
  allyTeamGold?: number
  enemyTeamGold?: number
  teamGoldDiff?: number
}

interface EconomyDiffResult {
  laneDiff?: CoachSummaryMatchDigest['laneDiff']
  economyTimeline?: CoachSummaryMatchDigest['economyTimeline']
  hasEconomyDiff: boolean
}

interface PriorCoachSummaryAnchor {
  anchorMatchIds: string[]
  latestMatchTimestamp: number | null
  fallbackGeneratedAtTimestamp: number | null
}

export async function prepareCoachSummaryGeneration({
  accountPuuid,
  database = getRendererDatabase(),
  sgpHydrationClient = getDefaultCoachSummarySgpHydrationClient(),
  hydrationDelayMs = COACH_SUMMARY_SGP_HYDRATION_DELAY_MS,
  hydrationRetryCount = COACH_SUMMARY_SGP_HYDRATION_RETRY_COUNT,
  hydrationDelay = delay,
  onHydrationProgress
}: PrepareCoachSummaryGenerationParams): Promise<CoachSummaryGenerationResult> {
  const normalizedPuuid = accountPuuid.trim()
  if (!normalizedPuuid) {
    throw new Error('accountPuuid is required to prepare a coach summary')
  }
  if (!database) {
    throw new Error('Local database API is unavailable')
  }

  const rankedRecords = await listRecentRankedRecords(database, normalizedPuuid)
  if (rankedRecords.length < COACH_SUMMARY_REQUIRED_RANKED_MATCHES) {
    return {
      status: 'insufficient_ranked_matches',
      message: '最近排位不足 20 局，暂时无法生成电子教练报告。',
      currentRankedMatchCount: rankedRecords.length,
      requiredRankedMatchCount: COACH_SUMMARY_REQUIRED_RANKED_MATCHES
    }
  }

  const latestCoachSummary = await loadLatestCoachSummaryResult(database, normalizedPuuid)
  if (latestCoachSummary) {
    const newRankedMatchCount = countNewRankedMatchesSinceLastReport(rankedRecords, latestCoachSummary)
    if (newRankedMatchCount < COACH_SUMMARY_REQUIRED_RANKED_MATCHES) {
      return {
        status: 'not_enough_new_ranked_matches',
        message: '距离上次电子教练报告还不足 20 局排位，继续多打几局后再来。',
        newRankedMatchCountSinceLastReport: newRankedMatchCount,
        requiredNewRankedMatchCount: COACH_SUMMARY_REQUIRED_RANKED_MATCHES,
        lastGeneratedAt: latestCoachSummary.createdAt
      }
    }
  }

  const selectedRankedRecords = rankedRecords.slice(0, COACH_SUMMARY_REQUIRED_RANKED_MATCHES)
  onHydrationProgress?.({
    stage: 'preparing',
    total: selectedRankedRecords.length,
    message: '正在准备最近 20 局排位数据...'
  })
  const sgpHydration = await ensureCoachSummarySgpHydration({
    database,
    accountPuuid: normalizedPuuid,
    rankedMatches: selectedRankedRecords,
    client: sgpHydrationClient,
    delayMs: hydrationDelayMs,
    retryCount: hydrationRetryCount,
    sleep: hydrationDelay,
    onProgress: onHydrationProgress
  })

  const snapshot = await buildCoachSummaryInputSnapshot({
    accountPuuid: normalizedPuuid,
    rankedRecords: selectedRankedRecords,
    database,
    sgpHydration
  })
  const integrity = validateCoachSummarySnapshotIntegrity(snapshot)
  const audit = auditCoachSummarySnapshot(snapshot)
  await exportCoachSummarySnapshotAuditForLocalDebug(snapshot)
  if (!integrity.valid) {
    return {
      status: 'snapshot_integrity_failed',
      message: SNAPSHOT_INTEGRITY_FAILED_MESSAGE,
      errors: integrity.errors,
      warnings: integrity.warnings,
      audit
    }
  }

  return {
    status: 'ready',
    message: '电子教练数据已准备完成，等待 AI 服务接入。',
    snapshot
  }
}

export async function buildCoachSummaryInputSnapshot({
  accountPuuid,
  rankedRecords,
  database,
  sgpHydration
}: BuildCoachSummaryInputSnapshotParams): Promise<CoachSummaryInputSnapshot> {
  const selectedRecords = rankedRecords.slice(0, COACH_SUMMARY_REQUIRED_RANKED_MATCHES)
  const generatedInputAt = new Date().toISOString()
  const digestResults: MatchDigestBuildResult[] = []

  for (const [index, record] of selectedRecords.entries()) {
    const detail = await readMatchDetail(database, record)
    digestResults.push(buildCoachSummaryRankedMatchDigest(record, detail, accountPuuid, formatMatchRef(index)))
  }

  const matches = digestResults.map(result => result.digest)
  const matchIdList = selectedRecords.map(record => record.matchId)
  const matchRefs = matches.map(match => match.matchRef)
  const timestamps = selectedRecords
    .map(record => readRecordTimestamp(record))
    .filter((value): value is number => value !== null)
  const latestMatchTimestamp = timestamps.length ? Math.max(...timestamps) : undefined
  const oldestMatchTimestamp = timestamps.length ? Math.min(...timestamps) : undefined
  const queueIds = uniqueSortedNumbers(selectedRecords.map(record => record.queueId))
  const snapshotWithoutHash: Omit<CoachSummaryInputSnapshot, 'inputHash'> = {
    schemaVersion: COACH_SUMMARY_SCHEMA_VERSION,
    analysisType: COACH_SUMMARY_ANALYSIS_TYPE,
    accountPuuidHash: hashText(accountPuuid),
    eligibility: {
      status: 'ready',
      rankedMatchCount: COACH_SUMMARY_REQUIRED_RANKED_MATCHES,
      queueIds,
      ...(latestMatchTimestamp !== undefined ? { latestMatchTimestamp } : {}),
      ...(oldestMatchTimestamp !== undefined ? { oldestMatchTimestamp } : {})
    },
    sample: {
      matchCount: COACH_SUMMARY_REQUIRED_RANKED_MATCHES,
      queues: buildQueueSample(selectedRecords),
      dateRange: {
        ...(oldestMatchTimestamp !== undefined ? { from: new Date(oldestMatchTimestamp).toISOString() } : {}),
        ...(latestMatchTimestamp !== undefined ? { to: new Date(latestMatchTimestamp).toISOString() } : {})
      }
    },
    matches,
    championDictionary: buildChampionDictionary(matches),
    dataQuality: buildDataQuality(selectedRecords, digestResults, sgpHydration),
    metadata: {
      generatedInputAt,
      ...(latestMatchTimestamp !== undefined ? { latestMatchTimestamp } : {}),
      ...(matchRefs[0] ? { latestMatchRef: matchRefs[0] } : {}),
      matchRefs,
      anchorMatchRefs: matchRefs,
      source: 'local_sqlite'
    }
  }

  return {
    ...snapshotWithoutHash,
    inputHash: buildCoachSummaryInputHash({
      analysisType: COACH_SUMMARY_ANALYSIS_TYPE,
      schemaVersion: COACH_SUMMARY_SCHEMA_VERSION,
      accountPuuid,
      matchIdList
    })
  }
}

export function buildCoachSummaryInputHash(payload: CoachSummaryInputHashPayload): string {
  return hashText(stableStringify({
    analysisType: payload.analysisType,
    accountPuuid: payload.accountPuuid,
    matchIdList: payload.matchIdList,
    schemaVersion: payload.schemaVersion
  }))
}

export function auditCoachSummarySnapshot(snapshot: CoachSummaryInputSnapshot): CoachSummarySnapshotAudit {
  return {
    inputHash: snapshot.inputHash,
    matchCount: snapshot.matches.length,
    dataQuality: snapshot.dataQuality,
    topChampions: buildTopChampions(snapshot.matches),
    matches: snapshot.matches.map(match => {
      const localChampion = resolveChampionIdentity(match.self.championId)
      return {
        matchRef: match.matchRef,
        matchIdHash: match.matchIdHash,
        rawMatchIdPresent: hasOwnProperty(match, 'matchId') || hasOwnProperty(match, 'gameId'),
        queueId: match.queueId,
        ...(match.gameStartTimestamp !== undefined ? { gameStartTimestamp: match.gameStartTimestamp } : {}),
        result: match.result,
        self: {
          ...(match.self.championId !== undefined ? { championId: match.self.championId } : {}),
          ...(match.self.championName ? { championName: match.self.championName } : {}),
          ...(match.self.championCanonicalName ? { championCanonicalName: match.self.championCanonicalName } : {}),
          ...(match.self.championDisplayName ? { championDisplayName: match.self.championDisplayName } : {}),
          ...(match.self.role ? { role: match.self.role } : {}),
          ...(match.self.position ? { position: match.self.position } : {}),
          ...(match.self.kdaText ? { kdaText: match.self.kdaText } : {})
        },
        identityCheck: match.self.identityCheck ?? {
          matchedByAccountPuuid: false,
          source: 'unconfirmed'
        },
        championMappingCheck: {
          ...(match.self.championId !== undefined ? { idFromSnapshot: match.self.championId } : {}),
          ...(match.self.championCanonicalName ? { nameFromSnapshot: match.self.championCanonicalName } : {}),
          ...(localChampion ? { nameFromLocalMetadata: localChampion.canonicalName } : {}),
          isConsistent: !localChampion || localChampion.canonicalName === match.self.championCanonicalName
        }
      }
    })
  }
}

export function validateCoachSummarySnapshotIntegrity(snapshot: CoachSummaryInputSnapshot): CoachSummarySnapshotIntegrityResult {
  const errors: string[] = []
  const warnings: string[] = []
  if (snapshot.matches.length !== COACH_SUMMARY_REQUIRED_RANKED_MATCHES || snapshot.sample.matchCount !== COACH_SUMMARY_REQUIRED_RANKED_MATCHES) {
    errors.push(`matchCount must be ${COACH_SUMMARY_REQUIRED_RANKED_MATCHES}`)
  }
  for (const match of snapshot.matches) {
    if (!isRankedQueueId(firstNumber(match.queueId))) {
      errors.push(`${match.matchRef}: queueId must be 420 or 440`)
    }
    if (!match.self) {
      errors.push(`${match.matchRef}: self participant is missing`)
      continue
    }
    if (!match.self.identityCheck?.matchedByAccountPuuid) {
      errors.push(`${match.matchRef}: self identity was not confirmed by accountPuuid`)
    }
    validateChampionIdentity(match, errors, warnings)
  }

  validateNoSensitiveSnapshotFields(snapshot, errors)
  validateSnapshotRefs(snapshot, errors)

  return {
    valid: errors.length === 0,
    errors,
    warnings
  }
}

let coachSummarySnapshotAuditWriteQueue: Promise<void> = Promise.resolve()

export async function exportCoachSummarySnapshotAuditForLocalDebug(
  snapshot: CoachSummaryInputSnapshot,
  filePath?: string
): Promise<boolean> {
  if (!isNodeRuntime()) {
    return false
  }
  coachSummarySnapshotAuditWriteQueue = coachSummarySnapshotAuditWriteQueue
    .catch(() => undefined)
    .then(() => writeCoachSummarySnapshotAuditForLocalDebug(snapshot, filePath))
  try {
    await coachSummarySnapshotAuditWriteQueue
    return true
  } catch {
    return false
  }
}

async function writeCoachSummarySnapshotAuditForLocalDebug(
  snapshot: CoachSummaryInputSnapshot,
  filePath?: string
): Promise<void> {
  let tempPath: string | undefined
  let fs: typeof import('node:fs/promises') | undefined
  try {
    const dynamicImport = new Function('specifier', 'return import(specifier)') as <T>(specifier: string) => Promise<T>
    fs = await dynamicImport<typeof import('node:fs/promises')>('node:fs/promises')
    const path = await dynamicImport<typeof import('node:path')>('node:path')
    const outputPath = filePath ?? path.join(process.cwd(), '.local-debug', 'coach-summary-snapshot.audit.json')
    tempPath = `${outputPath}.${process.pid}.${Date.now()}.${Math.random().toString(16).slice(2)}.tmp`
    await fs.mkdir(path.dirname(outputPath), { recursive: true })
    await fs.writeFile(tempPath, `${JSON.stringify(auditCoachSummarySnapshot(snapshot), null, 2)}\n`, 'utf8')
    await fs.rm(outputPath, { force: true })
    await fs.rename(tempPath, outputPath)
    tempPath = undefined
  } finally {
    if (fs && tempPath) {
      await fs.rm(tempPath, { force: true }).catch(() => undefined)
    }
  }
}

function buildTopChampions(matches: CoachSummaryMatchDigest[]): CoachSummarySnapshotAudit['topChampions'] {
  const counts = new Map<number, {
    championId: number
    championCanonicalName: string
    championDisplayName: string
    count: number
  }>()
  for (const match of matches) {
    const championId = match.self.championId
    if (championId === undefined) {
      continue
    }
    const champion = resolveChampionIdentity(championId, match.self.championCanonicalName)
    if (!champion) {
      continue
    }
    const current = counts.get(championId) ?? {
      championId,
      championCanonicalName: champion.canonicalName,
      championDisplayName: champion.displayName,
      count: 0
    }
    current.count += 1
    counts.set(championId, current)
  }
  return Array.from(counts.values()).sort((left, right) => right.count - left.count || left.championId - right.championId)
}

function buildChampionDictionary(matches: CoachSummaryMatchDigest[]): Record<string, CoachSummaryChampionDictionaryEntry> {
  const dictionary: Record<string, CoachSummaryChampionDictionaryEntry> = {}
  for (const match of matches) {
    addChampionDictionaryEntry(dictionary, match.self.championId, match.self.championCanonicalName)
    if (match.laneOpponent) {
      addChampionDictionaryEntry(dictionary, match.laneOpponent.championId, match.laneOpponent.championName)
    }
  }
  return dictionary
}

function addChampionDictionaryEntry(
  dictionary: Record<string, CoachSummaryChampionDictionaryEntry>,
  championId: number | undefined,
  rawName?: string
): void {
  if (championId === undefined || dictionary[String(championId)]) {
    return
  }
  const champion = resolveChampionIdentity(championId, rawName)
  if (!champion) {
    return
  }
  dictionary[String(championId)] = {
    canonicalName: champion.canonicalName,
    displayName: champion.displayName
  }
}

function resolveChampionIdentity(
  championId: number | null | undefined,
  rawName?: string | null
): (CoachSummaryChampionDictionaryEntry & { source: 'local_metadata' | 'raw_participant' }) | null {
  if (championId !== null && championId !== undefined) {
    const local = STABLE_CHAMPION_DICTIONARY[championId]
    if (local) {
      return {
        ...local,
        source: 'local_metadata'
      }
    }
  }
  const canonicalName = normalizeChampionCanonicalName(rawName)
  if (!canonicalName) {
    return null
  }
  return {
    canonicalName,
    displayName: canonicalName,
    source: 'raw_participant'
  }
}

function normalizeChampionCanonicalName(value: unknown): string | null {
  const raw = firstString(value)
  if (!raw) {
    return null
  }
  if (raw === 'JarvanIV') {
    return 'Jarvan IV'
  }
  return raw
}

function validateChampionIdentity(
  match: CoachSummaryMatchDigest,
  errors: string[],
  warnings: string[]
): void {
  const championId = match.self.championId
  const canonicalName = match.self.championCanonicalName
  if (championId === undefined || !canonicalName) {
    errors.push(`${match.matchRef}: self championId and championCanonicalName are required`)
    return
  }
  const localChampion = resolveChampionIdentity(championId)
  if (!localChampion) {
    errors.push(`${match.matchRef}: no local champion metadata for championId ${championId}`)
    return
  }
  if (localChampion.canonicalName !== canonicalName) {
    errors.push(`${match.matchRef}: championId ${championId} maps to ${localChampion.canonicalName}, not ${canonicalName}`)
  }
  if (canonicalName === 'Nidalee' && championId !== 76) {
    errors.push(`${match.matchRef}: Nidalee is only valid for championId 76`)
  }
  if (canonicalName === 'Jarvan IV' && championId !== 59) {
    errors.push(`${match.matchRef}: Jarvan IV is only valid for championId 59`)
  }
  if (championId === 233 && canonicalName === 'Nidalee') {
    errors.push(`${match.matchRef}: Briar was mislabeled as Nidalee`)
  }
  if (championId === 102 && canonicalName === 'Jarvan IV') {
    errors.push(`${match.matchRef}: Shyvana was mislabeled as Jarvan IV`)
  }
}

function validateNoSensitiveSnapshotFields(value: unknown, errors: string[], path = '$'): void {
  if (Array.isArray(value)) {
    value.forEach((item, index) => validateNoSensitiveSnapshotFields(item, errors, `${path}[${index}]`))
    return
  }
  if (!isRecord(value)) {
    if (typeof value === 'string') {
      if (!/Hash$/.test(path) && looksLikeRawMatchIdentifier(value)) {
        errors.push(`${path}: raw matchId/gameId-like value is not allowed`)
      }
      if (looksLikeLocalPathOrSecret(value)) {
        errors.push(`${path}: local path, cookie, or token-like value is not allowed`)
      }
    }
    return
  }
  for (const [key, child] of Object.entries(value)) {
    if (isForbiddenSensitiveKey(key)) {
      errors.push(`${path}.${key}: sensitive field is not allowed in anonymized coach_summary snapshot`)
      continue
    }
    validateNoSensitiveSnapshotFields(child, errors, `${path}.${key}`)
  }
}

function validateSnapshotRefs(snapshot: CoachSummaryInputSnapshot, errors: string[]): void {
  const refs = collectAllowedSnapshotRefs(snapshot)
  collectRefsByKey(snapshot, 'evidenceRefs').forEach(ref => {
    if (!refs.has(ref)) {
      errors.push(`evidenceRef ${ref} does not exist in snapshot refs`)
    }
    if (looksLikeRawMatchIdentifier(ref)) {
      errors.push(`evidenceRef ${ref} must not reference raw matchId/gameId`)
    }
  })
  collectRefsByKey(snapshot, 'dataRef').forEach(ref => {
    if (!refs.has(ref)) {
      errors.push(`dataRef ${ref} does not exist in snapshot refs`)
    }
  })
}

function collectAllowedSnapshotRefs(snapshot: CoachSummaryInputSnapshot): Set<string> {
  const refs = new Set<string>([
    'sample.queues',
    'matches[*].economyTimeline',
    'matches[*].events.deaths.nearestUpcomingObjective',
    'dataQuality'
  ])
  for (const match of snapshot.matches) {
    refs.add(match.matchRef)
    refs.add(`${match.matchRef}.laneDiff`)
    refs.add(`${match.matchRef}.economyTimeline`)
    refs.add(`${match.matchRef}.events.kills`)
    refs.add(`${match.matchRef}.events.deaths`)
    refs.add(`${match.matchRef}.events.objectives`)
    refs.add(`${match.matchRef}.events.buildings`)
    for (const event of [
      ...match.events.kills,
      ...match.events.deaths,
      ...match.events.objectives,
      ...match.events.buildings
    ]) {
      refs.add(event.eventRef)
    }
  }
  return refs
}

function collectRefsByKey(value: unknown, key: 'evidenceRefs' | 'dataRef'): string[] {
  if (Array.isArray(value)) {
    return value.flatMap(item => collectRefsByKey(item, key))
  }
  if (!isRecord(value)) {
    return []
  }
  const direct = value[key]
  const refs = key === 'evidenceRefs'
    ? (Array.isArray(direct) ? direct.filter((item): item is string => typeof item === 'string') : [])
    : (typeof direct === 'string' ? [direct] : [])
  return [
    ...refs,
    ...Object.entries(value)
      .filter(([childKey]) => childKey !== key)
      .flatMap(([_childKey, child]) => collectRefsByKey(child, key))
  ]
}

function isForbiddenSensitiveKey(key: string): boolean {
  return [
    'accountPuuid',
    'puuid',
    'summonerName',
    'gameName',
    'tagLine',
    'matchId',
    'gameId',
    'cookie',
    'token'
  ].includes(key)
}

function looksLikeRawMatchIdentifier(value: string): boolean {
  const trimmed = value.trim()
  return /^[A-Z]{2,}\d?[_-]\d{5,}$/i.test(trimmed)
    || /^match-\d+$/i.test(trimmed)
    || /^\d{6,}$/.test(trimmed)
}

function looksLikeLocalPathOrSecret(value: string): boolean {
  return /[A-Za-z]:\\/.test(value)
    || /(^|[/\\])\.local-debug([/\\]|$)/i.test(value)
    || /\b(cookie|token)\b/i.test(value)
}

function hasOwnProperty(value: object, key: string): boolean {
  return Object.prototype.hasOwnProperty.call(value, key)
}

function isNodeRuntime(): boolean {
  return typeof process !== 'undefined'
    && Boolean(process.versions?.node)
    && typeof window === 'undefined'
}

interface EnsureCoachSummarySgpHydrationParams {
  database: CoachSummaryDatabase
  accountPuuid: string
  rankedMatches: MatchRecord[]
  client: CoachSummarySgpHydrationClient | null | undefined
  delayMs: number
  retryCount: number
  sleep: (delayMs: number) => Promise<void>
  onProgress?: (progress: CoachSummaryHydrationProgress) => void
}

interface CoachSummaryMatchHydrationState {
  detail: MatchDetail | null
  parsed: ParsedMatchData
  hasTrustedDetail: boolean
  hasUsableTimeline: boolean
}

export async function ensureCoachSummarySgpHydration({
  database,
  accountPuuid,
  rankedMatches,
  client,
  delayMs,
  retryCount,
  sleep,
  onProgress
}: EnsureCoachSummarySgpHydrationParams): Promise<CoachSummarySgpHydrationSummary> {
  const hydration = createEmptySgpHydrationSummary(rankedMatches.length, delayMs, retryCount, Boolean(client && database.upsertMatchDetail))
  if (!client || !database.upsertMatchDetail || rankedMatches.length === 0) {
    return hydration
  }
  const loadSgpSummaryMap = createSgpSummaryMapLoader(client, accountPuuid)

  for (const [index, match] of rankedMatches.entries()) {
    const matchRef = formatMatchRef(index)
    onProgress?.({
      stage: 'hydrating_match',
      current: index + 1,
      total: rankedMatches.length,
      matchId: match.matchId,
      message: `正在补全第 ${index + 1}/${rankedMatches.length} 局对局详情...`
    })

    const state = await readCoachSummaryMatchHydrationState(database, match)
    let fetchedThisMatch = false
    if (state.hasTrustedDetail && state.hasUsableTimeline) {
      hydration.skippedBecauseAlreadyCompleteCount += 1
    } else {
      fetchedThisMatch = await ensureCoachSummaryMatchDetailAndTimeline({
        database,
        client,
        match,
        matchRef,
        state,
        hydration,
        retryCount,
        sleep,
        loadSgpSummaryMap
      })
    }

    if (fetchedThisMatch && index < rankedMatches.length - 1) {
      await sleep(delayMs)
    }
  }

  if (hydration.errors.length > 0) {
    onProgress?.({
      stage: 'partial_failure',
      total: rankedMatches.length,
      message: '部分对局时间线拉取失败，报告数据质量可能较低。'
    })
  }

  return hydration
}

function createEmptySgpHydrationSummary(
  totalMatches: number,
  delayMs: number,
  retryCount: number,
  attempted: boolean
): CoachSummarySgpHydrationSummary {
  return {
    attempted,
    totalMatches,
    detailFetchedCount: 0,
    timelineFetchedCount: 0,
    detailFailedMatchRefs: [],
    timelineFailedMatchRefs: [],
    skippedBecauseAlreadyCompleteCount: 0,
    delayMs,
    retryCount,
    errors: []
  }
}

function createSgpSummaryMapLoader(
  client: CoachSummarySgpHydrationClient,
  accountPuuid: string
): () => Promise<Map<number, Record<string, unknown>>> {
  if (!client.fetchRecentMatchSummariesFromSgpOnly) {
    return async () => new Map()
  }

  let cached: Promise<Map<number, Record<string, unknown>>> | null = null
  return () => {
    if (!cached) {
      cached = loadCoachSummarySgpSummaryMap(client, accountPuuid)
    }
    return cached
  }
}

async function loadCoachSummarySgpSummaryMap(
  client: CoachSummarySgpHydrationClient,
  accountPuuid: string
): Promise<Map<number, Record<string, unknown>>> {
  if (!client.fetchRecentMatchSummariesFromSgpOnly) {
    return new Map()
  }

  try {
    const response = await client.fetchRecentMatchSummariesFromSgpOnly(
      accountPuuid,
      COACH_SUMMARY_SGP_SUMMARY_PAGE_SIZE
    )
    const record = toRecord(response)
    const summaries = Array.isArray(response)
      ? response
      : firstRecordArray(record?.matches)
    const byGameId = new Map<number, Record<string, unknown>>()
    for (const summary of summaries) {
      const summaryRecord = toRecord(summary)
      if (!summaryRecord) {
        continue
      }
      const gameId = toPositiveInteger(firstNumber(summaryRecord.gameId, toRecord(summaryRecord.info)?.gameId))
      const participants = firstRecordArray(summaryRecord.participants, toRecord(summaryRecord.info)?.participants)
      if (gameId !== null && participants.length > 0) {
        byGameId.set(gameId, summaryRecord)
      }
    }
    return byGameId
  } catch {
    return new Map()
  }
}

async function readCoachSummaryMatchHydrationState(
  database: Pick<LocalDatabaseAPI, 'getMatchDetail'>,
  match: MatchRecord
): Promise<CoachSummaryMatchHydrationState> {
  const detail = await readMatchDetail(database, match)
  const parsed = parseMatchData(match, detail)
  return {
    detail,
    parsed,
    hasTrustedDetail: hasTrustedParticipantDetail(parsed, match.accountPuuid),
    hasUsableTimeline: hasUsableTimeline(parsed.timeline)
  }
}

interface EnsureCoachSummaryMatchDetailAndTimelineParams {
  database: CoachSummaryDatabase
  client: CoachSummarySgpHydrationClient
  match: MatchRecord
  matchRef: string
  state: CoachSummaryMatchHydrationState
  hydration: CoachSummarySgpHydrationSummary
  retryCount: number
  sleep: (delayMs: number) => Promise<void>
  loadSgpSummaryMap: () => Promise<Map<number, Record<string, unknown>>>
}

async function ensureCoachSummaryMatchDetailAndTimeline({
  database,
  client,
  match,
  matchRef,
  state,
  hydration,
  retryCount,
  sleep,
  loadSgpSummaryMap
}: EnsureCoachSummaryMatchDetailAndTimelineParams): Promise<boolean> {
  const gameId = readSgpFetchGameId(match)
  if (gameId === null) {
    recordHydrationError(hydration, matchRef, match.matchId, 'detail', 'Missing numeric gameId for SGP hydration')
    recordHydrationError(hydration, matchRef, match.matchId, 'timeline', 'Missing numeric gameId for SGP hydration')
    return false
  }

  let fetched = false
  let sgpDetail: Record<string, unknown> | null = state.hasTrustedDetail
    ? firstRecord(state.parsed.detail, state.parsed.normalizedDetail)
    : null
  let timeline: Record<string, unknown> | null = state.hasUsableTimeline ? state.parsed.timeline : null

  if (!sgpDetail) {
    const summaryDetail = (await loadSgpSummaryMap()).get(gameId)
    if (summaryDetail) {
      sgpDetail = normalizeFetchedSgpDetail(summaryDetail, gameId)
      if (sgpDetail) {
        hydration.detailFetchedCount += 1
      }
    }
  }

  if (!sgpDetail) {
    const detailResult = await fetchWithRetry(
      () => client.fetchGameDetailFromSgpOnly(gameId),
      retryCount,
      sleep
    )
    fetched = true
    if (detailResult.ok) {
      const normalized = normalizeFetchedSgpDetail(detailResult.value, gameId)
      if (normalized) {
        sgpDetail = normalized
        hydration.detailFetchedCount += 1
      } else {
        recordHydrationError(hydration, matchRef, match.matchId, 'detail', 'SGP detail response was incomplete')
      }
    } else {
      recordHydrationError(hydration, matchRef, match.matchId, 'detail', detailResult.message)
    }
  }

  if (!timeline) {
    const timelineResult = await fetchWithRetry(
      () => client.fetchGameTimelineFromSgpOnly(gameId),
      retryCount,
      sleep
    )
    fetched = true
    if (timelineResult.ok) {
      const normalizedTimeline = normalizeFetchedSgpTimeline(timelineResult.value)
      if (normalizedTimeline) {
        timeline = normalizedTimeline
        hydration.timelineFetchedCount += 1
      } else {
        recordHydrationError(hydration, matchRef, match.matchId, 'timeline', readTimelineFailureMessage(timelineResult.value))
      }
    } else {
      recordHydrationError(hydration, matchRef, match.matchId, 'timeline', timelineResult.message)
    }
  }

  const payload = {
    ...(sgpDetail ?? {
      gameId,
      participants: [],
      participantIdentities: []
    }),
    ...(timeline ? { timeline } : {}),
    coachSummaryHydration: {
      source: 'sgp',
      hydratedAt: new Date().toISOString()
    }
  }
  if (!sgpDetail && !timeline) {
    return fetched
  }
  try {
    await persistCoachSummarySgpDetail(database, match, payload)
  } catch (error) {
    recordHydrationError(hydration, matchRef, match.matchId, 'detail', `Failed to save SGP detail: ${errorMessage(error)}`)
  }
  return fetched
}

async function persistCoachSummarySgpDetail(
  database: CoachSummaryDatabase,
  match: MatchRecord,
  rawDetailJson: Record<string, unknown>
): Promise<void> {
  if (!database.upsertMatchDetail) {
    return
  }
  const detailInput: MatchDetailInput = {
    region: match.region,
    matchId: match.matchId,
    rawDetailJson,
    normalizedDetailJson: null,
    source: 'sgp',
    schemaVersion: 1
  }
  const result = await database.upsertMatchDetail(detailInput)
  if (!result.success) {
    throw new Error(result.error)
  }
}

async function fetchWithRetry<T>(
  operation: () => Promise<T>,
  retryCount: number,
  sleep: (delayMs: number) => Promise<void>
): Promise<{ ok: true; value: T } | { ok: false; message: string }> {
  const maxAttempts = Math.max(1, retryCount + 1)
  let lastMessage = 'SGP request failed'
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      return {
        ok: true,
        value: await operation()
      }
    } catch (error) {
      lastMessage = errorMessage(error)
      if (attempt < maxAttempts) {
        await sleep(backoffDelayForError(lastMessage))
      }
    }
  }
  return {
    ok: false,
    message: lastMessage
  }
}

function backoffDelayForError(message: string): number {
  const normalized = message.toLowerCase()
  return normalized.includes('429')
    || normalized.includes('timeout')
    || normalized.includes('timed out')
    || normalized.includes('network')
    ? COACH_SUMMARY_SGP_RETRY_BACKOFF_MS
    : COACH_SUMMARY_SGP_HYDRATION_DELAY_MS
}

function recordHydrationError(
  hydration: CoachSummarySgpHydrationSummary,
  matchRef: string,
  matchId: string,
  stage: 'detail' | 'timeline',
  message: string
): void {
  const failedMatchRefs = stage === 'detail' ? hydration.detailFailedMatchRefs : hydration.timelineFailedMatchRefs
  if (!failedMatchRefs.includes(matchRef)) {
    failedMatchRefs.push(matchRef)
  }
  hydration.errors.push({
    matchRef,
    matchIdHash: hashText(matchId),
    stage,
    message
  })
}

function normalizeFetchedSgpDetail(value: unknown, gameId: number): Record<string, unknown> | null {
  const record = toRecord(value)
  if (!record) {
    return null
  }
  const participants = firstRecordArray(record.participants, toRecord(record.info)?.participants)
  if (!participants.length) {
    return null
  }
  return {
    ...record,
    gameId: firstNumber(record.gameId, toRecord(record.info)?.gameId) ?? gameId
  }
}

function normalizeFetchedSgpTimeline(value: unknown): Record<string, unknown> | null {
  const response = toRecord(value)
  const timeline = normalizeTimelineCandidate(response?.timeline)
    ?? normalizeTimelineCandidate(response)
    ?? normalizeTimelineCandidate(parseJsonString(response?.rawTimelineJson))
    ?? normalizeTimelineCandidate(parseJsonString(response?.rawDetailJson))
  return timeline && hasUsableTimeline(timeline) ? timeline : null
}

function readTimelineFailureMessage(value: unknown): string {
  const response = toRecord(value)
  const status = firstString(response?.status)
  const lastError = firstString(response?.lastError)
  return lastError ?? (status ? `SGP timeline response status ${status}` : 'SGP timeline response was incomplete')
}

function readSgpFetchGameId(record: MatchRecord): number | null {
  const direct = safePositiveNumberFromString(record.matchId)
  if (direct !== null) {
    return direct
  }
  const summary = parseJsonObject(record.rawSummaryJson, `match summary ${record.matchId}`)
  return toPositiveInteger(firstNumber(summary?.gameId, toRecord(summary?.info)?.gameId))
}

function safePositiveNumberFromString(value: string): number | null {
  if (!value.trim()) {
    return null
  }
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

export function buildCoachSummaryRankedMatchDigest(
  record: MatchRecord,
  detail: MatchDetail | null,
  accountPuuid: string,
  matchRef = 'm01'
): MatchDigestBuildResult {
  const parsed = parseMatchData(record, detail)
  const self = resolveSelfParticipant(parsed, accountPuuid)
  const participantDetailAvailable = self.identityCheck?.source !== 'unconfirmed' && hasTrustedParticipantDetail(parsed, accountPuuid)
  const timelineAvailable = hasUsableTimeline(parsed.timeline)

  if (self.identityCheck?.source === 'unconfirmed') {
    const fallbackDigest = buildFallbackDigest(record, matchRef)
    return {
      digest: fallbackDigest,
      hasTimeline: timelineAvailable,
      hasParticipantDetail: false,
      hasLaneOpponent: false,
      hasEconomyDiff: false,
      hasRuneData: Boolean(fallbackDigest.self.runes),
      hasItemData: fallbackDigest.self.items.length > 0,
      hasRuneOrItemData: fallbackDigest.self.items.length > 0 || Boolean(fallbackDigest.self.runes),
      hasConfirmedSelfIdentity: false
    }
  }

  const participants = replaceParticipantWithSelf(parsed.participants, self.participant)
  const laneOpponent = findLaneOpponent(participants, self.participant)
  const economy = laneOpponent && parsed.timeline && getSortedFrames(parsed.timeline).length > 0
    ? buildEconomyDiff(parsed.timeline, participants, self.participant, laneOpponent.participant)
    : { hasEconomyDiff: false } satisfies EconomyDiffResult
  const events = parsed.timeline
    ? buildTimelineEvents(parsed.timeline, participants, self.participant, self.participantId, matchRef)
    : emptyEvents()
  const items = buildItems(self.stats, self.participant)
  const runes = buildRunes(self.stats)
  const summonerSpells = buildSummonerSpells(self.participant)
  const selfTeamId = firstNumber(self.participant.teamId)
  const durationSeconds = readDurationSeconds(record, parsed)
  const stats = buildSelfStats(self.stats, participants, selfTeamId, durationSeconds)
  const championId = firstNumber(self.participant.championId, record.championId)
  const champion = resolveChampionIdentity(championId, firstString(self.participant.championName, self.participant.championNameCn))
  const digest: CoachSummaryMatchDigest = {
    matchRef,
    matchIdHash: hashText(record.matchId),
    queueId: firstNumber(record.queueId) ?? 0,
    ...(record.gameCreation !== null ? { gameCreation: record.gameCreation } : {}),
    ...optionalTimestampFields(parsed),
    ...(durationSeconds !== undefined ? { durationSeconds } : {}),
    result: readResult(record, self.stats),
    self: {
      ...(championId !== null ? { championId } : {}),
      ...(champion ? { championName: champion.canonicalName, championCanonicalName: champion.canonicalName, championDisplayName: champion.displayName, championNameSource: champion.source } : {}),
      ...(self.identityCheck ? { identityCheck: self.identityCheck } : {}),
      ...(selfTeamId !== null ? { teamId: selfTeamId } : {}),
      ...(firstString(self.participant.role, self.timeline?.role) ? { role: firstString(self.participant.role, self.timeline?.role) ?? undefined } : {}),
      ...(firstString(self.participant.lane, self.timeline?.lane) ? { lane: firstString(self.participant.lane, self.timeline?.lane) ?? undefined } : {}),
      ...(firstString(self.participant.teamPosition, self.participant.individualPosition, self.timeline?.teamPosition, self.timeline?.lane) ? { position: firstString(self.participant.teamPosition, self.participant.individualPosition, self.timeline?.teamPosition, self.timeline?.lane) ?? undefined } : {}),
      ...(firstNumber(self.stats?.kills, record.kills) !== null ? { kills: firstNumber(self.stats?.kills, record.kills) ?? undefined } : {}),
      ...(firstNumber(self.stats?.deaths, record.deaths) !== null ? { deaths: firstNumber(self.stats?.deaths, record.deaths) ?? undefined } : {}),
      ...(firstNumber(self.stats?.assists, record.assists) !== null ? { assists: firstNumber(self.stats?.assists, record.assists) ?? undefined } : {}),
      ...(buildKdaText(self.stats, record) ? { kdaText: buildKdaText(self.stats, record) ?? undefined } : {}),
      items,
      ...(summonerSpells.length ? { summonerSpells } : {}),
      ...(runes ? { runes } : {}),
      ...(stats ? { stats } : {})
    },
    ...(laneOpponent ? { laneOpponent: buildLaneOpponentDigest(laneOpponent) } : {}),
    ...(economy.laneDiff ? { laneDiff: economy.laneDiff } : {}),
    ...(economy.economyTimeline ? { economyTimeline: economy.economyTimeline } : {}),
    events
  }

  return {
    digest,
    hasTimeline: timelineAvailable,
    hasParticipantDetail: participantDetailAvailable,
    hasLaneOpponent: Boolean(laneOpponent),
    hasEconomyDiff: economy.hasEconomyDiff,
    hasRuneData: Boolean(runes),
    hasItemData: items.length > 0,
    hasRuneOrItemData: items.length > 0 || Boolean(runes),
    hasConfirmedSelfIdentity: true
  }
}

async function listRecentRankedRecords(
  database: Pick<LocalDatabaseAPI, 'listMatchRecordsByAccount'>,
  accountPuuid: string
): Promise<MatchRecord[]> {
  const rankedRecordGroups = await Promise.all(
    COACH_SUMMARY_RANKED_QUEUE_IDS.map(queueId => listQueueRecords(database, accountPuuid, queueId))
  )
  const recordsByMatchId = new Map<string, MatchRecord>()
  for (const record of rankedRecordGroups.flat()) {
    if (!isRankedQueueId(record.queueId)) {
      continue
    }
    const existing = recordsByMatchId.get(record.matchId)
    if (!existing || (readRecordTimestamp(record) ?? 0) > (readRecordTimestamp(existing) ?? 0)) {
      recordsByMatchId.set(record.matchId, record)
    }
  }

  return Array.from(recordsByMatchId.values())
    .sort(compareMatchRecordsByRecency)
}

async function listQueueRecords(
  database: Pick<LocalDatabaseAPI, 'listMatchRecordsByAccount'>,
  accountPuuid: string,
  queueId: RankedQueueId
): Promise<MatchRecord[]> {
  const options: MatchRecordListOptions = {
    limit: RANKED_MATCH_QUERY_LIMIT_PER_QUEUE,
    offset: 0,
    queueId
  }
  const result = await database.listMatchRecordsByAccount(accountPuuid, options)
  if (!result.success) {
    throw new Error(result.error)
  }
  return result.data
}

async function loadLatestCoachSummaryResult(
  database: Pick<LocalDatabaseAPI, 'listAnalysisResultsByAccount'>,
  accountPuuid: string
): Promise<AiAnalysisResult | null> {
  const result = await database.listAnalysisResultsByAccount(accountPuuid, {
    analysisType: COACH_SUMMARY_ANALYSIS_TYPE,
    limit: 1,
    offset: 0
  })
  if (!result.success) {
    throw new Error(result.error)
  }
  return result.data[0] ?? null
}

function countNewRankedMatchesSinceLastReport(records: MatchRecord[], result: AiAnalysisResult): number {
  const anchor = parsePriorCoachSummaryAnchor(result)
  if (anchor.anchorMatchIds.length > 0) {
    const anchorSet = new Set(anchor.anchorMatchIds)
    const firstAnchorIndex = records.findIndex(record => anchorSet.has(record.matchId))
    return firstAnchorIndex >= 0 ? firstAnchorIndex : records.length
  }

  const threshold = anchor.latestMatchTimestamp ?? anchor.fallbackGeneratedAtTimestamp
  if (threshold === null) {
    return records.length
  }

  return records.filter(record => {
    const timestamp = readRecordTimestamp(record)
    return timestamp !== null && timestamp > threshold
  }).length
}

function parsePriorCoachSummaryAnchor(result: AiAnalysisResult): PriorCoachSummaryAnchor {
  const parsedOutput: Record<string, unknown> = parseJsonObject(result.outputJson, `AI analysis ${result.id}`) ?? {}
  const input = toRecord(parsedOutput.input)
  const snapshot = toRecord(parsedOutput.snapshot)
  const metadata = firstRecord(parsedOutput.metadata, input?.metadata, snapshot?.metadata)
  const anchorMatchIds = readStringArray(
    metadata?.anchorMatchIds,
    metadata?.matchIdList,
    parsedOutput.anchorMatchIds,
    parsedOutput.matchIdList
  )
  const latestMatchTimestamp = firstNumber(metadata?.latestMatchTimestamp, parsedOutput.latestMatchTimestamp)
  const generatedAt = firstString(metadata?.generatedInputAt, metadata?.generatedAt, parsedOutput.generatedAt, result.createdAt)
  const fallbackGeneratedAtTimestamp = generatedAt ? Date.parse(generatedAt) : Number.NaN

  return {
    anchorMatchIds,
    latestMatchTimestamp,
    fallbackGeneratedAtTimestamp: Number.isFinite(fallbackGeneratedAtTimestamp) ? fallbackGeneratedAtTimestamp : null
  }
}

async function readMatchDetail(
  database: Pick<LocalDatabaseAPI, 'getMatchDetail'>,
  record: MatchRecord
): Promise<MatchDetail | null> {
  try {
    const result = await database.getMatchDetail(record.region, record.matchId)
    if (!result.success) {
      console.warn(`Failed to read local match detail ${record.matchId}:`, result.error)
      return null
    }
    return result.data
  } catch (error) {
    console.warn(`Failed to read local match detail ${record.matchId}:`, error)
    return null
  }
}

function parseMatchData(record: MatchRecord, detail: MatchDetail | null): ParsedMatchData {
  const summary = parseJsonObject(record.rawSummaryJson, `match summary ${record.matchId}`)
  const detailRecord = detail ? parseJsonObject(detail.rawDetailJson, `match detail ${record.matchId}`) : null
  const normalizedDetail = detail?.normalizedDetailJson
    ? parseJsonObject(detail.normalizedDetailJson, `normalized match detail ${record.matchId}`)
    : null
  const summaryInfo = toRecord(summary?.info)
  const detailInfo = toRecord(detailRecord?.info)
  const normalizedInfo = toRecord(normalizedDetail?.info)
  const trustedDetail = isTrustedCoachSummaryDetail(detail, detailRecord, normalizedDetail)
  const trustedTimeline = trustedDetail
    ? extractTimeline(detailRecord, detailInfo, normalizedDetail, normalizedInfo)
    : null
  const summaryTimeline = extractTimeline(summary, summaryInfo)
  const timeline = trustedTimeline ?? summaryTimeline
  const trustedParticipants = trustedDetail ? firstRecordArray(
    detailRecord?.participants,
    detailInfo?.participants,
    normalizedDetail?.participants,
    normalizedInfo?.participants
  ) : []
  const trustedIdentities = trustedDetail ? firstRecordArray(
    detailRecord?.participantIdentities,
    detailInfo?.participantIdentities,
    normalizedDetail?.participantIdentities,
    normalizedInfo?.participantIdentities
  ) : []
  const summaryParticipants = firstRecordArray(summary?.participants, summaryInfo?.participants)
  const summaryIdentities = firstRecordArray(summary?.participantIdentities, summaryInfo?.participantIdentities)
  const participants = trustedParticipants.length ? trustedParticipants : summaryParticipants
  const identities = trustedIdentities.length ? trustedIdentities : summaryIdentities

  return {
    summary,
    detail: detailRecord,
    normalizedDetail,
    timeline,
    participants,
    identities,
    summaryParticipants,
    summaryIdentities,
    trustedParticipants,
    trustedIdentities
  }
}

function isTrustedCoachSummaryDetail(
  detail: MatchDetail | null,
  detailRecord: Record<string, unknown> | null,
  normalizedDetail: Record<string, unknown> | null
): boolean {
  if (!detail) {
    return false
  }
  if (isSgpSource(detail.source)) {
    return true
  }
  const hydration = firstRecord(
    toRecord(detailRecord?.coachSummaryHydration),
    toRecord(normalizedDetail?.coachSummaryHydration)
  )
  if (firstString(hydration?.source)?.toLowerCase() === 'sgp') {
    return true
  }
  const detailInfo = toRecord(detailRecord?.info)
  const normalizedInfo = toRecord(normalizedDetail?.info)
  const participants = firstRecordArray(
    detailRecord?.participants,
    detailInfo?.participants,
    normalizedDetail?.participants,
    normalizedInfo?.participants
  )
  const timeline = extractTimeline(detailRecord, detailInfo, normalizedDetail, normalizedInfo)
  return participants.length > 0 && hasUsableTimeline(timeline)
}

function isSgpSource(source: string | null | undefined): boolean {
  return typeof source === 'string' && source.trim().toLowerCase().includes('sgp')
}

function hasTrustedParticipantDetail(parsed: ParsedMatchData, accountPuuid: string): boolean {
  if (!parsed.trustedParticipants.length) {
    return false
  }
  if (findParticipantByPuuidInSource(parsed.trustedParticipants, parsed.trustedIdentities, accountPuuid, 'detail_puuid')) {
    return true
  }
  const summarySelf = findParticipantByPuuidInSource(parsed.summaryParticipants, parsed.summaryIdentities, accountPuuid, 'summary_puuid')
  return Boolean(summarySelf && findParticipantByParticipantId(parsed.trustedParticipants, summarySelf.participantId))
}

function buildFallbackDigest(record: MatchRecord, matchRef: string): CoachSummaryMatchDigest {
  const kills = firstNumber(record.kills)
  const deaths = firstNumber(record.deaths)
  const assists = firstNumber(record.assists)
  const durationSeconds = firstNumber(record.gameDuration)
  const championId = firstNumber(record.championId)
  const champion = resolveChampionIdentity(championId)
  const totalMinionsKilled = null
  const neutralMinionsKilled = null
  const selfStats = {
    ...(record.goldEarned !== null && record.goldEarned !== undefined ? { goldEarned: record.goldEarned } : {}),
    ...(record.totalDamageDealtToChampions !== null && record.totalDamageDealtToChampions !== undefined ? { totalDamageDealtToChampions: record.totalDamageDealtToChampions } : {}),
    ...(durationSeconds !== null && record.goldEarned !== null && record.goldEarned !== undefined ? { goldPerMin: roundMetric(record.goldEarned / (durationSeconds / 60)) } : {}),
    ...(totalMinionsKilled !== null ? { totalMinionsKilled } : {}),
    ...(neutralMinionsKilled !== null ? { neutralMinionsKilled } : {})
  }

  return {
    matchRef,
    matchIdHash: hashText(record.matchId),
    queueId: firstNumber(record.queueId) ?? 0,
    ...(record.gameCreation !== null ? { gameCreation: record.gameCreation } : {}),
    ...(durationSeconds !== null ? { durationSeconds } : {}),
    result: record.win === true ? 'win' : record.win === false ? 'loss' : 'unknown',
    self: {
      ...(championId !== null ? { championId } : {}),
      ...(champion ? { championName: champion.canonicalName, championCanonicalName: champion.canonicalName, championDisplayName: champion.displayName, championNameSource: champion.source } : {}),
      identityCheck: {
        matchedByAccountPuuid: false,
        source: 'unconfirmed'
      },
      ...(record.lane ? { lane: record.lane } : {}),
      ...(record.role ? { role: record.role } : {}),
      ...(record.lane ? { position: record.lane } : {}),
      ...(kills !== null ? { kills } : {}),
      ...(deaths !== null ? { deaths } : {}),
      ...(assists !== null ? { assists } : {}),
      ...(buildKdaTextFromNumbers(kills, deaths, assists) ? { kdaText: buildKdaTextFromNumbers(kills, deaths, assists) ?? undefined } : {}),
      items: [],
      ...(record.spell1Id || record.spell2Id ? { summonerSpells: [record.spell1Id, record.spell2Id].flatMap(spellId => toPositiveInteger(spellId) === null ? [] : [{ spellId: toPositiveInteger(spellId) ?? 0 }]) } : {}),
      ...(record.perk0 ? { runes: { keystoneId: record.perk0, selectedPerkIds: [record.perk0] } } : {}),
      ...(Object.keys(selfStats).length ? { stats: selfStats } : {})
    },
    events: emptyEvents()
  }
}

function resolveSelfParticipant(parsed: ParsedMatchData, accountPuuid: string): ParticipantContext {
  const detailSelf = findParticipantByPuuidInSource(
    parsed.trustedParticipants,
    parsed.trustedIdentities,
    accountPuuid,
    'detail_puuid'
  )
  if (detailSelf) {
    return detailSelf
  }

  const summarySelf = findParticipantByPuuidInSource(
    parsed.summaryParticipants,
    parsed.summaryIdentities,
    accountPuuid,
    'summary_puuid'
  )
  if (summarySelf) {
    const detailParticipant = findParticipantByParticipantId(parsed.trustedParticipants, summarySelf.participantId)
    if (detailParticipant) {
      const participant = mergeTrustedDetailIntoSummarySelf(summarySelf.participant, detailParticipant)
      return {
        participant,
        participantId: firstNumber(participant.participantId, summarySelf.participantId),
        identity: summarySelf.identity,
        stats: buildParticipantStats(participant),
        timeline: toRecord(participant.timeline),
        identityCheck: {
          matchedByAccountPuuid: true,
          matchedParticipantIndex: parsed.summaryParticipants.indexOf(summarySelf.participant),
          matchedParticipantPuuidHash: hashText(accountPuuid),
          source: 'summary_anchor',
          warnings: ['detail participant did not expose account puuid; summary self participant was used as the identity anchor']
        }
      }
    }
    return summarySelf
  }

  return {
    participant: {},
    participantId: null,
    identity: null,
    stats: null,
    timeline: null,
    identityCheck: {
      matchedByAccountPuuid: false,
      source: 'unconfirmed'
    }
  }
}

function findParticipantByPuuidInSource(
  participants: Record<string, unknown>[],
  identities: Record<string, unknown>[],
  accountPuuid: string,
  source: CoachSummaryIdentityCheck['source']
): ParticipantContext | null {
  const identity = identities.find(item => {
    const player = toRecord(item.player)
    return firstString(player?.puuid) === accountPuuid
  }) ?? null
  const participantId = firstNumber(identity?.participantId)
  let participant = participantId === null
    ? null
    : findParticipantByParticipantId(participants, participantId)

  if (!participant) {
    participant = participants.find(item => {
      const player = toRecord(item.player)
      return firstString(item.puuid, player?.puuid) === accountPuuid
    }) ?? null
  }

  if (!participant) {
    return null
  }

  return {
    participant,
    participantId: firstNumber(participant.participantId, participantId),
    identity,
    stats: buildParticipantStats(participant),
    timeline: toRecord(participant.timeline),
    identityCheck: {
      matchedByAccountPuuid: true,
      matchedParticipantIndex: participants.indexOf(participant),
      matchedParticipantPuuidHash: hashText(accountPuuid),
      source
    }
  }
}

function findParticipantByParticipantId(
  participants: Record<string, unknown>[],
  participantId: number | null
): Record<string, unknown> | null {
  if (participantId === null) {
    return null
  }
  return participants.find(item => firstNumber(item.participantId) === participantId) ?? null
}

function mergeTrustedDetailIntoSummarySelf(
  summarySelf: Record<string, unknown>,
  detailParticipant: Record<string, unknown>
): Record<string, unknown> {
  const merged = {
    ...detailParticipant,
    ...summarySelf
  }
  const detailStats = toRecord(detailParticipant.stats)
  const detailTimeline = toRecord(detailParticipant.timeline)
  if (detailStats) {
    merged.stats = detailStats
  }
  if (detailTimeline) {
    merged.timeline = detailTimeline
  }
  const summaryChampionId = firstNumber(summarySelf.championId)
  if (summaryChampionId !== null) {
    merged.championId = summaryChampionId
  }
  const summaryChampionName = firstString(summarySelf.championName, summarySelf.championNameCn)
  if (summaryChampionName) {
    merged.championName = summaryChampionName
  }
  return merged
}

function replaceParticipantWithSelf(
  participants: Record<string, unknown>[],
  self: Record<string, unknown>
): Record<string, unknown>[] {
  const participantId = firstNumber(self.participantId)
  if (participantId === null) {
    return participants
  }
  let replaced = false
  const next = participants.map(participant => {
    if (firstNumber(participant.participantId) !== participantId) {
      return participant
    }
    replaced = true
    return self
  })
  return replaced ? next : [self, ...next]
}

function findLaneOpponent(
  participants: Record<string, unknown>[],
  self: Record<string, unknown>
): ParticipantContext | null {
  const selfTeamId = firstNumber(self.teamId)
  const selfLane = resolveParticipantLane(self)
  if (selfTeamId === null || selfLane === null) {
    return null
  }

  const opponent = participants.find(participant =>
    firstNumber(participant.teamId) !== selfTeamId
    && resolveParticipantLane(participant) === selfLane
  )
  if (!opponent) {
    return null
  }

  return {
    participant: opponent,
    participantId: firstNumber(opponent.participantId),
    identity: null,
    stats: buildParticipantStats(opponent),
    timeline: toRecord(opponent.timeline)
  }
}

function buildParticipantStats(participant: Record<string, unknown>): Record<string, unknown> | null {
  const stats = toRecord(participant.stats)
  if (!stats) {
    return participant
  }
  return {
    ...participant,
    ...stats
  }
}

function buildLaneOpponentDigest(opponent: ParticipantContext): NonNullable<CoachSummaryMatchDigest['laneOpponent']> {
  const player = toRecord(opponent.identity?.player)
  const puuid = firstString(opponent.participant.puuid, player?.puuid)
  const timeline = opponent.timeline
  const championId = firstNumber(opponent.participant.championId)
  const champion = resolveChampionIdentity(championId, firstString(opponent.participant.championName, opponent.participant.championNameCn))

  return {
    ...(puuid ? { puuidHash: hashText(puuid) } : {}),
    ...(championId !== null ? { championId } : {}),
    ...(champion ? { championName: champion.canonicalName } : {}),
    ...(firstString(opponent.participant.role, timeline?.role) ? { role: firstString(opponent.participant.role, timeline?.role) ?? undefined } : {}),
    ...(firstString(opponent.participant.lane, timeline?.lane) ? { lane: firstString(opponent.participant.lane, timeline?.lane) ?? undefined } : {}),
    ...(firstString(opponent.participant.teamPosition, opponent.participant.individualPosition, timeline?.teamPosition, timeline?.lane) ? { position: firstString(opponent.participant.teamPosition, opponent.participant.individualPosition, timeline?.teamPosition, timeline?.lane) ?? undefined } : {})
  }
}

function buildEconomyDiff(
  timeline: Record<string, unknown>,
  participants: Record<string, unknown>[],
  self: Record<string, unknown>,
  opponent: Record<string, unknown>
): EconomyDiffResult {
  const selfId = firstNumber(self.participantId)
  const opponentId = firstNumber(opponent.participantId)
  const selfTeamId = firstNumber(self.teamId)
  if (selfId === null || opponentId === null || selfTeamId === null) {
    return { hasEconomyDiff: false }
  }

  const targetMinutes = [10, 15, 20]
  const targetPoints = targetMinutes.map(minute => buildTimelinePointAtMinute(timeline, participants, selfId, opponentId, selfTeamId, minute))
  const allPoints = getSortedFrames(timeline)
    .map(frame => buildTimelinePointFromFrame(frame, participants, selfId, opponentId, selfTeamId))
    .filter((point): point is TimelineFramePoint => point !== null)
  const laneDiff: NonNullable<CoachSummaryMatchDigest['laneDiff']> = {}
  const economyTimeline: NonNullable<CoachSummaryMatchDigest['economyTimeline']> = {}

  applyLaneDiff(laneDiff, 10, targetPoints[0])
  applyLaneDiff(laneDiff, 15, targetPoints[1])
  applyLaneDiff(laneDiff, 20, targetPoints[2])
  applyTeamDiff(economyTimeline, 10, targetPoints[0])
  applyTeamDiff(economyTimeline, 15, targetPoints[1])
  applyTeamDiff(economyTimeline, 20, targetPoints[2])

  if (allPoints.length) {
    economyTimeline.points = allPoints
  }

  return {
    ...(Object.keys(laneDiff).length ? { laneDiff } : {}),
    ...(Object.keys(economyTimeline).length ? { economyTimeline } : {}),
    hasEconomyDiff: Object.keys(laneDiff).length > 0 || Object.keys(economyTimeline).length > 0
  }
}

function buildTimelinePointAtMinute(
  timeline: Record<string, unknown>,
  participants: Record<string, unknown>[],
  selfId: number,
  opponentId: number,
  selfTeamId: number,
  minute: number
): TimelineFramePoint | null {
  const frame = findFrameAtOrBefore(timeline, minute * 60_000)
  return frame ? buildTimelinePointFromFrame(frame, participants, selfId, opponentId, selfTeamId) : null
}

function buildTimelinePointFromFrame(
  frame: Record<string, unknown>,
  participants: Record<string, unknown>[],
  selfId: number,
  opponentId: number,
  selfTeamId: number
): TimelineFramePoint | null {
  const timestamp = readTimelineTimestampMs(frame.timestamp)
  if (timestamp === null) {
    return null
  }
  const selfFrame = findParticipantFrame(frame, selfId)
  const opponentFrame = findParticipantFrame(frame, opponentId)
  const selfGold = firstNumber(selfFrame?.totalGold)
  const laneOpponentGold = firstNumber(opponentFrame?.totalGold)
  const selfCs = readFrameCreepScore(selfFrame)
  const laneOpponentCs = readFrameCreepScore(opponentFrame)
  const selfXp = firstNumber(selfFrame?.xp)
  const laneOpponentXp = firstNumber(opponentFrame?.xp)
  const teamGolds = calculateTeamGolds(frame, participants, selfTeamId)

  return {
    minute: Math.round(timestamp / 60_000),
    ...(selfGold !== null ? { selfGold } : {}),
    ...(laneOpponentGold !== null ? { laneOpponentGold } : {}),
    ...(selfGold !== null && laneOpponentGold !== null ? { selfGoldDiff: selfGold - laneOpponentGold } : {}),
    ...(selfCs !== null ? { selfCs } : {}),
    ...(laneOpponentCs !== null ? { laneOpponentCs } : {}),
    ...(selfCs !== null && laneOpponentCs !== null ? { csDiff: selfCs - laneOpponentCs } : {}),
    ...(selfXp !== null ? { selfXp } : {}),
    ...(laneOpponentXp !== null ? { laneOpponentXp } : {}),
    ...(selfXp !== null && laneOpponentXp !== null ? { xpDiff: selfXp - laneOpponentXp } : {}),
    ...(teamGolds.allyTeamGold !== null ? { allyTeamGold: teamGolds.allyTeamGold } : {}),
    ...(teamGolds.enemyTeamGold !== null ? { enemyTeamGold: teamGolds.enemyTeamGold } : {}),
    ...(teamGolds.teamGoldDiff !== null ? { teamGoldDiff: teamGolds.teamGoldDiff } : {})
  }
}

function applyLaneDiff(
  laneDiff: NonNullable<CoachSummaryMatchDigest['laneDiff']>,
  minute: 10 | 15 | 20,
  point: TimelineFramePoint | null
) {
  if (!point) {
    return
  }
  if (point.selfGoldDiff !== undefined) {
    if (minute === 10) {
      laneDiff.goldDiffAt10 = point.selfGoldDiff
    } else if (minute === 15) {
      laneDiff.goldDiffAt15 = point.selfGoldDiff
    } else {
      laneDiff.goldDiffAt20 = point.selfGoldDiff
    }
  }
  if (point.csDiff !== undefined) {
    if (minute === 10) {
      laneDiff.csDiffAt10 = point.csDiff
    } else if (minute === 15) {
      laneDiff.csDiffAt15 = point.csDiff
    }
  }
  if (point.xpDiff !== undefined) {
    if (minute === 10) {
      laneDiff.xpDiffAt10 = point.xpDiff
    } else if (minute === 15) {
      laneDiff.xpDiffAt15 = point.xpDiff
    }
  }
}

function applyTeamDiff(
  economyTimeline: NonNullable<CoachSummaryMatchDigest['economyTimeline']>,
  minute: 10 | 15 | 20,
  point: TimelineFramePoint | null
) {
  if (!point) {
    return
  }
  if (point.teamGoldDiff !== undefined) {
    if (minute === 10) {
      economyTimeline.teamGoldDiffAt10 = point.teamGoldDiff
    } else if (minute === 15) {
      economyTimeline.teamGoldDiffAt15 = point.teamGoldDiff
    } else {
      economyTimeline.teamGoldDiffAt20 = point.teamGoldDiff
    }
  }
  if (point.selfGoldDiff !== undefined) {
    if (minute === 10) {
      economyTimeline.selfGoldDiffAt10 = point.selfGoldDiff
    } else if (minute === 15) {
      economyTimeline.selfGoldDiffAt15 = point.selfGoldDiff
    } else {
      economyTimeline.selfGoldDiffAt20 = point.selfGoldDiff
    }
  }
}

function buildTimelineEvents(
  timeline: Record<string, unknown>,
  participants: Record<string, unknown>[],
  self: Record<string, unknown>,
  selfParticipantId: number | null,
  matchRef: string
): CoachSummaryMatchDigest['events'] {
  const events = getTimelineEvents(timeline)
  const participantsById = createParticipantsById(participants)
  let objectiveIndex = 0
  const objectiveEvents = events
    .map(event => {
      if (normalizeText(event.eventType) !== 'ELITE_MONSTER_KILL') {
        return null
      }
      objectiveIndex += 1
      return toObjectiveEventDigest(event, participantsById, firstNumber(self.teamId), timeline, participants, `${matchRef}:obj${String(objectiveIndex).padStart(2, '0')}`)
    })
    .filter((event): event is NonNullable<CoachSummaryMatchDigest['events']['objectives'][number]> => event !== null)
    .sort((left, right) => left.timeSeconds - right.timeSeconds)
  const killEvents: CoachSummaryMatchDigest['events']['kills'] = []
  const deathEvents: CoachSummaryMatchDigest['events']['deaths'] = []
  const buildingEvents: CoachSummaryMatchDigest['events']['buildings'] = []
  const selfTeamId = firstNumber(self.teamId)

  for (const event of events) {
    const eventType = normalizeText(event.eventType)
    if (eventType === 'CHAMPION_KILL') {
      const killEvent = toKillEventDigest(event, participantsById, selfParticipantId, `${matchRef}:k${String(killEvents.length + 1).padStart(2, '0')}`)
      if (killEvent) {
        killEvents.push(killEvent)
      }
      if (firstNumber(event.victimId) === selfParticipantId) {
        const death = toDeathEventDigest(event, participantsById, selfTeamId, timeline, participants, objectiveEvents, `${matchRef}:d${String(deathEvents.length + 1).padStart(2, '0')}`)
        if (death) {
          deathEvents.push(death)
        }
      }
      continue
    }

    if (eventType === 'BUILDING_KILL') {
      const building = toBuildingEventDigest(event, participantsById, `${matchRef}:b${String(buildingEvents.length + 1).padStart(2, '0')}`)
      if (building) {
        buildingEvents.push(building)
      }
    }
  }

  return {
    kills: killEvents,
    deaths: deathEvents,
    buildings: buildingEvents,
    objectives: objectiveEvents
  }
}

function toKillEventDigest(
  event: Record<string, unknown>,
  participantsById: Map<number, Record<string, unknown>>,
  selfParticipantId: number | null,
  eventRef: string
): CoachSummaryMatchDigest['events']['kills'][number] | null {
  const timeSeconds = readTimelineTimeSeconds(event.timestamp)
  if (timeSeconds === null) {
    return null
  }
  const killerId = firstNumber(event.killerId)
  const victimId = firstNumber(event.victimId)
  const assistingIds = readNumberArray(event.assistingParticipantIds)
  const position = readPosition(event.position)
  return {
    eventRef,
    timeSeconds,
    ...(selfParticipantId !== null && killerId === selfParticipantId ? { killerIsSelf: true } : {}),
    ...(selfParticipantId !== null && victimId === selfParticipantId ? { victimIsSelf: true } : {}),
    ...(selfParticipantId !== null && assistingIds.includes(selfParticipantId) ? { assisterIsSelf: true } : {}),
    ...(killerId !== null ? { killerChampionName: readChampionName(participantsById.get(killerId)) ?? undefined } : {}),
    ...(victimId !== null ? { victimChampionName: readChampionName(participantsById.get(victimId)) ?? undefined } : {}),
    ...(position ? { position } : {})
  }
}

function toDeathEventDigest(
  event: Record<string, unknown>,
  participantsById: Map<number, Record<string, unknown>>,
  selfTeamId: number | null,
  timeline: Record<string, unknown>,
  participants: Record<string, unknown>[],
  objectiveEvents: CoachSummaryMatchDigest['events']['objectives'],
  eventRef: string
): CoachSummaryMatchDigest['events']['deaths'][number] | null {
  const timeSeconds = readTimelineTimeSeconds(event.timestamp)
  if (timeSeconds === null) {
    return null
  }
  const killerId = firstNumber(event.killerId)
  const assistingIds = readNumberArray(event.assistingParticipantIds)
  const teamGoldDiffAtDeath = selfTeamId === null
    ? null
    : calculateTeamGoldDiffAtTime(timeline, participants, selfTeamId, timeSeconds * 1000)
  const nearestObjective = findNearestUpcomingObjective(objectiveEvents, timeSeconds)
  const position = readPosition(event.position)

  return {
    eventRef,
    timeSeconds,
    ...(killerId !== null ? { killerChampionName: readChampionName(participantsById.get(killerId)) ?? undefined } : {}),
    ...(assistingIds.length ? { assistingChampionNames: assistingIds.flatMap(id => readChampionName(participantsById.get(id)) ?? []) } : {}),
    ...(teamGoldDiffAtDeath !== null ? { teamGoldDiffAtDeath } : {}),
    ...(nearestObjective ? { nearestUpcomingObjective: nearestObjective } : {}),
    ...(position ? { position } : {})
  }
}

function toBuildingEventDigest(
  event: Record<string, unknown>,
  participantsById: Map<number, Record<string, unknown>>,
  eventRef: string
): CoachSummaryMatchDigest['events']['buildings'][number] | null {
  const timeSeconds = readTimelineTimeSeconds(event.timestamp)
  if (timeSeconds === null) {
    return null
  }
  const killerId = firstNumber(event.killerId)
  const killerTeamId = killerId === null ? firstNumber(event.teamId) : firstNumber(participantsById.get(killerId)?.teamId, event.teamId)
  const position = readPosition(event.position)

  return {
    eventRef,
    timeSeconds,
    ...(classifyBuildingType(event) ? { type: classifyBuildingType(event) ?? undefined } : {}),
    ...(firstNumber(event.teamId) !== null ? { teamId: firstNumber(event.teamId) ?? undefined } : {}),
    ...(killerTeamId !== null ? { killerTeamId } : {}),
    ...(firstString(event.laneType) ? { laneType: firstString(event.laneType) ?? undefined } : {}),
    ...(position ? { position } : {})
  }
}

function toObjectiveEventDigest(
  event: Record<string, unknown>,
  participantsById: Map<number, Record<string, unknown>>,
  selfTeamId: number | null,
  timeline: Record<string, unknown>,
  participants: Record<string, unknown>[],
  eventRef: string
): CoachSummaryMatchDigest['events']['objectives'][number] | null {
  if (normalizeText(event.eventType) !== 'ELITE_MONSTER_KILL') {
    return null
  }
  const type = classifyObjectiveType(event)
  const timeSeconds = readTimelineTimeSeconds(event.timestamp)
  if (!type || timeSeconds === null) {
    return null
  }
  const killerId = firstNumber(event.killerId)
  const killerTeamId = killerId === null ? firstNumber(event.teamId) : firstNumber(participantsById.get(killerId)?.teamId, event.teamId)
  const teamGoldDiffAtObjective = selfTeamId === null
    ? null
    : calculateTeamGoldDiffAtTime(timeline, participants, selfTeamId, timeSeconds * 1000)

  return {
    eventRef,
    timeSeconds,
    type,
    ...(killerTeamId !== null ? { killerTeamId } : {}),
    ...(selfTeamId !== null && killerTeamId !== null ? { isAllyObjective: killerTeamId === selfTeamId } : {}),
    ...(teamGoldDiffAtObjective !== null ? { teamGoldDiffAtObjective } : {})
  }
}

function buildSelfStats(
  stats: Record<string, unknown> | null,
  participants: Record<string, unknown>[],
  selfTeamId: number | null,
  durationSeconds: number | undefined
): CoachSummaryMatchDigest['self']['stats'] | undefined {
  const totalMinionsKilled = firstNumber(stats?.totalMinionsKilled, stats?.minionsKilled)
  const neutralMinionsKilled = firstNumber(stats?.neutralMinionsKilled)
  const creepScore = sumNullable(totalMinionsKilled, neutralMinionsKilled)
  const goldEarned = firstNumber(stats?.goldEarned)
  const totalDamageDealtToChampions = firstNumber(stats?.totalDamageDealtToChampions)
  const visionScore = firstNumber(stats?.visionScore)
  const teamParticipants = selfTeamId === null ? [] : participants.filter(participant => firstNumber(participant.teamId) === selfTeamId)
  const teamKills = sumNumbers(teamParticipants.map(participant => firstNumber(toRecord(participant.stats)?.kills)))
  const teamDamage = sumNumbers(teamParticipants.map(participant => firstNumber(toRecord(participant.stats)?.totalDamageDealtToChampions)))
  const kills = firstNumber(stats?.kills)
  const assists = firstNumber(stats?.assists)
  const result = {
    ...(totalMinionsKilled !== null ? { totalMinionsKilled } : {}),
    ...(neutralMinionsKilled !== null ? { neutralMinionsKilled } : {}),
    ...(durationSeconds && durationSeconds > 0 && creepScore !== null ? { csPerMin: roundMetric(creepScore / (durationSeconds / 60)) } : {}),
    ...(goldEarned !== null ? { goldEarned } : {}),
    ...(durationSeconds && durationSeconds > 0 && goldEarned !== null ? { goldPerMin: roundMetric(goldEarned / (durationSeconds / 60)) } : {}),
    ...(totalDamageDealtToChampions !== null ? { totalDamageDealtToChampions } : {}),
    ...(totalDamageDealtToChampions !== null && teamDamage > 0 ? { damageShare: roundMetric(totalDamageDealtToChampions / teamDamage) } : {}),
    ...(visionScore !== null ? { visionScore } : {}),
    ...(kills !== null && assists !== null && teamKills > 0 ? { killParticipation: roundMetric((kills + assists) / teamKills) } : {})
  }

  return Object.keys(result).length ? result : undefined
}

function buildItems(...sources: Array<Record<string, unknown> | null>): CoachSummaryMatchDigest['self']['items'] {
  return Array.from({ length: 7 }, (_item, slot) => {
    const itemId = firstNumber(...sources.map(source => source?.[`item${slot}`]))
    return itemId !== null && itemId > 0 ? { slot, itemId } : null
  }).filter((item): item is { slot: number; itemId: number } => item !== null)
}

function buildSummonerSpells(participant: Record<string, unknown>): NonNullable<CoachSummaryMatchDigest['self']['summonerSpells']> {
  return [
    firstNumber(participant.spell1Id, participant.summoner1Id),
    firstNumber(participant.spell2Id, participant.summoner2Id)
  ]
    .filter((spellId): spellId is number => spellId !== null && spellId > 0)
    .map(spellId => ({ spellId }))
}

function buildRunes(stats: Record<string, unknown> | null): CoachSummaryMatchDigest['self']['runes'] | undefined {
  if (!stats) {
    return undefined
  }
  const perks = toRecord(stats.perks)
  const styleData = readModernRuneStyles(perks)
  const selectedPerkIds = uniqueSortedNumbers([
    ...['perk0', 'perk1', 'perk2', 'perk3', 'perk4', 'perk5'].flatMap(key => firstNumber(stats[key]) ?? []),
    ...styleData.selectedPerkIds
  ])
  const keystoneId = firstNumber(stats.perk0, styleData.keystoneId)
  const result = {
    ...(firstNumber(stats.perkPrimaryStyle, styleData.primaryStyleId) !== null ? { primaryStyleId: firstNumber(stats.perkPrimaryStyle, styleData.primaryStyleId) ?? undefined } : {}),
    ...(firstNumber(stats.perkSubStyle, styleData.subStyleId) !== null ? { subStyleId: firstNumber(stats.perkSubStyle, styleData.subStyleId) ?? undefined } : {}),
    ...(keystoneId !== null ? { keystoneId } : {}),
    selectedPerkIds
  }
  return selectedPerkIds.length || keystoneId !== null || result.primaryStyleId !== undefined || result.subStyleId !== undefined
    ? result
    : undefined
}

function readModernRuneStyles(perks: Record<string, unknown> | null): {
  primaryStyleId: number | null
  subStyleId: number | null
  keystoneId: number | null
  selectedPerkIds: number[]
} {
  const styles = firstRecordArray(perks?.styles)
  const primary = styles[0] ?? null
  const sub = styles[1] ?? null
  const selectedPerkIds = styles.flatMap(style =>
    firstRecordArray(style.selections)
      .flatMap(selection => firstNumber(selection.perk) ?? [])
  )
  return {
    primaryStyleId: firstNumber(primary?.style),
    subStyleId: firstNumber(sub?.style),
    keystoneId: selectedPerkIds[0] ?? null,
    selectedPerkIds
  }
}

function buildDataQuality(
  records: MatchRecord[],
  results: MatchDigestBuildResult[],
  sgpHydration?: CoachSummarySgpHydrationSummary
): CoachSummaryDataQuality {
  const missingTimelineMatchRefs = collectMissing(results, result => result.hasTimeline)
  const missingParticipantDetailMatchRefs = collectMissing(results, result => result.hasParticipantDetail)
  const missingRuneMatchRefs = collectMissing(results, result => result.hasRuneData)
  const missingItemMatchRefs = collectMissing(results, result => result.hasItemData)
  const missingRuneOrItemMatchRefs = collectMissing(results, result => result.hasRuneOrItemData)
  const missingLaneOpponentMatchRefs = collectMissing(results, result => result.hasLaneOpponent)
  const missingEconomyDiffMatchRefs = collectMissing(results, result => result.hasEconomyDiff)
  const missingDataReasons = buildMissingDataReasons(records, results)
  const confidence = calculateDataQualityConfidence(records.length, {
    missingTimelineMatchRefs,
    missingParticipantDetailMatchRefs,
    missingLaneOpponentMatchRefs,
    missingEconomyDiffMatchRefs
  })

  return {
    hasAllTimelines: missingTimelineMatchRefs.length === 0,
    hasAllParticipantDetails: missingParticipantDetailMatchRefs.length === 0,
    hasAllRuneData: missingRuneMatchRefs.length === 0,
    hasAllItemData: missingItemMatchRefs.length === 0,
    hasAnyRuneOrItemData: missingRuneOrItemMatchRefs.length < records.length,
    missingTimelineMatchRefs,
    missingParticipantDetailMatchRefs,
    missingRuneMatchRefs,
    missingItemMatchRefs,
    missingRuneOrItemMatchRefs,
    missingLaneOpponentMatchRefs,
    missingEconomyDiffMatchRefs,
    ...(sgpHydration ? { sgpHydration } : {}),
    missingDataReasons,
    confidence
  }
}

function buildMissingDataReasons(
  records: MatchRecord[],
  results: MatchDigestBuildResult[]
): CoachSummaryDataQuality['missingDataReasons'] {
  return results.flatMap((result, index) => {
    const reasons: CoachSummaryDataQuality['missingDataReasons'][number]['reasons'] = []
    if (!result.hasTimeline) {
      reasons.push('timeline_missing')
    }
    if (!result.hasParticipantDetail) {
      reasons.push('participant_detail_missing')
    }
    if (!result.hasRuneData) {
      reasons.push('rune_data_missing')
    }
    if (!result.hasItemData) {
      reasons.push('item_data_missing')
    }
    if (!result.hasLaneOpponent) {
      reasons.push('lane_opponent_unmatched')
    }
    if (!result.hasEconomyDiff) {
      reasons.push('economy_diff_unavailable')
    }

    return reasons.length
      ? [{ matchRef: result.digest.matchRef, reasons }]
      : []
  })
}

function collectMissing(
  results: MatchDigestBuildResult[],
  predicate: (result: MatchDigestBuildResult) => boolean
): string[] {
  return results.flatMap(result => predicate(result) ? [] : [result.digest.matchRef])
}

function calculateDataQualityConfidence(
  totalMatches: number,
  missing: Pick<
    CoachSummaryDataQuality,
    'missingTimelineMatchRefs' | 'missingParticipantDetailMatchRefs' | 'missingLaneOpponentMatchRefs' | 'missingEconomyDiffMatchRefs'
  >
): CoachSummaryDataQuality['confidence'] {
  if (missing.missingTimelineMatchRefs.length > 8 || missing.missingParticipantDetailMatchRefs.length > 5) {
    return 'low'
  }
  if (
    missing.missingTimelineMatchRefs.length <= 2
    && missing.missingParticipantDetailMatchRefs.length === 0
    && missing.missingLaneOpponentMatchRefs.length <= Math.max(2, Math.floor(totalMatches * 0.2))
    && missing.missingEconomyDiffMatchRefs.length <= Math.max(2, Math.floor(totalMatches * 0.2))
  ) {
    return 'high'
  }
  if (
    missing.missingTimelineMatchRefs.length <= 8
    && missing.missingParticipantDetailMatchRefs.length <= 2
  ) {
    return 'medium'
  }
  return 'low'
}

function buildQueueSample(records: MatchRecord[]): CoachSummaryInputSnapshot['sample']['queues'] {
  const counts = new Map<number, number>()
  for (const record of records) {
    const queueId = firstNumber(record.queueId)
    if (queueId !== null) {
      counts.set(queueId, (counts.get(queueId) ?? 0) + 1)
    }
  }
  return Array.from(counts.entries())
    .map(([queueId, count]) => ({ queueId, count }))
    .sort((left, right) => left.queueId - right.queueId)
}

function formatMatchRef(index: number): string {
  return `m${String(index + 1).padStart(2, '0')}`
}

function optionalTimestampFields(parsed: ParsedMatchData): Pick<CoachSummaryMatchDigest, 'gameStartTimestamp' | 'gameEndTimestamp'> {
  const sources = matchMetadataSources(parsed)
  const gameStartTimestamp = firstNumber(...sources.map(source => source?.gameStartTimestamp))
  const gameEndTimestamp = firstNumber(...sources.map(source => source?.gameEndTimestamp))
  return {
    ...(gameStartTimestamp !== null ? { gameStartTimestamp } : {}),
    ...(gameEndTimestamp !== null ? { gameEndTimestamp } : {})
  }
}

function readDurationSeconds(record: MatchRecord, parsed: ParsedMatchData): number | undefined {
  const duration = firstNumber(
    record.gameDuration,
    ...matchMetadataSources(parsed).map(source => source?.gameDuration)
  )
  if (duration === null) {
    return undefined
  }
  return duration > 100_000 ? Math.round(duration / 1000) : duration
}

function matchMetadataSources(parsed: ParsedMatchData): Array<Record<string, unknown> | null> {
  return [
    parsed.detail,
    toRecord(parsed.detail?.info),
    parsed.normalizedDetail,
    toRecord(parsed.normalizedDetail?.info),
    parsed.summary,
    toRecord(parsed.summary?.info)
  ]
}

function readResult(record: MatchRecord, stats: Record<string, unknown> | null): CoachSummaryMatchDigest['result'] {
  const win = firstBoolean(stats?.win, record.win)
  if (win === true) {
    return 'win'
  }
  if (win === false) {
    return 'loss'
  }
  return 'unknown'
}

function buildKdaText(stats: Record<string, unknown> | null, record: MatchRecord): string | null {
  return buildKdaTextFromNumbers(
    firstNumber(stats?.kills, record.kills),
    firstNumber(stats?.deaths, record.deaths),
    firstNumber(stats?.assists, record.assists)
  )
}

function buildKdaTextFromNumbers(kills: number | null, deaths: number | null, assists: number | null): string | null {
  if (kills === null || deaths === null || assists === null) {
    return null
  }
  const ratio = deaths === 0 ? kills + assists : (kills + assists) / deaths
  return `${kills}/${deaths}/${assists} (${roundMetric(ratio)})`
}

function extractTimeline(...sources: Array<Record<string, unknown> | null>): Record<string, unknown> | null {
  for (const source of sources) {
    const direct = normalizeTimelineSource(source)
    if (direct) {
      return direct
    }
  }
  return null
}

function normalizeTimelineSource(source: Record<string, unknown> | null): Record<string, unknown> | null {
  if (!source) {
    return null
  }
  const candidates: unknown[] = [
    source.timeline,
    source.matchTimeline,
    source.gameTimeline,
    source.timelineDto,
    source,
    parseJsonString(source.rawTimelineJson),
    parseJsonString(source.timelineJson)
  ]
  for (const candidate of candidates) {
    const normalized = normalizeTimelineCandidate(candidate)
    if (normalized) {
      return normalized
    }
  }
  return null
}

function normalizeTimelineCandidate(candidate: unknown): Record<string, unknown> | null {
  const record = toRecord(candidate)
  if (!record) {
    return null
  }
  if (Array.isArray(record.frames) || Array.isArray(record.events)) {
    return record
  }
  const info = toRecord(record.info)
  if (info && (Array.isArray(info.frames) || Array.isArray(info.events))) {
    return info
  }
  const data = toRecord(record.data)
  if (data && (Array.isArray(data.frames) || Array.isArray(data.events))) {
    return data
  }
  return null
}

function hasUsableTimeline(timeline: Record<string, unknown> | null | undefined): boolean {
  return Boolean(timeline && (getSortedFrames(timeline).length > 0 || getTimelineEvents(timeline).length > 0))
}

function getSortedFrames(timeline: Record<string, unknown>): Record<string, unknown>[] {
  return firstRecordArray(timeline.frames)
    .filter(frame => readTimelineTimestampMs(frame.timestamp) !== null)
    .sort((left, right) => (readTimelineTimestampMs(left.timestamp) ?? 0) - (readTimelineTimestampMs(right.timestamp) ?? 0))
}

function getTimelineEvents(timeline: Record<string, unknown>): Record<string, unknown>[] {
  const rootEvents = firstRecordArray(timeline.events)
  if (rootEvents.length) {
    return rootEvents
  }
  return getSortedFrames(timeline).flatMap(frame => firstRecordArray(frame.events))
}

function findFrameAtOrBefore(timeline: Record<string, unknown>, targetTimestampMs: number): Record<string, unknown> | null {
  const frames = getSortedFrames(timeline)
  let candidate: Record<string, unknown> | null = null
  for (const frame of frames) {
    const timestamp = readTimelineTimestampMs(frame.timestamp)
    if (timestamp === null || timestamp > targetTimestampMs) {
      continue
    }
    candidate = frame
  }
  return candidate
}

function findParticipantFrame(frame: Record<string, unknown>, participantId: number): Record<string, unknown> | null {
  const participantFrames = toRecord(frame.participantFrames)
  if (!participantFrames) {
    return null
  }
  const direct = toRecord(participantFrames[String(participantId)])
  if (direct) {
    return direct
  }
  return Object.values(participantFrames)
    .map(toRecord)
    .find(participantFrame => firstNumber(participantFrame?.participantId) === participantId) ?? null
}

function readFrameCreepScore(participantFrame: Record<string, unknown> | null): number | null {
  if (!participantFrame) {
    return null
  }
  return sumNullable(
    firstNumber(participantFrame.minionsKilled),
    firstNumber(participantFrame.jungleMinionsKilled)
  )
}

function calculateTeamGolds(
  frame: Record<string, unknown>,
  participants: Record<string, unknown>[],
  selfTeamId: number
): {
  allyTeamGold: number | null
  enemyTeamGold: number | null
  teamGoldDiff: number | null
} {
  const allyGold = sumTeamGold(frame, participants, selfTeamId)
  const enemyTeamId = selfTeamId === BLUE_TEAM_ID ? RED_TEAM_ID : BLUE_TEAM_ID
  const enemyGold = sumTeamGold(frame, participants, enemyTeamId)
  return {
    allyTeamGold: allyGold,
    enemyTeamGold: enemyGold,
    teamGoldDiff: allyGold !== null && enemyGold !== null ? allyGold - enemyGold : null
  }
}

function sumTeamGold(frame: Record<string, unknown>, participants: Record<string, unknown>[], teamId: number): number | null {
  const goldValues = participants
    .filter(participant => firstNumber(participant.teamId) === teamId)
    .flatMap(participant => {
      const participantId = firstNumber(participant.participantId)
      if (participantId === null) {
        return []
      }
      const totalGold = firstNumber(findParticipantFrame(frame, participantId)?.totalGold)
      return totalGold === null ? [] : [totalGold]
    })
  return goldValues.length ? sumNumbers(goldValues) : null
}

function calculateTeamGoldDiffAtTime(
  timeline: Record<string, unknown>,
  participants: Record<string, unknown>[],
  selfTeamId: number,
  timestampMs: number
): number | null {
  const frame = findFrameAtOrBefore(timeline, timestampMs)
  if (!frame) {
    return null
  }
  return calculateTeamGolds(frame, participants, selfTeamId).teamGoldDiff
}

function createParticipantsById(participants: Record<string, unknown>[]): Map<number, Record<string, unknown>> {
  const map = new Map<number, Record<string, unknown>>()
  for (const participant of participants) {
    const participantId = firstNumber(participant.participantId)
    if (participantId !== null) {
      map.set(participantId, participant)
    }
  }
  return map
}

function findNearestUpcomingObjective(
  objectiveEvents: CoachSummaryMatchDigest['events']['objectives'],
  deathTimeSeconds: number
): NonNullable<CoachSummaryMatchDigest['events']['deaths'][number]['nearestUpcomingObjective']> | null {
  const objective = objectiveEvents.find(event =>
    event.timeSeconds > deathTimeSeconds
    && event.timeSeconds - deathTimeSeconds <= OBJECTIVE_LOOKAHEAD_SECONDS
    && isKnownNearestObjectiveType(event.type)
  )
  if (!objective) {
    return null
  }
  return {
    type: objective.type as 'dragon' | 'baron' | 'herald' | 'grub',
    spawnOrEventTimeSeconds: objective.timeSeconds,
    secondsBeforeObjective: Math.round(objective.timeSeconds - deathTimeSeconds)
  }
}

function isKnownNearestObjectiveType(type: string): boolean {
  return type === 'dragon' || type === 'baron' || type === 'herald' || type === 'grub'
}

function classifyObjectiveType(event: Record<string, unknown>): CoachSummaryObjectiveType | null {
  const monsterType = normalizeText(event.monsterType)
  if (monsterType.includes('DRAGON')) {
    return 'dragon'
  }
  if (monsterType.includes('BARON')) {
    return 'baron'
  }
  if (monsterType.includes('RIFTHERALD')) {
    return 'riftHerald'
  }
  if (monsterType.includes('HERALD')) {
    return 'herald'
  }
  if (monsterType.includes('HORDE') || monsterType.includes('VOIDGRUB') || monsterType.includes('GRUB')) {
    return 'grub'
  }
  return null
}

function classifyBuildingType(event: Record<string, unknown>): 'tower' | 'inhibitor' | 'nexus' | string | null {
  const buildingType = normalizeText(event.buildingType)
  const towerType = normalizeText(event.towerType)
  const combined = `${buildingType} ${towerType}`
  if (combined.includes('TOWER') || combined.includes('TURRET')) {
    return 'tower'
  }
  if (combined.includes('INHIBITOR')) {
    return 'inhibitor'
  }
  if (combined.includes('NEXUS')) {
    return 'nexus'
  }
  return firstString(event.buildingType, event.towerType)
}

function readRecordTimestamp(record: MatchRecord): number | null {
  const summary = parseJsonObject(record.rawSummaryJson, `match summary ${record.matchId}`)
  return firstNumber(
    summary?.gameEndTimestamp,
    toRecord(summary?.info)?.gameEndTimestamp,
    summary?.gameStartTimestamp,
    toRecord(summary?.info)?.gameStartTimestamp,
    record.gameCreation,
    summary?.gameCreation,
    toRecord(summary?.info)?.gameCreation
  )
}

function compareMatchRecordsByRecency(left: MatchRecord, right: MatchRecord): number {
  const rightTimestamp = readRecordTimestamp(right) ?? right.gameCreation ?? 0
  const leftTimestamp = readRecordTimestamp(left) ?? left.gameCreation ?? 0
  if (rightTimestamp !== leftTimestamp) {
    return rightTimestamp - leftTimestamp
  }
  return right.id - left.id
}

function isRankedQueueId(queueId: number | null): queueId is RankedQueueId {
  return queueId === 420 || queueId === 440
}

function emptyEvents(): CoachSummaryMatchDigest['events'] {
  return {
    kills: [],
    deaths: [],
    buildings: [],
    objectives: []
  }
}

function resolveParticipantLane(participant: Record<string, unknown>): ParticipantLane | null {
  const timeline = toRecord(participant.timeline)
  const directPosition = firstLaneAlias(
    firstString(participant.teamPosition),
    firstString(participant.individualPosition),
    firstString(participant.selectedPosition),
    firstString(timeline?.teamPosition)
  )
  if (directPosition) {
    return directPosition
  }

  const lane = normalizeText(firstString(timeline?.rawLane, timeline?.lane, participant.lane))
  const role = normalizeText(firstString(timeline?.rawRole, timeline?.role, participant.role))
  if (lane === 'BOTTOM' || lane === 'BOT') {
    if (role === 'SUPPORT' || role === 'DUO_SUPPORT') {
      return 'support'
    }
    return 'bottom'
  }
  return laneToParticipantLane(lane) ?? laneToParticipantLane(role)
}

function firstLaneAlias(...values: Array<string | null>): ParticipantLane | null {
  for (const value of values) {
    const lane = laneToParticipantLane(normalizeText(value))
    if (lane) {
      return lane
    }
  }
  return null
}

function laneToParticipantLane(value: string): ParticipantLane | null {
  switch (value) {
    case 'TOP':
      return 'top'
    case 'JUNGLE':
      return 'jungle'
    case 'MIDDLE':
    case 'MID':
      return 'middle'
    case 'BOTTOM':
    case 'BOT':
    case 'DUO_CARRY':
      return 'bottom'
    case 'UTILITY':
    case 'SUPPORT':
    case 'DUO_SUPPORT':
      return 'support'
    default:
      return null
  }
}

function readTimelineTimestampMs(value: unknown): number | null {
  const timestamp = firstNumber(value)
  if (timestamp === null) {
    return null
  }
  return timestamp < 10_000 ? timestamp * 1000 : timestamp
}

function readTimelineTimeSeconds(value: unknown): number | null {
  const timestamp = readTimelineTimestampMs(value)
  return timestamp === null ? null : Math.round(timestamp / 1000)
}

function readPosition(value: unknown): { x: number; y: number } | null {
  const position = toRecord(value)
  const x = firstNumber(position?.x)
  const y = firstNumber(position?.y)
  return x === null || y === null ? null : { x, y }
}

function readChampionName(participant: Record<string, unknown> | null | undefined): string | undefined {
  const champion = resolveChampionIdentity(
    firstNumber(participant?.championId),
    firstString(participant?.championName, participant?.championNameCn)
  )
  return champion?.canonicalName
}

function readStringArray(...values: unknown[]): string[] {
  for (const value of values) {
    if (Array.isArray(value)) {
      return value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
    }
  }
  return []
}

function readNumberArray(value: unknown): number[] {
  return Array.isArray(value)
    ? value.filter((item): item is number => typeof item === 'number' && Number.isFinite(item))
    : []
}

function firstRecord(...values: unknown[]): Record<string, unknown> | null {
  for (const value of values) {
    const record = toRecord(value)
    if (record) {
      return record
    }
  }
  return null
}

function firstRecordArray(...values: unknown[]): Record<string, unknown>[] {
  for (const value of values) {
    if (Array.isArray(value)) {
      return value.filter(isRecord)
    }
  }
  return []
}

function firstNumber(...values: unknown[]): number | null {
  for (const value of values) {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value
    }
  }
  return null
}

function firstString(...values: unknown[]): string | null {
  for (const value of values) {
    if (typeof value === 'string' && value.trim().length > 0) {
      return value.trim()
    }
  }
  return null
}

function firstBoolean(...values: unknown[]): boolean | null {
  for (const value of values) {
    if (typeof value === 'boolean') {
      return value
    }
    if (value === 1) {
      return true
    }
    if (value === 0) {
      return false
    }
  }
  return null
}

function toPositiveInteger(value: unknown): number | null {
  const numberValue = firstNumber(value)
  return numberValue !== null && Number.isInteger(numberValue) && numberValue > 0 ? numberValue : null
}

function sumNullable(...values: Array<number | null>): number | null {
  const numbers = values.filter((value): value is number => value !== null)
  return numbers.length ? sumNumbers(numbers) : null
}

function sumNumbers(values: Array<number | null>): number {
  return values.reduce<number>((total, value) => total + (value ?? 0), 0)
}

function uniqueSortedNumbers(values: Array<number | null | undefined>): number[] {
  return [...new Set(values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value)))]
    .sort((left, right) => left - right)
}

function roundMetric(value: number): number {
  return Number(value.toFixed(3))
}

function normalizeText(value: unknown): string {
  return typeof value === 'string' && value.trim()
    ? value.trim().toUpperCase().replace(/[\s-]+/g, '_')
    : ''
}

function parseJsonObject(value: string, label: string): Record<string, unknown> | null {
  try {
    const parsed = JSON.parse(value) as unknown
    return toRecord(parsed)
  } catch (error) {
    console.warn(`Skipping malformed local ${label} JSON:`, error)
    return null
  }
}

function parseJsonString(value: unknown): unknown {
  if (typeof value !== 'string' || !value.trim()) {
    return null
  }
  try {
    return JSON.parse(value) as unknown
  } catch {
    return null
  }
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

function toRecord(value: unknown): Record<string, unknown> | null {
  return isRecord(value) ? value : null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function hashText(source: string): string {
  let hash = 0x811c9dc5
  for (let index = 0; index < source.length; index += 1) {
    hash ^= source.charCodeAt(index)
    hash = Math.imul(hash, 0x01000193) >>> 0
  }
  return hash.toString(16).padStart(8, '0')
}

function getRendererDatabase(): CoachSummaryDatabase | null {
  if (typeof window === 'undefined') {
    return null
  }
  return window.electronAPI?.database ?? null
}

function getDefaultCoachSummarySgpHydrationClient(): CoachSummarySgpHydrationClient {
  return {
    fetchRecentMatchSummariesFromSgpOnly: async (accountPuuid: string, pageSize: number) => {
      const { apiClient } = await import('../api/httpClient.ts')
      const response = await apiClient.getMatchHistoryPage(accountPuuid, {
        page: 1,
        pageSize,
        source: 'sgp',
        forceRefresh: true
      })
      return response.matches
    },
    fetchGameDetailFromSgpOnly: async (gameId: number) => {
      const { apiClient } = await import('../api/httpClient.ts')
      return apiClient.getGameDetail(gameId, { source: 'sgp', sgpOnly: true })
    },
    fetchGameTimelineFromSgpOnly: async (gameId: number) => {
      const { apiClient } = await import('../api/httpClient.ts')
      return apiClient.getGameTimeline(gameId, { source: 'sgp', sgpOnly: true })
    }
  }
}

function delay(delayMs: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, delayMs))
}
