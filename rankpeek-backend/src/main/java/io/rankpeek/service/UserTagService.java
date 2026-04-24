package io.rankpeek.service;

import io.rankpeek.constant.QueueType;
import io.rankpeek.exception.LcuException;
import io.rankpeek.exception.ResourceNotFoundException;
import io.rankpeek.model.GameDetail;
import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.MatchHistoryFetchResult;
import io.rankpeek.model.OneGamePlayer;
import io.rankpeek.model.OneGamePlayerSummoner;
import io.rankpeek.model.Rank;
import io.rankpeek.model.RankTag;
import io.rankpeek.model.RecentData;
import io.rankpeek.model.RecordStatus;
import io.rankpeek.model.Summoner;
import io.rankpeek.model.UserTag;
import io.rankpeek.model.UserTagSummary;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Builds full and lightweight user-tag views.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserTagService {

    private final LcuHttpClient lcuHttpClient;
    private final SummonerService summonerService;
    private final MatchHistoryService matchHistoryService;
    private final TagConfigService tagConfigService;
    private final RankService rankService;

    private final Cache<Long, GameDetail> gameDetailCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    public UserTag getUserTagByName(String name, Integer mode) {
        try {
            Summoner summoner = summonerService.getSummonerByName(name);
            if (summoner == null) {
                throw new ResourceNotFoundException("Summoner not found: " + name);
            }
            return getUserTagByPuuid(summoner.getPuuid(), mode);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new LcuException("Failed to fetch user tag for " + name, e);
        }
    }

    public UserTag getUserTagByPuuid(String puuid, Integer mode) {
        int normalizedMode = normalizeMode(mode);
        Rank rank = safeGetRank(puuid);

        MatchHistoryFetchResult fetchResult;
        try {
            fetchResult = matchHistoryService.getMatchHistoryFetchResult(puuid);
        } catch (Exception e) {
            log.error("Failed to fetch match history for full tag view, puuid={}", puuid, e);
            return createEmptyTag(normalizedMode, RecordStatus.ERROR);
        }

        RecordStatus status = matchHistoryService.resolveRecordStatus(fetchResult, rank);
        List<MatchHistory> recentMatches = sliceMatches(fetchResult.getMatches(), 0, 19);
        if (status != RecordStatus.NORMAL) {
            return createEmptyTag(normalizedMode, status);
        }

        try {
            List<MatchHistory> enrichedHistory = enrichMatchHistory(recentMatches);
            RecentData recentData = calculateRecentData(enrichedHistory, puuid, normalizedMode);
            Map<String, List<OneGamePlayer>> oneGamePlayersMap = analyzeOneGamePlayers(enrichedHistory, puuid);
            recentData.setOneGamePlayersMap(oneGamePlayersMap);
            calculateFriendAndDispute(oneGamePlayersMap, recentData);

            return UserTag.builder()
                    .recordStatus(status)
                    .recentData(recentData)
                    .tag(buildTags(enrichedHistory, puuid, normalizedMode, oneGamePlayersMap))
                    .build();
        } catch (Exception e) {
            log.error("Failed to build full user tag, puuid={}", puuid, e);
            return createEmptyTag(normalizedMode, RecordStatus.ERROR);
        }
    }

    public UserTagSummary getUserTagSummaryByPuuid(String puuid, Integer mode) {
        int normalizedMode = normalizeMode(mode);
        Rank rank = safeGetRank(puuid);

        try {
            MatchHistoryFetchResult fetchResult = matchHistoryService.getMatchHistoryFetchResult(puuid);
            RecordStatus status = matchHistoryService.resolveRecordStatus(fetchResult, rank);
            return buildSummary(puuid, normalizedMode, fetchResult.getMatches(), status, false);
        } catch (Exception e) {
            log.warn("Failed to fetch user tag summary, puuid={}", puuid, e);
            return createEmptySummary(normalizedMode, RecordStatus.ERROR);
        }
    }

    public UserTagSummary buildSummaryFromPrefetchedData(String puuid,
                                                         Integer mode,
                                                         Rank rank,
                                                         List<MatchHistory> matchHistory) {
        int normalizedMode = normalizeMode(mode);
        RecordStatus status;
        if (matchHistory == null) {
            status = RecordStatus.ERROR;
        } else if (!matchHistory.isEmpty()) {
            status = RecordStatus.NORMAL;
        } else {
            status = matchHistoryService.resolveRecordStatus(
                    MatchHistoryFetchResult.builder().matches(List.of()).rawEmpty(true).build(),
                    rank
            );
        }
        return buildSummary(puuid, normalizedMode, matchHistory, status, true);
    }

    public Map<String, UserTagSummary> getUserTagSummaryBatch(List<String> puuids, Integer mode) {
        int normalizedMode = normalizeMode(mode);
        Map<String, UserTagSummary> result = new LinkedHashMap<>();
        if (puuids == null || puuids.isEmpty()) {
            return result;
        }

        puuids.stream()
                .filter(puuid -> puuid != null && !puuid.isBlank())
                .distinct()
                .forEach(puuid -> result.put(puuid, getUserTagSummaryByPuuid(puuid, normalizedMode)));
        return result;
    }

    private UserTagSummary buildSummary(String puuid,
                                        int mode,
                                        List<MatchHistory> matchHistory,
                                        RecordStatus status,
                                        boolean includePlayerMap) {
        if (status != RecordStatus.NORMAL || matchHistory == null || matchHistory.isEmpty()) {
            return createEmptySummary(mode, status);
        }

        try {
            List<MatchHistory> recentMatches = sliceMatches(matchHistory, 0, 19);
            RecentData recentData = calculateRecentData(recentMatches, puuid, mode);
            Map<String, List<OneGamePlayer>> oneGamePlayersMap = null;
            if (includePlayerMap) {
                oneGamePlayersMap = analyzeOneGamePlayers(recentMatches, puuid);
                recentData.setOneGamePlayersMap(oneGamePlayersMap);
            }

            return UserTagSummary.builder()
                    .recordStatus(status)
                    .recentData(recentData)
                    .tag(buildTags(recentMatches, puuid, mode, oneGamePlayersMap))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to build summary tag, puuid={}", puuid, e);
            return createEmptySummary(mode, RecordStatus.ERROR);
        }
    }

    private List<RankTag> buildTags(List<MatchHistory> matchHistory,
                                    String puuid,
                                    int mode,
                                    Map<String, List<OneGamePlayer>> oneGamePlayersMap) {
        List<RankTag> tags = new ArrayList<>(tagConfigService.evaluateTags(matchHistory, puuid, mode));
        Map<String, List<OneGamePlayer>> playerMap = oneGamePlayersMap != null
                ? oneGamePlayersMap
                : analyzeOneGamePlayers(matchHistory, puuid);

        if (hasStablePremade(playerMap)) {
            addTagIfMissing(tags, RankTag.builder()
                    .good(null)
                    .tagName("开黑仔")
                    .tagDesc("最近固定队友出现得很勤，十有八九不是单排。")
                    .build());
        }

        if (hasSignatureChampion(matchHistory, puuid, mode)) {
            addTagIfMissing(tags, RankTag.builder()
                    .good(true)
                    .tagName("绝活哥")
                    .tagDesc("最近反复拿同一个英雄，熟练度路线很明显。")
                    .build());
        }

        tags.sort((left, right) -> Integer.compare(tagPriority(left), tagPriority(right)));
        return tags;
    }

    private void addTagIfMissing(List<RankTag> tags, RankTag candidate) {
        boolean exists = tags.stream().anyMatch(tag -> tag.getTagName().equals(candidate.getTagName()));
        if (!exists) {
            tags.add(candidate);
        }
    }

    private boolean hasStablePremade(Map<String, List<OneGamePlayer>> oneGamePlayersMap) {
        if (oneGamePlayersMap == null || oneGamePlayersMap.isEmpty()) {
            return false;
        }

        return oneGamePlayersMap.values().stream()
                .anyMatch(games -> games.size() >= 3 && games.stream().allMatch(OneGamePlayer::getIsMyTeam));
    }

    private boolean hasSignatureChampion(List<MatchHistory> matchHistory, String puuid, int mode) {
        Map<Integer, Integer> championCount = new LinkedHashMap<>();
        int total = 0;

        for (MatchHistory game : matchHistory) {
            if (mode > 0 && !modeEquals(mode, game.getQueueId())) {
                continue;
            }

            MatchHistory.Participant participant = getParticipantByPuuid(game, puuid);
            if (participant == null || participant.getChampionId() == null || participant.getChampionId() <= 0) {
                continue;
            }

            total++;
            championCount.merge(participant.getChampionId(), 1, Integer::sum);
        }

        if (total < 4) {
            return false;
        }

        int topCount = championCount.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return topCount >= 4 && topCount * 2 >= total;
    }

    private int tagPriority(RankTag tag) {
        String name = tag.getTagName();
        if (name == null || name.isBlank()) {
            return 99;
        }
        if (name.endsWith("连胜")) {
            return 0;
        }
        if (name.endsWith("连败")) {
            return 1;
        }
        return switch (name) {
            case "高胜率" -> 2;
            case "低迷" -> 3;
            case "稳定C" -> 4;
            case "高伤" -> 5;
            case "暴毙" -> 6;
            case "摆烂" -> 7;
            case "娱乐" -> 8;
            case "开黑仔" -> 9;
            case "绝活哥" -> 10;
            case "小火龙" -> 11;
            default -> 50;
        };
    }

    private Rank safeGetRank(String puuid) {
        try {
            return rankService.getRankByPuuid(puuid);
        } catch (Exception e) {
            log.debug("Failed to fetch rank, puuid={}", puuid, e);
            return null;
        }
    }

    private List<MatchHistory> enrichMatchHistory(List<MatchHistory> matchHistory) {
        for (MatchHistory game : matchHistory) {
            try {
                GameDetail detail = gameDetailCache.get(game.getGameId(),
                        id -> lcuHttpClient.get("lol-match-history/v1/games/" + id, GameDetail.class));

                if (detail == null || detail.getParticipants() == null) {
                    continue;
                }

                List<MatchHistory.Participant> participants = new ArrayList<>();
                List<MatchHistory.ParticipantIdentity> identities = new ArrayList<>();

                for (GameDetail.GameParticipant gameParticipant : detail.getParticipants()) {
                    MatchHistory.Participant participant = new MatchHistory.Participant();
                    participant.setParticipantId(gameParticipant.getParticipantId());
                    participant.setTeamId(gameParticipant.getTeamId());
                    participant.setChampionId(gameParticipant.getChampionId());
                    participant.setSpell1Id(gameParticipant.getSpell1Id());
                    participant.setSpell2Id(gameParticipant.getSpell2Id());

                    MatchHistory.Stats stats = new MatchHistory.Stats();
                    if (gameParticipant.getStats() != null) {
                        stats.setWin(gameParticipant.getStats().getWin());
                        stats.setKills(gameParticipant.getStats().getKills());
                        stats.setDeaths(gameParticipant.getStats().getDeaths());
                        stats.setAssists(gameParticipant.getStats().getAssists());
                        stats.setGoldEarned(toInteger(gameParticipant.getStats().getGoldEarned()));
                        stats.setTotalDamageDealtToChampions(toInteger(gameParticipant.getStats().getTotalDamageDealtToChampions()));
                        stats.setTotalDamageTaken(toInteger(gameParticipant.getStats().getTotalDamageTaken()));
                        stats.setTotalHeal(toInteger(gameParticipant.getStats().getTotalHeal()));
                        stats.setTotalMinionsKilled(gameParticipant.getStats().getTotalMinionsKilled());
                        stats.setNeutralMinionsKilled(gameParticipant.getStats().getNeutralMinionsKilled());
                        stats.setItem0(gameParticipant.getStats().getItem0());
                        stats.setItem1(gameParticipant.getStats().getItem1());
                        stats.setItem2(gameParticipant.getStats().getItem2());
                        stats.setItem3(gameParticipant.getStats().getItem3());
                        stats.setItem4(gameParticipant.getStats().getItem4());
                        stats.setItem5(gameParticipant.getStats().getItem5());
                        stats.setItem6(gameParticipant.getStats().getItem6());
                        stats.setDamageDealtToChampionsRate(gameParticipant.getStats().getDamageDealtToChampionsRate());
                        stats.setDamageTakenRate(gameParticipant.getStats().getDamageTakenRate());
                        stats.setHealRate(gameParticipant.getStats().getHealRate());
                        stats.setMvp(gameParticipant.getStats().getMvp());
                        stats.setPerk0(gameParticipant.getStats().getPerk0());
                        stats.setMinionsKilled(gameParticipant.getStats().getTotalMinionsKilled());
                        stats.setDamageDealtToTurrets(toInteger(gameParticipant.getStats().getDamageDealtToTurrets()));
                        stats.setPlayerAugment1(gameParticipant.getStats().getPlayerAugment1());
                        stats.setPlayerAugment2(gameParticipant.getStats().getPlayerAugment2());
                        stats.setPlayerAugment3(gameParticipant.getStats().getPlayerAugment3());
                        stats.setPlayerAugment4(gameParticipant.getStats().getPlayerAugment4());
                    }
                    participant.setStats(stats);
                    participants.add(participant);
                }

                for (GameDetail.ParticipantIdentity identity : detail.getParticipantIdentities()) {
                    MatchHistory.ParticipantIdentity participantIdentity = new MatchHistory.ParticipantIdentity();
                    participantIdentity.setParticipantId(identity.getParticipantId());

                    MatchHistory.Player player = new MatchHistory.Player();
                    if (identity.getPlayer() != null) {
                        player.setPuuid(identity.getPlayer().getPuuid());
                        player.setGameName(identity.getPlayer().getGameName());
                        player.setTagLine(identity.getPlayer().getTagLine());
                        player.setSummonerName(identity.getPlayer().getSummonerName());
                        player.setAccountId(identity.getPlayer().getAccountId());
                        player.setSummonerId(identity.getPlayer().getSummonerId());
                        player.setPlatformId(identity.getPlayer().getPlatformId());
                    }
                    participantIdentity.setPlayer(player);
                    identities.add(participantIdentity);
                }

                game.setParticipants(participants);
                game.setParticipantIdentities(identities);
            } catch (Exception e) {
                log.debug("Failed to enrich game detail, gameId={}", game.getGameId(), e);
            }
        }
        return matchHistory;
    }

    private Integer toInteger(Long value) {
        return value != null ? value.intValue() : null;
    }

    private MatchHistory.Participant getParticipantByPuuid(MatchHistory game, String puuid) {
        Integer participantId = null;
        if (game.getParticipantIdentities() != null) {
            for (MatchHistory.ParticipantIdentity identity : game.getParticipantIdentities()) {
                if (identity.getPlayer() != null && puuid.equals(identity.getPlayer().getPuuid())) {
                    participantId = identity.getParticipantId();
                    break;
                }
            }
        }

        if (participantId == null || game.getParticipants() == null) {
            return null;
        }

        for (MatchHistory.Participant participant : game.getParticipants()) {
            if (participantId.equals(participant.getParticipantId())) {
                return participant;
            }
        }
        return null;
    }

    private RecentData calculateRecentData(List<MatchHistory> matchHistory, String puuid, Integer mode) {
        int count = 0;
        double kills = 0;
        double deaths = 0;
        double assists = 0;
        double totalGroupRate = 0;
        double totalGoldRate = 0;
        double totalDamageRate = 0;
        int selectWins = 0;
        int selectLosses = 0;
        long totalGold = 0;
        long totalDamage = 0;

        for (MatchHistory game : matchHistory) {
            if (mode != 0 && !modeEquals(mode, game.getQueueId())) {
                continue;
            }

            MatchHistory.Participant participant = getParticipantByPuuid(game, puuid);
            if (participant == null || participant.getStats() == null) {
                continue;
            }

            MatchHistory.Stats stats = participant.getStats();
            count++;
            kills += valueOrZero(stats.getKills());
            deaths += valueOrZero(stats.getDeaths());
            assists += valueOrZero(stats.getAssists());
            totalGold += valueOrZero(stats.getGoldEarned());
            totalDamage += valueOrZero(stats.getTotalDamageDealtToChampions());
            totalGroupRate += calculateKillParticipationRate(game, participant);
            totalGoldRate += calculateGoldShareRate(game, participant);
            totalDamageRate += calculateDamageShareRate(game, participant);

            if (Boolean.TRUE.equals(stats.getWin())) {
                selectWins++;
            } else {
                selectLosses++;
            }
        }

        if (count == 0) {
            return createEmptyRecentData(mode);
        }

        double kda = deaths > 0 ? (kills + assists) / deaths : kills + assists;
        return RecentData.builder()
                .kda(round1(kda))
                .kills(round1(kills / count))
                .deaths(round1(deaths / count))
                .assists(round1(assists / count))
                .selectMode(mode)
                .selectModeCn(mode == 0 ? "全部模式" : QueueType.getQueueNameCn(mode))
                .selectWins(selectWins)
                .selectLosses(selectLosses)
                .groupRate((int) Math.round(totalGroupRate / count))
                .averageGold((int) (totalGold / count))
                .goldRate((int) Math.round(totalGoldRate / count))
                .averageDamageDealtToChampions((int) (totalDamage / count))
                .damageDealtToChampionsRate((int) Math.round(totalDamageRate / count))
                .build();
    }

    private double calculateKillParticipationRate(MatchHistory game, MatchHistory.Participant participant) {
        int teamKills = 0;
        if (game.getParticipants() == null || participant.getStats() == null) {
            return 0;
        }

        for (MatchHistory.Participant teammate : game.getParticipants()) {
            if (participant.getTeamId() != null
                    && participant.getTeamId().equals(teammate.getTeamId())
                    && teammate.getStats() != null) {
                teamKills += valueOrZero(teammate.getStats().getKills());
            }
        }

        if (teamKills <= 0) {
            return 0;
        }

        double impact = valueOrZero(participant.getStats().getKills()) + valueOrZero(participant.getStats().getAssists());
        return impact * 100.0 / teamKills;
    }

    private double calculateGoldShareRate(MatchHistory game, MatchHistory.Participant participant) {
        return calculateTeamRate(game, participant, MatchMetric.GOLD);
    }

    private double calculateDamageShareRate(MatchHistory game, MatchHistory.Participant participant) {
        return calculateTeamRate(game, participant, MatchMetric.DAMAGE);
    }

    private double calculateTeamRate(MatchHistory game, MatchHistory.Participant participant, MatchMetric metric) {
        if (game.getParticipants() == null || participant.getStats() == null) {
            return 0;
        }

        long teamTotal = 0;
        for (MatchHistory.Participant teammate : game.getParticipants()) {
            if (participant.getTeamId() != null
                    && participant.getTeamId().equals(teammate.getTeamId())
                    && teammate.getStats() != null) {
                teamTotal += metric == MatchMetric.GOLD
                        ? valueOrZero(teammate.getStats().getGoldEarned())
                        : valueOrZero(teammate.getStats().getTotalDamageDealtToChampions());
            }
        }

        if (teamTotal <= 0) {
            return 0;
        }

        long value = metric == MatchMetric.GOLD
                ? valueOrZero(participant.getStats().getGoldEarned())
                : valueOrZero(participant.getStats().getTotalDamageDealtToChampions());
        return value * 100.0 / teamTotal;
    }

    private long valueOrZero(Integer value) {
        return value != null ? value : 0;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private boolean modeEquals(int mode, Integer queueId) {
        return queueId != null && mode == queueId;
    }

    private String getPlayerPuuid(MatchHistory game, Integer participantId) {
        if (game.getParticipantIdentities() == null) {
            return null;
        }
        for (MatchHistory.ParticipantIdentity identity : game.getParticipantIdentities()) {
            if (participantId.equals(identity.getParticipantId()) && identity.getPlayer() != null) {
                return identity.getPlayer().getPuuid();
            }
        }
        return null;
    }

    private Map<String, List<OneGamePlayer>> analyzeOneGamePlayers(List<MatchHistory> matchHistory, String myPuuid) {
        Map<String, List<OneGamePlayer>> result = new LinkedHashMap<>();
        if (matchHistory == null || matchHistory.isEmpty()) {
            return result;
        }

        for (int index = 0; index < matchHistory.size(); index++) {
            MatchHistory game = matchHistory.get(index);
            if (game.getParticipants() == null) {
                continue;
            }

            Integer myTeamId = null;
            MatchHistory.Participant myParticipant = getParticipantByPuuid(game, myPuuid);
            if (myParticipant != null) {
                myTeamId = myParticipant.getTeamId();
            }

            for (MatchHistory.Participant participant : game.getParticipants()) {
                String playerPuuid = getPlayerPuuid(game, participant.getParticipantId());
                if (playerPuuid == null
                        || playerPuuid.isEmpty()
                        || playerPuuid.equals(myPuuid)
                        || participant.getStats() == null) {
                    continue;
                }

                String gameName = "";
                String tagLine = "";
                if (game.getParticipantIdentities() != null) {
                    for (MatchHistory.ParticipantIdentity identity : game.getParticipantIdentities()) {
                        if (participant.getParticipantId().equals(identity.getParticipantId()) && identity.getPlayer() != null) {
                            gameName = identity.getPlayer().getGameName();
                            tagLine = identity.getPlayer().getTagLine();
                            break;
                        }
                    }
                }

                OneGamePlayer player = OneGamePlayer.builder()
                        .index(index)
                        .gameId(game.getGameId())
                        .puuid(playerPuuid)
                        .gameCreatedAt(new java.util.Date(game.getGameCreation()).toString())
                        .isMyTeam(myTeamId != null && myTeamId.equals(participant.getTeamId()))
                        .gameName(gameName)
                        .tagLine(tagLine)
                        .championId(participant.getChampionId())
                        .kills(participant.getStats().getKills())
                        .deaths(participant.getStats().getDeaths())
                        .assists(participant.getStats().getAssists())
                        .win(participant.getStats().getWin())
                        .queueIdCn(QueueType.getQueueNameCn(game.getQueueId()))
                        .build();

                result.computeIfAbsent(playerPuuid, key -> new ArrayList<>()).add(player);
            }
        }

        return result;
    }

    private void calculateFriendAndDispute(Map<String, List<OneGamePlayer>> oneGamePlayersMap, RecentData recentData) {
        if (recentData.getFriendAndDispute() == null) {
            recentData.setFriendAndDispute(new io.rankpeek.model.FriendAndDispute());
        }

        List<List<OneGamePlayer>> friendsList = new ArrayList<>();
        List<List<OneGamePlayer>> disputeList = new ArrayList<>();

        oneGamePlayersMap.values().stream()
                .filter(games -> games.size() >= 3)
                .forEach(games -> {
                    boolean allSameTeam = games.stream().allMatch(OneGamePlayer::getIsMyTeam);
                    if (allSameTeam) {
                        friendsList.add(games);
                    } else {
                        disputeList.add(games);
                    }
                });

        List<OneGamePlayerSummoner> friendsSummoner = new ArrayList<>();
        int friendsWins = 0;
        int friendsLosses = 0;

        for (List<OneGamePlayer> games : friendsList.stream().limit(5).toList()) {
            int wins = (int) games.stream().filter(OneGamePlayer::getWin).count();
            int losses = games.size() - wins;
            friendsWins += wins;
            friendsLosses += losses;

            try {
                Summoner summoner = summonerService.getSummonerByPuuid(games.getFirst().getPuuid());
                friendsSummoner.add(OneGamePlayerSummoner.builder()
                        .winRate(wins * 100 / games.size())
                        .wins(wins)
                        .losses(losses)
                        .summoner(summoner)
                        .oneGamePlayer(games)
                        .build());
            } catch (Exception e) {
                log.debug("Failed to resolve friend summoner info", e);
            }
        }

        List<OneGamePlayerSummoner> disputeSummoner = new ArrayList<>();
        int disputeWins = 0;
        int disputeLosses = 0;

        for (List<OneGamePlayer> games : disputeList.stream().limit(5).toList()) {
            List<OneGamePlayer> enemyGames = games.stream()
                    .filter(game -> !game.getIsMyTeam())
                    .toList();

            if (enemyGames.isEmpty()) {
                continue;
            }

            int wins = (int) enemyGames.stream().filter(OneGamePlayer::getWin).count();
            int losses = enemyGames.size() - wins;
            disputeWins += wins;
            disputeLosses += losses;

            try {
                Summoner summoner = summonerService.getSummonerByPuuid(games.getFirst().getPuuid());
                disputeSummoner.add(OneGamePlayerSummoner.builder()
                        .winRate(wins * 100 / enemyGames.size())
                        .wins(wins)
                        .losses(losses)
                        .summoner(summoner)
                        .oneGamePlayer(new ArrayList<>(enemyGames))
                        .build());
            } catch (Exception e) {
                log.debug("Failed to resolve dispute summoner info", e);
            }
        }

        int totalFriends = friendsWins + friendsLosses;
        int totalDispute = disputeWins + disputeLosses;

        recentData.getFriendAndDispute().setFriendsRate(totalFriends > 0 ? friendsWins * 100 / totalFriends : 0);
        recentData.getFriendAndDispute().setDisputeRate(totalDispute > 0 ? disputeWins * 100 / totalDispute : 0);
        recentData.getFriendAndDispute().setFriendsSummoner(friendsSummoner);
        recentData.getFriendAndDispute().setDisputeSummoner(disputeSummoner);
    }

    private UserTag createEmptyTag(int mode, RecordStatus recordStatus) {
        return UserTag.builder()
                .recordStatus(recordStatus)
                .recentData(createEmptyRecentData(mode))
                .tag(new ArrayList<>())
                .build();
    }

    private UserTagSummary createEmptySummary(int mode, RecordStatus recordStatus) {
        return UserTagSummary.builder()
                .recordStatus(recordStatus)
                .recentData(createEmptyRecentData(mode))
                .tag(new ArrayList<>())
                .build();
    }

    private RecentData createEmptyRecentData(int mode) {
        return RecentData.builder()
                .selectMode(mode)
                .selectModeCn(mode == 0 ? "全部模式" : QueueType.getQueueNameCn(mode))
                .build();
    }

    private List<MatchHistory> sliceMatches(List<MatchHistory> matches, int begIndex, int endIndex) {
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }

        int beg = Math.max(0, begIndex);
        int end = Math.min(endIndex + 1, matches.size());
        if (beg >= end) {
            return List.of();
        }

        return new ArrayList<>(matches.subList(beg, end));
    }

    private int normalizeMode(Integer mode) {
        return mode != null ? mode : 0;
    }

    private enum MatchMetric {
        GOLD,
        DAMAGE
    }
}
