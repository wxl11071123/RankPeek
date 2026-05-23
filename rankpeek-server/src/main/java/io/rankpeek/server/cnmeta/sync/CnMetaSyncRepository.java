package io.rankpeek.server.cnmeta.sync;

import io.rankpeek.server.common.JdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class CnMetaSyncRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<CnMetaSyncJob> jobMapper = (rs, rowNum) -> new CnMetaSyncJob(
            rs.getLong("id"),
            rs.getString("source"),
            rs.getString("patch_key"),
            rs.getInt("queue_id"),
            rs.getString("tier_scope"),
            rs.getString("role"),
            rs.getString("status"),
            instantOrNull(rs.getTimestamp("started_at")),
            instantOrNull(rs.getTimestamp("finished_at")),
            rs.getString("error_message"),
            rs.getInt("request_count"),
            rs.getInt("row_count"),
            rs.getString("content_hash"),
            instantOrNull(rs.getTimestamp("created_at"))
    );

    private final RowMapper<CnMetaSourceDocument> documentMapper = (rs, rowNum) -> new CnMetaSourceDocument(
            rs.getLong("id"),
            rs.getLong("sync_job_id"),
            rs.getString("source"),
            rs.getString("source_url"),
            rs.getString("request_key"),
            rs.getObject("http_status", Integer.class),
            rs.getString("raw_content"),
            rs.getString("content_hash"),
            instantOrNull(rs.getTimestamp("fetched_at")),
            instantOrNull(rs.getTimestamp("created_at"))
    );

    public CnMetaSyncRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CnMetaSyncJob createJob(String source, String patchKey, Integer queueId, String tierScope, String role, Instant startedAt) {
        KeyHolder keyHolder = JdbcSupport.newKeyHolder();
        jdbcTemplate.update(connection -> JdbcSupport.prepareInsert(
                connection,
                """
                        insert into cn_meta_sync_jobs (
                            source, patch_key, queue_id, tier_scope, role, status, started_at, created_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                source,
                patchKey,
                queueId,
                tierScope,
                role,
                "RUNNING",
                Timestamp.from(startedAt),
                Timestamp.from(startedAt)
        ), keyHolder);
        return findJobById(JdbcSupport.requireGeneratedId(keyHolder)).orElseThrow();
    }

    public CnMetaSourceDocument insertSourceDocument(Long syncJobId, CnMetaSourcePayload payload, String contentHash, Instant fetchedAt) {
        KeyHolder keyHolder = JdbcSupport.newKeyHolder();
        jdbcTemplate.update(connection -> JdbcSupport.prepareInsert(
                connection,
                """
                        insert into cn_meta_source_documents (
                            sync_job_id, source, source_url, request_key, http_status,
                            raw_content, content_hash, fetched_at, created_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                syncJobId,
                payload.source(),
                payload.sourceUrl(),
                payload.requestKey(),
                payload.httpStatus(),
                payload.rawContent(),
                contentHash,
                Timestamp.from(fetchedAt),
                Timestamp.from(fetchedAt)
        ), keyHolder);
        return findSourceDocumentById(JdbcSupport.requireGeneratedId(keyHolder)).orElseThrow();
    }

    public boolean hasSuccessfulSnapshot(
            String source,
            String patchKey,
            Integer queueId,
            String tierScope,
            String role,
            String contentHash
    ) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from cn_meta_snapshots
                        where source = ? and patch_key = ? and queue_id = ? and tier_scope = ?
                          and role = ? and content_hash = ? and status = 'ACTIVE'
                        """,
                Integer.class,
                source,
                patchKey,
                queueId,
                tierScope,
                role,
                contentHash
        );
        return count != null && count > 0;
    }

    public Long insertSnapshot(
            CnMetaSourcePayload payload,
            String patchKey,
            Integer queueId,
            String tierScope,
            String role,
            String contentHash,
            Instant capturedAt
    ) {
        KeyHolder keyHolder = JdbcSupport.newKeyHolder();
        jdbcTemplate.update(connection -> JdbcSupport.prepareInsert(
                connection,
                """
                        insert into cn_meta_snapshots (
                            source, patch_key, queue_id, tier_scope, role, data_date,
                            captured_at, source_url, content_hash, status
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                payload.source(),
                patchKey,
                queueId,
                tierScope,
                role,
                Date.valueOf(payload.dataDate()),
                Timestamp.from(capturedAt),
                payload.sourceUrl(),
                contentHash,
                "ACTIVE"
        ), keyHolder);
        return JdbcSupport.requireGeneratedId(keyHolder);
    }

    public void insertChampionStats(Long snapshotId, List<CnMetaChampionStatRow> rows) {
        for (CnMetaChampionStatRow row : rows) {
            jdbcTemplate.update(
                    """
                            insert into cn_champion_stats (
                                snapshot_id, champion_id, role, tier_scope, win_rate, pick_rate, ban_rate,
                                avg_kda, avg_gold, avg_damage_share, avg_damage_taken_share, rank_index, sample_note,
                                avg_damage, avg_damage_taken, avg_heal, avg_duration_seconds,
                                avg_kills, avg_assists, data_source_note
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    snapshotId,
                    row.championId(),
                    row.role(),
                    row.tierScope(),
                    row.winRate(),
                    row.pickRate(),
                    row.banRate(),
                    row.avgKda(),
                    row.avgGold(),
                    row.avgDamageShare(),
                    row.avgDamageTakenShare(),
                    row.rankIndex(),
                    row.sampleNote(),
                    row.avgDamage(),
                    row.avgDamageTaken(),
                    row.avgHeal(),
                    row.avgDurationSeconds(),
                    row.avgKills(),
                    row.avgAssists(),
                    row.dataSourceNote()
            );
        }
    }

    public void deleteSupersededMeta(
            String source,
            Integer queueId,
            String tierScope,
            String role,
            Long keepSnapshotId,
            Long keepJobId
    ) {
        jdbcTemplate.update(
                """
                        delete from cn_meta_source_documents
                        where source = ?
                          and sync_job_id in (
                              select id
                              from cn_meta_sync_jobs
                              where source = ? and queue_id = ? and tier_scope = ? and role = ? and id <> ?
                          )
                        """,
                source,
                source,
                queueId,
                tierScope,
                role,
                keepJobId
        );
        jdbcTemplate.update(
                """
                        delete from cn_champion_stats
                        where snapshot_id in (
                            select id
                            from cn_meta_snapshots
                            where source = ? and queue_id = ? and tier_scope = ? and role = ? and id <> ?
                        )
                        """,
                source,
                queueId,
                tierScope,
                role,
                keepSnapshotId
        );
        jdbcTemplate.update(
                """
                        delete from cn_meta_snapshots
                        where source = ? and queue_id = ? and tier_scope = ? and role = ? and id <> ?
                        """,
                source,
                queueId,
                tierScope,
                role,
                keepSnapshotId
        );
    }

    public CnMetaSyncJob updateJobFinished(
            Long jobId,
            String status,
            Integer requestCount,
            Integer rowCount,
            String contentHash,
            String errorMessage,
            Instant finishedAt
    ) {
        jdbcTemplate.update(
                """
                        update cn_meta_sync_jobs
                        set status = ?, request_count = ?, row_count = ?, content_hash = ?,
                            error_message = ?, finished_at = ?
                        where id = ?
                        """,
                status,
                requestCount,
                rowCount,
                contentHash,
                errorMessage,
                Timestamp.from(finishedAt),
                jobId
        );
        return findJobById(jobId).orElseThrow();
    }

    public List<CnMetaSyncJob> findRecentJobs(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query(
                """
                        select *
                        from cn_meta_sync_jobs
                        order by started_at desc, id desc
                        limit %d
                        """.formatted(safeLimit),
                jobMapper
        );
    }

    public java.util.Optional<CnMetaSyncJob> findJobById(Long id) {
        return jdbcTemplate.query(
                "select * from cn_meta_sync_jobs where id = ?",
                jobMapper,
                id
        ).stream().findFirst();
    }

    public java.util.Optional<CnMetaSourceDocument> findSourceDocumentById(Long id) {
        return jdbcTemplate.query(
                "select * from cn_meta_source_documents where id = ?",
                documentMapper,
                id
        ).stream().findFirst();
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
