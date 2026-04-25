package io.rankpeek.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchHistoryPrewarmServiceTest {

    @Mock
    private SummonerService summonerService;
    @Mock
    private RankService rankService;
    @Mock
    private MatchHistoryService matchHistoryService;

    private MatchHistoryPrewarmService service;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        service = new MatchHistoryPrewarmService(
                summonerService,
                rankService,
                matchHistoryService,
                directExecutor
        );
    }

    @Test
    void prewarmPlayers_prewarmsUniquePuuidsOnly() {
        service.prewarmPlayers(List.of("player-a", "player-a", "player-b"), "test");

        verify(summonerService, times(1)).getSummonerByPuuid("player-a");
        verify(rankService, times(1)).getRankByPuuid("player-a");
        verify(matchHistoryService, times(1)).getMatchHistoryFetchResult("player-a", false);
        verify(summonerService, times(1)).getSummonerByPuuid("player-b");
        verify(rankService, times(1)).getRankByPuuid("player-b");
        verify(matchHistoryService, times(1)).getMatchHistoryFetchResult("player-b", false);
    }

    @Test
    void prewarmPlayers_skipsDuplicateSubmissionsWithinDedupeWindow() {
        service.prewarmPlayers(List.of("player-a"), "first");
        service.prewarmPlayers(List.of("player-a"), "second");

        verify(summonerService, times(1)).getSummonerByPuuid("player-a");
        verify(rankService, times(1)).getRankByPuuid("player-a");
        verify(matchHistoryService, times(1)).getMatchHistoryFetchResult("player-a", false);
    }

    @Test
    void prewarmPlayers_singlePlayerFailureDoesNotStopOtherPlayers() {
        doThrow(new RuntimeException("summoner failed"))
                .when(summonerService)
                .getSummonerByPuuid("player-a");

        service.prewarmPlayers(List.of("player-a", "player-b"), "test");

        verify(summonerService).getSummonerByPuuid("player-a");
        verify(summonerService).getSummonerByPuuid("player-b");
        verify(rankService).getRankByPuuid("player-b");
        verify(matchHistoryService).getMatchHistoryFetchResult("player-b", false);
    }

    @Test
    void prewarmPlayers_doesNotForceRefreshMatchHistory() {
        service.prewarmPlayers(List.of("player-a"), "test");

        verify(matchHistoryService).getMatchHistoryFetchResult("player-a", false);
        verify(matchHistoryService, never()).getMatchHistoryFetchResult("player-a", true);
    }
}
