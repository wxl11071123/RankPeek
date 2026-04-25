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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    void getMatchHistory_hydratesOnlyVisibleIncompleteRostersAndSavesHydratedCache() {
        List<MatchHistory> cachedMatches = new ArrayList<>();
        for (long gameId = 1; gameId <= 12; gameId++) {
            cachedMatches.add(createIncompleteMatch(gameId, "puuid-1"));
        }
        when(repository.findRecentMatchHistory("puuid-1", 50))
                .thenReturn(Optional.of(MatchHistoryFetchResult.builder()
                        .matches(cachedMatches)
                        .rawEmpty(false)
                        .build()));
        when(repository.findGameDetail(anyLong())).thenReturn(Optional.empty());
        when(lcuHttpClient.get(anyString(), eq(GameDetail.class)))
                .thenAnswer(invocation -> createDetail(gameIdFromDetailUri(invocation.getArgument(0))));

        List<MatchHistory> result = service.getMatchHistory("puuid-1", 0, 9, false);

        assertThat(result).hasSize(10);
        assertThat(result)
                .allSatisfy(match -> {
                    assertThat(match.getParticipants()).hasSize(10);
                    assertThat(match.getParticipantIdentities()).hasSize(10);
                });
        verify(lcuHttpClient, times(10)).get(anyString(), eq(GameDetail.class));
        verify(lcuHttpClient, never()).get("lol-match-history/v1/games/11", GameDetail.class);
        verify(lcuHttpClient, never()).get("lol-match-history/v1/games/12", GameDetail.class);
        verify(repository).saveMatchHistory(eq("puuid-1"), argThat(matches ->
                matches.size() == 12
                        && matches.get(0).getParticipants().size() == 10
                        && matches.get(9).getParticipantIdentities().size() == 10
                        && matches.get(10).getParticipants().size() == 1
        ));
    }

    @Test
    void getFilteredMatchHistory_hydratesSlicedFilteredMatches() {
        List<MatchHistory> cachedMatches = List.of(
                createIncompleteMatch(21L, "puuid-1", 420, 11),
                createIncompleteMatch(22L, "puuid-1", 420, 22),
                createIncompleteMatch(23L, "puuid-1", 420, 22)
        );
        when(repository.findRecentMatchHistory("puuid-1", 50))
                .thenReturn(Optional.of(MatchHistoryFetchResult.builder()
                        .matches(cachedMatches)
                        .rawEmpty(false)
                        .build()));
        when(repository.findGameDetail(anyLong())).thenReturn(Optional.empty());
        when(lcuHttpClient.get(anyString(), eq(GameDetail.class)))
                .thenAnswer(invocation -> createDetail(gameIdFromDetailUri(invocation.getArgument(0))));

        List<MatchHistory> result = service.getFilteredMatchHistory("puuid-1", 0, 9, 420, 22, 10, false);

        assertThat(result).extracting(MatchHistory::getGameId).containsExactly(22L, 23L);
        assertThat(result)
                .allSatisfy(match -> {
                    assertThat(match.getParticipants()).hasSize(10);
                    assertThat(match.getParticipantIdentities()).hasSize(10);
                });
        verify(lcuHttpClient, times(2)).get(anyString(), eq(GameDetail.class));
        verify(lcuHttpClient, never()).get("lol-match-history/v1/games/21", GameDetail.class);
    }

    private MatchHistory createMatch(long gameId) {
        MatchHistory match = new MatchHistory();
        match.setGameId(gameId);
        match.setGameCreation(1710000000000L + gameId);
        return match;
    }

    private MatchHistory createIncompleteMatch(long gameId, String targetPuuid) {
        return createIncompleteMatch(gameId, targetPuuid, 420, 101);
    }

    private MatchHistory createIncompleteMatch(long gameId, String targetPuuid, int queueId, int championId) {
        MatchHistory match = createMatch(gameId);
        match.setQueueId(queueId);
        match.setParticipants(List.of(createParticipant(1, 100, championId)));
        match.setParticipantIdentities(List.of(createIdentity(1, targetPuuid)));
        return match;
    }

    private GameDetail createDetail(long gameId) {
        GameDetail detail = new GameDetail();
        detail.setGameId(gameId);
        detail.setQueueId(420);
        detail.setGameCreation(1710000000000L + gameId);
        detail.setGameDuration(1800L);

        List<GameDetail.GameParticipant> participants = new ArrayList<>();
        List<GameDetail.ParticipantIdentity> identities = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            GameDetail.GameParticipant participant = new GameDetail.GameParticipant();
            participant.setParticipantId(i);
            participant.setTeamId(i <= 5 ? 100 : 200);
            participant.setChampionId(100 + i);
            participant.setSpell1Id(4);
            participant.setSpell2Id(14);
            GameDetail.Stats stats = new GameDetail.Stats();
            stats.setWin(i <= 5);
            stats.setKills(i);
            stats.setDeaths(2);
            stats.setAssists(3);
            stats.setGoldEarned(9000L + i);
            stats.setTotalDamageDealtToChampions(12000L + i);
            stats.setTotalDamageTaken(15000L + i);
            stats.setTotalMinionsKilled(150 + i);
            stats.setNeutralMinionsKilled(5);
            participant.setStats(stats);
            participants.add(participant);

            identities.add(createDetailIdentity(i, i == 1 ? "puuid-1" : "player-" + i));
        }
        detail.setParticipants(participants);
        detail.setParticipantIdentities(identities);
        return detail;
    }

    private MatchHistory.Participant createParticipant(int participantId, int teamId, int championId) {
        MatchHistory.Participant participant = new MatchHistory.Participant();
        participant.setParticipantId(participantId);
        participant.setTeamId(teamId);
        participant.setChampionId(championId);
        participant.setSpell1Id(4);
        participant.setSpell2Id(14);
        MatchHistory.Stats stats = new MatchHistory.Stats();
        stats.setWin(teamId == 100);
        stats.setKills(participantId);
        stats.setDeaths(2);
        stats.setAssists(3);
        participant.setStats(stats);
        return participant;
    }

    private MatchHistory.ParticipantIdentity createIdentity(int participantId, String puuid) {
        MatchHistory.ParticipantIdentity identity = new MatchHistory.ParticipantIdentity();
        identity.setParticipantId(participantId);
        MatchHistory.Player player = new MatchHistory.Player();
        player.setPuuid(puuid);
        player.setGameName("Player" + participantId);
        player.setTagLine("CN1");
        player.setSummonerName("Player" + participantId);
        identity.setPlayer(player);
        return identity;
    }

    private GameDetail.ParticipantIdentity createDetailIdentity(int participantId, String puuid) {
        GameDetail.ParticipantIdentity identity = new GameDetail.ParticipantIdentity();
        identity.setParticipantId(participantId);
        GameDetail.Player player = new GameDetail.Player();
        player.setPuuid(puuid);
        player.setGameName("DetailPlayer" + participantId);
        player.setTagLine("CN1");
        player.setSummonerName("DetailPlayer" + participantId);
        identity.setPlayer(player);
        return identity;
    }

    private long gameIdFromDetailUri(String uri) {
        return Long.parseLong(uri.substring(uri.lastIndexOf('/') + 1));
    }
}
