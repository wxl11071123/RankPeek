package io.rankpeek.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.model.GameDetail;
import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.MatchHistoryFetchResult;
import io.rankpeek.model.Rank;
import io.rankpeek.model.Summoner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcMatchHistoryCacheRepository implements MatchHistoryCacheRepository {

    private static final int DEFAULT_MATCH_INDEX_KEEP_COUNT = 50;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<MatchHistoryFetchResult> findRecentMatchHistory(String puuid, int limit) {
        if (puuid == null || puuid.isBlank() || limit <= 0) {
            return Optional.empty();
        }

        try {
            List<String> rawRows = jdbcTemplate.query(
                    """
                            SELECT m.raw_json
                            FROM player_match_index i
                            JOIN match_cache m ON m.game_id = i.game_id
                            WHERE i.puuid = ?
                            ORDER BY i.game_creation DESC, i.game_id DESC
                            LIMIT ?
                            """,
                    (rs, rowNum) -> rs.getString("raw_json"),
                    puuid,
                    limit
            );

            if (rawRows.isEmpty()) {
                return findEmptyFetchState(puuid);
            }

            List<MatchHistory> matches = rawRows.stream()
                    .map(rawJson -> readValue(rawJson, MatchHistory.class, "match history"))
                    .flatMap(Optional::stream)
                    .toList();

            if (matches.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(MatchHistoryFetchResult.builder()
                    .matches(matches)
                    .rawEmpty(false)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to read match history from local cache, puuid={}", puuidPrefix(puuid), e);
            return Optional.empty();
        }
    }

    @Override
    public void saveMatchHistory(String puuid, List<MatchHistory> matches) {
        if (puuid == null || puuid.isBlank() || matches == null) {
            return;
        }

        long now = System.currentTimeMillis();
        try {
            for (MatchHistory match : matches) {
                if (match == null || match.getGameId() == null) {
                    continue;
                }
                saveMatch(match, now);
                saveParticipants(match, now);
                savePlayerMatchIndex(puuid, match, now);
            }
            updatePlayerFetchState(puuid, matches, "OK", null);
            trimPlayerMatchIndex(puuid, DEFAULT_MATCH_INDEX_KEEP_COUNT);
        } catch (Exception e) {
            log.warn("Failed to save match history to local cache, puuid={}", puuidPrefix(puuid), e);
            updatePlayerFetchState(puuid, matches, "ERROR", e.getMessage());
        }
    }

    @Override
    public Optional<GameDetail> findGameDetail(Long gameId) {
        if (gameId == null) {
            return Optional.empty();
        }

        try {
            String rawJson = jdbcTemplate.queryForObject(
                    "SELECT raw_json FROM game_detail_cache WHERE game_id = ?",
                    String.class,
                    gameId
            );
            return readValue(rawJson, GameDetail.class, "game detail");
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to read game detail from local cache, gameId={}", gameId, e);
            return Optional.empty();
        }
    }

    @Override
    public void saveGameDetail(GameDetail detail) {
        if (detail == null || detail.getGameId() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        try {
            jdbcTemplate.update(
                    """
                            MERGE INTO game_detail_cache (game_id, raw_json, updated_at)
                            KEY(game_id) VALUES (?, ?, ?)
                            """,
                    detail.getGameId(),
                    writeValue(detail),
                    now
            );
        } catch (Exception e) {
            log.warn("Failed to save game detail to local cache, gameId={}", detail.getGameId(), e);
        }
    }

    @Override
    public Optional<Summoner> findSummonerByPuuid(String puuid) {
        if (puuid == null || puuid.isBlank()) {
            return Optional.empty();
        }

        try {
            String rawJson = jdbcTemplate.queryForObject(
                    "SELECT raw_json FROM summoner_cache WHERE puuid = ?",
                    String.class,
                    puuid
            );
            return readValue(rawJson, Summoner.class, "summoner");
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to read summoner from local cache, puuid={}", puuidPrefix(puuid), e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Summoner> findSummonerByName(String gameName, String tagLine) {
        if (gameName == null || gameName.isBlank()) {
            return Optional.empty();
        }

        try {
            List<String> rawRows;
            if (tagLine != null && !tagLine.isBlank()) {
                rawRows = jdbcTemplate.query(
                        """
                                SELECT raw_json
                                FROM summoner_cache
                                WHERE LOWER(game_name) = LOWER(?)
                                  AND LOWER(COALESCE(tag_line, '')) = LOWER(?)
                                LIMIT 1
                                """,
                        (rs, rowNum) -> rs.getString("raw_json"),
                        gameName,
                        tagLine
                );
            } else {
                rawRows = jdbcTemplate.query(
                        """
                                SELECT raw_json
                                FROM summoner_cache
                                WHERE LOWER(game_name) = LOWER(?)
                                   OR LOWER(summoner_name) = LOWER(?)
                                LIMIT 1
                                """,
                        (rs, rowNum) -> rs.getString("raw_json"),
                        gameName,
                        gameName
                );
            }

            return rawRows.stream()
                    .findFirst()
                    .flatMap(rawJson -> readValue(rawJson, Summoner.class, "summoner"));
        } catch (Exception e) {
            log.warn("Failed to read summoner by name from local cache, name={}", gameName, e);
            return Optional.empty();
        }
    }

    @Override
    public void saveSummoner(Summoner summoner) {
        if (summoner == null || summoner.getPuuid() == null || summoner.getPuuid().isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        try {
            jdbcTemplate.update(
                    """
                            MERGE INTO summoner_cache (
                                puuid, game_name, tag_line, summoner_name, profile_icon_id,
                                summoner_level, platform_id, raw_json, updated_at
                            )
                            KEY(puuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    summoner.getPuuid(),
                    summoner.getGameName(),
                    summoner.getTagLine(),
                    summoner.getGameName(),
                    summoner.getProfileIconId(),
                    summoner.getSummonerLevel(),
                    null,
                    writeValue(summoner),
                    now
            );
            mergeFetchStateTimestamp(summoner.getPuuid(), "summoner_updated_at", now);
        } catch (Exception e) {
            log.warn("Failed to save summoner to local cache, puuid={}", puuidPrefix(summoner.getPuuid()), e);
        }
    }

    @Override
    public Optional<Rank> findRank(String puuid) {
        if (puuid == null || puuid.isBlank()) {
            return Optional.empty();
        }

        try {
            String rawJson = jdbcTemplate.queryForObject(
                    "SELECT raw_json FROM rank_cache WHERE puuid = ?",
                    String.class,
                    puuid
            );
            return readValue(rawJson, Rank.class, "rank");
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to read rank from local cache, puuid={}", puuidPrefix(puuid), e);
            return Optional.empty();
        }
    }

    @Override
    public void saveRank(String puuid, Rank rank) {
        if (puuid == null || puuid.isBlank() || rank == null) {
            return;
        }

        long now = System.currentTimeMillis();
        try {
            jdbcTemplate.update(
                    """
                            MERGE INTO rank_cache (puuid, raw_json, updated_at)
                            KEY(puuid) VALUES (?, ?, ?)
                            """,
                    puuid,
                    writeValue(rank),
                    now
            );
            mergeFetchStateTimestamp(puuid, "rank_updated_at", now);
        } catch (Exception e) {
            log.warn("Failed to save rank to local cache, puuid={}", puuidPrefix(puuid), e);
        }
    }

    @Override
    public void updatePlayerFetchState(String puuid, List<MatchHistory> matches, String status, String lastError) {
        if (puuid == null || puuid.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        MatchHistory latest = latestMatch(matches);
        try {
            jdbcTemplate.update(
                    """
                            MERGE INTO player_fetch_state (
                                puuid, latest_game_id, latest_game_creation, match_updated_at,
                                last_error, status, updated_at
                            )
                            KEY(puuid) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    puuid,
                    latest == null ? null : latest.getGameId(),
                    latest == null ? null : latest.getGameCreation(),
                    now,
                    lastError,
                    status,
                    now
            );
        } catch (Exception e) {
            log.warn("Failed to update local fetch state, puuid={}", puuidPrefix(puuid), e);
        }
    }

    @Override
    public Optional<Long> getMatchUpdatedAt(String puuid) {
        if (puuid == null || puuid.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT match_updated_at FROM player_fetch_state WHERE puuid = ?",
                    Long.class,
                    puuid
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to read match cache timestamp, puuid={}", puuidPrefix(puuid), e);
            return Optional.empty();
        }
    }

    @Override
    public void trimPlayerMatchIndex(String puuid, int keepCount) {
        if (puuid == null || puuid.isBlank() || keepCount <= 0) {
            return;
        }

        try {
            jdbcTemplate.update(
                    """
                            DELETE FROM player_match_index
                            WHERE puuid = ?
                              AND game_id NOT IN (
                                  SELECT game_id FROM (
                                      SELECT game_id
                                      FROM player_match_index
                                      WHERE puuid = ?
                                      ORDER BY game_creation DESC, game_id DESC
                                      LIMIT ?
                                  )
                              )
                            """,
                    puuid,
                    puuid,
                    keepCount
            );
        } catch (Exception e) {
            log.warn("Failed to trim local match index, puuid={}", puuidPrefix(puuid), e);
        }
    }

    private void saveMatch(MatchHistory match, long now) throws JsonProcessingException {
        jdbcTemplate.update(
                """
                        MERGE INTO match_cache (
                            game_id, queue_id, queue_name, game_mode, game_type, map_id,
                            game_creation, game_duration, platform_id, raw_json, updated_at
                        )
                        KEY(game_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                match.getGameId(),
                match.getQueueId(),
                match.getQueueName(),
                match.getGameMode(),
                match.getGameType(),
                match.getMapId(),
                match.getGameCreation(),
                match.getGameDuration(),
                match.getPlatformId(),
                writeValue(match),
                now
        );
    }

    private void saveParticipants(MatchHistory match, long now) throws JsonProcessingException {
        if (match.getParticipants() == null || match.getParticipants().isEmpty()) {
            return;
        }

        Map<Integer, MatchHistory.Player> playerByParticipantId = playersByParticipantId(match);
        for (MatchHistory.Participant participant : match.getParticipants()) {
            if (participant == null || participant.getParticipantId() == null) {
                continue;
            }

            MatchHistory.Player player = playerByParticipantId.get(participant.getParticipantId());
            if (player == null || player.getPuuid() == null || player.getPuuid().isBlank()) {
                continue;
            }

            MatchHistory.Stats stats = participant.getStats();
            jdbcTemplate.update(
                    """
                            MERGE INTO match_participant_cache (
                                game_id, puuid, participant_id, team_id, champion_id, spell1_id, spell2_id,
                                win, kills, deaths, assists, gold_earned, total_damage_dealt_to_champions,
                                total_damage_taken, total_minions_killed, neutral_minions_killed,
                                game_name, tag_line, summoner_name, raw_json, updated_at
                            )
                            KEY(game_id, puuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    match.getGameId(),
                    player.getPuuid(),
                    participant.getParticipantId(),
                    participant.getTeamId(),
                    participant.getChampionId(),
                    participant.getSpell1Id(),
                    participant.getSpell2Id(),
                    stats == null ? null : stats.getWin(),
                    stats == null ? null : stats.getKills(),
                    stats == null ? null : stats.getDeaths(),
                    stats == null ? null : stats.getAssists(),
                    stats == null ? null : stats.getGoldEarned(),
                    stats == null ? null : stats.getTotalDamageDealtToChampions(),
                    stats == null ? null : stats.getTotalDamageTaken(),
                    stats == null ? null : stats.getTotalMinionsKilled(),
                    stats == null ? null : stats.getNeutralMinionsKilled(),
                    player.getGameName(),
                    player.getTagLine(),
                    player.getSummonerName(),
                    writeValue(participant),
                    now
            );
        }
    }

    private void savePlayerMatchIndex(String puuid, MatchHistory match, long now) {
        MatchHistory.Participant participant = participantByPuuid(match, puuid).orElse(null);
        MatchHistory.Stats stats = participant == null ? null : participant.getStats();
        jdbcTemplate.update(
                """
                        MERGE INTO player_match_index (
                            puuid, game_id, game_creation, queue_id, champion_id, win, updated_at
                        )
                        KEY(puuid, game_id) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                puuid,
                match.getGameId(),
                match.getGameCreation(),
                match.getQueueId(),
                participant == null ? null : participant.getChampionId(),
                stats == null ? null : stats.getWin(),
                now
        );
    }

    private void mergeFetchStateTimestamp(String puuid, String columnName, long timestamp) {
        jdbcTemplate.update(
                "MERGE INTO player_fetch_state (puuid, " + columnName + ", updated_at) KEY(puuid) VALUES (?, ?, ?)",
                puuid,
                timestamp,
                timestamp
        );
    }

    private Optional<MatchHistory.Participant> participantByPuuid(MatchHistory match, String puuid) {
        if (match.getParticipantIdentities() == null || match.getParticipants() == null) {
            return Optional.empty();
        }

        Integer participantId = null;
        for (MatchHistory.ParticipantIdentity identity : match.getParticipantIdentities()) {
            if (identity != null
                    && identity.getPlayer() != null
                    && puuid.equals(identity.getPlayer().getPuuid())) {
                participantId = identity.getParticipantId();
                break;
            }
        }

        if (participantId == null) {
            return Optional.empty();
        }

        for (MatchHistory.Participant participant : match.getParticipants()) {
            if (participant != null && participantId.equals(participant.getParticipantId())) {
                return Optional.of(participant);
            }
        }
        return Optional.empty();
    }

    private Map<Integer, MatchHistory.Player> playersByParticipantId(MatchHistory match) {
        Map<Integer, MatchHistory.Player> players = new HashMap<>();
        if (match.getParticipantIdentities() == null) {
            return players;
        }

        for (MatchHistory.ParticipantIdentity identity : match.getParticipantIdentities()) {
            if (identity != null && identity.getParticipantId() != null && identity.getPlayer() != null) {
                players.put(identity.getParticipantId(), identity.getPlayer());
            }
        }
        return players;
    }

    private MatchHistory latestMatch(List<MatchHistory> matches) {
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        return matches.stream()
                .filter(match -> match != null && match.getGameId() != null)
                .max(Comparator.comparingLong(match -> match.getGameCreation() == null ? Long.MIN_VALUE : match.getGameCreation()))
                .orElse(null);
    }

    private <T> Optional<T> readValue(String rawJson, Class<T> type, String description) {
        if (rawJson == null || rawJson.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(rawJson, type));
        } catch (Exception e) {
            log.debug("Skipping corrupt {} cache row", description, e);
            return Optional.empty();
        }
    }

    private String writeValue(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    private Optional<MatchHistoryFetchResult> findEmptyFetchState(String puuid) {
        try {
            List<String> statuses = jdbcTemplate.query(
                    "SELECT status FROM player_fetch_state WHERE puuid = ? AND match_updated_at IS NOT NULL LIMIT 1",
                    (rs, rowNum) -> rs.getString("status"),
                    puuid
            );
            if (statuses.isEmpty()) {
                return Optional.empty();
            }
            if (!"OK".equalsIgnoreCase(statuses.getFirst())) {
                return Optional.empty();
            }
            return Optional.of(MatchHistoryFetchResult.builder()
                    .matches(List.of())
                    .rawEmpty(true)
                    .build());
        } catch (Exception e) {
            log.debug("Failed to read empty match-history cache state, puuid={}", puuidPrefix(puuid), e);
            return Optional.empty();
        }
    }

    private String puuidPrefix(String puuid) {
        if (puuid == null || puuid.isBlank()) {
            return "null";
        }
        return puuid.substring(0, Math.min(8, puuid.length()));
    }
}
