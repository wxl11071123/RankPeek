package io.rankpeek.service;

import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.MatchHistoryFetchResult;
import io.rankpeek.model.Rank;
import io.rankpeek.model.RankTag;
import io.rankpeek.model.RecordStatus;
import io.rankpeek.model.UserTagSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTagServiceTest {

    @Mock
    private LcuHttpClient lcuHttpClient;
    @Mock
    private SummonerService summonerService;
    @Mock
    private MatchHistoryService matchHistoryService;
    @Mock
    private TagConfigService tagConfigService;
    @Mock
    private RankService rankService;

    private UserTagService userTagService;

    @BeforeEach
    void setUp() {
        userTagService = new UserTagService(
                lcuHttpClient,
                summonerService,
                matchHistoryService,
                tagConfigService,
                rankService
        );
    }

    @Test
    void buildSummaryFromPrefetchedData_doesNotLoadGameDetail() {
        mockDefaultTags();
        MatchHistory history = createMatch("self-puuid", 1L, true);

        UserTagSummary summary = userTagService.buildSummaryFromPrefetchedData(
                "self-puuid",
                0,
                null,
                List.of(history)
        );

        assertThat(summary.getRecordStatus()).isEqualTo(RecordStatus.NORMAL);
        assertThat(summary.getRecentData().getSelectWins()).isEqualTo(1);
        assertThat(summary.getTag()).isNotEmpty();
        verifyNoInteractions(lcuHttpClient);
    }

    @Test
    void getUserTagSummaryBatch_deduplicatesRequestsAndReturnsStatuses() {
        mockDefaultTags();

        MatchHistoryFetchResult normalResult = MatchHistoryFetchResult.builder()
                .matches(List.of(createMatch("dup-puuid", 7L, true)))
                .rawEmpty(false)
                .build();
        MatchHistoryFetchResult privateResult = MatchHistoryFetchResult.builder()
                .matches(List.of())
                .rawEmpty(true)
                .build();
        MatchHistoryFetchResult emptyResult = MatchHistoryFetchResult.builder()
                .matches(List.of())
                .rawEmpty(true)
                .build();

        MatchHistoryService statusResolver = new MatchHistoryService(lcuHttpClient);

        when(rankService.getRankByPuuid("dup-puuid")).thenReturn(null);
        when(rankService.getRankByPuuid("private-puuid")).thenReturn(createRankWithGames());
        when(rankService.getRankByPuuid("empty-puuid")).thenReturn(null);
        when(rankService.getRankByPuuid("error-puuid")).thenReturn(null);

        when(matchHistoryService.getMatchHistoryFetchResult("dup-puuid")).thenReturn(normalResult);
        when(matchHistoryService.getMatchHistoryFetchResult("private-puuid")).thenReturn(privateResult);
        when(matchHistoryService.getMatchHistoryFetchResult("empty-puuid")).thenReturn(emptyResult);
        when(matchHistoryService.getMatchHistoryFetchResult("error-puuid")).thenThrow(new RuntimeException("boom"));
        when(matchHistoryService.resolveRecordStatus(any(), any()))
                .thenAnswer(invocation -> statusResolver.resolveRecordStatus(
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));

        Map<String, UserTagSummary> summaries = userTagService.getUserTagSummaryBatch(
                List.of("dup-puuid", "dup-puuid", "private-puuid", "empty-puuid", "error-puuid"),
                0
        );

        assertThat(summaries).hasSize(4);
        assertThat(summaries.get("dup-puuid").getRecordStatus()).isEqualTo(RecordStatus.NORMAL);
        assertThat(summaries.get("private-puuid").getRecordStatus()).isEqualTo(RecordStatus.PRIVATE);
        assertThat(summaries.get("empty-puuid").getRecordStatus()).isEqualTo(RecordStatus.EMPTY);
        assertThat(summaries.get("error-puuid").getRecordStatus()).isEqualTo(RecordStatus.ERROR);

        verify(matchHistoryService, times(1)).getMatchHistoryFetchResult("dup-puuid");
    }

    @Test
    void getUserTagSummaryBatch_returnsEmptyMapForEmptyInput() {
        Map<String, UserTagSummary> summaries = userTagService.getUserTagSummaryBatch(List.of(), 0);

        assertThat(summaries).isEmpty();
        verifyNoInteractions(matchHistoryService, rankService);
    }

    private void mockDefaultTags() {
        when(tagConfigService.evaluateTags(anyList(), anyString(), anyInt())).thenReturn(List.of(
                RankTag.builder()
                        .good(true)
                        .tagName("High Win Rate")
                        .tagDesc("Test tag")
                        .build()
        ));
    }

    private Rank createRankWithGames() {
        Rank rank = new Rank();
        Rank.QueueMap queueMap = new Rank.QueueMap();
        Rank.QueueInfo solo = new Rank.QueueInfo();
        solo.setWins(6);
        solo.setLosses(4);
        queueMap.setRankedSolo5x5(solo);
        rank.setQueueMap(queueMap);
        return rank;
    }

    private MatchHistory createMatch(String selfPuuid, long gameId, boolean selfWin) {
        MatchHistory history = new MatchHistory();
        history.setGameId(gameId);
        history.setQueueId(420);
        history.setGameCreation(1710000000000L + gameId);

        MatchHistory.Participant me = new MatchHistory.Participant();
        me.setParticipantId(1);
        me.setTeamId(100);
        me.setChampionId(11);
        me.setStats(createStats(selfWin, 10, 2, 8, 12000, 18000, 11000));

        MatchHistory.Participant teammate = new MatchHistory.Participant();
        teammate.setParticipantId(2);
        teammate.setTeamId(100);
        teammate.setChampionId(22);
        teammate.setStats(createStats(selfWin, 4, 5, 9, 9800, 12000, 15000));

        MatchHistory.Participant enemy = new MatchHistory.Participant();
        enemy.setParticipantId(3);
        enemy.setTeamId(200);
        enemy.setChampionId(55);
        enemy.setStats(createStats(!selfWin, 6, 6, 4, 10200, 14000, 10000));

        history.setParticipants(List.of(me, teammate, enemy));

        MatchHistory.ParticipantIdentity meIdentity = new MatchHistory.ParticipantIdentity();
        meIdentity.setParticipantId(1);
        meIdentity.setPlayer(createPlayer(selfPuuid, "Self"));

        MatchHistory.ParticipantIdentity teammateIdentity = new MatchHistory.ParticipantIdentity();
        teammateIdentity.setParticipantId(2);
        teammateIdentity.setPlayer(createPlayer("friend-puuid", "Friend"));

        MatchHistory.ParticipantIdentity enemyIdentity = new MatchHistory.ParticipantIdentity();
        enemyIdentity.setParticipantId(3);
        enemyIdentity.setPlayer(createPlayer("enemy-puuid", "Enemy"));

        history.setParticipantIdentities(List.of(meIdentity, teammateIdentity, enemyIdentity));
        return history;
    }

    private MatchHistory.Stats createStats(boolean win,
                                           int kills,
                                           int deaths,
                                           int assists,
                                           int gold,
                                           int damage,
                                           int taken) {
        MatchHistory.Stats stats = new MatchHistory.Stats();
        stats.setWin(win);
        stats.setKills(kills);
        stats.setDeaths(deaths);
        stats.setAssists(assists);
        stats.setGoldEarned(gold);
        stats.setTotalDamageDealtToChampions(damage);
        stats.setTotalDamageTaken(taken);
        stats.setTotalHeal(2000);
        stats.setTotalMinionsKilled(150);
        stats.setNeutralMinionsKilled(12);
        return stats;
    }

    private MatchHistory.Player createPlayer(String puuid, String gameName) {
        MatchHistory.Player player = new MatchHistory.Player();
        player.setPuuid(puuid);
        player.setGameName(gameName);
        player.setTagLine("CN1");
        player.setSummonerName(gameName);
        return player;
    }
}
