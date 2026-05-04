package io.rankpeek.service;

import io.rankpeek.model.Lobby;
import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.Rank;
import io.rankpeek.model.RankTag;
import io.rankpeek.model.ScoutTagContext;
import io.rankpeek.model.ScoutTagSample;
import io.rankpeek.model.GameSession;
import io.rankpeek.model.Summoner;
import io.rankpeek.model.ChampionSelectSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAnalysisServiceTest {

    @Mock
    private SummonerService summonerService;
    @Mock
    private RankService rankService;
    @Mock
    private MatchHistoryRefreshService matchHistoryRefreshService;
    @Mock
    private GameFlowService gameFlowService;
    @Mock
    private ChampionSelectService championSelectService;
    @Mock
    private ScoutTagSampleService scoutTagSampleService;
    @Mock
    private ScoutTagRuleService scoutTagRuleService;

    private SessionAnalysisService service;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        service = new SessionAnalysisService(
                summonerService,
                rankService,
                matchHistoryRefreshService,
                gameFlowService,
                championSelectService,
                scoutTagSampleService,
                scoutTagRuleService,
                directExecutor
        );
    }

    @Test
    void lobbyPhaseUsesLobbyQueueIdOverProvidedMode() {
        Summoner me = new Summoner();
        me.setPuuid("my-puuid");
        when(summonerService.getMySummoner()).thenReturn(me);
        when(gameFlowService.getGamePhase()).thenReturn("Lobby");

        Lobby lobby = new Lobby();
        Lobby.Member member = new Lobby.Member();
        member.setPuuid("player-puuid");
        lobby.setMembers(List.of(member));
        lobby.setQueueId(450);
        when(gameFlowService.getLobby()).thenReturn(lobby);

        Summoner player = new Summoner();
        player.setPuuid("player-puuid");
        when(summonerService.getSummonerByPuuid("player-puuid")).thenReturn(player);

        Rank rank = new Rank();
        when(rankService.getRankByPuuid("player-puuid")).thenReturn(rank);

        List<MatchHistory> history = createMatches(20, 450);
        ScoutTagSample sample = stubScout("player-puuid", 450, history);

        var data = service.getSessionData(420);

        assertThat(data.getQueueId()).isEqualTo(450);
        verify(scoutTagSampleService).getCurrentModeSample("player-puuid", 450, 50, 20);
        verify(scoutTagRuleService).buildTags(
                anyContextWithQueueAndTeam(450, List.of("player-puuid")),
                eq(sample)
        );
    }

    @Test
    void lobbyPhaseUsesGameConfigQueueIdWhenLobbyQueueIsMissing() {
        Summoner me = new Summoner();
        me.setPuuid("my-puuid");
        when(summonerService.getMySummoner()).thenReturn(me);
        when(gameFlowService.getGamePhase()).thenReturn("Lobby");

        Lobby lobby = new Lobby();
        Lobby.Member member = new Lobby.Member();
        member.setPuuid("player-puuid");
        lobby.setMembers(List.of(member));
        Lobby.GameConfig gameConfig = new Lobby.GameConfig();
        gameConfig.setQueueId(440);
        lobby.setGameConfig(gameConfig);
        when(gameFlowService.getLobby()).thenReturn(lobby);

        Summoner player = new Summoner();
        player.setPuuid("player-puuid");
        when(summonerService.getSummonerByPuuid("player-puuid")).thenReturn(player);

        Rank rank = new Rank();
        when(rankService.getRankByPuuid("player-puuid")).thenReturn(rank);

        stubScout("player-puuid", 440, createMatches(20, 440));

        service.getSessionData(null);

        verify(scoutTagSampleService).getCurrentModeSample("player-puuid", 440, 50, 20);
    }

    @Test
    void sessionAnalysisUsesZeroWhenCurrentQueueIsMissingEvenIfModeIsProvided() {
        Summoner me = new Summoner();
        me.setPuuid("my-puuid");
        when(summonerService.getMySummoner()).thenReturn(me);
        when(gameFlowService.getGamePhase()).thenReturn("Lobby");

        Lobby lobby = new Lobby();
        Lobby.Member member = new Lobby.Member();
        member.setPuuid("player-puuid");
        lobby.setMembers(List.of(member));
        when(gameFlowService.getLobby()).thenReturn(lobby);

        Summoner player = new Summoner();
        player.setPuuid("player-puuid");
        when(summonerService.getSummonerByPuuid("player-puuid")).thenReturn(player);

        Rank rank = new Rank();
        when(rankService.getRankByPuuid("player-puuid")).thenReturn(rank);

        ScoutTagSample sample = stubScout("player-puuid", 0, createMatches(20, 450));

        service.getSessionData(420);

        verify(scoutTagSampleService).getCurrentModeSample("player-puuid", 0, 50, 20);
        verify(scoutTagRuleService).buildTags(anyContextWithQueueAndTeam(0, List.of("player-puuid")), eq(sample));
    }

    @Test
    void champSelectPhaseUsesGameSessionQueueId() {
        Summoner me = new Summoner();
        me.setPuuid("my-puuid");
        when(summonerService.getMySummoner()).thenReturn(me);
        when(gameFlowService.getGamePhase()).thenReturn("ChampSelect");

        ChampionSelectSession selectSession = new ChampionSelectSession();
        ChampionSelectSession.Player player = new ChampionSelectSession.Player();
        player.setPuuid("player-puuid");
        player.setChampionId(901);
        selectSession.setMyTeam(List.of(player));
        selectSession.setTheirTeam(List.of());
        when(championSelectService.getChampionSelectSession()).thenReturn(selectSession);
        when(gameFlowService.getGameSession()).thenReturn(gameSession(1700, List.of(), List.of()));

        Summoner summoner = new Summoner();
        summoner.setPuuid("player-puuid");
        when(summonerService.getSummonerByPuuid("player-puuid")).thenReturn(summoner);
        when(rankService.getRankByPuuid("player-puuid")).thenReturn(new Rank());
        stubScout("player-puuid", 1700, createMatches(20, 1700));

        var data = service.getSessionData(420);

        assertThat(data.getQueueId()).isEqualTo(1700);
        verify(scoutTagSampleService).getCurrentModeSample("player-puuid", 1700, 50, 20);
    }

    @Test
    void gamePhaseUsesGameSessionQueueId() {
        Summoner me = new Summoner();
        me.setPuuid("my-puuid");
        when(summonerService.getMySummoner()).thenReturn(me);
        when(gameFlowService.getGamePhase()).thenReturn("InProgress");

        GameSession.OnePlayer player = new GameSession.OnePlayer();
        player.setPuuid("player-puuid");
        player.setChampionId(221);
        player.setSelectedPosition("MIDDLE");
        when(gameFlowService.getGameSession()).thenReturn(gameSession(2400, List.of(player), List.of()));

        Summoner summoner = new Summoner();
        summoner.setPuuid("player-puuid");
        when(summonerService.getSummonerByPuuid("player-puuid")).thenReturn(summoner);
        when(rankService.getRankByPuuid("player-puuid")).thenReturn(new Rank());
        ScoutTagSample sample = stubScout("player-puuid", 2400, createMatches(20, 2400));

        var data = service.getSessionData(null);

        assertThat(data.getQueueId()).isEqualTo(2400);
        verify(scoutTagSampleService).getCurrentModeSample("player-puuid", 2400, 50, 20);
        verify(scoutTagRuleService).buildTags(
                org.mockito.ArgumentMatchers.argThat(context ->
                        context.getCurrentQueueId() == 2400
                                && context.getCurrentTeamPuuids().contains("player-puuid")
                                && "MIDDLE".equals(context.getCurrentPosition())
                                && Integer.valueOf(221).equals(context.getCurrentChampionId())),
                eq(sample)
        );
    }

    @Test
    void scoutRecentDataIncludesGoldAndDamageRatesForGamingDamageConversion() {
        Summoner me = new Summoner();
        me.setPuuid("my-puuid");
        when(summonerService.getMySummoner()).thenReturn(me);
        when(gameFlowService.getGamePhase()).thenReturn("InProgress");

        GameSession.OnePlayer player = new GameSession.OnePlayer();
        player.setPuuid("player-puuid");
        player.setChampionId(221);
        when(gameFlowService.getGameSession()).thenReturn(gameSession(420, List.of(player), List.of()));

        Summoner summoner = new Summoner();
        summoner.setPuuid("player-puuid");
        when(summonerService.getSummonerByPuuid("player-puuid")).thenReturn(summoner);
        when(rankService.getRankByPuuid("player-puuid")).thenReturn(new Rank());

        stubScout("player-puuid", 420, List.of(createMatchWithParticipantStats("player-puuid", 10000, 20000, 10000, 20000)));

        var data = service.getSessionData(null);
        var recentData = data.getTeamOne().getFirst().getUserTag().getRecentData();

        assertThat(recentData.getAverageGold()).isEqualTo(10000);
        assertThat(recentData.getAverageDamageDealtToChampions()).isEqualTo(20000);
        assertThat(recentData.getGoldRate()).isEqualTo(50);
        assertThat(recentData.getDamageDealtToChampionsRate()).isEqualTo(50);
    }

    @Test
    void scoutRecentDataDoesNotTreatMissingDamageConversionFieldsAsZero() {
        Summoner me = new Summoner();
        me.setPuuid("my-puuid");
        when(summonerService.getMySummoner()).thenReturn(me);
        when(gameFlowService.getGamePhase()).thenReturn("InProgress");

        GameSession.OnePlayer player = new GameSession.OnePlayer();
        player.setPuuid("player-puuid");
        player.setChampionId(221);
        when(gameFlowService.getGameSession()).thenReturn(gameSession(420, List.of(player), List.of()));

        Summoner summoner = new Summoner();
        summoner.setPuuid("player-puuid");
        when(summonerService.getSummonerByPuuid("player-puuid")).thenReturn(summoner);
        when(rankService.getRankByPuuid("player-puuid")).thenReturn(new Rank());

        stubScout("player-puuid", 420, List.of(
                createMatchWithParticipantStats("player-puuid", 10000, 20000, 10000, 20000),
                createMatchWithMissingEconomyStats("player-puuid")
        ));

        var data = service.getSessionData(null);
        var recentData = data.getTeamOne().getFirst().getUserTag().getRecentData();

        assertThat(recentData.getAverageGold()).isEqualTo(10000);
        assertThat(recentData.getAverageDamageDealtToChampions()).isEqualTo(20000);
        assertThat(recentData.getGoldRate()).isEqualTo(50);
        assertThat(recentData.getDamageDealtToChampionsRate()).isEqualTo(50);
    }

    @Test
    void scoutRecentDataCalculatesDamageConversionFromSingleParticipantStats() {
        Summoner me = new Summoner();
        me.setPuuid("my-puuid");
        when(summonerService.getMySummoner()).thenReturn(me);
        when(gameFlowService.getGamePhase()).thenReturn("InProgress");

        GameSession.OnePlayer player = new GameSession.OnePlayer();
        player.setPuuid("player-puuid");
        player.setChampionId(221);
        when(gameFlowService.getGameSession()).thenReturn(gameSession(420, List.of(player), List.of()));

        Summoner summoner = new Summoner();
        summoner.setPuuid("player-puuid");
        when(summonerService.getSummonerByPuuid("player-puuid")).thenReturn(summoner);
        when(rankService.getRankByPuuid("player-puuid")).thenReturn(new Rank());

        stubScout("player-puuid", 420, List.of(createSingleParticipantMatch("player-puuid", 12345, 23456)));

        var data = service.getSessionData(null);
        var recentData = data.getTeamOne().getFirst().getUserTag().getRecentData();

        assertThat(recentData.getAverageGold()).isEqualTo(12345);
        assertThat(recentData.getAverageDamageDealtToChampions()).isEqualTo(23456);
    }

    private List<MatchHistory> createMatches(int count, int queueId) {
        return createMixedMatches(count, count, queueId, queueId + 1);
    }

    private List<MatchHistory> createMixedMatches(int count, int selectedCount, int selectedQueueId, int otherQueueId) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> {
                    MatchHistory history = new MatchHistory();
                    history.setQueueId(index < selectedCount ? selectedQueueId : otherQueueId);
                    return history;
                })
                .toList();
    }

    private ScoutTagSample stubScout(String puuid, int queueId, List<MatchHistory> history) {
        ScoutTagSample sample = ScoutTagSample.builder()
                .puuid(puuid)
                .currentQueueId(queueId)
                .lookbackMatches(history)
                .currentModeMatches(history)
                .source("SGP")
                .build();
        when(scoutTagSampleService.getCurrentModeSample(puuid, queueId, 50, 20)).thenReturn(sample);
        when(scoutTagRuleService.buildTags(any(ScoutTagContext.class), eq(sample))).thenReturn(List.of(
                RankTag.builder().tagName("高胜率").good(true).build()
        ));
        return sample;
    }

    private ScoutTagContext anyContextWithQueueAndTeam(int queueId, List<String> teamPuuids) {
        return org.mockito.ArgumentMatchers.argThat(context ->
                context != null
                        && context.getCurrentQueueId() == queueId
                        && context.getCurrentTeamPuuids().containsAll(teamPuuids));
    }

    private MatchHistory createMatchWithParticipantStats(
            String puuid,
            int goldEarned,
            int damageDealt,
            int teammateGoldEarned,
            int teammateDamageDealt
    ) {
        MatchHistory history = new MatchHistory();
        history.setQueueId(420);

        MatchHistory.Participant participant = participant(1, 100, goldEarned, damageDealt);
        MatchHistory.Participant teammate = participant(2, 100, teammateGoldEarned, teammateDamageDealt);
        history.setParticipants(List.of(participant, teammate));

        MatchHistory.ParticipantIdentity identity = new MatchHistory.ParticipantIdentity();
        identity.setParticipantId(1);
        MatchHistory.Player player = new MatchHistory.Player();
        player.setPuuid(puuid);
        identity.setPlayer(player);
        history.setParticipantIdentities(List.of(identity));

        return history;
    }

    private MatchHistory createMatchWithMissingEconomyStats(String puuid) {
        MatchHistory history = new MatchHistory();
        history.setQueueId(420);

        MatchHistory.Participant participant = participant(1, 100, 0, 0);
        participant.getStats().setGoldEarned(null);
        participant.getStats().setTotalDamageDealtToChampions(null);
        MatchHistory.Participant teammate = participant(2, 100, 0, 0);
        teammate.getStats().setGoldEarned(null);
        teammate.getStats().setTotalDamageDealtToChampions(null);
        history.setParticipants(List.of(participant, teammate));

        MatchHistory.ParticipantIdentity identity = new MatchHistory.ParticipantIdentity();
        identity.setParticipantId(1);
        MatchHistory.Player player = new MatchHistory.Player();
        player.setPuuid(puuid);
        identity.setPlayer(player);
        history.setParticipantIdentities(List.of(identity));

        return history;
    }

    private MatchHistory createSingleParticipantMatch(String puuid, int goldEarned, int damageDealt) {
        MatchHistory history = new MatchHistory();
        history.setQueueId(420);
        history.setParticipants(List.of(participant(1, 100, goldEarned, damageDealt)));

        MatchHistory.ParticipantIdentity identity = new MatchHistory.ParticipantIdentity();
        identity.setParticipantId(1);
        MatchHistory.Player player = new MatchHistory.Player();
        player.setPuuid(puuid);
        identity.setPlayer(player);
        history.setParticipantIdentities(List.of(identity));

        return history;
    }

    private MatchHistory.Participant participant(int participantId, int teamId, int goldEarned, int damageDealt) {
        MatchHistory.Participant participant = new MatchHistory.Participant();
        participant.setParticipantId(participantId);
        participant.setTeamId(teamId);
        MatchHistory.Stats stats = new MatchHistory.Stats();
        stats.setWin(true);
        stats.setKills(6);
        stats.setDeaths(3);
        stats.setAssists(9);
        stats.setGoldEarned(goldEarned);
        stats.setTotalDamageDealtToChampions(damageDealt);
        participant.setStats(stats);
        return participant;
    }

    private GameSession gameSession(int queueId, List<GameSession.OnePlayer> teamOne, List<GameSession.OnePlayer> teamTwo) {
        GameSession session = new GameSession();
        GameSession.GameData gameData = new GameSession.GameData();
        GameSession.Queue queue = new GameSession.Queue();
        queue.setId(queueId);
        queue.setType("RANKED_SOLO_5x5");
        gameData.setQueue(queue);
        gameData.setTeamOne(teamOne);
        gameData.setTeamTwo(teamTwo);
        session.setGameData(gameData);
        return session;
    }
}
