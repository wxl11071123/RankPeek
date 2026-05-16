package io.rankpeek.server.cnmeta.sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "rankpeek.cn-meta.sync.request-delay-ms=0")
@ActiveProfiles("test")
class CnMetaSyncServiceTest {

    @Autowired
    private CnMetaSyncService syncService;

    @Autowired
    private CnMetaSyncRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void syncOnceWithMockSourceWritesJobDocumentSnapshotAndChampionStats() {
        CnMetaSyncResult result = syncService.syncOnce("26.31", 420, "GOLD", "MID");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.source()).isEqualTo("mock-101");
        assertThat(result.rowCount()).isEqualTo(3);
        assertThat(result.requestCount()).isEqualTo(1);
        assertThat(result.contentHash()).isNotBlank();

        assertThat(count("select count(*) from cn_meta_sync_jobs where id = ?", result.jobId())).isEqualTo(1);
        assertThat(count("select count(*) from cn_meta_source_documents where sync_job_id = ?", result.jobId())).isEqualTo(1);
        assertThat(count("select count(*) from cn_meta_snapshots where content_hash = ?", result.contentHash())).isEqualTo(1);
        assertThat(count("""
                select count(*)
                from cn_champion_stats c
                join cn_meta_snapshots s on s.id = c.snapshot_id
                where s.content_hash = ?
                """, result.contentHash())).isEqualTo(3);
    }

    @Test
    void real101PayloadWritesAverageFieldsAndStoresAggregateRoleAll() {
        CnMetaSyncResult result = syncService.syncOnceWithSource("test-real-101", "26.41", 420, "GOLD", "TOP");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.source()).isEqualTo("test-real-101");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                        select s.source, s.role as snapshot_role, c.role as stat_role,
                               c.avg_damage, c.avg_damage_taken, c.avg_heal, c.avg_duration_seconds,
                               c.avg_kills, c.avg_assists, c.data_source_note
                        from cn_champion_stats c
                        join cn_meta_snapshots s on s.id = c.snapshot_id
                        where s.content_hash = ?
                        """,
                result.contentHash()
        );

        assertThat(row.get("source")).isEqualTo("real-101");
        assertThat(row.get("snapshot_role")).isEqualTo("ALL");
        assertThat(row.get("stat_role")).isEqualTo("ALL");
        assertThat((BigDecimal) row.get("avg_damage")).isEqualByComparingTo("8945");
        assertThat((BigDecimal) row.get("avg_damage_taken")).isEqualByComparingTo("9126");
        assertThat((BigDecimal) row.get("avg_heal")).isEqualByComparingTo("7979");
        assertThat(row.get("avg_duration_seconds")).isEqualTo(1634);
        assertThat((BigDecimal) row.get("avg_kills")).isEqualByComparingTo("2");
        assertThat((BigDecimal) row.get("avg_assists")).isEqualByComparingTo("18");
        assertThat(row.get("data_source_note"))
                .isEqualTo("101 getRankFieldAverage aggregate; role=ALL; not lane-specific");
    }

    @Test
    void duplicateContentHashSkipsNewSnapshotAndChampionStats() {
        CnMetaSyncResult first = syncService.syncOnce("26.32", 420, "GOLD", "ADC");
        CnMetaSyncResult second = syncService.syncOnce("26.32", 420, "GOLD", "ADC");

        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(second.status()).isEqualTo("SUCCESS_NO_CHANGE");
        assertThat(second.rowCount()).isZero();
        assertThat(second.contentHash()).isEqualTo(first.contentHash());
        assertThat(count("""
                select count(*)
                from cn_meta_snapshots
                where source = ? and patch_key = ? and queue_id = ? and tier_scope = ? and role = ? and content_hash = ?
                """, "mock-101", "26.32", 420, "GOLD", "ADC", first.contentHash())).isEqualTo(1);
        assertThat(count("""
                select count(*)
                from cn_champion_stats c
                join cn_meta_snapshots s on s.id = c.snapshot_id
                where s.patch_key = ? and s.queue_id = ? and s.tier_scope = ? and s.role = ?
                """, "26.32", 420, "GOLD", "ADC")).isEqualTo(3);
    }

    @Test
    void failedSourceUpdatesJobAsFailedWithoutServerCrash() {
        CnMetaSyncResult result = syncService.syncOnceWithSource("real", "26.33", 420, "GOLD", "TOP");

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.rowCount()).isZero();
        assertThat(result.errorMessage()).contains("disabled");
        assertThat(queryString("select status from cn_meta_sync_jobs where id = ?", result.jobId()))
                .isEqualTo("FAILED");
        assertThat(count("select count(*) from cn_meta_sync_jobs where id = ? and status = 'RUNNING'", result.jobId()))
                .isZero();
    }

    @Test
    void stopSignalUpdatesJobAsStopped() {
        CnMetaSyncResult result = syncService.syncOnceWithSource("test-429", "26.38", 420, "GOLD", "TOP");

        assertThat(result.status()).isEqualTo("STOPPED");
        assertThat(result.requestCount()).isEqualTo(1);
        assertThat(result.errorMessage()).contains("429");
        assertThat(queryString("select status from cn_meta_sync_jobs where id = ?", result.jobId()))
                .isEqualTo("STOPPED");
        assertThat(count("select count(*) from cn_meta_sync_jobs where id = ? and status = 'RUNNING'", result.jobId()))
                .isZero();
    }

    @Test
    void riskControlStopSignalUpdatesJobAsStopped() {
        CnMetaSyncResult result = syncService.syncOnceWithSource("test-risk", "26.39", 420, "GOLD", "TOP");

        assertThat(result.status()).isEqualTo("STOPPED");
        assertThat(result.requestCount()).isEqualTo(1);
        assertThat(result.errorMessage()).contains("risk control");
        assertThat(count("select count(*) from cn_meta_sync_jobs where id = ? and status = 'RUNNING'", result.jobId()))
                .isZero();
    }

    @Test
    void stopSignalInterruptsConfiguredMatrix() {
        CnMetaSyncProperties stopProperties = new CnMetaSyncProperties(
                false,
                false,
                "test-429",
                "0 30 4 * * *",
                "Asia/Shanghai",
                0,
                2,
                java.util.List.of(401, 403, 429),
                420,
                java.util.List.of("GOLD", "PLATINUM"),
                java.util.List.of("MID"),
                false,
                "",
                1,
                666,
                1,
                Map.of("PLATINUM", "20"),
                "RankPeek/dev-public-aggregate-client",
                500,
                2000,
                20000
        );
        CnMetaSyncService stopService = new CnMetaSyncService(
                stopProperties,
                repository,
                java.util.List.of(new CnMetaSourceClient() {
                    @Override
                    public String source() {
                        return "test-429";
                    }

                    @Override
                    public CnMetaSourcePayload fetchChampionStats(String patchKey, Integer queueId, String tierScope, String role) {
                        throw new CnMetaSourceException("HTTP 429 from source", 429);
                    }
                })
        );

        assertThat(stopService.syncConfiguredMatrix("26.40"))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.status()).isEqualTo("STOPPED");
                    assertThat(result.tierScope()).isEqualTo("GOLD");
                    assertThat(result.role()).isEqualTo("MID");
                });
    }

    @Test
    void jobLimitIsCappedAtOneHundred() {
        for (int i = 0; i < 105; i++) {
            syncService.syncOnce("limit-" + i, 420, "GOLD", "MID");
        }

        assertThat(syncService.findRecentJobs(500)).hasSize(100);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String queryString(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    @TestConfiguration
    static class SourceClientTestConfig {

        @Bean
        CnMetaSourceClient http429SourceClient() {
            return new CnMetaSourceClient() {
                @Override
                public String source() {
                    return "test-429";
                }

                @Override
                public CnMetaSourcePayload fetchChampionStats(String patchKey, Integer queueId, String tierScope, String role) {
                    throw new CnMetaSourceException("HTTP 429 from source", 429);
                }
            };
        }

        @Bean
        CnMetaSourceClient riskControlSourceClient() {
            return new CnMetaSourceClient() {
                @Override
                public String source() {
                    return "test-risk";
                }

                @Override
                public CnMetaSourcePayload fetchChampionStats(String patchKey, Integer queueId, String tierScope, String role) {
                    throw CnMetaSourceException.stopSignal("Detected CAPTCHA or risk control page");
                }
            };
        }

        @Bean
        CnMetaSourceClient real101AggregatePayloadSourceClient() {
            return new CnMetaSourceClient() {
                @Override
                public String source() {
                    return "test-real-101";
                }

                @Override
                public CnMetaSourcePayload fetchChampionStats(String patchKey, Integer queueId, String tierScope, String role) {
                    return new CnMetaSourcePayload(
                            "real-101",
                            "mock://real-101/championdetails",
                            "%s|%d|%s|%s".formatted(patchKey, queueId, tierScope, role),
                            200,
                            "championdetails-fixture",
                            LocalDate.of(2026, 5, 14),
                            List.of(new CnMetaChampionStatRow(
                                    666,
                                    role,
                                    tierScope,
                                    null,
                                    new BigDecimal("0.0056"),
                                    new BigDecimal("0.3114"),
                                    new BigDecimal("4.6825"),
                                    new BigDecimal("8296"),
                                    null,
                                    null,
                                    1,
                                    "fixture",
                                    new BigDecimal("8945"),
                                    new BigDecimal("9126"),
                                    new BigDecimal("7979"),
                                    1634,
                                    new BigDecimal("2"),
                                    new BigDecimal("18"),
                                    null
                            ))
                    );
                }
            };
        }
    }
}
