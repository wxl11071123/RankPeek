import type { LocalStorageRetentionResult, SqliteDatabase } from '../types.ts'

const MATCH_RECORDS_PER_ACCOUNT_LIMIT = 500

export function runStorageRetention(connection: SqliteDatabase): LocalStorageRetentionResult {
  const runRetention = connection.transaction(() => {
    const matchRecordsDeleted = connection.prepare(`
      DELETE FROM match_records
      WHERE id IN (
        SELECT id
        FROM (
          SELECT
            id,
            ROW_NUMBER() OVER (
              PARTITION BY account_puuid
              ORDER BY COALESCE(game_creation, 0) DESC, id DESC
            ) AS row_number
          FROM match_records
        )
        WHERE row_number > @limit
      )
    `).run({ limit: MATCH_RECORDS_PER_ACCOUNT_LIMIT }).changes

    const matchDetailsDeleted = connection.prepare(`
      DELETE FROM match_details
      WHERE NOT EXISTS (
        SELECT 1
        FROM match_records
        WHERE match_records.region = match_details.region
          AND match_records.match_id = match_details.match_id
      )
      AND NOT EXISTS (
        SELECT 1
        FROM ai_analysis_results
        WHERE ai_analysis_results.match_id = match_details.match_id
      )
    `).run().changes

    const retained = connection.prepare(`
      SELECT COUNT(*) AS count
      FROM match_records
    `).get() as { count: number }

    return {
      matchRecordsDeleted,
      matchDetailsDeleted,
      aiAnalysisDeleted: 0,
      matchRecordsRetained: retained.count
    }
  })

  return runRetention()
}
