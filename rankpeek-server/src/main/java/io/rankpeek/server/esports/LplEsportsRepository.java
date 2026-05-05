package io.rankpeek.server.esports;

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
public class LplEsportsRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<LplChampionUsage> usageMapper = (rs, rowNum) -> new LplChampionUsage(
            "mock-lpl",
            rs.getString("patch_key"),
            rs.getInt("champion_id"),
            rs.getString("role"),
            rs.getString("tournament"),
            rs.getString("split"),
            rs.getString("team"),
            rs.getString("player_name"),
            rs.getInt("kills"),
            rs.getInt("deaths"),
            rs.getInt("assists")
    );

    public LplEsportsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LplChampionUsage saveMockUsage(String patchKey, Integer championId, String role) {
        List<LplChampionUsage> existing = findChampionUsage(patchKey, championId, role);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        Instant capturedAt = Instant.parse("2026-05-05T00:00:00Z");
        KeyHolder matchKey = JdbcSupport.newKeyHolder();
        jdbcTemplate.update(connection -> JdbcSupport.prepareInsert(
                connection,
                """
                        insert into lpl_matches (
                            bmid, tournament, split, match_date, team_blue, team_red, source_url, captured_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "mock-bmid-" + patchKey + "-" + championId + "-" + role,
                "LPL",
                "MOCK_SPLIT",
                Date.valueOf(LocalDate.of(2026, 5, 5)),
                "MOCK_BLUE",
                "MOCK_RED",
                "mock://lpl/" + patchKey,
                Timestamp.from(capturedAt)
        ), matchKey);
        Long matchId = JdbcSupport.requireGeneratedId(matchKey);

        KeyHolder gameKey = JdbcSupport.newKeyHolder();
        jdbcTemplate.update(connection -> JdbcSupport.prepareInsert(
                connection,
                """
                        insert into lpl_games (
                            match_id, game_number, patch_key, duration_seconds, winner_side
                        ) values (?, ?, ?, ?, ?)
                        """,
                matchId,
                1,
                patchKey,
                1888,
                "BLUE"
        ), gameKey);
        Long gameId = JdbcSupport.requireGeneratedId(gameKey);

        jdbcTemplate.update(
                """
                        insert into lpl_pick_bans (
                            game_id, phase, side, champion_id, team, player_name, role
                        ) values (?, ?, ?, ?, ?, ?, ?)
                        """,
                gameId,
                "PICK",
                "BLUE",
                championId,
                "MOCK_BLUE",
                "MockPlayer",
                role
        );
        jdbcTemplate.update(
                """
                        insert into lpl_player_game_stats (
                            game_id, player_name, team, role, champion_id, kills, deaths, assists,
                            cs, gold, damage, items_json, runes_json
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                gameId,
                "MockPlayer",
                "MOCK_BLUE",
                role,
                championId,
                5,
                1,
                7,
                302,
                14500,
                27600,
                "[\"mock_item\"]",
                "[\"mock_rune\"]"
        );
        return findChampionUsage(patchKey, championId, role).getFirst();
    }

    public List<LplChampionUsage> findChampionUsage(String patchKey, Integer championId, String role) {
        return jdbcTemplate.query(
                """
                        select g.patch_key, s.champion_id, s.role, m.tournament, m.split,
                               s.team, s.player_name, s.kills, s.deaths, s.assists
                        from lpl_player_game_stats s
                        join lpl_games g on g.id = s.game_id
                        join lpl_matches m on m.id = g.match_id
                        where g.patch_key = ? and s.champion_id = ? and s.role = ?
                        order by m.match_date desc, g.game_number desc, s.id desc
                        """,
                usageMapper,
                patchKey,
                championId,
                role
        );
    }
}
