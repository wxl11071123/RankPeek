package io.rankpeek.cache;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LocalCacheSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;
    private final LocalCacheRecoveryCoordinator recoveryCoordinator;

    @Autowired
    public LocalCacheSchemaInitializer(JdbcTemplate jdbcTemplate, LocalCacheRecoveryCoordinator recoveryCoordinator) {
        this.jdbcTemplate = jdbcTemplate;
        this.recoveryCoordinator = recoveryCoordinator;
    }

    public LocalCacheSchemaInitializer(JdbcTemplate jdbcTemplate, LocalCacheRecoveryService recoveryService) {
        this(jdbcTemplate, recoveryService == null
                ? null
                : new LocalCacheRecoveryCoordinator(recoveryService, java.time.Clock.systemDefaultZone()));
    }

    public LocalCacheSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, (LocalCacheRecoveryCoordinator) null);
    }

    @PostConstruct
    public void initializeSchema() {
        initializeSchemaIfPossible();
    }

    public boolean initializeSchemaIfPossible() {
        try {
            runSchemaInitialization();
            log.info("Local cache schema is ready");
            return true;
        } catch (Exception e) {
            return recoverAndRetry(e);
        }
    }

    private boolean recoverAndRetry(Exception initializationError) {
        if (recoveryCoordinator == null || !recoveryCoordinator.isRecoverableCorruption(initializationError)) {
            log.warn("Failed to initialize local cache schema; persistent cache will be skipped when unavailable: rootCause={}",
                    rootCauseSummary(initializationError),
                    initializationError);
            return false;
        }

        log.warn("Detected local H2 cache corruption during schema initialization: rootCause={}",
                recoveryCoordinator.rootCauseSummary(initializationError),
                initializationError);
        LocalCacheRecoveryCoordinator.CoordinatedRecoveryResult recoveryResult =
                recoveryCoordinator.recoverIfCorrupt(initializationError, "schema.initialize", false);
        if (!recoveryResult.recovered()) {
            log.warn("Detected local H2 cache corruption, but recovery failed; persistent cache will be disabled: {}",
                    recoveryResult.message(),
                    recoveryFailure(recoveryResult, initializationError));
            return false;
        }

        try {
            runSchemaInitialization();
            log.info("Local cache schema is ready after quarantining corrupt H2 cache files");
            return true;
        } catch (Exception retryError) {
            log.warn("Failed to initialize local cache schema after H2 cache recovery; persistent cache will be disabled: rootCause={}",
                    rootCauseSummary(retryError),
                    retryError);
            return false;
        }
    }

    private String rootCauseSummary(Throwable error) {
        if (recoveryCoordinator != null) {
            return recoveryCoordinator.rootCauseSummary(error);
        }
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getMessage();
        }
        return current.getClass().getSimpleName() + ": " + message;
    }

    private Throwable recoveryFailure(
            LocalCacheRecoveryCoordinator.CoordinatedRecoveryResult recoveryResult,
            Throwable fallback) {
        if (recoveryResult.recoveryResult() == null || recoveryResult.recoveryResult().failure() == null) {
            return fallback;
        }
        return recoveryResult.recoveryResult().failure();
    }

    private void runSchemaInitialization() {
        createTables();
        migrateTables();
        createIndexes();
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS summoner_cache (
                    puuid VARCHAR(128) PRIMARY KEY,
                    game_name VARCHAR(255),
                    tag_line VARCHAR(64),
                    summoner_name VARCHAR(255),
                    profile_icon_id INT,
                    summoner_level INT,
                    platform_id VARCHAR(64),
                    raw_json CLOB,
                    updated_at BIGINT
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rank_cache (
                    puuid VARCHAR(128) PRIMARY KEY,
                    raw_json CLOB,
                    updated_at BIGINT
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS match_cache (
                    game_id BIGINT PRIMARY KEY,
                    queue_id INT,
                    queue_name VARCHAR(255),
                    game_mode VARCHAR(64),
                    game_type VARCHAR(64),
                    map_id INT,
                    game_creation BIGINT,
                    game_duration INT,
                    platform_id VARCHAR(64),
                    raw_json CLOB,
                    updated_at BIGINT
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS game_detail_cache (
                    game_id BIGINT PRIMARY KEY,
                    raw_json CLOB,
                    updated_at BIGINT
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS match_data_scope_cache (
                    game_id BIGINT PRIMARY KEY,
                    source VARCHAR(32),
                    summary_raw_json CLOB,
                    detail_raw_json CLOB,
                    timeline_raw_json CLOB,
                    timeline_json CLOB,
                    summary_status VARCHAR(64),
                    detail_status VARCHAR(64),
                    timeline_status VARCHAR(64),
                    fetched_at BIGINT,
                    schema_version INT DEFAULT 1,
                    last_error VARCHAR(2000),
                    updated_at BIGINT
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS match_participant_cache (
                    game_id BIGINT,
                    puuid VARCHAR(128),
                    participant_id INT,
                    team_id INT,
                    champion_id INT,
                    spell1_id INT,
                    spell2_id INT,
                    win BOOLEAN,
                    kills INT,
                    deaths INT,
                    assists INT,
                    gold_earned INT,
                    total_damage_dealt_to_champions INT,
                    total_damage_taken INT,
                    total_minions_killed INT,
                    neutral_minions_killed INT,
                    game_name VARCHAR(255),
                    tag_line VARCHAR(64),
                    summoner_name VARCHAR(255),
                    raw_json CLOB,
                    updated_at BIGINT,
                    PRIMARY KEY(game_id, puuid)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS player_match_index (
                    puuid VARCHAR(128),
                    game_id BIGINT,
                    game_creation BIGINT,
                    queue_id INT,
                    champion_id INT,
                    win BOOLEAN,
                    updated_at BIGINT,
                    PRIMARY KEY(puuid, game_id)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS player_fetch_state (
                    puuid VARCHAR(128) PRIMARY KEY,
                    latest_game_id BIGINT,
                    latest_game_creation BIGINT,
                    match_updated_at BIGINT,
                    rank_updated_at BIGINT,
                    summoner_updated_at BIGINT,
                    last_error VARCHAR(2000),
                    status VARCHAR(64),
                    updated_at BIGINT
                )
                """);
    }

    private void migrateTables() {
        addColumnIfMissing("match_data_scope_cache", "source VARCHAR(32)");
        addColumnIfMissing("match_data_scope_cache", "summary_raw_json CLOB");
        addColumnIfMissing("match_data_scope_cache", "detail_raw_json CLOB");
        addColumnIfMissing("match_data_scope_cache", "timeline_raw_json CLOB");
        addColumnIfMissing("match_data_scope_cache", "timeline_json CLOB");
        addColumnIfMissing("match_data_scope_cache", "summary_status VARCHAR(64)");
        addColumnIfMissing("match_data_scope_cache", "detail_status VARCHAR(64)");
        addColumnIfMissing("match_data_scope_cache", "timeline_status VARCHAR(64)");
        addColumnIfMissing("match_data_scope_cache", "fetched_at BIGINT");
        addColumnIfMissing("match_data_scope_cache", "schema_version INT DEFAULT 1");
        addColumnIfMissing("match_data_scope_cache", "last_error VARCHAR(2000)");
        addColumnIfMissing("match_data_scope_cache", "updated_at BIGINT");
    }

    private void addColumnIfMissing(String tableName, String columnDefinition) {
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN IF NOT EXISTS " + columnDefinition);
    }

    private void createIndexes() {
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_player_match_index_recent
                ON player_match_index(puuid, game_creation DESC)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_player_match_index_filter
                ON player_match_index(puuid, queue_id, champion_id, game_creation DESC)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_match_participant_cache_puuid
                ON match_participant_cache(puuid)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_match_cache_creation
                ON match_cache(game_creation DESC)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_match_data_scope_updated
                ON match_data_scope_cache(updated_at DESC)
                """);
    }
}
