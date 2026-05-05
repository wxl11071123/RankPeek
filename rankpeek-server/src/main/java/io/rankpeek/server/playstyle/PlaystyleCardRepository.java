package io.rankpeek.server.playstyle;

import io.rankpeek.server.common.JdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class PlaystyleCardRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<PlaystyleCard> cardMapper = (rs, rowNum) -> new PlaystyleCard(
            rs.getLong("id"),
            rs.getString("patch_key"),
            rs.getInt("champion_id"),
            rs.getString("role"),
            rs.getString("title"),
            rs.getString("card_type"),
            rs.getString("summary"),
            rs.getString("when_to_use"),
            rs.getString("when_not_to_use"),
            rs.getString("core_items_json"),
            rs.getString("runes_json"),
            rs.getString("skill_order"),
            rs.getString("source_tier"),
            rs.getBigDecimal("confidence"),
            rs.getString("freshness_status"),
            rs.getString("status"),
            instantOrNull(rs.getTimestamp("expires_at")),
            instantOrNull(rs.getTimestamp("created_at")),
            instantOrNull(rs.getTimestamp("updated_at"))
    );

    public PlaystyleCardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PlaystyleCard createMockCard(String patchKey, Integer championId, String role) {
        List<PlaystyleCard> existing = findStoredCards(patchKey, championId, role);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        Instant now = Instant.parse("2026-05-05T00:00:00Z");
        KeyHolder keyHolder = JdbcSupport.newKeyHolder();
        jdbcTemplate.update(connection -> JdbcSupport.prepareInsert(
                connection,
                """
                        insert into playstyle_cards (
                            patch_key, champion_id, role, title, card_type, summary,
                            when_to_use, when_not_to_use, core_items_json, runes_json,
                            skill_order, source_tier, confidence, freshness_status,
                            status, expires_at, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                patchKey,
                championId,
                role,
                "Mock lane pressure card",
                "MOCK_META",
                "Deterministic mock playstyle card for server foundation tests.",
                "Use when the team has engage and enough frontline.",
                "Avoid when the lane requires early all-in defense.",
                "[\"mock_core_item\"]",
                "[\"mock_rune_page\"]",
                "Q-W-E",
                "MOCK_REVIEWED",
                "0.9000",
                "FRESH",
                "ACTIVE",
                Timestamp.from(Instant.parse("2026-06-05T00:00:00Z")),
                Timestamp.from(now),
                Timestamp.from(now)
        ), keyHolder);
        Long cardId = JdbcSupport.requireGeneratedId(keyHolder);
        jdbcTemplate.update(
                """
                        insert into playstyle_card_sources (
                            card_id, source_name, source_url, source_type, observed_at, evidence_note
                        ) values (?, ?, ?, ?, ?, ?)
                        """,
                cardId,
                "mock-curation",
                "mock://playstyle-card/" + patchKey + "/" + championId + "/" + role,
                "MOCK",
                Timestamp.from(now),
                "deterministic mock source"
        );
        return findById(cardId);
    }

    public List<PlaystyleCard> findStoredCards(String patchKey, Integer championId, String role) {
        return jdbcTemplate.query(
                """
                        select * from playstyle_cards
                        where patch_key = ? and champion_id = ? and role = ? and status = 'ACTIVE'
                        order by id
                        """,
                cardMapper,
                patchKey,
                championId,
                role
        );
    }

    public void addInvalidatingRule(String patchKey, Integer championId, String note) {
        jdbcTemplate.update(
                """
                        insert into patch_relevance_rules (
                            patch_key, champion_id, change_type, invalidates_playstyle_cards, note
                        ) values (?, ?, ?, ?, ?)
                        """,
                patchKey,
                championId,
                "MOCK_INVALIDATION",
                true,
                note
        );
    }

    public boolean hasInvalidatingRule(String patchKey, Integer championId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*) from patch_relevance_rules
                        where patch_key = ? and champion_id = ? and invalidates_playstyle_cards = true
                        """,
                Integer.class,
                patchKey,
                championId
        );
        return count != null && count > 0;
    }

    private PlaystyleCard findById(Long id) {
        return jdbcTemplate.query(
                "select * from playstyle_cards where id = ?",
                cardMapper,
                id
        ).getFirst();
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
