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
                "patch_relevance_rules"
        );

        for (String table : tables) {
            assertThatCode(() -> {
                Integer count = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
                assertThat(count).as(table).isNotNull();
            }).as(table).doesNotThrowAnyException();
        }
    }
}
