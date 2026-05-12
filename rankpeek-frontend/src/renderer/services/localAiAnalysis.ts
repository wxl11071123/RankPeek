import type {
  AiAnalysisListOptions,
  AiAnalysisResult,
  LocalDatabaseAPI
} from '../types/localDatabase'
import type {
  CoachSummaryChartDatum,
  CoachSummaryChartKind,
  CoachSummaryChartPlacement,
  CoachSummaryReportV1,
  NormalizedCoachSummaryChartBlock
} from '../types/coachSummaryReport'

const INVALID_OUTPUT_SUMMARY = '无法解析的分析结果'
const SUMMARY_LIMIT = 160
const COACH_SUMMARY_REPORT_SCHEMA_VERSION = 'coach_summary_report.v1'
const COACH_SUMMARY_ANALYSIS_TYPE = 'coach_summary'
const COACH_HEADLINE_FALLBACK = '近期排位复盘'
const COACH_HEADLINE_LIMIT = 18

const COACH_CHART_KINDS = new Set<CoachSummaryChartKind>(['bar', 'line', 'scatter', 'timeline', 'table'])
const COACH_CHART_PLACEMENTS = new Set<CoachSummaryChartPlacement>(['overview', 'analysis', 'summary'])
const LEGACY_CHART_KIND_MAP: Record<string, CoachSummaryChartKind> = {
  gold_curve: 'line',
  death_timeline: 'timeline',
  champion_pool: 'bar',
  role_profile: 'bar',
  metric_comparison: 'bar',
  objective_deaths: 'bar'
}

type AiAnalysisDatabase = Pick<LocalDatabaseAPI, 'listAnalysisResultsByAccount'>

export interface ParsedAiAnalysisOutput {
  status: 'parsed' | 'invalid'
  title: string | null
  summary: string
  highlights: string[]
}

export interface LocalAiAnalysisDisplayResult extends AiAnalysisResult {
  analysisTypeLabel: string
  createdAtLabel: string
  output: ParsedAiAnalysisOutput
}

export interface LoadLocalAiAnalysisOptions extends AiAnalysisListOptions {
  database?: AiAnalysisDatabase | null
}

export interface LoadLocalAiAnalysisResult {
  results: LocalAiAnalysisDisplayResult[]
  unavailable: boolean
  error: string | null
}

export interface ParseCoachSummaryReportOutputResult {
  status: 'parsed' | 'unsupported' | 'invalid'
  report: CoachSummaryReportV1 | null
  error?: string
}

export async function loadLocalAiAnalysisResults(
  accountPuuid: string | null | undefined,
  options: LoadLocalAiAnalysisOptions = {}
): Promise<LoadLocalAiAnalysisResult> {
  const trimmedPuuid = accountPuuid?.trim()
  if (!trimmedPuuid) {
    return {
      results: [],
      unavailable: false,
      error: null
    }
  }

  const database = options.database ?? getRendererDatabase()
  if (!database) {
    console.warn('Local AI analysis database API is unavailable')
    return {
      results: [],
      unavailable: true,
      error: '本地分析记录暂不可用'
    }
  }

  const queryOptions: AiAnalysisListOptions = {}
  if (options.limit !== undefined) {
    queryOptions.limit = options.limit
  }
  if (options.offset !== undefined) {
    queryOptions.offset = options.offset
  }
  if (options.analysisType !== undefined) {
    queryOptions.analysisType = options.analysisType
  }
  if (options.matchId !== undefined) {
    queryOptions.matchId = options.matchId
  }

  try {
    const result = await database.listAnalysisResultsByAccount(trimmedPuuid, queryOptions)
    if (!result.success) {
      console.warn('Failed to read local AI analysis results:', result.error)
      return {
        results: [],
        unavailable: true,
        error: result.error
      }
    }

    return {
      results: result.data.map(toDisplayResult),
      unavailable: false,
      error: null
    }
  } catch (error) {
    console.warn('Failed to read local AI analysis results:', error)
    return {
      results: [],
      unavailable: true,
      error: error instanceof Error ? error.message : String(error)
    }
  }
}

export function parseAiAnalysisOutput(outputJson: string): ParsedAiAnalysisOutput {
  try {
    const parsed = JSON.parse(outputJson) as unknown
    return normalizeParsedOutput(parsed)
  } catch (error) {
    console.warn('Failed to parse local AI analysis output:', error)
    return {
      status: 'invalid',
      title: null,
      summary: INVALID_OUTPUT_SUMMARY,
      highlights: []
    }
  }
}

export function parseCoachSummaryReportOutput(outputJson: string): ParseCoachSummaryReportOutputResult {
  try {
    const parsed = JSON.parse(outputJson) as unknown
    if (!isRecord(parsed)) {
      return {
        status: 'invalid',
        report: null,
        error: 'Output is not an object'
      }
    }

    if (
      parsed.schemaVersion !== COACH_SUMMARY_REPORT_SCHEMA_VERSION ||
      parsed.analysisType !== COACH_SUMMARY_ANALYSIS_TYPE
    ) {
      return {
        status: 'unsupported',
        report: null
      }
    }

    const title = getStringField(parsed, 'title')
    const summary = getStringField(parsed, 'summary')
    const inputHash = getStringField(parsed, 'inputHash')
    const verdict = isRecord(parsed.verdict) ? parsed.verdict : null
    const metadata = isRecord(parsed.metadata) ? parsed.metadata : null

    if (!title || !summary || !inputHash || !verdict || !metadata) {
      return {
        status: 'invalid',
        report: null,
        error: 'Missing required coach summary report fields'
      }
    }

    const report: CoachSummaryReportV1 = {
      schemaVersion: COACH_SUMMARY_REPORT_SCHEMA_VERSION,
      analysisType: COACH_SUMMARY_ANALYSIS_TYPE,
      inputHash,
      title,
      summary,
      verdict: normalizeVerdict(verdict),
      keyFindings: normalizeRecordArray(parsed.keyFindings),
      trainingPlan: normalizeRecordArray(parsed.trainingPlan),
      championAdvice: normalizeRecordArray(parsed.championAdvice),
      chartBlocks: normalizeCoachChartBlocks(parsed.chartBlocks),
      warnings: normalizeRecordArray(parsed.warnings),
      metadata: normalizeMetadata(metadata)
    }

    const headline = getStringField(parsed, 'headline')
    const cardTitle = getStringField(parsed, 'cardTitle')
    const shortTitle = getStringField(parsed, 'shortTitle')
    const finalSummary = getStringField(parsed, 'finalSummary')
    const overview = normalizeOverview(parsed.overview)
    if (headline) {
      report.headline = headline
    }
    if (cardTitle) {
      report.cardTitle = cardTitle
    }
    if (shortTitle) {
      report.shortTitle = shortTitle
    }
    if (overview) {
      report.overview = overview
    }
    if (finalSummary) {
      report.finalSummary = finalSummary
    }

    return {
      status: 'parsed',
      report
    }
  } catch (error) {
    console.warn('Failed to parse coach summary report output:', error)
    return {
      status: 'invalid',
      report: null,
      error: error instanceof Error ? error.message : String(error)
    }
  }
}

export function getCoachReportHeadline({
  report,
  result
}: {
  report?: unknown
  result?: Pick<AiAnalysisResult, 'outputJson'> | LocalAiAnalysisDisplayResult | null
}): string {
  const reportRecord = isRecord(report) ? report : readReportFromResult(result)
  const candidates = [
    getStringField(reportRecord, 'headline'),
    getStringField(reportRecord, 'cardTitle'),
    getStringField(reportRecord, 'shortTitle'),
    getNestedString(reportRecord, 'verdict', 'label'),
    truncateHeadline(getStringField(reportRecord, 'title') || '')
  ].filter((item): item is string => Boolean(item))

  return candidates[0] || COACH_HEADLINE_FALLBACK
}

export function normalizeCoachChartBlocks(blocks: unknown): NormalizedCoachSummaryChartBlock[] {
  if (!Array.isArray(blocks)) {
    return []
  }

  return blocks
    .map(normalizeCoachChartBlock)
    .filter((block): block is NormalizedCoachSummaryChartBlock => Boolean(block))
}

export function formatAnalysisType(type: string): string {
  const normalized = type.trim().toLowerCase()
  if (!normalized) {
    return '未知分析'
  }

  if (normalized.includes('pre') || normalized.includes('before')) {
    return '赛前分析'
  }

  if (normalized.includes('post') || normalized.includes('review') || normalized.includes('after')) {
    return '赛后复盘'
  }

  if (normalized.includes('coach') || normalized.includes('weekly') || normalized.includes('monthly')) {
    return '电子教练'
  }

  if (normalized.includes('fun') || normalized.includes('entertain') || normalized.includes('meme')) {
    return '娱乐分析'
  }

  return normalized
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export function formatAnalysisTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function toDisplayResult(result: AiAnalysisResult): LocalAiAnalysisDisplayResult {
  return {
    ...result,
    analysisTypeLabel: formatAnalysisType(result.analysisType),
    createdAtLabel: formatAnalysisTime(result.createdAt),
    output: parseAiAnalysisOutput(result.outputJson)
  }
}

function readReportFromResult(
  result?: Pick<AiAnalysisResult, 'outputJson'> | LocalAiAnalysisDisplayResult | null
): Record<string, unknown> {
  if (!result) {
    return {}
  }

  const parsed = parseCoachSummaryReportOutput(result.outputJson)
  return parsed.report && isRecord(parsed.report) ? parsed.report : {}
}

function normalizeParsedOutput(parsed: unknown): ParsedAiAnalysisOutput {
  if (typeof parsed === 'string') {
    return {
      status: 'parsed',
      title: null,
      summary: truncate(parsed),
      highlights: []
    }
  }

  if (!isRecord(parsed)) {
    return {
      status: 'parsed',
      title: null,
      summary: truncate(JSON.stringify(parsed)),
      highlights: []
    }
  }

  const title = getStringField(parsed, 'title')
  const summary = getStringField(parsed, 'summary')
  const verdict = getStringField(parsed, 'verdict')
  const sectionHighlights = getSectionHighlights(parsed.sections)
  const highlights = [verdict, ...sectionHighlights].filter((item): item is string => Boolean(item))

  return {
    status: 'parsed',
    title,
    summary: summary ?? truncate(JSON.stringify(parsed)),
    highlights
  }
}

function getSectionHighlights(sections: unknown): string[] {
  if (!Array.isArray(sections)) {
    return []
  }

  return sections
    .map(sectionToText)
    .filter((item): item is string => Boolean(item))
    .slice(0, 4)
}

function sectionToText(section: unknown): string | null {
  if (typeof section === 'string') {
    return truncate(section)
  }

  if (!isRecord(section)) {
    return null
  }

  const title = getStringField(section, 'title')
  const summary = getStringField(section, 'summary') ?? getStringField(section, 'body')

  if (title && summary) {
    return `${title}: ${summary}`
  }

  return title ?? summary
}

function getStringField(record: Record<string, unknown>, key: string): string | null {
  const value = record[key]
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null
}

function getNestedString(record: Record<string, unknown>, parentKey: string, childKey: string): string | null {
  const parent = record[parentKey]
  return isRecord(parent) ? getStringField(parent, childKey) : null
}

function truncate(value: string): string {
  const compact = value.replace(/\s+/g, ' ').trim()
  if (compact.length <= SUMMARY_LIMIT) {
    return compact
  }

  return `${compact.slice(0, SUMMARY_LIMIT - 3)}...`
}

function truncateHeadline(value: string): string {
  const compact = value.replace(/\s+/g, ' ').trim()
  if (!compact) {
    return ''
  }
  if (compact.length <= COACH_HEADLINE_LIMIT) {
    return compact
  }
  return `${compact.slice(0, COACH_HEADLINE_LIMIT - 3)}...`
}

function normalizeVerdict(record: Record<string, unknown>): CoachSummaryReportV1['verdict'] {
  return {
    label: getStringField(record, 'label') || '',
    score: finiteNumber(record.score) ?? 0,
    confidence: normalizeConfidence(record.confidence),
    summary: getStringField(record, 'summary') || ''
  }
}

function normalizeMetadata(record: Record<string, unknown>): CoachSummaryReportV1['metadata'] {
  return {
    modelName: getStringField(record, 'modelName') || '',
    promptVersion: getStringField(record, 'promptVersion') || '',
    generatedAt: getStringField(record, 'generatedAt') || '',
    snapshotSchemaVersion: getStringField(record, 'snapshotSchemaVersion') || '',
    dataQualityConfidence: normalizeConfidence(record.dataQualityConfidence)
  }
}

function normalizeConfidence(value: unknown): 'high' | 'medium' | 'low' {
  return value === 'high' || value === 'low' ? value : 'medium'
}

function normalizeRecordArray<T extends Record<string, unknown>>(value: unknown): T[] {
  return Array.isArray(value) ? value.filter(isRecord) as T[] : []
}

function normalizeOverview(value: unknown): CoachSummaryReportV1['overview'] | undefined {
  if (!isRecord(value)) {
    return undefined
  }

  const totalMatches = finiteNumber(value.totalMatches)
  const summary = getStringField(value, 'summary')
  if (totalMatches === null || !summary) {
    return undefined
  }

  const overview: CoachSummaryReportV1['overview'] = {
    totalMatches,
    summary
  }
  const wins = finiteNumber(value.wins)
  const losses = finiteNumber(value.losses)
  const winRate = finiteNumber(value.winRate)
  const primaryRoles = normalizePrimaryRoles(value.primaryRoles)
  const heroStats = normalizeHeroStats(value.heroStats)
  const roleStats = normalizeRoleStats(value.roleStats)

  if (wins !== null) {
    overview.wins = wins
  }
  if (losses !== null) {
    overview.losses = losses
  }
  if (winRate !== null) {
    overview.winRate = winRate
  }
  if (primaryRoles.length) {
    overview.primaryRoles = primaryRoles
  }
  if (heroStats.length) {
    overview.heroStats = heroStats
  }
  if (roleStats.length) {
    overview.roleStats = roleStats
  }

  return overview
}

function normalizePrimaryRoles(value: unknown): NonNullable<CoachSummaryReportV1['overview']>['primaryRoles'] {
  if (!Array.isArray(value)) {
    return []
  }
  return value.flatMap((item) => {
    if (!isRecord(item)) {
      return []
    }
    const role = getStringField(item, 'role')
    const count = finiteNumber(item.count)
    return role && count !== null ? [{ role, count }] : []
  })
}

function normalizeHeroStats(value: unknown): NonNullable<CoachSummaryReportV1['overview']>['heroStats'] {
  if (!Array.isArray(value)) {
    return []
  }
  return value.flatMap((item) => {
    if (!isRecord(item)) {
      return []
    }
    const championDisplayName = getStringField(item, 'championDisplayName')
    const role = getStringField(item, 'role')
    const games = finiteNumber(item.games)
    if (!championDisplayName || !role || games === null) {
      return []
    }
    const hero = {
      championDisplayName,
      role,
      games
    } as NonNullable<NonNullable<CoachSummaryReportV1['overview']>['heroStats']>[number]
    copyOptionalNumber(item, hero, 'championId')
    copyOptionalNumber(item, hero, 'wins')
    copyOptionalNumber(item, hero, 'losses')
    copyOptionalNumber(item, hero, 'winRate')
    copyOptionalNumber(item, hero, 'averageKda')
    copyOptionalString(item, hero, 'championCanonicalName')
    copyOptionalString(item, hero, 'kda')
    copyOptionalString(item, hero, 'summary')
    return [hero]
  })
}

function normalizeRoleStats(value: unknown): NonNullable<CoachSummaryReportV1['overview']>['roleStats'] {
  if (!Array.isArray(value)) {
    return []
  }
  return value.flatMap((item) => {
    if (!isRecord(item)) {
      return []
    }
    const role = getStringField(item, 'role')
    const games = finiteNumber(item.games)
    if (!role || games === null) {
      return []
    }
    const roleStat = { role, games } as NonNullable<NonNullable<CoachSummaryReportV1['overview']>['roleStats']>[number]
    copyOptionalNumber(item, roleStat, 'wins')
    copyOptionalNumber(item, roleStat, 'losses')
    copyOptionalNumber(item, roleStat, 'winRate')
    return [roleStat]
  })
}

function normalizeCoachChartBlock(block: unknown): NormalizedCoachSummaryChartBlock | null {
  if (!isRecord(block)) {
    return null
  }

  const id = getStringField(block, 'id')
  const title = getStringField(block, 'title')
  if (!id || !title) {
    return null
  }

  const rawKind = getStringField(block, 'kind')
  const rawType = getStringField(block, 'type')
  const kind = normalizeChartKind(rawKind, rawType)
  if (!kind) {
    return null
  }

  const rawPlacement = getStringField(block, 'placement')
  const placement = normalizeChartPlacement(rawPlacement, rawType)
  if (!placement) {
    return null
  }

  const normalized: NormalizedCoachSummaryChartBlock = {
    id,
    title,
    kind,
    placement,
    evidenceRefs: normalizeStringArray(block.evidenceRefs)
  }

  copyOptionalString(block, normalized, 'type')
  copyOptionalString(block, normalized, 'dataRef')
  copyOptionalString(block, normalized, 'xKey')
  copyOptionalStringArray(block, normalized, 'yKeys')
  copyOptionalString(block, normalized, 'labelKey')
  copyOptionalString(block, normalized, 'valueKey')
  copyOptionalString(block, normalized, 'intent')
  copyOptionalString(block, normalized, 'interpretation')
  copyOptionalString(block, normalized, 'description')
  copyOptionalString(block, normalized, 'highlight')

  const data = normalizeChartData(block.data)
  if (data.length) {
    normalized.data = data
  }

  return normalized
}

function normalizeChartKind(rawKind: string | null, rawType: string | null): CoachSummaryChartKind | null {
  if (rawKind && COACH_CHART_KINDS.has(rawKind as CoachSummaryChartKind)) {
    return rawKind as CoachSummaryChartKind
  }
  if (rawType) {
    return LEGACY_CHART_KIND_MAP[rawType] || null
  }
  return null
}

function normalizeChartPlacement(
  rawPlacement: string | null,
  rawType: string | null
): CoachSummaryChartPlacement | null {
  if (rawPlacement && COACH_CHART_PLACEMENTS.has(rawPlacement as CoachSummaryChartPlacement)) {
    return rawPlacement as CoachSummaryChartPlacement
  }
  return rawType ? 'analysis' : null
}

function normalizeChartData(value: unknown): CoachSummaryChartDatum[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value
    .filter(isRecord)
    .map((row) => {
      const entries = Object.entries(row).filter((entry): entry is [string, string | number | boolean | null] => (
        isChartValue(entry[1])
      ))
      return Object.fromEntries(entries)
    })
    .filter(row => Object.keys(row).length > 0)
    .slice(0, 20)
}

function isChartValue(value: unknown): value is string | number | boolean | null {
  return value === null ||
    typeof value === 'string' ||
    typeof value === 'boolean' ||
    (typeof value === 'number' && Number.isFinite(value))
}

function normalizeStringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0).map(item => item.trim())
    : []
}

function copyOptionalString<T extends Record<string, unknown>>(source: Record<string, unknown>, target: T, key: string) {
  const value = getStringField(source, key)
  if (value) {
    target[key as keyof T] = value as T[keyof T]
  }
}

function copyOptionalStringArray<T extends Record<string, unknown>>(
  source: Record<string, unknown>,
  target: T,
  key: string
) {
  const value = normalizeStringArray(source[key])
  if (value.length) {
    target[key as keyof T] = value as T[keyof T]
  }
}

function copyOptionalNumber<T extends Record<string, unknown>>(source: Record<string, unknown>, target: T, key: string) {
  const value = finiteNumber(source[key])
  if (value !== null) {
    target[key as keyof T] = value as T[keyof T]
  }
}

function finiteNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function getRendererDatabase(): AiAnalysisDatabase | null {
  if (typeof window === 'undefined') {
    return null
  }

  return window.electronAPI?.database ?? null
}
