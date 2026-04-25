package io.rankpeek.cache;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalCacheSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initializeSchema() {
        try {
            createTables();
            createIndexes();
            log.info("Local cache schema is ready");
        } catch (Exception e) {
            log.warn("Failed to initialize local cache schema; persistent cache will be skipped when unavailable", e);
        }
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
    }
}
