package io.rankpeek.service;

import io.rankpeek.constant.GameConstants;
import io.rankpeek.constant.QueueType;
import io.rankpeek.model.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 会话数据分析服务
 * 提供游戏会话数据处理、队伍分析、玩家信息聚合功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionAnalysisService {

    private final SummonerService summonerService;
    private final RankService rankService;
    private final MatchHistoryService matchHistoryService;
    private final GameFlowService gameFlowService;
    private final ChampionSelectService championSelectService;
    private final UserTagService userTagService;

    @Qualifier("dataLoaderExecutor")
    private final Executor dataLoaderExecutor;

    // ========== 可配置常量 ==========
    /** 近期战绩查询数量 */
    private static final int RECENT_MATCHES_COUNT = 10;
    /** 预组队判定阈值：同队场次 */
    private static final int PRE_GROUP_FRIEND_THRESHOLD = 3;
    /** 预组队最小人数 */
    private static final int PRE_GROUP_MIN_MEMBERS = 2;

    @PostConstruct
    public void init() {
        log.info("会话数据分析服务初始化完成");
    }

    /**
     * 获取完整会话数据（包含所有玩家信息）
     */
    public SessionData getSessionData(Integer mode) {
        Summoner mySummoner = summonerService.getMySummoner();
        if (mySummoner == null) {
            log.warn("无法获取当前召唤师信息");
            return SessionData.builder().build();
        }

        String phase = gameFlowService.getGamePhase();
        log.info("getSessionData: phase={}, myPuuid={}", phase, mySummoner.getPuuid() != null ? mySummoner.getPuuid().substring(0, Math.min(8, mySummoner.getPuuid().length())) : "null");

        List<String> lobbyPhases = List.of("Lobby", "Matchmaking", "ReadyCheck");
        if (lobbyPhases.contains(phase)) {
            return processLobbyPhase(phase, mode);
        }

        if ("ChampSelect".equals(phase)) {
            return processChampSelectPhase(mySummoner, mode);
        }

        List<String> validPhases = List.of("GameStart", "InProgress", "PreEndOfGame", "EndOfGame");
        if (!validPhases.contains(phase)) {
            return SessionData.builder().phase(phase).build();
        }

        return processGamePhase(phase, mySummoner, mode);
    }

    private SessionData processChampSelectPhase(Summoner mySummoner, Integer mode) {
        ChampionSelectSession selectSession = championSelectService.getChampionSelectSession();
        if (selectSession == null) {
            log.warn("ChampSelect 阶段但获取不到选人会话数据，尝试从 GameSession 获取");
            return processGamePhase("ChampSelect", mySummoner, mode);
        }

        List<ChampionSelectSession.Player> myTeam = selectSession.getMyTeam();
        List<ChampionSelectSession.Player> theirTeam = selectSession.getTheirTeam();

        log.info("ChampSelect 直接模式: myTeam={}, theirTeam={}",
                myTeam != null ? myTeam.size() : 0,
                theirTeam != null ? theirTeam.size() : 0);

        Integer queueId = resolveQueueIdFromGameSession();
        String typeCn = "未知模式";
        String queueType = "";
        if (queueId != null && queueId > 0) {
            typeCn = GameConstants.getQueueCnName(queueId);
        }

        int analysisMode = resolveAnalysisMode(mode);
        List<SessionSummoner> teamOne = buildTeamFromChampSelectPlayers(myTeam, analysisMode);
        List<SessionSummoner> teamTwo = buildTeamFromChampSelectPlayers(theirTeam, analysisMode);

        ensureMyTeamIsFirst(teamOne, teamTwo, mySummoner.getPuuid());

        addPreGroupMarkers(teamOne, teamTwo);
        insertMeetGamersRecord(teamOne, teamTwo, mySummoner.getPuuid());

        return SessionData.builder()
                .phase("ChampSelect")
                .queueType(queueType)
                .typeCn(typeCn)
                .queueId(queueId != null ? queueId : 0)
                .teamOne(teamOne)
                .teamTwo(teamTwo)
                .build();
    }

    private Integer resolveQueueIdFromGameSession() {
        try {
            GameSession session = gameFlowService.getGameSession();
            if (session != null && session.getGameData() != null && session.getGameData().getQueue() != null) {
                return session.getGameData().getQueue().getId();
            }
        } catch (Exception e) {
            log.debug("从 GameSession 获取 queueId 失败: {}", e.getMessage());
        }
        return null;
    }

    private List<SessionSummoner> buildTeamFromChampSelectPlayers(List<ChampionSelectSession.Player> players, Integer analysisMode) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<SessionSummoner>> futures = players.stream()
                .map(p -> CompletableFuture.supplyAsync(
                        () -> processChampSelectPlayer(p, analysisMode),
                        dataLoaderExecutor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private SessionSummoner processChampSelectPlayer(ChampionSelectSession.Player player, Integer analysisMode) {
        String puuid = player.getPuuid();
        Integer championId = player.getChampionId() != null ? player.getChampionId() : 0;

        if (puuid == null || puuid.isEmpty()) {
            log.debug("ChampSelect 玩家 puuid 为空, cellId={}", player.getCellId());
            return buildEmptySessionSummoner(championId);
        }

        try {
            Summoner summoner = safeGetSummoner(puuid);
            Rank rank = safeGetRank(puuid);
            List<MatchHistory> history = safeGetMatchHistory(puuid);
            UserTag userTag = safeBuildSessionUserTag(puuid, analysisMode, rank, history);

            return SessionSummoner.builder()
                    .championId(championId)
                    .championKey(championId > 0 ? "champion_" + championId : "")
                    .summoner(summoner != null ? summoner : new Summoner())
                    .matchHistory(history != null ? history : List.of())
                    .userTag(userTag != null ? userTag : UserTag.builder().build())
                    .rank(rank != null ? rank : new Rank())
                    .meetGames(List.of())
                    .preGroupMarkers(PreGroupMarker.empty())
                    .isLoading(false)
                    .build();
        } catch (Exception e) {
            log.warn("获取 ChampSelect 玩家信息失败: puuid={}, error={}", puuid, e.getMessage());
            return buildEmptySessionSummoner(championId);
        }
    }

    private void ensureMyTeamIsFirst(List<SessionSummoner> teamOne, List<SessionSummoner> teamTwo, String myPuuid) {
        if (myPuuid == null || teamOne == null) {
            return;
        }

        boolean iAmInTeamOne = teamOne.stream()
                .anyMatch(s -> s.getSummoner() != null && myPuuid.equals(s.getSummoner().getPuuid()));

        if (!iAmInTeamOne && teamTwo != null && !teamTwo.isEmpty()) {
            List<SessionSummoner> temp = new ArrayList<>(teamOne);
            teamOne.clear();
            teamOne.addAll(teamTwo);
            teamTwo.clear();
            teamTwo.addAll(temp);
        }
    }

    private SessionData processGamePhase(String phase, Summoner mySummoner, Integer mode) {
        GameSession session = gameFlowService.getGameSession();
        if (session == null || session.getGameData() == null) {
            if ("GameStart".equals(phase)) {
                log.info("GameStart 阶段，session 数据不完整");
                return SessionData.builder()
                        .phase(phase)
                        .queueType("")
                        .typeCn("匹配中")
                        .queueId(0)
                        .teamOne(List.of())
                        .teamTwo(List.of())
                        .build();
            }
            log.warn("阶段 {} 但 session 数据为空", phase);
            return SessionData.builder().phase(phase).build();
        }

        log.info("GamePhase: phase={}, teamOne={}, teamTwo={}, selections={}",
                phase,
                session.getGameData().getTeamOne() != null ? session.getGameData().getTeamOne().size() : 0,
                session.getGameData().getTeamTwo() != null ? session.getGameData().getTeamTwo().size() : 0,
                session.getGameData().getPlayerChampionSelections() != null ? session.getGameData().getPlayerChampionSelections().size() : 0);

        if (isTeamDataIncomplete(session)) {
            supplementFromChampSelect(session);
        }

        ensureMyTeamIsTeamOne(session, mySummoner);
        fillMissingPlayers(session);

        log.info("处理后: teamOne={}, teamTwo={}",
                session.getGameData().getTeamOne() != null ? session.getGameData().getTeamOne().size() : 0,
                session.getGameData().getTeamTwo() != null ? session.getGameData().getTeamTwo().size() : 0);

        Integer queueId = 0;
        String queueType = "";
        String typeCn = "未知模式";

        if (session.getGameData().getQueue() != null) {
            queueId = session.getGameData().getQueue().getId();
            queueType = session.getGameData().getQueue().getType();

            typeCn = GameConstants.getQueueTypeCnName(queueType);
            if ("其他".equals(typeCn) && queueId > 0) {
                typeCn = GameConstants.getQueueCnName(queueId);
            }
        }

        int analysisMode = resolveAnalysisMode(mode);
        List<SessionSummoner> teamOne = processTeam(session.getGameData().getTeamOne(), analysisMode);
        List<SessionSummoner> teamTwo = processTeam(session.getGameData().getTeamTwo(), analysisMode);

        addPreGroupMarkers(teamOne, teamTwo);
        insertMeetGamersRecord(teamOne, teamTwo, mySummoner.getPuuid());

        return SessionData.builder()
                .phase(phase)
                .queueType(queueType)
                .typeCn(typeCn)
                .queueId(queueId)
                .teamOne(teamOne)
                .teamTwo(teamTwo)
                .build();
    }

    /**
     * 处理大厅阶段数据
     * 从 Lobby API 获取队列信息和队友列表
     */
    private SessionData processLobbyPhase(String phase, Integer mode) {
        try {
            Lobby lobby = gameFlowService.getLobby();
            if (lobby == null) {
                log.debug("Lobby 数据为空，返回默认值");
                return SessionData.builder()
                    .phase(phase)
                    .typeCn("未知模式")
                    .teamOne(List.of())
                    .teamTwo(List.of())
                    .build();
            }

            // 获取队列 ID
            Integer queueId = lobby.getQueueId();
            if (queueId == null && lobby.getGameConfig() != null) {
                queueId = lobby.getGameConfig().getQueueId();
            }

            // 获取队列名称
            String typeCn = "未知模式";
            if (queueId != null && queueId > 0) {
                typeCn = GameConstants.getQueueCnName(queueId);
            }

            // 从 Lobby 成员构建队伍数据
            int analysisMode = resolveAnalysisMode(mode);
            List<SessionSummoner> teamOne = buildTeamFromLobbyMembers(lobby.getMembers(), analysisMode);

            return SessionData.builder()
                .phase(phase)
                .queueType("")
                .typeCn(typeCn)
                .queueId(queueId != null ? queueId : 0)
                .teamOne(teamOne)
                .teamTwo(List.of())
                .build();
        } catch (Exception e) {
            log.warn("获取大厅数据失败: {}", e.getMessage());
            return SessionData.builder()
                .phase(phase)
                .typeCn("未知模式")
                .teamOne(List.of())
                .teamTwo(List.of())
                .build();
        }
    }

    /**
     * 从 Lobby 成员构建队伍数据
     */
    private List<SessionSummoner> buildTeamFromLobbyMembers(List<Lobby.Member> members, Integer analysisMode) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<SessionSummoner>> futures = members.stream()
            .map(member -> CompletableFuture.supplyAsync(
                () -> processLobbyMember(member, analysisMode),
                dataLoaderExecutor))
            .toList();

        return futures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 处理单个 Lobby 成员
     */
    private SessionSummoner processLobbyMember(Lobby.Member member, Integer analysisMode) {
        String puuid = member.getPuuid();
        if (puuid == null || puuid.isEmpty()) {
            return null;
        }

        try {
            Summoner summoner = safeGetSummoner(puuid);
            Rank rank = safeGetRank(puuid);
            List<MatchHistory> history = safeGetMatchHistory(puuid);
            UserTag userTag = safeBuildSessionUserTag(puuid, analysisMode, rank, history);

            return SessionSummoner.builder()
                .championId(0)
                .championKey("")
                .summoner(summoner != null ? summoner : new Summoner())
                .matchHistory(history != null ? history : List.of())
                .userTag(userTag != null ? userTag : UserTag.builder().build())
                .rank(rank != null ? rank : new Rank())
                .meetGames(List.of())
                .preGroupMarkers(PreGroupMarker.empty())
                .isLoading(false)
                .build();
        } catch (Exception e) {
            log.warn("获取大厅成员信息失败: puuid={}, error={}", puuid, e.getMessage());
            return null;
        }
    }

    private boolean isTeamDataIncomplete(GameSession session) {
        int teamOneSize = session.getGameData().getTeamOne() != null ? session.getGameData().getTeamOne().size() : 0;
        int teamTwoSize = session.getGameData().getTeamTwo() != null ? session.getGameData().getTeamTwo().size() : 0;
        return teamOneSize < 5 || teamTwoSize < 5;
    }

    private void supplementFromChampSelect(GameSession session) {
        try {
            ChampionSelectSession selectSession = championSelectService.getChampionSelectSession();
            if (selectSession == null) {
                return;
            }

            int teamOneSize = session.getGameData().getTeamOne() != null ? session.getGameData().getTeamOne().size() : 0;
            int teamTwoSize = session.getGameData().getTeamTwo() != null ? session.getGameData().getTeamTwo().size() : 0;

            if (teamOneSize < 5 && selectSession.getMyTeam() != null) {
                session.getGameData().setTeamOne(
                        selectSession.getMyTeam().stream()
                                .map(p -> {
                                    GameSession.OnePlayer player = new GameSession.OnePlayer();
                                    player.setChampionId(p.getChampionId());
                                    player.setPuuid(p.getPuuid());
                                    player.setSelectedPosition("");
                                    return player;
                                })
                                .toList());
                log.info("从 ChampSelect 会话补充 teamOne 数据: {}", selectSession.getMyTeam().size());
            }

            if (teamTwoSize < 5 && selectSession.getTheirTeam() != null) {
                session.getGameData().setTeamTwo(
                        selectSession.getTheirTeam().stream()
                                .map(p -> {
                                    GameSession.OnePlayer player = new GameSession.OnePlayer();
                                    player.setChampionId(p.getChampionId());
                                    player.setPuuid(p.getPuuid());
                                    player.setSelectedPosition("");
                                    return player;
                                })
                                .toList());
                log.info("从 ChampSelect 会话补充 teamTwo 数据: {}", selectSession.getTheirTeam().size());
            }
        } catch (Exception e) {
            log.warn("从 ChampSelect 会话补充数据失败: {}", e.getMessage());
        }
    }

    /**
     * 确保我方在 teamOne
     */
    private void ensureMyTeamIsTeamOne(GameSession session, Summoner mySummoner) {
        String myPuuid = mySummoner.getPuuid();
        if (myPuuid == null) {
            return;
        }

        List<GameSession.OnePlayer> teamOne = session.getGameData().getTeamOne();
        List<GameSession.OnePlayer> teamTwo = session.getGameData().getTeamTwo();

        if (teamOne == null || teamOne.isEmpty()) {
            return;
        }

        boolean iAmInTeamOne = teamOne.stream()
                .anyMatch(p -> myPuuid.equals(p.getPuuid()));

        if (!iAmInTeamOne && teamTwo != null && !teamTwo.isEmpty()) {
            session.getGameData().setTeamOne(teamTwo);
            session.getGameData().setTeamTwo(teamOne);
        }
    }

    /**
     * 补全缺失的玩家信息
     * 当 gameData.teamOne/teamTwo 不完整时，从 playerChampionSelections 补全
     */
    private void fillMissingPlayers(GameSession session) {
        List<GameSession.PlayerChampionSelection> selections = session.getGameData().getPlayerChampionSelections();
        if (selections == null || selections.isEmpty()) {
            return;
        }

        int teamOneSize = session.getGameData().getTeamOne() != null ? session.getGameData().getTeamOne().size() : 0;
        int teamTwoSize = session.getGameData().getTeamTwo() != null ? session.getGameData().getTeamTwo().size() : 0;

        if (teamOneSize >= 5 && teamTwoSize >= 5) {
            return;
        }

        int halfSize = selections.size() / 2;
        if (halfSize == 0) {
            return;
        }

        boolean useSecondHalf = shouldUseSecondHalfForTeamOne(session, selections);

        List<GameSession.PlayerChampionSelection> teamOneSelections = useSecondHalf
                ? selections.subList(halfSize, selections.size())
                : selections.subList(0, halfSize);
        List<GameSession.PlayerChampionSelection> teamTwoSelections = useSecondHalf
                ? selections.subList(0, halfSize)
                : selections.subList(halfSize, selections.size());

        if (teamOneSize < 5) {
            session.getGameData().setTeamOne(buildPlayersFromSelections(teamOneSelections));
        }
        if (teamTwoSize < 5) {
            session.getGameData().setTeamTwo(buildPlayersFromSelections(teamTwoSelections));
        }
    }

    /**
     * 判断是否应该使用 selections 的后半部分填充 teamOne
     */
    private boolean shouldUseSecondHalfForTeamOne(GameSession session,
            List<GameSession.PlayerChampionSelection> selections) {
        List<GameSession.OnePlayer> teamOne = session.getGameData().getTeamOne();
        if (teamOne == null || teamOne.isEmpty()) {
            return false;
        }

        Set<String> teamOnePuuids = teamOne.stream()
                .map(GameSession.OnePlayer::getPuuid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (teamOnePuuids.isEmpty()) {
            return false;
        }

        int halfSize = selections.size() / 2;

        long firstHalfMatch = selections.subList(0, halfSize).stream()
                .filter(s -> s.getPuuid() != null && teamOnePuuids.contains(s.getPuuid()))
                .count();

        long secondHalfMatch = selections.subList(halfSize, selections.size()).stream()
                .filter(s -> s.getPuuid() != null && teamOnePuuids.contains(s.getPuuid()))
                .count();

        return secondHalfMatch > firstHalfMatch;
    }

    /**
     * 从 selections 构建玩家列表
     */
    private List<GameSession.OnePlayer> buildPlayersFromSelections(
            List<GameSession.PlayerChampionSelection> selections) {
        return selections.stream()
                .map(s -> {
                    GameSession.OnePlayer p = new GameSession.OnePlayer();
                    p.setChampionId(s.getChampionId());
                    p.setPuuid(s.getPuuid());
                    p.setSelectedPosition("");
                    return p;
                })
                .toList();
    }

    /**
     * 处理队伍数据（使用指定线程池并行处理）
     */
    private List<SessionSummoner> processTeam(List<GameSession.OnePlayer> team, Integer analysisMode) {
        if (team == null || team.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<SessionSummoner>> futures = team.stream()
                .map(player -> CompletableFuture.supplyAsync(
                        () -> processPlayer(player, analysisMode),
                        dataLoaderExecutor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    /**
     * 处理单个玩家数据（串行获取各数据，避免嵌套并行）
     */
    private SessionSummoner processPlayer(GameSession.OnePlayer player, Integer analysisMode) {
        String puuid = player.getPuuid();
        Integer championId = player.getChampionId();

        if (puuid == null || puuid.isEmpty()) {
            return buildEmptySessionSummoner(championId);
        }

        try {
            // 串行获取数据（外层已并行处理10个玩家，此处无需再嵌套并行）
            Summoner summoner = safeGetSummoner(puuid);
            Rank rank = safeGetRank(puuid);
            List<MatchHistory> history = safeGetMatchHistory(puuid);
            UserTag userTag = safeBuildSessionUserTag(puuid, analysisMode, rank, history);

            return SessionSummoner.builder()
                    .championId(championId)
                    .championKey("champion_" + championId)
                    .summoner(summoner != null ? summoner : new Summoner())
                    .matchHistory(history != null ? history : List.of())
                    .userTag(userTag != null ? userTag : UserTag.builder().build())
                    .rank(rank != null ? rank : new Rank())
                    .meetGames(List.of())
                    .preGroupMarkers(PreGroupMarker.empty())
                    .isLoading(false)
                    .build();

        } catch (Exception e) {
            log.warn("获取玩家信息失败：puuid={}, championId={}, error={}", puuid, championId, e.getMessage());
            return buildEmptySessionSummoner(championId);
        }
    }

    // ========== 安全获取数据方法 ==========

    private Summoner safeGetSummoner(String puuid) {
        try {
            return summonerService.getSummonerByPuuid(puuid);
        } catch (Exception e) {
            log.warn("获取召唤师信息失败：puuid={}, error={}", puuid, e.getMessage());
            return null;
        }
    }

    private Rank safeGetRank(String puuid) {
        try {
            return rankService.getRankByPuuid(puuid);
        } catch (Exception e) {
            log.warn("获取段位信息失败：puuid={}, error={}", puuid, e.getMessage());
            return null;
        }
    }

    private List<MatchHistory> safeGetMatchHistory(String puuid) {
        try {
            return matchHistoryService.getMatchHistory(puuid, 0, RECENT_MATCHES_COUNT);
        } catch (Exception e) {
            log.warn("获取战绩信息失败：puuid={}, error={}", puuid, e.getMessage());
            return null;
        }
    }

    private UserTag safeBuildSessionUserTag(String puuid, Integer queueId, Rank rank, List<MatchHistory> matchHistory) {
        try {
            UserTagSummary summary = userTagService.buildSummaryFromPrefetchedData(puuid, queueId, rank, matchHistory);
            return UserTag.builder()
                    .recordStatus(summary.getRecordStatus())
                    .recentData(summary.getRecentData())
                    .tag(summary.getTag())
                    .build();
        } catch (Exception e) {
            log.warn("获取用户标签失败：puuid={}, error={}", puuid, e.getMessage());
            return null;
        }
    }

    /**
     * 构建空的 SessionSummoner
     */
    private int resolveAnalysisMode(Integer mode) {
        if (mode != null && mode > 0) {
            return mode;
        }
        return QueueType.QUEUE_SOLO_5X5;
    }

    private SessionSummoner buildEmptySessionSummoner(Integer championId) {
        return SessionSummoner.builder()
                .championId(championId)
                .championKey(championId != null ? "champion_" + championId : "champion_unknown")
                .summoner(new Summoner())
                .matchHistory(List.of())
                .userTag(UserTag.builder().build())
                .rank(new Rank())
                .meetGames(List.of())
                .preGroupMarkers(PreGroupMarker.empty())
                .isLoading(false)
                .build();
    }

    /**
     * 标记预组队
     */
    private void addPreGroupMarkers(List<SessionSummoner> teamOne, List<SessionSummoner> teamTwo) {
        // 安全获取所有有效 puuid
        Set<String> currentGamePuuids = new HashSet<>();
        addValidPuuids(currentGamePuuids, teamOne);
        addValidPuuids(currentGamePuuids, teamTwo);

        if (currentGamePuuids.isEmpty()) {
            return;
        }

        // 查找所有可能的预组队
        List<List<String>> allMaybeTeams = new ArrayList<>();
        allMaybeTeams.addAll(findPreGroupsInTeam(teamOne, currentGamePuuids));
        allMaybeTeams.addAll(findPreGroupsInTeam(teamTwo, currentGamePuuids));

        // 合并并去重
        List<List<String>> mergedTeams = removeSubsets(allMaybeTeams);

        // 预组队标记
        PreGroupMarker[] markers = createPreGroupMarkers();

        // 建立 puuid -> SessionSummoner 映射
        Map<String, SessionSummoner> puuidToSummoner = new HashMap<>();
        addSummonerMappings(puuidToSummoner, teamOne);
        addSummonerMappings(puuidToSummoner, teamTwo);

        // 应用标记
        int markerIndex = 0;
        for (List<String> team : mergedTeams) {
            if (markerIndex >= markers.length)
                break;

            long markedCount = tryApplyMarker(team, puuidToSummoner, markers[markerIndex]);
            if (markedCount >= PRE_GROUP_MIN_MEMBERS) {
                markerIndex++;
            }
        }
    }

    /**
     * 添加有效的 puuid 到集合
     */
    private void addValidPuuids(Set<String> puuids, List<SessionSummoner> team) {
        if (team == null)
            return;
        team.stream()
                .filter(s -> s != null && s.getSummoner() != null && s.getSummoner().getPuuid() != null)
                .forEach(s -> puuids.add(s.getSummoner().getPuuid()));
    }

    /**
     * 在队伍中查找预组队
     */
    private List<List<String>> findPreGroupsInTeam(List<SessionSummoner> team, Set<String> currentGamePuuids) {
        List<List<String>> groups = new ArrayList<>();
        if (team == null)
            return groups;

        for (SessionSummoner summoner : team) {
            List<String> group = findPreGroupMembers(summoner, currentGamePuuids);
            if (!group.isEmpty()) {
                groups.add(group);
            }
        }
        return groups;
    }

    /**
     * 建立 puuid 到 SessionSummoner 的映射
     */
    private void addSummonerMappings(Map<String, SessionSummoner> map, List<SessionSummoner> team) {
        if (team == null)
            return;
        team.stream()
                .filter(s -> s != null && s.getSummoner() != null && s.getSummoner().getPuuid() != null)
                .forEach(s -> map.put(s.getSummoner().getPuuid(), s));
    }

    /**
     * 创建预组队标记数组
     */
    private PreGroupMarker[] createPreGroupMarkers() {
        return new PreGroupMarker[] {
                PreGroupMarker.builder().name("队伍 1").type("success").build(),
                PreGroupMarker.builder().name("队伍 2").type("warning").build(),
                PreGroupMarker.builder().name("队伍 3").type("error").build(),
                PreGroupMarker.builder().name("队伍 4").type("info").build()
        };
    }

    /**
     * 尝试应用预组队标记，返回标记的玩家数量
     */
    private long tryApplyMarker(List<String> group, Map<String, SessionSummoner> puuidToSummoner,
            PreGroupMarker marker) {
        long markedCount = 0;
        for (String puuid : group) {
            SessionSummoner summoner = puuidToSummoner.get(puuid);
            if (summoner != null && canApplyMarker(summoner)) {
                summoner.setPreGroupMarkers(marker);
                markedCount++;
            }
        }
        return markedCount;
    }

    /**
     * 判断是否可以应用预组队标记
     */
    private boolean canApplyMarker(SessionSummoner summoner) {
        PreGroupMarker currentMarker = summoner.getPreGroupMarkers();
        return currentMarker == null || currentMarker.getName() == null || currentMarker.getName().isEmpty();
    }

    /**
     * 查找预组队成员
     */
    private List<String> findPreGroupMembers(SessionSummoner summoner, Set<String> currentGamePuuids) {
        List<String> groupMembers = new ArrayList<>();

        if (summoner == null || summoner.getUserTag() == null ||
                summoner.getUserTag().getRecentData() == null ||
                summoner.getUserTag().getRecentData().getOneGamePlayersMap() == null) {
            return groupMembers;
        }

        Map<String, List<OneGamePlayer>> playersMap = summoner.getUserTag().getRecentData().getOneGamePlayersMap();

        for (Map.Entry<String, List<OneGamePlayer>> entry : playersMap.entrySet()) {
            String playerPuuid = entry.getKey();
            if (!currentGamePuuids.contains(playerPuuid)) {
                continue;
            }

            long teamCount = entry.getValue().stream()
                    .filter(p -> p != null && Boolean.TRUE.equals(p.getIsMyTeam()))
                    .count();

            if (teamCount >= PRE_GROUP_FRIEND_THRESHOLD) {
                groupMembers.add(playerPuuid);
            }
        }

        return groupMembers;
    }

    /**
     * 插入遇到过的玩家记录
     */
    private void insertMeetGamersRecord(List<SessionSummoner> teamOne, List<SessionSummoner> teamTwo, String myPuuid) {
        if (myPuuid == null)
            return;

        // 找到自己的记录
        SessionSummoner myRecord = findMyRecord(teamOne, myPuuid);
        if (myRecord == null) {
            return;
        }

        Map<String, List<OneGamePlayer>> myMap = getMeetGamesMap(myRecord);
        if (myMap == null || myMap.isEmpty()) {
            return;
        }

        // 为队友设置遇到记录
        setMeetGamesForTeam(teamOne, myMap, myPuuid);
        setMeetGamesForTeam(teamTwo, myMap, myPuuid);
    }

    /**
     * 查找自己的记录
     */
    private SessionSummoner findMyRecord(List<SessionSummoner> team, String myPuuid) {
        if (team == null)
            return null;
        return team.stream()
                .filter(s -> s != null && s.getSummoner() != null && myPuuid.equals(s.getSummoner().getPuuid()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取遇到过的玩家映射
     */
    private Map<String, List<OneGamePlayer>> getMeetGamesMap(SessionSummoner summoner) {
        if (summoner.getUserTag() != null &&
                summoner.getUserTag().getRecentData() != null) {
            return summoner.getUserTag().getRecentData().getOneGamePlayersMap();
        }
        return null;
    }

    /**
     * 为队伍设置遇到记录
     */
    private void setMeetGamesForTeam(List<SessionSummoner> team, Map<String, List<OneGamePlayer>> myMap,
            String myPuuid) {
        if (team == null)
            return;

        for (SessionSummoner s : team) {
            if (s == null || s.getSummoner() == null)
                continue;

            String puuid = s.getSummoner().getPuuid();
            if (puuid == null || puuid.equals(myPuuid))
                continue;

            if (myMap.containsKey(puuid)) {
                s.setMeetGames(myMap.get(puuid));
            }
        }
    }

    /**
     * 去重并保留最大范围的数组
     */
    private List<List<String>> removeSubsets(List<List<String>> arrays) {
        if (arrays == null || arrays.isEmpty()) {
            return List.of();
        }

        // 按大小降序排序
        List<List<String>> sortedArrays = new ArrayList<>(arrays);
        sortedArrays.sort((a, b) -> Integer.compare(b.size(), a.size()));

        List<List<String>> result = new ArrayList<>();
        for (List<String> arr : sortedArrays) {
            if (arr == null || arr.isEmpty())
                continue;

            boolean isSubset = result.stream().anyMatch(resArr -> isSubset(arr, resArr));
            if (!isSubset) {
                result.add(arr);
            }
        }
        return result;
    }

    /**
     * 判断 a 是否是 b 的子集
     */
    private boolean isSubset(List<String> a, List<String> b) {
        if (a == null || b == null || a.size() >= b.size()) {
            return false;
        }
        return new HashSet<>(b).containsAll(a);
    }
}
