package io.rankpeek.server.opgg;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpggChampionServiceTest {

    @Test
    void cachesSuccessfulDetailByModeRegionTierPositionAndChampion() {
        RecordingSourceClient source = new RecordingSourceClient(detailWithSkillOrders("emerald_plus", "mid"), championList("emerald_plus"));
        OpggChampionService service = new OpggChampionService(
                source,
                Duration.ofMinutes(30),
                Clock.fixed(Instant.parse("2026-05-23T04:00:00Z"), ZoneOffset.UTC)
        );
        OpggChampionDetailQuery query = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "mid");

        OpggChampionDetail first = service.getChampionDetail(query);
        OpggChampionDetail second = service.getChampionDetail(query);

        assertThat(first).isSameAs(second);
        assertThat(source.calls()).isEqualTo(1);
    }

    @Test
    void cachesSuccessfulListByModeRegionAndTier() {
        RecordingSourceClient source = new RecordingSourceClient(detail("emerald_plus", "mid"), championList("emerald_plus"));
        OpggChampionService service = new OpggChampionService(
                source,
                Duration.ofMinutes(30),
                Clock.fixed(Instant.parse("2026-05-23T04:00:00Z"), ZoneOffset.UTC)
        );
        OpggChampionListQuery query = new OpggChampionListQuery("ranked", "kr", "emerald_plus");

        OpggChampionList first = service.getChampionList(query);
        OpggChampionList second = service.getChampionList(query);

        assertThat(first).isSameAs(second);
        assertThat(source.calls()).isEqualTo(1);
    }

    @Test
    void failedSourceRequestsAreNotCached() {
        RecordingSourceClient source = new RecordingSourceClient(detail("emerald_plus", "mid"), championList("emerald_plus"));
        source.failNext();
        OpggChampionService service = new OpggChampionService(
                source,
                Duration.ofMinutes(30),
                Clock.fixed(Instant.parse("2026-05-23T04:00:00Z"), ZoneOffset.UTC)
        );
        OpggChampionDetailQuery query = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "mid");

        assertThatThrownBy(() -> service.getChampionDetail(query))
                .isInstanceOf(OpggSourceException.class)
                .hasMessageContaining("temporary");

        OpggChampionDetail detail = service.getChampionDetail(query);

        assertThat(detail.championId()).isEqualTo(103);
        assertThat(source.calls()).isEqualTo(2);
    }

    @Test
    void todayDatabaseCacheHitDoesNotCallSource() {
        RecordingSourceClient source = new RecordingSourceClient(detail("emerald_plus", "mid"), championList("emerald_plus"));
        OpggChampionCacheRepository repository = mock(OpggChampionCacheRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-23T16:01:00Z"), ZoneOffset.UTC);
        OpggChampionDetailQuery query = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "mid");
        LocalDate cacheDate = LocalDate.of(2026, 5, 24);
        OpggChampionDetail cached = detailWithSkillOrders("emerald_plus", "mid");
        when(repository.findToday(query, cacheDate)).thenReturn(Optional.of(cached));
        OpggChampionService service = new OpggChampionService(
                source,
                Duration.ofMinutes(30),
                clock,
                cacheProperties(true),
                repository
        );

        OpggChampionDetail detail = service.getChampionDetail(query);

        assertThat(detail).isSameAs(cached);
        assertThat(source.calls()).isZero();
    }

    @Test
    void rankedDetailCacheWithoutSkillOrdersIsRefetchedAndOverwritten() {
        OpggChampionDetail cached = detail("emerald_plus", "mid");
        OpggChampionDetail fresh = detailWithSkillOrders("emerald_plus", "mid");
        RecordingSourceClient source = new RecordingSourceClient(fresh, championList("emerald_plus"));
        OpggChampionCacheRepository repository = mock(OpggChampionCacheRepository.class);
        Instant now = Instant.parse("2026-05-23T16:01:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        OpggChampionDetailQuery query = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "mid");
        LocalDate cacheDate = LocalDate.of(2026, 5, 24);
        when(repository.findToday(query, cacheDate)).thenReturn(Optional.of(cached));
        OpggChampionService service = new OpggChampionService(
                source,
                Duration.ofMinutes(30),
                clock,
                cacheProperties(true),
                repository
        );

        OpggChampionDetail detail = service.getChampionDetail(query);

        assertThat(detail).isSameAs(fresh);
        assertThat(source.calls()).isEqualTo(1);
        verify(repository).upsertToday(query, cacheDate, fresh, now);
    }

    @Test
    void rankedDetailMemoryCacheWithoutSkillOrdersIsRefetched() {
        OpggChampionDetail stale = detail("emerald_plus", "mid");
        OpggChampionDetail fresh = detailWithSkillOrders("emerald_plus", "mid");
        AtomicInteger sourceCalls = new AtomicInteger();
        OpggSourceClient source = new OpggSourceClient() {
            @Override
            public OpggChampionDetail fetchChampionDetail(OpggChampionDetailQuery query) {
                return sourceCalls.incrementAndGet() == 1 ? stale : fresh;
            }

            @Override
            public OpggChampionList fetchChampionList(OpggChampionListQuery query) {
                return championList("emerald_plus");
            }
        };
        OpggChampionCacheRepository repository = mock(OpggChampionCacheRepository.class);
        Instant now = Instant.parse("2026-05-23T16:01:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        OpggChampionDetailQuery query = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "mid");
        LocalDate cacheDate = LocalDate.of(2026, 5, 24);
        when(repository.findToday(query, cacheDate)).thenReturn(Optional.empty());
        OpggChampionService service = new OpggChampionService(
                source,
                Duration.ofMinutes(30),
                clock,
                cacheProperties(true),
                repository
        );

        OpggChampionDetail first = service.getChampionDetail(query);
        OpggChampionDetail second = service.getChampionDetail(query);

        assertThat(first).isSameAs(stale);
        assertThat(second).isSameAs(fresh);
        assertThat(sourceCalls).hasValue(2);
    }

    @Test
    void databaseMissFetchesSourceAndWritesTodayCache() {
        RecordingSourceClient source = new RecordingSourceClient(detail("emerald_plus", "mid"), championList("emerald_plus"));
        OpggChampionCacheRepository repository = mock(OpggChampionCacheRepository.class);
        Instant now = Instant.parse("2026-05-23T16:01:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        OpggChampionDetailQuery query = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "mid");
        LocalDate cacheDate = LocalDate.of(2026, 5, 24);
        when(repository.findToday(query, cacheDate)).thenReturn(Optional.empty());
        OpggChampionService service = new OpggChampionService(
                source,
                Duration.ofMinutes(30),
                clock,
                cacheProperties(true),
                repository
        );

        OpggChampionDetail detail = service.getChampionDetail(query);

        assertThat(detail.championId()).isEqualTo(103);
        assertThat(source.calls()).isEqualTo(1);
        verify(repository).upsertToday(query, cacheDate, detail, now);
    }

    @Test
    void databaseListMissFetchesSourceAndWritesTodayCache() {
        RecordingSourceClient source = new RecordingSourceClient(detail("emerald_plus", "mid"), championList("emerald_plus"));
        OpggChampionCacheRepository repository = mock(OpggChampionCacheRepository.class);
        Instant now = Instant.parse("2026-05-23T16:01:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        OpggChampionListQuery query = new OpggChampionListQuery("ranked", "kr", "emerald_plus");
        LocalDate cacheDate = LocalDate.of(2026, 5, 24);
        when(repository.findToday(query, cacheDate)).thenReturn(Optional.empty());
        OpggChampionService service = new OpggChampionService(
                source,
                Duration.ofMinutes(30),
                clock,
                cacheProperties(true),
                repository
        );

        OpggChampionList list = service.getChampionList(query);

        assertThat(list.items()).hasSize(1);
        assertThat(source.calls()).isEqualTo(1);
        verify(repository).upsertToday(query, cacheDate, list, now);
    }

    @Test
    void failedSourceRequestDoesNotWriteDatabaseCache() {
        RecordingSourceClient source = new RecordingSourceClient(detail("emerald_plus", "mid"), championList("emerald_plus"));
        source.failNext();
        OpggChampionCacheRepository repository = mock(OpggChampionCacheRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-23T16:01:00Z"), ZoneOffset.UTC);
        OpggChampionDetailQuery query = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "mid");
        LocalDate cacheDate = LocalDate.of(2026, 5, 24);
        when(repository.findToday(query, cacheDate)).thenReturn(Optional.empty());
        OpggChampionService service = new OpggChampionService(
                source,
                Duration.ofMinutes(30),
                clock,
                cacheProperties(true),
                repository
        );

        assertThatThrownBy(() -> service.getChampionDetail(query))
                .isInstanceOf(OpggSourceException.class)
                .hasMessageContaining("temporary");

        verify(repository, never()).upsertToday(
                org.mockito.Mockito.eq(query),
                org.mockito.Mockito.eq(cacheDate),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any()
        );
    }

    @Test
    void memoryCacheDoesNotCrossShanghaiNaturalDay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-23T15:59:00Z"));
        RecordingSourceClient source = new RecordingSourceClient(detail("emerald_plus", "mid"), championList("emerald_plus"));
        OpggChampionCacheRepository repository = mock(OpggChampionCacheRepository.class);
        OpggChampionDetailQuery query = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "mid");
        when(repository.findToday(query, LocalDate.of(2026, 5, 23))).thenReturn(Optional.empty());
        when(repository.findToday(query, LocalDate.of(2026, 5, 24))).thenReturn(Optional.empty());
        OpggChampionService service = new OpggChampionService(
                source,
                Duration.ofMinutes(30),
                clock,
                cacheProperties(true),
                repository
        );

        service.getChampionDetail(query);
        clock.set(Instant.parse("2026-05-23T16:01:00Z"));
        service.getChampionDetail(query);

        assertThat(source.calls()).isEqualTo(2);
        verify(repository).upsertToday(
                org.mockito.Mockito.eq(query),
                org.mockito.Mockito.eq(LocalDate.of(2026, 5, 23)),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.eq(Instant.parse("2026-05-23T15:59:00Z"))
        );
        verify(repository).upsertToday(
                org.mockito.Mockito.eq(query),
                org.mockito.Mockito.eq(LocalDate.of(2026, 5, 24)),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.eq(Instant.parse("2026-05-23T16:01:00Z"))
        );
    }

    @Test
    void expiredCacheEntriesAreRefetched() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-23T04:00:00Z"));
        RecordingSourceClient source = new RecordingSourceClient(detailWithSkillOrders("emerald_plus", "mid"), championList("emerald_plus"));
        OpggChampionService service = new OpggChampionService(source, Duration.ofMinutes(30), clock);
        OpggChampionDetailQuery query = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "mid");

        service.getChampionDetail(query);
        clock.set(Instant.parse("2026-05-23T04:29:59Z"));
        service.getChampionDetail(query);
        clock.set(Instant.parse("2026-05-23T04:30:01Z"));
        service.getChampionDetail(query);

        assertThat(source.calls()).isEqualTo(2);
    }

    @Test
    void disabledDatabaseCacheKeepsExistingMemoryOnlyBehavior() {
        RecordingSourceClient source = new RecordingSourceClient(detailWithSkillOrders("emerald_plus", "mid"), championList("emerald_plus"));
        OpggChampionCacheRepository repository = mock(OpggChampionCacheRepository.class);
        OpggChampionService service = new OpggChampionService(
                source,
                Duration.ofMinutes(30),
                Clock.fixed(Instant.parse("2026-05-23T04:00:00Z"), ZoneOffset.UTC),
                cacheProperties(false),
                repository
        );
        OpggChampionDetailQuery query = new OpggChampionDetailQuery(103, "ranked", "kr", "emerald_plus", "mid");

        service.getChampionDetail(query);
        service.getChampionDetail(query);

        assertThat(source.calls()).isEqualTo(1);
        verifyNoInteractions(repository);
    }

    private static OpggChampionDetail detail(String tier, String position) {
        return new OpggChampionDetail(
                103,
                "Ahri",
                "ranked",
                "kr",
                tier,
                position,
                "16.10",
                Instant.parse("2026-05-23T04:00:00Z"),
                new OpggChampionStats(1000, 0.51, 0.12, 0.03, 2.6),
                List.of(new OpggBuildOption("spells", List.of(4, 12), List.of(), 100L, 0.52, 0.6)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static OpggChampionDetail detailWithSkillOrders(String tier, String position) {
        return new OpggChampionDetail(
                103,
                "Ahri",
                "ranked",
                "kr",
                tier,
                position,
                "16.10",
                Instant.parse("2026-05-23T04:00:00Z"),
                new OpggChampionStats(1000, 0.51, 0.12, 0.03, 2.6),
                List.of(new OpggBuildOption("spells", List.of(4, 12), List.of(), 100L, 0.52, 0.6)),
                List.of(),
                List.of(new OpggBuildOption("skill_order", List.of(3, 1, 2), List.of(3, 1, 2, 1, 4), 3000L, 0.6, 0.375)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static OpggChampionList championList(String tier) {
        return new OpggChampionList(
                "ranked",
                "kr",
                tier,
                "16.10",
                Instant.parse("2026-05-23T04:00:00Z"),
                List.of(new OpggChampionListItem(
                        103,
                        1,
                        7,
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

    private static OpggCacheProperties cacheProperties(boolean enabled) {
        return new OpggCacheProperties(enabled, "Asia/Shanghai", true, "0 20 4 * * *");
    }

    private static class RecordingSourceClient implements OpggSourceClient {
        private final OpggChampionDetail detail;
        private final OpggChampionList list;
        private final AtomicInteger calls = new AtomicInteger();
        private boolean failNext;

        private RecordingSourceClient(OpggChampionDetail detail, OpggChampionList list) {
            this.detail = detail;
            this.list = list;
        }

        @Override
        public OpggChampionDetail fetchChampionDetail(OpggChampionDetailQuery query) {
            calls.incrementAndGet();
            if (failNext) {
                failNext = false;
                throw new OpggSourceException("temporary source failure");
            }
            return detail;
        }

        @Override
        public OpggChampionList fetchChampionList(OpggChampionListQuery query) {
            calls.incrementAndGet();
            if (failNext) {
                failNext = false;
                throw new OpggSourceException("temporary source failure");
            }
            return list;
        }

        void failNext() {
            failNext = true;
        }

        int calls() {
            return calls.get();
        }
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
