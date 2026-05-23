package io.rankpeek.server.opgg;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OpggCacheCleanupSchedulerTest {

    @Test
    void disabledCleanupDoesNotDeleteCacheRows() {
        OpggChampionCacheRepository repository = mock(OpggChampionCacheRepository.class);
        OpggCacheCleanupScheduler scheduler = new OpggCacheCleanupScheduler(
                new OpggCacheProperties(true, "Asia/Shanghai", false, "0 20 4 * * *"),
                repository,
                Clock.fixed(Instant.parse("2026-05-23T16:01:00Z"), ZoneOffset.UTC)
        );

        scheduler.runDailyCleanup();

        verifyNoInteractions(repository);
    }

    @Test
    void enabledCleanupDeletesRowsBeforeCurrentShanghaiDate() {
        OpggChampionCacheRepository repository = mock(OpggChampionCacheRepository.class);
        OpggCacheCleanupScheduler scheduler = new OpggCacheCleanupScheduler(
                new OpggCacheProperties(true, "Asia/Shanghai", true, "0 20 4 * * *"),
                repository,
                Clock.fixed(Instant.parse("2026-05-23T16:01:00Z"), ZoneOffset.UTC)
        );

        scheduler.runDailyCleanup();

        verify(repository).deleteBefore(LocalDate.of(2026, 5, 24));
    }
}
