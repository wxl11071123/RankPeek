package io.rankpeek.server.patch;

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
import java.util.Optional;

@Repository
public class PatchRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<PatchVersion> patchMapper = (rs, rowNum) -> new PatchVersion(
            rs.getLong("id"),
            rs.getString("patch_key"),
            rs.getString("ddragon_version"),
            rs.getString("game_version"),
            dateOrNull(rs.getDate("release_date")),
            rs.getString("source_status"),
            instantOrNull(rs.getTimestamp("detected_at")),
            instantOrNull(rs.getTimestamp("published_at")),
            rs.getString("checksum")
    );

    private final RowMapper<PatchChange> changeMapper = (rs, rowNum) -> new PatchChange(
            rs.getLong("id"),
            rs.getString("patch_key"),
            rs.getString("target_type"),
            rs.getString("target_key"),
            rs.getString("target_name"),
            rs.getString("change_type"),
            rs.getString("field"),
            rs.getString("before_value"),
            rs.getString("after_value"),
            rs.getString("summary_zh"),
            rs.getString("summary_en"),
            rs.getBigDecimal("confidence"),
            instantOrNull(rs.getTimestamp("created_at"))
    );

    public PatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PatchVersion> findByPatchKey(String patchKey) {
        List<PatchVersion> rows = jdbcTemplate.query(
                "select * from patch_versions where patch_key = ?",
                patchMapper,
                patchKey
        );
        return rows.stream().findFirst();
    }

    public Optional<PatchVersion> findCurrent() {
        List<PatchVersion> rows = jdbcTemplate.query(
                """
                        select * from patch_versions
                        order by coalesce(published_at, detected_at) desc, id desc
                        limit 1
                        """,
                patchMapper
        );
        return rows.stream().findFirst();
    }

    public PatchVersion insertMockPatch(String patchKey) {
        Instant detectedAt = Instant.parse("2026-05-05T00:00:00Z");
        KeyHolder keyHolder = JdbcSupport.newKeyHolder();
        jdbcTemplate.update(connection -> JdbcSupport.prepareInsert(
                connection,
                """
                        insert into patch_versions (
                            patch_key, ddragon_version, game_version, release_date, source_status,
                            detected_at, published_at, checksum
                        ) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                patchKey,
                patchKey + ".mock-ddragon",
                patchKey + ".mock-game",
                Date.valueOf(LocalDate.of(2026, 5, 5)),
                "MOCK",
                Timestamp.from(detectedAt),
                Timestamp.from(detectedAt),
                "mock-checksum-" + patchKey
        ), keyHolder);
        Long id = JdbcSupport.requireGeneratedId(keyHolder);
        return findById(id).orElseThrow();
    }

    public Optional<PatchVersion> findById(Long id) {
        List<PatchVersion> rows = jdbcTemplate.query(
                "select * from patch_versions where id = ?",
                patchMapper,
                id
        );
        return rows.stream().findFirst();
    }

    public void ensureMockChange(PatchVersion patchVersion) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*) from patch_changes
                        where patch_version_id = ? and target_type = 'CHAMPION' and target_key = '81'
                        """,
                Integer.class,
                patchVersion.id()
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
                """
                        insert into patch_changes (
                            patch_version_id, target_type, target_key, target_name, change_type,
                            field, before_value, after_value, summary_zh, summary_en, confidence
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                patchVersion.id(),
                "CHAMPION",
                "81",
                "Ezreal",
                "MOCK_BALANCE",
                "q_damage",
                "100",
                "105",
                "Mock patch change for champion 81.",
                "Mock balance update for champion 81 on patch " + patchVersion.patchKey() + ".",
                "0.9500"
        );
    }

    public List<PatchChange> findChanges(String patchKey) {
        return jdbcTemplate.query(
                """
                        select pc.*, pv.patch_key
                        from patch_changes pc
                        join patch_versions pv on pv.id = pc.patch_version_id
                        where pv.patch_key = ?
                        order by pc.id
                        """,
                changeMapper,
                patchKey
        );
    }

    public boolean hasChampionChange(String patchKey, Integer championId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from patch_changes pc
                        join patch_versions pv on pv.id = pc.patch_version_id
                        where pv.patch_key = ? and pc.target_type = 'CHAMPION' and pc.target_key = ?
                        """,
                Integer.class,
                patchKey,
                String.valueOf(championId)
        );
        return count != null && count > 0;
    }

    private static LocalDate dateOrNull(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
