package io.rankpeek.server.cnmeta;

import io.rankpeek.server.common.JdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public class CnMetaRepository {

    private static final Integer MOCK_QUEUE_ID = 420;
    private static final String MOCK_TIER_SCOPE = "PLATINUM_PLUS";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<CnChampionMeta> metaMapper = (rs, rowNum) -> new CnChampionMeta(
            rs.getString("source"),
            rs.getString("patch_key"),
            rs.getInt("queue_id"),
            rs.getString("tier_scope"),
            rs.getInt("champion_id"),
            rs.getString("role"),
            rs.getBigDecimal("win_rate"),
            rs.getBigDecimal("pick_rate"),
            rs.getBigDecimal("ban_rate"),
            rs.getBigDecimal("avg_kda"),
            rs.getInt("rank_index"),
            rs.getString("sample_note")
    );

    public CnMetaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CnChampionMeta saveMockSnapshot(String patchKey, Integer championId, String role) {
        List<CnChampionMeta> existing = findChampionMeta(patchKey, championId, role, MOCK_TIER_SCOPE);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        Instant capturedAt = Instant.parse("2026-05-05T00:00:00Z");
        KeyHolder keyHolder = JdbcSupport.newKeyHolder();
        jdbcTemplate.update(connection -> JdbcSupport.prepareInsert(
                connection,
                """
                        insert into cn_meta_snapshots (
                            source, patch_key, queue_id, tier_scope, role, data_date,
                            captured_at, source_url, content_hash, status
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "mock-101",
                patchKey,
                MOCK_QUEUE_ID,
                MOCK_TIER_SCOPE,
                role,
                Date.valueOf(LocalDate.of(2026, 5, 5)),
                Timestamp.from(capturedAt),
                "mock://101-meta/" + patchKey,
                "mock-cn-meta-" + patchKey + "-" + championId + "-" + role,
                "MOCK"
        ), keyHolder);
        Long snapshotId = JdbcSupport.requireGeneratedId(keyHolder);
        jdbcTemplate.update(
                """
                        insert into cn_champion_stats (
                            snapshot_id, champion_id, role, tier_scope, win_rate, pick_rate, ban_rate,
                            avg_kda, avg_gold, avg_damage_share, avg_damage_taken_share, rank_index, sample_note
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                snapshotId,
                championId,
                role,
                MOCK_TIER_SCOPE,
                "0.5123",
                "0.1475",
                "0.0830",
                "3.25",
                "12100.00",
                "0.2840",
                "0.1920",
                8,
                "deterministic mock 101 snapshot"
        );
        return findChampionMeta(patchKey, championId, role, MOCK_TIER_SCOPE).getFirst();
    }

    public List<CnChampionMeta> findChampionMeta(String patchKey, Integer championId, String role, String tierScope) {
        return jdbcTemplate.query(
                """
                        select s.source, s.patch_key, s.queue_id, s.tier_scope,
                               c.champion_id, c.role, c.win_rate, c.pick_rate, c.ban_rate,
                               c.avg_kda, c.rank_index, c.sample_note
                        from cn_champion_stats c
                        join cn_meta_snapshots s on s.id = c.snapshot_id
                        where s.patch_key = ? and c.champion_id = ? and c.role = ? and c.tier_scope = ?
                        order by s.captured_at desc, c.id desc
                        """,
                metaMapper,
                patchKey,
                championId,
                role,
                tierScope
        );
    }
}
