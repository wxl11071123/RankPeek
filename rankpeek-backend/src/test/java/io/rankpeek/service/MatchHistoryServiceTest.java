package io.rankpeek.service;

import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.MatchHistoryFetchResult;
import io.rankpeek.model.Rank;
import io.rankpeek.model.RecordStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchHistoryServiceTest {

    @Mock
    private LcuHttpClient lcuHttpClient;

    private MatchHistoryService matchHistoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        matchHistoryService = new MatchHistoryService(lcuHttpClient);
        matchHistoryService.init();
    }

    @Test
    void getMatchHistoryFetchResult_usesCacheOnRepeatedReads() {
        when(lcuHttpClient.getObjectMapper()).thenReturn(objectMapper);
        when(lcuHttpClient.get(
                eq("lol-match-history/v1/products/lol/puuid-1/matches?begIndex=0&endIndex=49"),
                eq(JsonNode.class)
        )).thenReturn(createHistoryResponse(1L));

        MatchHistoryFetchResult first = matchHistoryService.getMatchHistoryFetchResult("puuid-1");
        MatchHistoryFetchResult second = matchHistoryService.getMatchHistoryFetchResult("puuid-1");

        assertThat(first.getMatches()).hasSize(1);
        assertThat(second.getMatches()).hasSize(1);
        verify(lcuHttpClient, times(1)).get(
                "lol-match-history/v1/products/lol/puuid-1/matches?begIndex=0&endIndex=49",
                JsonNode.class
        );
    }

    @Test
    void resolveRecordStatus_distinguishesNormalPrivateEmptyAndError() {
        MatchHistory normalMatch = new MatchHistory();
        normalMatch.setGameId(99L);

        MatchHistoryFetchResult normal = MatchHistoryFetchResult.builder()
                .matches(List.of(normalMatch))
                .rawEmpty(false)
                .build();
        MatchHistoryFetchResult rawEmpty = MatchHistoryFetchResult.builder()
                .matches(List.of())
                .rawEmpty(true)
                .build();
        MatchHistoryFetchResult error = MatchHistoryFetchResult.builder()
                .matches(List.of())
                .rawEmpty(false)
                .build();

        Rank rankWithGames = new Rank();
        Rank.QueueMap queueMap = new Rank.QueueMap();
        Rank.QueueInfo solo = new Rank.QueueInfo();
        solo.setWins(6);
        solo.setLosses(4);
        queueMap.setRankedSolo5x5(solo);
        rankWithGames.setQueueMap(queueMap);

        assertThat(matchHistoryService.resolveRecordStatus(normal, null)).isEqualTo(RecordStatus.NORMAL);
        assertThat(matchHistoryService.resolveRecordStatus(rawEmpty, rankWithGames)).isEqualTo(RecordStatus.PRIVATE);
        assertThat(matchHistoryService.resolveRecordStatus(rawEmpty, null)).isEqualTo(RecordStatus.EMPTY);
        assertThat(matchHistoryService.resolveRecordStatus(error, null)).isEqualTo(RecordStatus.ERROR);
        assertThat(matchHistoryService.resolveRecordStatus(null, null)).isEqualTo(RecordStatus.ERROR);
    }

    private JsonNode createHistoryResponse(long gameId) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode games = root.putArray("games");
        games.addObject().put("gameId", gameId);
        return root;
    }
}
