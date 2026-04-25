package io.rankpeek.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.rankpeek.cache.MatchHistoryCacheRepository;
import io.rankpeek.model.GameDetail;
import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.MatchHistoryFetchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchHistoryServiceCacheTest {

    @Mock
    private LcuHttpClient lcuHttpClient;
    @Mock
    private MatchHistoryCacheRepository repository;

    private MatchHistoryService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new MatchHistoryService(lcuHttpClient, repository);
        service.init();
    }

    @Test
    void getMatchHistoryFetchResult_usesDatabaseOnMemoryMiss() {
        MatchHistory cachedMatch = createMatch(1L);
        when(repository.findRecentMatchHistory("puuid-1", 50))
                .thenReturn(Optional.of(MatchHistoryFetchResult.builder()
                        .matches(List.of(cachedMatch))
                        .rawEmpty(false)
                        .build()));

        MatchHistoryFetchResult result = service.getMatchHistoryFetchResult("puuid-1", false);

        assertThat(result.getMatches()).extracting(MatchHistory::getGameId).containsExactly(1L);
        verify(lcuHttpClient, never()).get(any(String.class), eq(JsonNode.class));
    }

    @Test
    void getMatchHistoryFetchResult_forceRefreshFetchesLcuAndSavesDatabase() {
        ObjectNode response = objectMapper.createObjectNode();
        response.putArray("games").addObject().put("gameId", 2L);
        when(lcuHttpClient.getObjectMapper()).thenReturn(objectMapper);
        when(lcuHttpClient.get(
                "lol-match-history/v1/products/lol/puuid-1/matches?begIndex=0&endIndex=49",
                JsonNode.class
        )).thenReturn(response);

        MatchHistoryFetchResult result = service.getMatchHistoryFetchResult("puuid-1", true);

        assertThat(result.getMatches()).extracting(MatchHistory::getGameId).containsExactly(2L);
        verify(repository).saveMatchHistory(eq("puuid-1"), any());
    }

    @Test
    void getMatchHistoryFetchResult_forceRefreshFallsBackToDatabaseWhenLcuFails() {
        MatchHistory staleMatch = createMatch(3L);
        when(repository.findRecentMatchHistory("puuid-1", 50))
                .thenReturn(Optional.of(MatchHistoryFetchResult.builder()
                        .matches(List.of(staleMatch))
                        .rawEmpty(false)
                        .build()));
        when(lcuHttpClient.get(
                "lol-match-history/v1/products/lol/puuid-1/matches?begIndex=0&endIndex=49",
                JsonNode.class
        )).thenThrow(new RuntimeException("LCU down"));

        MatchHistoryFetchResult result = service.getMatchHistoryFetchResult("puuid-1", true);

        assertThat(result.getMatches()).extracting(MatchHistory::getGameId).containsExactly(3L);
    }

    @Test
    void getGameDetailById_usesDatabaseBeforeLcu() {
        GameDetail cached = new GameDetail();
        cached.setGameId(99L);
        when(repository.findGameDetail(99L)).thenReturn(Optional.of(cached));

        GameDetail result = service.getGameDetailById(99L);

        assertThat(result).isSameAs(cached);
        verify(lcuHttpClient, never()).get("lol-match-history/v1/games/99", GameDetail.class);
    }

    private MatchHistory createMatch(long gameId) {
        MatchHistory match = new MatchHistory();
        match.setGameId(gameId);
        match.setGameCreation(1710000000000L + gameId);
        return match;
    }
}
