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
            rs.getBigDecimal("avg_gold"),
            rs.getBigDecimal("avg_damage"),
            rs.getBigDecimal("avg_damage_taken"),
            rs.getBigDecimal("avg_heal"),
            rs.getObject("avg_duration_seconds", Integer.class),
            rs.getBigDecimal("avg_kills"),
            rs.getBigDecimal("avg_assists"),
            rs.getBigDecimal("avg_damage_share"),
            rs.getBigDecimal("avg_damage_taken_share"),
            rs.getInt("rank_index"),
            rs.getString("sample_note"),
            rs.getString("data_source_note")
    );

    public CnMetaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CnChampionMeta saveMockSnapshot(String patchKey, Integer championId, String role) {
        List<CnChampionMeta> existing = findChampionMetaByRole(patchKey, championId, role, MOCK_TIER_SCOPE);
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
        List<CnChampionMeta> exact = findChampionMetaByRole(patchKey, championId, role, tierScope);
        if (!exact.isEmpty() || CnMetaRoles.ALL.equalsIgnoreCase(role)) {
            return exact;
        }
        return findChampionMetaByRole(patchKey, championId, CnMetaRoles.ALL, tierScope);
    }

    public List<CnChampionMeta> findLatestChampionMeta(Integer championId, String tierScope) {
        return jdbcTemplate.query(
                """
                        select s.source, s.patch_key, s.queue_id, s.tier_scope,
                               c.champion_id, c.role, c.win_rate, c.pick_rate, c.ban_rate,
                               c.avg_kda, c.avg_gold, c.avg_damage, c.avg_damage_taken,
                               c.avg_heal, c.avg_duration_seconds, c.avg_kills, c.avg_assists,
                               c.avg_damage_share, c.avg_damage_taken_share,
                               c.rank_index, c.sample_note, c.data_source_note
                        from cn_champion_stats c
                        join cn_meta_snapshots s on s.id = c.snapshot_id
                        where c.champion_id = ? and c.tier_scope = ? and s.status = 'ACTIVE'
                        order by s.captured_at desc, c.id desc
                        limit 1
                        """,
                metaMapper,
                championId,
                tierScope
        );
    }

    private List<CnChampionMeta> findChampionMetaByRole(String patchKey, Integer championId, String role, String tierScope) {
        return jdbcTemplate.query(
                """
                        select s.source, s.patch_key, s.queue_id, s.tier_scope,
                               c.champion_id, c.role, c.win_rate, c.pick_rate, c.ban_rate,
                               c.avg_kda, c.avg_gold, c.avg_damage, c.avg_damage_taken,
                               c.avg_heal, c.avg_duration_seconds, c.avg_kills, c.avg_assists,
                               c.avg_damage_share, c.avg_damage_taken_share,
                               c.rank_index, c.sample_note, c.data_source_note
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
