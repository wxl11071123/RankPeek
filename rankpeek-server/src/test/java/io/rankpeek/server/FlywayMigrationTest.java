package io.rankpeek.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesCoreTables() {
        List<String> tables = List.of(
                "patch_versions",
                "patch_source_documents",
                "patch_changes",
                "cn_meta_snapshots",
                "cn_champion_stats",
                "cn_champion_builds",
                "lpl_matches",
                "lpl_games",
                "lpl_pick_bans",
                "lpl_player_game_stats",
                "playstyle_cards",
                "playstyle_card_sources",
                "patch_relevance_rules",
                "users",
                "auth_refresh_tokens",
                "auth_password_reset_tokens",
                "cn_meta_sync_jobs",
                "cn_meta_source_documents",
                "user_credit_balances",
                "credit_ledger_entries",
                "ai_analysis_runs"
        );

        for (String table : tables) {
            assertThatCode(() -> {
                Integer count = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
                assertThat(count).as(table).isNotNull();
            }).as(table).doesNotThrowAnyException();
        }
    }

    @Test
    void migrationAddsAiAnalysisRunReplayColumns() {
        assertThatCode(() -> jdbcTemplate.queryForList(
                """
                        select request_hash, response_json, error_message,
                            charge_ledger_entry_id, refund_ledger_entry_id
                        from ai_analysis_runs
                        where 1 = 0
                """
        )).doesNotThrowAnyException();
    }

    @Test
    void migrationAddsPasswordResetTokenTable() {
        assertThatCode(() -> jdbcTemplate.queryForList(
                """
                        select user_id, token_hash, expires_at, used_at, created_at
                        from auth_password_reset_tokens
                        where 1 = 0
                        """
        )).doesNotThrowAnyException();
    }
}
