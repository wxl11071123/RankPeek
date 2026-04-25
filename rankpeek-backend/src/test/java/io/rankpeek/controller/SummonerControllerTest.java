package io.rankpeek.controller;

import io.rankpeek.model.ApiResponse;
import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.Rank;
import io.rankpeek.model.Summoner;
import io.rankpeek.service.LcuHttpClient;
import io.rankpeek.service.MatchHistoryService;
import io.rankpeek.service.RankService;
import io.rankpeek.service.SummonerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummonerControllerTest {

    @Mock
    private SummonerService summonerService;
    @Mock
    private RankService rankService;
    @Mock
    private MatchHistoryService matchHistoryService;
    @Mock
    private LcuHttpClient lcuHttpClient;

    private SummonerController controller;

    @BeforeEach
    void setUp() {
        controller = new SummonerController(
                summonerService,
                rankService,
                matchHistoryService,
                lcuHttpClient
        );
    }

    @Test
    void getMatchHistory_passesForceRefreshToService() {
        MatchHistory match = new MatchHistory();
        match.setGameId(1L);
        when(matchHistoryService.getMatchHistory("puuid-1", 0, 9, true)).thenReturn(List.of(match));

        ApiResponse<List<MatchHistory>> response = controller.getMatchHistory("puuid-1", 0, 9, true);

        assertThat(response.getData()).containsExactly(match);
        verify(matchHistoryService).getMatchHistory("puuid-1", 0, 9, true);
    }

    @Test
    void getFilteredMatchHistory_passesForceRefreshToService() {
        MatchHistory match = new MatchHistory();
        match.setGameId(2L);
        when(matchHistoryService.getFilteredMatchHistory("puuid-1", 0, 49, 420, 22, 10, true))
                .thenReturn(List.of(match));

        ApiResponse<List<MatchHistory>> response = controller.getFilteredMatchHistory(
                "puuid-1",
                0,
                49,
                420,
                22,
                10,
                true
        );

        assertThat(response.getData()).containsExactly(match);
        verify(matchHistoryService).getFilteredMatchHistory("puuid-1", 0, 49, 420, 22, 10, true);
    }

    @Test
    void getRank_keepsExistingEndpointBehavior() {
        Rank rank = new Rank();
        when(rankService.getRankByPuuid("puuid-1")).thenReturn(rank);

        assertThat(controller.getRank("puuid-1").getData()).isSameAs(rank);
    }

    @Test
    void getSummonerByPuuid_keepsExistingEndpointBehavior() {
        Summoner summoner = new Summoner();
        when(summonerService.getSummonerByPuuid("puuid-1")).thenReturn(summoner);

        assertThat(controller.getSummonerByPuuid("puuid-1").getData()).isSameAs(summoner);
    }
}
