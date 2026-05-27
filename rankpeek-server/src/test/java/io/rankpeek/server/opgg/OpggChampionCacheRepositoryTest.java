package io.rankpeek.server.opgg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OpggChampionCacheRepositoryTest {

    @Autowired
    private OpggChampionCacheRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearCacheRows() {
        jdbcTemplate.update("delete from opgg_champion_list_cache");
        jdbcTemplate.update("delete from opgg_champion_detail_cache");
    }

    @Test
    void upsertTodayKeepsOneRowAndOverwritesDetailForSameQueryAndDate() {
        OpggChampionDetailQuery query = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "mid");
        LocalDate cacheDate = LocalDate.of(2026, 5, 24);

        repository.upsertToday(query, cacheDate, detail(query, "16.10", 0.51), Instant.parse("2026-05-24T04:00:00Z"));
        repository.upsertToday(query, cacheDate, detail(query, "16.11", 0.52), Instant.parse("2026-05-24T05:00:00Z"));

        OpggChampionDetail cached = repository.findToday(query, cacheDate).orElseThrow();

        assertThat(cached.version()).isEqualTo("16.11");
        assertThat(cached.stats().winRate()).isEqualTo(0.52);
        assertThat(countRows()).isEqualTo(1);
    }

    @Test
    void cacheKeySeparatesModeTierPositionChampionAndDate() {
        LocalDate cacheDate = LocalDate.of(2026, 5, 24);
        OpggChampionDetailQuery mid = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "mid");
        OpggChampionDetailQuery adc = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "adc");

        repository.upsertToday(mid, cacheDate, detail(mid, "16.10", 0.51), Instant.parse("2026-05-24T04:00:00Z"));
        repository.upsertToday(adc, cacheDate, detail(adc, "16.10", 0.48), Instant.parse("2026-05-24T04:01:00Z"));

        assertThat(repository.findToday(mid, cacheDate)).isPresent();
        assertThat(repository.findToday(adc, cacheDate)).isPresent();
        assertThat(countRows()).isEqualTo(2);
    }

    @Test
    void upsertTodayKeepsOneListRowAndOverwritesForSameQueryAndDate() {
        OpggChampionListQuery query = new OpggChampionListQuery("ranked", "kr", "emerald_plus");
        LocalDate cacheDate = LocalDate.of(2026, 5, 24);

        repository.upsertToday(query, cacheDate, championList(query, "16.10", 7), Instant.parse("2026-05-24T04:00:00Z"));
        repository.upsertToday(query, cacheDate, championList(query, "16.11", 3), Instant.parse("2026-05-24T05:00:00Z"));

        OpggChampionList cached = repository.findToday(query, cacheDate).orElseThrow();

        assertThat(cached.version()).isEqualTo("16.11");
        assertThat(cached.items()).singleElement().satisfies(item -> assertThat(item.rank()).isEqualTo(3));
        assertThat(countListRows()).isEqualTo(1);
    }

    @Test
    void deleteBeforeRemovesOldDatesAndKeepsToday() {
        OpggChampionDetailQuery query = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "mid");
        LocalDate oldDate = LocalDate.of(2026, 5, 23);
        LocalDate today = LocalDate.of(2026, 5, 24);

        repository.upsertToday(query, oldDate, detail(query, "16.09", 0.50), Instant.parse("2026-05-23T04:00:00Z"));
        repository.upsertToday(query, today, detail(query, "16.10", 0.51), Instant.parse("2026-05-24T04:00:00Z"));
        OpggChampionListQuery listQuery = new OpggChampionListQuery("ranked", "kr", "emerald_plus");
        repository.upsertToday(listQuery, oldDate, championList(listQuery, "16.09", 9), Instant.parse("2026-05-23T04:00:00Z"));
        repository.upsertToday(listQuery, today, championList(listQuery, "16.10", 7), Instant.parse("2026-05-24T04:00:00Z"));

        int deleted = repository.deleteBefore(today);

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findToday(query, oldDate)).isEmpty();
        assertThat(repository.findToday(query, today)).isPresent();
        assertThat(repository.findToday(listQuery, oldDate)).isEmpty();
        assertThat(repository.findToday(listQuery, today)).isPresent();
    }

    private int countRows() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from opgg_champion_detail_cache", Integer.class);
        return count == null ? 0 : count;
    }

    private int countListRows() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from opgg_champion_list_cache", Integer.class);
        return count == null ? 0 : count;
    }

    private static OpggChampionDetail detail(OpggChampionDetailQuery query, String version, double winRate) {
        return new OpggChampionDetail(
                query.championId(),
                "Ahri",
                query.mode(),
                query.region(),
                query.tier(),
                query.position(),
                version,
                Instant.parse("2026-05-24T04:00:00Z"),
                new OpggChampionStats(1000, winRate, 0.12, 0.03, 2.6),
                List.of(new OpggBuildOption("spells", List.of(4, 12), List.of(), 100L, 0.52, 0.6)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new OpggBuildOption("core", List.of(3118, 3152, 4645), List.of(), 70L, 0.54, 0.19)),
                List.of(new OpggBuildOption("last", List.of(3089), List.of(), 30L, 0.63, 0.12)),
                List.of()
        );
    }

    private static OpggChampionList championList(OpggChampionListQuery query, String version, int rank) {
        return new OpggChampionList(
                query.mode(),
                query.region(),
                query.tier(),
                version,
                Instant.parse("2026-05-24T04:00:00Z"),
                List.of(new OpggChampionListItem(
                        103,
                        1,
                        rank,
                        new OpggChampionStats(0, 0.51, 0.12, 0.03, 2.6),
                        List.of(new OpggChampionPositionStats(
                                "mid",
                                0,
                                2,
                                new OpggChampionStats(0, 0.50, 0.10, 0.03, 2.5),
                                List.of(new OpggChampionCounter(238, 1200, 590L))
                        ))
                ))
        );
    }
}
