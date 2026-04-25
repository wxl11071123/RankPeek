package io.rankpeek.service;

import io.rankpeek.model.Lobby;
import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.Rank;
import io.rankpeek.model.Summoner;
import io.rankpeek.model.UserTagSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAnalysisServiceTest {

    @Mock
    private SummonerService summonerService;
    @Mock
    private RankService rankService;
    @Mock
    private MatchHistoryService matchHistoryService;
    @Mock
    private MatchHistoryRefreshService matchHistoryRefreshService;
    @Mock
    private MatchHistoryPrewarmService matchHistoryPrewarmService;
    @Mock
    private GameFlowService gameFlowService;
    @Mock
    private ChampionSelectService championSelectService;
    @Mock
    private UserTagService userTagService;

    private SessionAnalysisService service;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        service = new SessionAnalysisService(
                summonerService,
                rankService,
                matchHistoryService,
                matchHistoryRefreshService,
                matchHistoryPrewarmService,
                gameFlowService,
                championSelectService,
                userTagService,
                directExecutor
        );
    }

    @Test
    void sessionAnalysisRequestsFiftyInclusiveMatchHistoryRowsForLobbyMembers() {
        Summoner me = new Summoner();
        me.setPuuid("my-puuid");
        when(summonerService.getMySummoner()).thenReturn(me);
        when(gameFlowService.getGamePhase()).thenReturn("Lobby");

        Lobby lobby = new Lobby();
        Lobby.Member member = new Lobby.Member();
        member.setPuuid("player-puuid");
        lobby.setMembers(List.of(member));
        lobby.setQueueId(420);
        when(gameFlowService.getLobby()).thenReturn(lobby);

        Summoner player = new Summoner();
        player.setPuuid("player-puuid");
        when(summonerService.getSummonerByPuuid("player-puuid")).thenReturn(player);

        Rank rank = new Rank();
        when(rankService.getRankByPuuid("player-puuid")).thenReturn(rank);

        List<MatchHistory> history = List.of(new MatchHistory());
        when(matchHistoryService.getMatchHistory("player-puuid", 0, 49)).thenReturn(history);
        when(userTagService.buildSummaryFromPrefetchedData(eq("player-puuid"), eq(420), eq(rank), eq(history)))
                .thenReturn(UserTagSummary.builder().build());

        service.getSessionData(420);

        verify(matchHistoryService).getMatchHistory("player-puuid", 0, 49);
        verify(matchHistoryService, never()).getMatchHistory(any(String.class), eq(0), eq(50));
    }
}
