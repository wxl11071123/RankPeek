package io.rankpeek.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchHistoryRefreshServiceTest {

    @Mock
    private MatchHistoryService matchHistoryService;
    @Mock
    private RankService rankService;
    @Mock
    private ScheduledExecutorService scheduler;

    private MatchHistoryRefreshService refreshService;

    @BeforeEach
    void setUp() {
        when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(mock(ScheduledFuture.class));
        refreshService = new MatchHistoryRefreshService(matchHistoryService, rankService, scheduler);
    }

    @Test
    void refreshAfterGameEnd_invalidatesPlayersAndSchedulesDelayedRefreshes() {
        refreshService.rememberSessionPuuids(List.of("recent-1", "recent-2", "", "  "));

        refreshService.refreshAfterGameEnd("current-1");

        verify(matchHistoryService).refreshCache("recent-1");
        verify(matchHistoryService).refreshCache("recent-2");
        verify(matchHistoryService).refreshCache("current-1");
        verify(rankService).refreshCache("recent-1");
        verify(rankService).refreshCache("recent-2");
        verify(rankService).refreshCache("current-1");
        verify(scheduler).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS));
        verify(scheduler).schedule(any(Runnable.class), eq(20L), eq(TimeUnit.SECONDS));
        verify(scheduler).schedule(any(Runnable.class), eq(60L), eq(TimeUnit.SECONDS));

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(3)).schedule(runnableCaptor.capture(), anyLong(), eq(TimeUnit.SECONDS));

        runnableCaptor.getAllValues().getFirst().run();

        verify(matchHistoryService, times(2)).refreshCache("recent-1");
        verify(rankService, times(2)).refreshCache("recent-1");
        verify(matchHistoryService).getMatchHistoryFetchResult("recent-1", true);
        verify(matchHistoryService).getMatchHistoryFetchResult("recent-2", true);
        verify(matchHistoryService).getMatchHistoryFetchResult("current-1", true);
    }

    @Test
    void refreshAfterGameEnd_deduplicatesDelayedTaskGroupsWithinSixtySeconds() {
        refreshService.rememberSessionPuuids(List.of("recent-1"));
        refreshService.refreshAfterGameEnd("current-1");

        refreshService.rememberSessionPuuids(List.of("recent-1"));
        refreshService.refreshAfterGameEnd("current-1");

        verify(scheduler, times(3)).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.SECONDS));
    }
}
