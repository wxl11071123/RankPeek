import type {
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
