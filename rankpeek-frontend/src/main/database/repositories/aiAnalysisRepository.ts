import type {
  AiMemoryExportPayload,
  AiMemoryStats,
  AiAnalysisListOptions,
  AiAnalysisRepository,
  AiAnalysisResult,
  AiAnalysisResultInput,
  SqliteDatabase
} from '../types.ts'
import { jsonText, normalizedLimit, normalizedOffset, nullableString, nowIso } from './helpers.ts'

interface AiAnalysisResultRow {
  id: number
  account_puuid: string
  match_id: string | null
  analysis_type: string
  subject_key: string | null
  game_version: string | null
  model_name: string | null
  prompt_version: string | null
  input_hash: string | null
  output_json: string
  created_at: string
  updated_at: string
}

interface AiMemoryTypeCountRow {
  analysis_type: string
  count: number
}

interface AiMemorySummaryRow {
  total_count: number
  linked_match_count: number
  earliest_created_at: string | null
  latest_created_at: string | null
}

export function createAiAnalysisRepository(connection: SqliteDatabase): AiAnalysisRepository {
  const insert = connection.prepare(`
    INSERT INTO ai_analysis_results (
      account_puuid,
      match_id,
      analysis_type,
      subject_key,
      game_version,
      model_name,
      prompt_version,
      input_hash,
      output_json,
      created_at,
      updated_at
    ) VALUES (
      @accountPuuid,
      @matchId,
      @analysisType,
      @subjectKey,
      @gameVersion,
      @modelName,
      @promptVersion,
      @inputHash,
      @outputJson,
      @createdAt,
      @updatedAt
    )
    RETURNING *
  `)

  return {
    saveAnalysisResult(result) {
      return mapAiAnalysisResultRow(insert.get(toAiAnalysisParameters(result)) as AiAnalysisResultRow)
    },

    listAnalysisResultsByAccount(accountPuuid, options) {
      return listAnalysisResults(connection, accountPuuid, options)
    },

    getAnalysisResultById(id) {
      const row = connection
        .prepare('SELECT * FROM ai_analysis_results WHERE id = ?')
        .get(id) as AiAnalysisResultRow | undefined

      return row ? mapAiAnalysisResultRow(row) : null
    },

    findAnalysisByInputHash(inputHash) {
      const row = connection
        .prepare(`
          SELECT *
          FROM ai_analysis_results
          WHERE input_hash = ?
          ORDER BY created_at DESC, id DESC
          LIMIT 1
        `)
        .get(inputHash) as AiAnalysisResultRow | undefined

      return row ? mapAiAnalysisResultRow(row) : null
    },

    getMemoryStats(accountPuuid) {
      return getMemoryStats(connection, accountPuuid)
    },

    exportMemory(accountPuuid) {
      return {
        accountPuuid,
        exportedAt: new Date().toISOString(),
        stats: getMemoryStats(connection, accountPuuid),
        records: listAllAnalysisResults(connection, accountPuuid)
      } satisfies AiMemoryExportPayload
    }
  }
}

function listAnalysisResults(
  connection: SqliteDatabase,
  accountPuuid: string,
  options: AiAnalysisListOptions | undefined
) {
  const conditions = ['account_puuid = @accountPuuid']
  const parameters: Record<string, string | number> = {
    accountPuuid,
    limit: normalizedLimit(options?.limit),
    offset: normalizedOffset(options?.offset)
  }

  if (typeof options?.analysisType === 'string' && options.analysisType.length > 0) {
    conditions.push('analysis_type = @analysisType')
    parameters.analysisType = options.analysisType
  }

  if (typeof options?.matchId === 'string' && options.matchId.length > 0) {
    conditions.push('match_id = @matchId')
    parameters.matchId = options.matchId
  }

  const rows = connection
    .prepare(`
      SELECT *
      FROM ai_analysis_results
      WHERE ${conditions.join(' AND ')}
      ORDER BY created_at DESC, id DESC
      LIMIT @limit OFFSET @offset
    `)
    .all(parameters) as AiAnalysisResultRow[]

  return rows.map(mapAiAnalysisResultRow)
}

function listAllAnalysisResults(connection: SqliteDatabase, accountPuuid: string): AiAnalysisResult[] {
  const rows = connection
    .prepare(`
      SELECT *
      FROM ai_analysis_results
      WHERE account_puuid = @accountPuuid
      ORDER BY created_at DESC, id DESC
    `)
    .all({ accountPuuid }) as AiAnalysisResultRow[]

  return rows.map(mapAiAnalysisResultRow)
}

function getMemoryStats(connection: SqliteDatabase, accountPuuid: string): AiMemoryStats {
  const summary = connection
    .prepare(`
      SELECT
        COUNT(*) AS total_count,
        COUNT(DISTINCT CASE WHEN match_id IS NOT NULL AND match_id <> '' THEN match_id END) AS linked_match_count,
        MIN(created_at) AS earliest_created_at,
        MAX(created_at) AS latest_created_at
      FROM ai_analysis_results
      WHERE account_puuid = @accountPuuid
    `)
    .get({ accountPuuid }) as AiMemorySummaryRow

  const typeCounts = connection
    .prepare(`
      SELECT analysis_type, COUNT(*) AS count
      FROM ai_analysis_results
      WHERE account_puuid = @accountPuuid
      GROUP BY analysis_type
      ORDER BY count DESC, analysis_type ASC
    `)
    .all({ accountPuuid }) as AiMemoryTypeCountRow[]

  return {
    accountPuuid,
    totalCount: summary.total_count,
    linkedMatchCount: summary.linked_match_count,
    earliestCreatedAt: summary.earliest_created_at,
    latestCreatedAt: summary.latest_created_at,
    analysisTypeCounts: typeCounts.map((row) => ({
      analysisType: row.analysis_type,
      count: row.count
    }))
  }
}

function toAiAnalysisParameters(result: AiAnalysisResultInput) {
  const timestamp = nowIso()

  return {
    accountPuuid: result.accountPuuid,
    matchId: nullableString(result.matchId),
    analysisType: result.analysisType,
    subjectKey: nullableString(result.subjectKey),
    gameVersion: nullableString(result.gameVersion),
    modelName: nullableString(result.modelName),
    promptVersion: nullableString(result.promptVersion),
    inputHash: nullableString(result.inputHash),
    outputJson: jsonText(result.outputJson),
    createdAt: timestamp,
    updatedAt: timestamp
  }
}

function mapAiAnalysisResultRow(row: AiAnalysisResultRow): AiAnalysisResult {
  return {
    id: row.id,
    accountPuuid: row.account_puuid,
    matchId: row.match_id,
    analysisType: row.analysis_type,
    subjectKey: row.subject_key,
    gameVersion: row.game_version,
    modelName: row.model_name,
    promptVersion: row.prompt_version,
    inputHash: row.input_hash,
    outputJson: row.output_json,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  }
}
