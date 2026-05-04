import type {
  AiAnalysisListOptions,
  AiAnalysisResult,
  LocalDatabaseAPI
} from '../types/localDatabase'

const INVALID_OUTPUT_SUMMARY = '无法解析的分析结果'
const SUMMARY_LIMIT = 160

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

function truncate(value: string): string {
  const compact = value.replace(/\s+/g, ' ').trim()
  if (compact.length <= SUMMARY_LIMIT) {
    return compact
  }

  return `${compact.slice(0, SUMMARY_LIMIT - 3)}...`
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
