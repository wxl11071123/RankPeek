package io.rankpeek.server.cnmeta.sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CnMetaRepositoryFallbackTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void championMetaFallsBackToAllRoleWhenRequestedRoleHasNoExactRows() throws Exception {
        Long snapshotId = insertSnapshot("26.51", "GOLD", "ALL", "fallback-all-hash");
        insertChampionStat(snapshotId, 666, "GOLD", "ALL", "8945.00", "101 getRankFieldAverage aggregate; role=ALL; not lane-specific");

        mockMvc.perform(get("/api/cn-meta/champions/666")
                        .param("patchKey", "26.51")
                        .param("role", "TOP")
                        .param("tierScope", "GOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].championId").value(666))
                .andExpect(jsonPath("$.data[0].role").value("ALL"))
                .andExpect(jsonPath("$.data[0].avgDamage").value(8945.0))
                .andExpect(jsonPath("$.data[0].dataSourceNote")
                        .value("101 getRankFieldAverage aggregate; role=ALL; not lane-specific"));
    }

    @Test
    void championMetaPrefersExactRoleOverAllFallback() throws Exception {
        Long allSnapshotId = insertSnapshot("26.52", "GOLD", "ALL", "exact-prefers-all-hash");
        insertChampionStat(allSnapshotId, 666, "GOLD", "ALL", "8945.00", "all aggregate");
        Long topSnapshotId = insertSnapshot("26.52", "GOLD", "TOP", "exact-prefers-top-hash");
        insertChampionStat(topSnapshotId, 666, "GOLD", "TOP", "7777.00", "lane-specific fixture");

        mockMvc.perform(get("/api/cn-meta/champions/666")
                        .param("patchKey", "26.52")
                        .param("role", "TOP")
                        .param("tierScope", "GOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].role").value("TOP"))
                .andExpect(jsonPath("$.data[0].avgDamage").value(7777.0))
                .andExpect(jsonPath("$.data[0].dataSourceNote").value("lane-specific fixture"));
    }

    private Long insertSnapshot(String patchKey, String tierScope, String role, String contentHash) {
        jdbcTemplate.update(
                """
                        insert into cn_meta_snapshots (
                            source, patch_key, queue_id, tier_scope, role, data_date,
                            captured_at, source_url, content_hash, status
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "real-101",
                patchKey,
                420,
                tierScope,
                role,
                Date.valueOf(LocalDate.of(2026, 5, 14)),
                Timestamp.from(Instant.parse("2026-05-14T00:00:00Z")),
                "mock://repository-fallback/" + contentHash,
                contentHash,
                "ACTIVE"
        );
        return jdbcTemplate.queryForObject(
                "select id from cn_meta_snapshots where content_hash = ?",
                Long.class,
                contentHash
        );
    }

    private void insertChampionStat(Long snapshotId, Integer championId, String tierScope, String role, String avgDamage, String note) {
        jdbcTemplate.update(
                """
                        insert into cn_champion_stats (
                            snapshot_id, champion_id, role, tier_scope, pick_rate, ban_rate,
                            avg_kda, avg_gold, rank_index, avg_damage, data_source_note
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                snapshotId,
                championId,
                role,
                tierScope,
                "0.0056",
                "0.3114",
                "4.6825",
                "8296.00",
                1,
                avgDamage,
                note
        );
    }
}
