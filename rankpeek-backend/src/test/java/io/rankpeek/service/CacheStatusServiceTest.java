package io.rankpeek.service;

import io.rankpeek.cache.LocalCacheSchemaInitializer;
import io.rankpeek.config.LocalDataPathService;
import io.rankpeek.model.CacheStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheStatusServiceTest {

    @TempDir
    private Path tempDir;

    private JdbcTemplate jdbcTemplate;
    private LocalDataPathService localDataPathService;
    private CacheStatusService service;
    private Path cacheDatabasePath;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:rankpeek-cache-status-" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        new LocalCacheSchemaInitializer(jdbcTemplate).initializeSchema();

        cacheDatabasePath = tempDir.resolve("rankpeek-cache").toAbsolutePath();
        localDataPathService = mock(LocalDataPathService.class);
        when(localDataPathService.getCacheDatabasePath()).thenReturn(cacheDatabasePath);
        service = new CacheStatusService(jdbcTemplate, localDataPathService);
    }

    @Test
    void getStatus_returnsEnabledWithZeroCountsForEmptyDatabase() {
        CacheStatus status = service.getStatus();

        assertThat(status.isEnabled()).isTrue();
        assertThat(status.getDatabasePath()).isEqualTo(cacheDatabasePath.toString());
        assertThat(status.getDatabaseSizeBytes()).isZero();
        assertThat(status.getSummonerCount()).isZero();
        assertThat(status.getRankCount()).isZero();
        assertThat(status.getMatchCount()).isZero();
        assertThat(status.getGameDetailCount()).isZero();
        assertThat(status.getParticipantCount()).isZero();
        assertThat(status.getPlayerMatchIndexCount()).isZero();
        assertThat(status.getTrackedPlayerCount()).isZero();
        assertThat(status.getLatestMatchCreation()).isNull();
    }

    @Test
    void getStatus_countsLocalCacheRowsAndDatabaseFileSize() throws Exception {
        Files.writeString(cacheDatabasePath.resolveSibling("rankpeek-cache.mv.db"), "cache-file");
        jdbcTemplate.update("""
                INSERT INTO summoner_cache (puuid, raw_json, updated_at)
                VALUES ('puuid-1', '{}', 1), ('puuid-2', '{}', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO rank_cache (puuid, raw_json, updated_at)
                VALUES ('puuid-1', '{}', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO match_cache (game_id, game_creation, raw_json, updated_at)
                VALUES (1001, 1710000000000, '{}', 1), (1002, 1710000001000, '{}', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO game_detail_cache (game_id, raw_json, updated_at)
                VALUES (1001, '{}', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO match_participant_cache (game_id, puuid, participant_id, updated_at)
                VALUES (1001, 'puuid-1', 1, 1), (1001, 'puuid-2', 2, 1), (1002, 'puuid-3', 1, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO player_match_index (puuid, game_id, game_creation, updated_at)
                VALUES ('puuid-1', 1001, 1710000000000, 1),
                       ('puuid-2', 1001, 1710000000000, 1),
                       ('puuid-1', 1002, 1710000001000, 1)
                """);

        CacheStatus status = service.getStatus();

        assertThat(status.isEnabled()).isTrue();
        assertThat(status.getSummonerCount()).isEqualTo(2);
        assertThat(status.getRankCount()).isEqualTo(1);
        assertThat(status.getMatchCount()).isEqualTo(2);
        assertThat(status.getGameDetailCount()).isEqualTo(1);
        assertThat(status.getParticipantCount()).isEqualTo(3);
        assertThat(status.getPlayerMatchIndexCount()).isEqualTo(3);
        assertThat(status.getTrackedPlayerCount()).isEqualTo(2);
        assertThat(status.getLatestMatchCreation()).isEqualTo(1710000001000L);
        assertThat(status.getDatabaseSizeBytes()).isEqualTo(10);
    }

    @Test
    void getStatus_returnsDisabledWhenDatabaseQueryFails() {
        JdbcTemplate brokenJdbcTemplate = mock(JdbcTemplate.class);
        when(brokenJdbcTemplate.queryForObject("SELECT COUNT(*) FROM summoner_cache", Long.class))
                .thenThrow(new RuntimeException("database down"));
        CacheStatusService brokenService = new CacheStatusService(brokenJdbcTemplate, localDataPathService);

        CacheStatus status = brokenService.getStatus();

        assertThat(status.isEnabled()).isFalse();
        assertThat(status.getDatabasePath()).isEqualTo(cacheDatabasePath.toString());
        assertThat(status.getSummonerCount()).isZero();
        assertThat(status.getRankCount()).isZero();
        assertThat(status.getMatchCount()).isZero();
        assertThat(status.getGameDetailCount()).isZero();
        assertThat(status.getParticipantCount()).isZero();
        assertThat(status.getPlayerMatchIndexCount()).isZero();
        assertThat(status.getTrackedPlayerCount()).isZero();
        assertThat(status.getLatestMatchCreation()).isNull();
    }
}
