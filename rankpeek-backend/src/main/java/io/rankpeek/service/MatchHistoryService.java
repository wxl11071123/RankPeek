package io.rankpeek.service;

import io.rankpeek.cache.MatchHistoryCacheRepository;
import io.rankpeek.model.GameDetail;
import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.MatchHistoryFetchResult;
import io.rankpeek.model.Rank;
import io.rankpeek.model.RecordStatus;
import io.rankpeek.model.WinRate;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Match-history service.
 */
@Slf4j
@Service
public class MatchHistoryService {

    private static final int SUMMONER_SPELL_SMITE = 11;
    private static final int SUMMONER_SPELL_TELEPORT = 12;
    private static final int SUMMONER_SPELL_HEAL = 7;
    private static final int SUMMONER_SPELL_BARRIER = 21;
    private static final int SUMMONERS_RIFT_MAP_ID = 11;
    private static final int VISIBLE_MATCH_HISTORY_LIMIT = 50;
    private static final int RAW_MATCH_HISTORY_LOOKBACK_LIMIT = 100;
    private static final int REMAKE_DURATION_THRESHOLD_SECONDS = 300;
    private static final String POSITION_TOP = "TOP";
    private static final String POSITION_JUNGLE = "JUNGLE";
    private static final String POSITION_MIDDLE = "MIDDLE";
    private static final String POSITION_BOTTOM = "BOTTOM";
    private static final String POSITION_SUPPORT = "SUPPORT";

    private final LcuHttpClient lcuHttpClient;
    private final MatchHistoryCacheRepository cacheRepository;

    private Cache<String, MatchHistoryFetchResult> matchHistoryCache;
    private Cache<Long, GameDetail> gameDetailCache;

    @Autowired
    public MatchHistoryService(LcuHttpClient lcuHttpClient,
                               ObjectProvider<MatchHistoryCacheRepository> cacheRepositoryProvider) {
        this(lcuHttpClient, cacheRepositoryProvider.getIfAvailable());
    }

    public MatchHistoryService(LcuHttpClient lcuHttpClient) {
        this(lcuHttpClient, (MatchHistoryCacheRepository) null);
    }

    public MatchHistoryService(LcuHttpClient lcuHttpClient, MatchHistoryCacheRepository cacheRepository) {
        this.lcuHttpClient = lcuHttpClient;
        this.cacheRepository = cacheRepository;
    }

    @PostConstruct
    public void init() {
        this.matchHistoryCache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
        this.gameDetailCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
        log.info("战绩服务初始化完成");
    }

    /**
     * Returns the cached raw fetch result for a player.
     */
    public MatchHistoryFetchResult getMatchHistoryFetchResult(String puuid) {
        return getMatchHistoryFetchResult(puuid, false);
    }

    /**
     * Returns the raw fetch result for a player, optionally bypassing the in-memory cache.
     */
    public MatchHistoryFetchResult getMatchHistoryFetchResult(String puuid, boolean forceRefresh) {
        if (forceRefresh) {
            log.info("Force refreshing match history fetch result: puuid={}", puuidPrefix(puuid));
            matchHistoryCache.invalidate(puuid);
            return fetchLcuMatchHistoryWithDatabaseFallback(puuid);
        }

        MatchHistoryFetchResult memoryResult = matchHistoryCache.getIfPresent(puuid);
        if (memoryResult != null) {
            return memoryResult;
        }

        Optional<MatchHistoryFetchResult> databaseResult = findCachedMatchHistory(puuid);
        if (databaseResult.isPresent()) {
            matchHistoryCache.put(puuid, databaseResult.get());
            return databaseResult.get();
        }

        return fetchLcuMatchHistoryWithDatabaseFallback(puuid);
    }

    /**
     * Fetch visible match history.
     */
    public List<MatchHistory> getMatchHistory(String puuid, int begIndex, int endIndex) {
        return getMatchHistory(puuid, begIndex, endIndex, false);
    }

    /**
     * Fetch visible match history, optionally bypassing the in-memory cache.
     */
    public List<MatchHistory> getMatchHistory(String puuid, int begIndex, int endIndex, boolean forceRefresh) {
        if (forceRefresh) {
            log.info("Force refreshing match history request: puuid={}, begIndex={}, endIndex={}",
                    puuidPrefix(puuid), begIndex, endIndex);
        }
        List<MatchHistory> matches = getMatchHistoryFetchResult(puuid, forceRefresh).getMatches();
        List<MatchHistory> sliced = sliceMatches(matches, begIndex, endIndex);
        return ensureRosterForVisibleMatches(puuid, matches, sliced);
    }

    /**
     * Resolve display status from the current fetch result and rank signal.
     */
    public RecordStatus resolveRecordStatus(MatchHistoryFetchResult fetchResult, Rank rank) {
        if (fetchResult == null) {
            return RecordStatus.ERROR;
        }
        if (!fetchResult.getMatches().isEmpty()) {
            return RecordStatus.NORMAL;
        }
        if (fetchResult.isRawEmpty() && hasRankEvidence(rank)) {
            return RecordStatus.PRIVATE;
        }
        return fetchResult.isRawEmpty() ? RecordStatus.EMPTY : RecordStatus.ERROR;
    }

    private MatchHistoryFetchResult fetchMatchHistoryResult(String puuid) {
        List<MatchHistory> matches = new ArrayList<>();
        int begIndex = 0;

        while (begIndex < RAW_MATCH_HISTORY_LOOKBACK_LIMIT && matches.size() < VISIBLE_MATCH_HISTORY_LIMIT) {
            int endIndex = Math.min(begIndex + VISIBLE_MATCH_HISTORY_LIMIT - 1, RAW_MATCH_HISTORY_LOOKBACK_LIMIT - 1);
            String uri = String.format("lol-match-history/v1/products/lol/%s/matches?begIndex=%d&endIndex=%d",
                    puuid, begIndex, endIndex);

            JsonNode response = lcuHttpClient.get(uri, JsonNode.class);
            JsonNode gamesNode = extractGamesNode(response);
            if (gamesNode == null || !gamesNode.isArray() || gamesNode.isEmpty()) {
                break;
            }

            for (JsonNode game : gamesNode) {
                if (isRemakeGame(game)) {
                    continue;
                }
                matches.add(lcuHttpClient.getObjectMapper().convertValue(game, MatchHistory.class));
            }

            if (gamesNode.size() < endIndex - begIndex + 1) {
                break;
            }
            begIndex = endIndex + 1;
        }

        matches.sort(Comparator.comparingLong(this::gameCreationOrMin).reversed());
        if (matches.size() > VISIBLE_MATCH_HISTORY_LIMIT) {
            matches = new ArrayList<>(matches.subList(0, VISIBLE_MATCH_HISTORY_LIMIT));
        }

        return MatchHistoryFetchResult.builder()
                .matches(matches)
                .rawEmpty(matches.isEmpty())
                .build();
    }

    private MatchHistoryFetchResult fetchLcuMatchHistoryWithDatabaseFallback(String puuid) {
        try {
            MatchHistoryFetchResult result = fetchMatchHistoryResult(puuid);
            saveMatchHistoryToLocalCache(puuid, result.getMatches());
            matchHistoryCache.put(puuid, result);
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch match history from LCU, puuid={}, error={}", puuidPrefix(puuid), e.getMessage());
            log.debug("LCU match-history failure details", e);
            if (cacheRepository != null) {
                cacheRepository.updatePlayerFetchState(puuid, List.of(), "ERROR", e.getMessage());
            }
            Optional<MatchHistoryFetchResult> fallback = findCachedMatchHistory(puuid);
            if (fallback.isPresent()) {
                matchHistoryCache.put(puuid, fallback.get());
                return fallback.get();
            }
            throw e;
        }
    }

    private Optional<MatchHistoryFetchResult> findCachedMatchHistory(String puuid) {
        if (cacheRepository == null) {
            return Optional.empty();
        }
        return cacheRepository.findRecentMatchHistory(puuid, VISIBLE_MATCH_HISTORY_LIMIT);
    }

    private void saveMatchHistoryToLocalCache(String puuid, List<MatchHistory> matches) {
        if (cacheRepository != null) {
            cacheRepository.saveMatchHistory(puuid, matches);
        }
    }

    private JsonNode extractGamesNode(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode gamesWrapper = response.get("games");
        if (gamesWrapper == null) {
            return null;
        }
        if (gamesWrapper.isArray()) {
            return gamesWrapper;
        }
        return gamesWrapper.get("games");
    }

    private boolean isRemakeGame(JsonNode game) {
        if (game == null || game.isNull()) {
            return false;
        }
        if (readBoolean(game, "isRemake") || readBoolean(game, "remake")) {
            return true;
        }

        Integer duration = readInt(game, "gameDuration");
        // LCU does not consistently expose a remake flag, so very short finished games are treated as remakes.
        return duration != null && duration < REMAKE_DURATION_THRESHOLD_SECONDS;
    }

    private boolean readBoolean(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return value.isTextual() && Boolean.parseBoolean(value.asText());
    }

    private Integer readInt(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isInt() || value.isLong()) {
            return value.asInt();
        }
        if (value.isTextual() && !value.asText().isBlank()) {
            try {
                return Integer.parseInt(value.asText());
            } catch (NumberFormatException ignored) {
                // Ignore malformed optional fields; the match should not be dropped on uncertain data.
            }
        }
        return null;
    }

    private long gameCreationOrMin(MatchHistory match) {
        return match.getGameCreation() == null ? Long.MIN_VALUE : match.getGameCreation();
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

    private boolean hasRankEvidence(Rank rank) {
        if (rank == null || rank.getQueueMap() == null) {
            return false;
        }
        return hasGames(rank.getQueueMap().getRankedSolo5x5()) || hasGames(rank.getQueueMap().getRankedFlexSr());
    }

    private boolean hasGames(Rank.QueueInfo queueInfo) {
        if (queueInfo == null) {
            return false;
        }
        if (queueInfo.getTotalGames() > 0) {
            return true;
        }
        if (queueInfo.getTier() != null && !"UNRANKED".equalsIgnoreCase(queueInfo.getTier())) {
            return true;
        }
        return queueInfo.getHighestTier() != null && !queueInfo.getHighestTier().isBlank();
    }

    /**
     * Fetch one game detail.
     */
    public GameDetail getGameDetailById(Long gameId) {
        GameDetail memoryDetail = gameDetailCache.getIfPresent(gameId);
        if (memoryDetail != null) {
            return memoryDetail;
        }

        Optional<GameDetail> databaseDetail = findCachedGameDetail(gameId);
        if (databaseDetail.isPresent()) {
            GameDetail detail = databaseDetail.get();
            enrichParticipantStats(detail);
            gameDetailCache.put(gameId, detail);
            return detail;
        }

        String uri = String.format("lol-match-history/v1/games/%d", gameId);
        try {
            GameDetail detail = lcuHttpClient.get(uri, GameDetail.class);
            enrichParticipantStats(detail);
            if (cacheRepository != null) {
                cacheRepository.saveGameDetail(detail);
            }
            gameDetailCache.put(gameId, detail);
            return detail;
        } catch (Exception e) {
            log.warn("Failed to fetch game detail from LCU, gameId={}", gameId, e);
            Optional<GameDetail> fallback = findCachedGameDetail(gameId);
            if (fallback.isPresent()) {
                GameDetail detail = fallback.get();
                enrichParticipantStats(detail);
                gameDetailCache.put(gameId, detail);
                return detail;
            }
            throw e;
        }
    }

    private Optional<GameDetail> findCachedGameDetail(Long gameId) {
        if (cacheRepository == null) {
            return Optional.empty();
        }
        return cacheRepository.findGameDetail(gameId);
    }

    /**
     * Fetch filtered match history.
     */
    public List<MatchHistory> getFilteredMatchHistory(String puuid, int begIndex, int endIndex,
                                                      Integer queueId, Integer championId, int maxResults) {
        return getFilteredMatchHistory(puuid, begIndex, endIndex, queueId, championId, maxResults, false);
    }

    /**
     * Fetch filtered match history, optionally bypassing the in-memory cache.
     */
    public List<MatchHistory> getFilteredMatchHistory(String puuid, int begIndex, int endIndex,
                                                      Integer queueId, Integer championId, int maxResults,
                                                      boolean forceRefresh) {
        if (forceRefresh) {
            log.info("Force refreshing filtered match history request: puuid={}, begIndex={}, endIndex={}",
                    puuidPrefix(puuid), begIndex, endIndex);
        }
        List<MatchHistory> allMatches = getMatchHistoryFetchResult(puuid, forceRefresh).getMatches();
        if (allMatches.isEmpty()) {
            return List.of();
        }

        List<MatchHistory> filteredMatches = new ArrayList<>();
        for (MatchHistory match : allMatches) {
            boolean queueMatches = queueId == null || queueId <= 0
                    || (match.getQueueId() != null && match.getQueueId().equals(queueId));

            boolean championMatches = championId == null || championId <= 0;
            if (!championMatches && match.getParticipants() != null) {
                Integer participantId = findParticipantId(match, puuid);
                if (participantId != null) {
                    championMatches = match.getParticipants().stream()
                            .anyMatch(p -> participantId.equals(p.getParticipantId())
                                    && p.getChampionId() != null
                                    && p.getChampionId().equals(championId));
                }
            }

            if (queueMatches && championMatches) {
                filteredMatches.add(match);
            }
        }

        List<MatchHistory> sliced = sliceMatches(filteredMatches, begIndex, endIndex);
        if (maxResults > 0 && sliced.size() > maxResults) {
            sliced = new ArrayList<>(sliced.subList(0, maxResults));
        }
        return ensureRosterForVisibleMatches(puuid, allMatches, sliced);
    }

    private List<MatchHistory> ensureRosterForVisibleMatches(String puuid,
                                                             List<MatchHistory> cachedMatches,
                                                             List<MatchHistory> visibleMatches) {
        long completeBefore = visibleMatches.stream().filter(this::hasCompleteRoster).count();
        List<MatchHistory> hydratedMatches = ensureRosterForVisibleMatches(visibleMatches);
        long completeAfter = hydratedMatches.stream().filter(this::hasCompleteRoster).count();

        if (completeAfter > completeBefore && cacheRepository != null && cachedMatches != null && !cachedMatches.isEmpty()) {
            saveMatchHistoryToLocalCache(puuid, cachedMatches);
        }

        return hydratedMatches;
    }

    private List<MatchHistory> ensureRosterForVisibleMatches(List<MatchHistory> matches) {
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }

        List<MatchHistory> hydratedMatches = new ArrayList<>(matches.size());
        for (MatchHistory match : matches) {
            if (hasCompleteRoster(match) || match == null || match.getGameId() == null) {
                hydratedMatches.add(match);
                continue;
            }

            try {
                GameDetail detail = getGameDetailById(match.getGameId());
                hydratedMatches.add(mergeGameDetailIntoMatchHistory(match, detail));
            } catch (Exception e) {
                log.debug("Failed to hydrate visible match roster, gameId={}", match.getGameId(), e);
                hydratedMatches.add(match);
            }
        }
        return hydratedMatches;
    }

    private boolean hasCompleteRoster(MatchHistory match) {
        return match != null
                && match.getParticipants() != null
                && match.getParticipants().size() >= 10
                && match.getParticipantIdentities() != null
                && match.getParticipantIdentities().size() >= 10;
    }

    private MatchHistory mergeGameDetailIntoMatchHistory(MatchHistory match, GameDetail detail) {
        if (match == null || detail == null) {
            return match;
        }
        int currentParticipantCount = match.getParticipants() == null ? 0 : match.getParticipants().size();
        int currentIdentityCount = match.getParticipantIdentities() == null ? 0 : match.getParticipantIdentities().size();
        if (detail.getParticipants() != null && detail.getParticipants().size() >= currentParticipantCount) {
            match.setParticipants(detail.getParticipants().stream()
                    .map(this::toMatchParticipant)
                    .toList());
        }
        if (detail.getParticipantIdentities() != null && detail.getParticipantIdentities().size() >= currentIdentityCount) {
            match.setParticipantIdentities(detail.getParticipantIdentities().stream()
                    .map(this::toMatchParticipantIdentity)
                    .toList());
        }
        if (match.getQueueId() == null) {
            match.setQueueId(detail.getQueueId());
        }
        if (match.getGameMode() == null) {
            match.setGameMode(detail.getGameMode());
        }
        if (match.getGameType() == null) {
            match.setGameType(detail.getGameType());
        }
        if (match.getMapId() == null) {
            match.setMapId(detail.getMapId());
        }
        if (match.getGameCreation() == null) {
            match.setGameCreation(detail.getGameCreation());
        }
        if (match.getGameDuration() == null && detail.getGameDuration() != null) {
            match.setGameDuration(detail.getGameDuration().intValue());
        }
        return match;
    }

    private MatchHistory.Participant toMatchParticipant(GameDetail.GameParticipant gameParticipant) {
        MatchHistory.Participant participant = new MatchHistory.Participant();
        participant.setParticipantId(gameParticipant.getParticipantId());
        participant.setTeamId(gameParticipant.getTeamId());
        participant.setChampionId(gameParticipant.getChampionId());
        participant.setSpell1Id(gameParticipant.getSpell1Id());
        participant.setSpell2Id(gameParticipant.getSpell2Id());
        participant.setStats(toMatchStats(gameParticipant.getStats()));
        return participant;
    }

    private MatchHistory.Stats toMatchStats(GameDetail.Stats detailStats) {
        MatchHistory.Stats stats = new MatchHistory.Stats();
        if (detailStats == null) {
            return stats;
        }
        stats.setWin(detailStats.getWin());
        stats.setKills(detailStats.getKills());
        stats.setDeaths(detailStats.getDeaths());
        stats.setAssists(detailStats.getAssists());
        stats.setGoldEarned(toInteger(detailStats.getGoldEarned()));
        stats.setTotalDamageDealtToChampions(toInteger(detailStats.getTotalDamageDealtToChampions()));
        stats.setTotalDamageTaken(toInteger(detailStats.getTotalDamageTaken()));
        stats.setTotalHeal(toInteger(detailStats.getTotalHeal()));
        stats.setTotalMinionsKilled(detailStats.getTotalMinionsKilled());
        stats.setNeutralMinionsKilled(detailStats.getNeutralMinionsKilled());
        stats.setItem0(detailStats.getItem0());
        stats.setItem1(detailStats.getItem1());
        stats.setItem2(detailStats.getItem2());
        stats.setItem3(detailStats.getItem3());
        stats.setItem4(detailStats.getItem4());
        stats.setItem5(detailStats.getItem5());
        stats.setItem6(detailStats.getItem6());
        stats.setDamageDealtToChampionsRate(detailStats.getDamageDealtToChampionsRate());
        stats.setDamageTakenRate(detailStats.getDamageTakenRate());
        stats.setHealRate(detailStats.getHealRate());
        stats.setMvp(detailStats.getMvp());
        stats.setPerk0(detailStats.getPerk0());
        stats.setMinionsKilled(detailStats.getTotalMinionsKilled());
        stats.setDamageDealtToTurrets(toInteger(detailStats.getDamageDealtToTurrets()));
        stats.setPlayerAugment1(detailStats.getPlayerAugment1());
        stats.setPlayerAugment2(detailStats.getPlayerAugment2());
        stats.setPlayerAugment3(detailStats.getPlayerAugment3());
        stats.setPlayerAugment4(detailStats.getPlayerAugment4());
        return stats;
    }

    private MatchHistory.ParticipantIdentity toMatchParticipantIdentity(GameDetail.ParticipantIdentity identity) {
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
        return participantIdentity;
    }

    private Integer toInteger(Long value) {
        return value == null ? null : value.intValue();
    }

    /**
     * Win rate over recent matches.
     */
    public WinRate getWinRate(String puuid, Integer mode) {
        List<MatchHistory> matches = getMatchHistory(puuid, 0, 49);

        int wins = 0;
        int losses = 0;

        for (MatchHistory match : matches) {
            if (mode != null && mode > 0 && !mode.equals(match.getQueueId())) {
                continue;
            }

            Integer participantId = findParticipantId(match, puuid);
            if (participantId != null && match.getParticipants() != null) {
                for (MatchHistory.Participant p : match.getParticipants()) {
                    if (participantId.equals(p.getParticipantId()) && p.getStats() != null) {
                        if (Boolean.TRUE.equals(p.getStats().getWin())) {
                            wins++;
                        } else {
                            losses++;
                        }
                        break;
                    }
                }
            }
        }

        return WinRate.of(wins, losses);
    }

    /**
     * Ranked win rates over recent matches.
     */
    public Map<String, WinRate> getRankedWinRates(String puuid) {
        List<MatchHistory> matches = getMatchHistory(puuid, 0, 49);

        int soloWins = 0;
        int soloLosses = 0;
        int flexWins = 0;
        int flexLosses = 0;

        for (MatchHistory match : matches) {
            Integer queueId = match.getQueueId();
            if (queueId == null || (queueId != 420 && queueId != 440)) {
                continue;
            }

            Integer participantId = findParticipantId(match, puuid);
            if (participantId != null && match.getParticipants() != null) {
                for (MatchHistory.Participant p : match.getParticipants()) {
                    if (participantId.equals(p.getParticipantId()) && p.getStats() != null) {
                        boolean win = Boolean.TRUE.equals(p.getStats().getWin());
                        if (queueId == 420) {
                            if (win) {
                                soloWins++;
                            } else {
                                soloLosses++;
                            }
                        } else if (win) {
                            flexWins++;
                        } else {
                            flexLosses++;
                        }
                        break;
                    }
                }
            }
        }

        return Map.of(
                "RANKED_SOLO_5x5", WinRate.of(soloWins, soloLosses),
                "RANKED_FLEX_SR", WinRate.of(flexWins, flexLosses)
        );
    }

    private Integer findParticipantId(MatchHistory match, String puuid) {
        if (match.getParticipantIdentities() == null) {
            return null;
        }
        for (MatchHistory.ParticipantIdentity identity : match.getParticipantIdentities()) {
            if (identity.getPlayer() != null && puuid.equals(identity.getPlayer().getPuuid())) {
                return identity.getParticipantId();
            }
        }
        return null;
    }

    private void enrichParticipantStats(GameDetail detail) {
        if (detail == null || detail.getParticipants() == null || detail.getParticipants().isEmpty()) {
            return;
        }

        normalizeParticipantPositions(detail);

        for (GameDetail.GameParticipant participant : detail.getParticipants()) {
            GameDetail.Stats stats = participant.getStats();
            if (stats == null) {
                continue;
            }
            if (stats.getVisionScore() == null) {
                stats.setVisionScore(0);
            }
        }

        for (GameDetail.GameParticipant participant : detail.getParticipants()) {
            GameDetail.Stats stats = participant.getStats();
            if (stats == null) {
                continue;
            }
            stats.setEarlyGoldDiff(resolveEarlyGoldDiff(detail, participant));
        }
    }

    private Integer resolveEarlyGoldDiff(GameDetail detail, GameDetail.GameParticipant participant) {
        GameDetail.Stats stats = participant.getStats();
        if (stats == null) {
            return null;
        }

        Integer precomputedValue = readPrecomputedEarlyGoldDiff(stats);
        if (precomputedValue != null) {
            return precomputedValue;
        }

        String position = resolvePosition(participant);
        if (detail.getMapId() == null || detail.getMapId() != SUMMONERS_RIFT_MAP_ID || position == null) {
            return null;
        }

        GameDetail.GameParticipant opponent = findLaneOpponent(detail, participant, position);
        if (opponent == null || opponent.getStats() == null) {
            return null;
        }

        int ownCs = getEstimatedCreepScore(stats, position);
        int opponentCs = getEstimatedCreepScore(opponent.getStats(), position);
        double durationMinutes = detail.getGameDuration() == null ? 0D : detail.getGameDuration() / 60D;
        double earlyRatio = durationMinutes > 0D && durationMinutes < 15D ? 15D / durationMinutes : 1D;
        return (int) Math.round((ownCs - opponentCs) * earlyRatio * 20D);
    }

    private Integer readPrecomputedEarlyGoldDiff(GameDetail.Stats stats) {
        Number value = readNumber(stats.getChallenges(),
                "laneGoldDiff15",
                "goldDiff15",
                "goldDiffAt15",
                "goldDifferenceAt15",
                "fifteenMinuteGoldDiff",
                "earlyGoldDiff");
        if (value == null) {
            value = readNumber(stats.getExtraFields(),
                    "laneGoldDiff15",
                    "goldDiff15",
                    "goldDiffAt15",
                    "goldDifferenceAt15",
                    "fifteenMinuteGoldDiff",
                    "earlyGoldDiff");
        }
        return value == null ? null : (int) Math.round(value.doubleValue());
    }

    private GameDetail.GameParticipant findLaneOpponent(
            GameDetail detail,
            GameDetail.GameParticipant participant,
            String position) {
        if (detail == null || detail.getParticipants() == null || participant == null || participant.getTeamId() == null) {
            return null;
        }

        for (GameDetail.GameParticipant candidate : detail.getParticipants()) {
            if (candidate == null || candidate.getTeamId() == null || participant.getTeamId().equals(candidate.getTeamId())) {
                continue;
            }
            if (position.equals(resolvePosition(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    private void normalizeParticipantPositions(GameDetail detail) {
        Map<Integer, List<GameDetail.GameParticipant>> teams = new HashMap<>();
        for (GameDetail.GameParticipant participant : detail.getParticipants()) {
            if (participant == null || participant.getTeamId() == null) {
                continue;
            }
            ensureTimeline(participant);
            rememberRawTimeline(participant);
            teams.computeIfAbsent(participant.getTeamId(), ignored -> new ArrayList<>()).add(participant);
        }

        for (List<GameDetail.GameParticipant> team : teams.values()) {
            normalizeTeamPositions(team);
        }
    }

    private void normalizeTeamPositions(List<GameDetail.GameParticipant> team) {
        if (team == null || team.isEmpty()) {
            return;
        }

        Map<GameDetail.GameParticipant, String> assignedPositions = new HashMap<>();
        Set<GameDetail.GameParticipant> assignedParticipants = new HashSet<>();

        assignPosition(team, assignedPositions, assignedParticipants, POSITION_JUNGLE, this::chooseJungle);
        assignPosition(team, assignedPositions, assignedParticipants, POSITION_SUPPORT, this::chooseSupport);
        assignPosition(team, assignedPositions, assignedParticipants, POSITION_BOTTOM, this::chooseBottom);
        assignPosition(team, assignedPositions, assignedParticipants, POSITION_MIDDLE, this::chooseMiddle);
        assignPosition(team, assignedPositions, assignedParticipants, POSITION_TOP, this::chooseTop);

        for (GameDetail.GameParticipant participant : team) {
            if (!assignedPositions.containsKey(participant)) {
                String fallbackPosition = firstUnusedPosition(assignedPositions);
                if (fallbackPosition != null) {
                    assignedPositions.put(participant, fallbackPosition);
                }
            }
        }

        assignedPositions.forEach(this::applyNormalizedPosition);
    }

    private void assignPosition(
            List<GameDetail.GameParticipant> team,
            Map<GameDetail.GameParticipant, String> positions,
            Set<GameDetail.GameParticipant> used,
            String position,
            PositionChooser chooser) {
        GameDetail.GameParticipant participant = chooser.choose(team, used);
        if (participant != null) {
            positions.put(participant, position);
            used.add(participant);
        }
    }

    private GameDetail.GameParticipant chooseJungle(List<GameDetail.GameParticipant> team, Set<GameDetail.GameParticipant> used) {
        return team.stream()
                .filter(participant -> !used.contains(participant))
                .max(Comparator
                        .comparingInt((GameDetail.GameParticipant participant) -> hasSmite(participant) ? 1 : 0)
                        .thenComparingInt(participant -> intValue(participant.getStats() == null
                                ? null
                                : participant.getStats().getNeutralMinionsKilled())))
                .filter(participant -> hasSmite(participant)
                        || (participant.getStats() != null && intValue(participant.getStats().getNeutralMinionsKilled()) >= 20))
                .orElse(null);
    }

    private GameDetail.GameParticipant chooseSupport(List<GameDetail.GameParticipant> team, Set<GameDetail.GameParticipant> used) {
        return team.stream()
                .filter(participant -> !used.contains(participant))
                .filter(participant -> POSITION_SUPPORT.equals(rawPosition(participant)) || supportScore(participant) > 0)
                .max(Comparator.comparingInt(this::supportScore))
                .orElseGet(() -> team.stream()
                        .filter(participant -> !used.contains(participant))
                        .min(Comparator.comparingInt(participant -> intValue(participant.getStats() == null
                                ? null
                                : participant.getStats().getTotalMinionsKilled())))
                        .orElse(null));
    }

    private GameDetail.GameParticipant chooseBottom(List<GameDetail.GameParticipant> team, Set<GameDetail.GameParticipant> used) {
        return team.stream()
                .filter(participant -> !used.contains(participant))
                .filter(participant -> POSITION_BOTTOM.equals(rawPosition(participant)) || hasBottomCarrySpell(participant))
                .max(Comparator
                        .comparingInt((GameDetail.GameParticipant participant) -> POSITION_BOTTOM.equals(rawPosition(participant)) ? 1000 : 0)
                        .thenComparingInt(participant -> hasBottomCarrySpell(participant) ? 200 : 0)
                        .thenComparingInt(participant -> intValue(participant.getStats() == null
                                ? null
                                : participant.getStats().getTotalMinionsKilled())))
                .orElseGet(() -> team.stream()
                        .filter(participant -> !used.contains(participant))
                        .max(Comparator.comparingInt(participant -> intValue(participant.getStats() == null
                                ? null
                                : participant.getStats().getTotalMinionsKilled())))
                        .orElse(null));
    }

    private GameDetail.GameParticipant chooseMiddle(List<GameDetail.GameParticipant> team, Set<GameDetail.GameParticipant> used) {
        return team.stream()
                .filter(participant -> !used.contains(participant))
                .filter(participant -> POSITION_MIDDLE.equals(rawPosition(participant)) || hasTeleport(participant))
                .max(Comparator
                        .comparingInt((GameDetail.GameParticipant participant) -> POSITION_MIDDLE.equals(rawPosition(participant)) ? 1000 : 0)
                        .thenComparingInt(participant -> hasTeleport(participant) ? 200 : 0)
                        .thenComparingInt(participant -> intValue(participant.getStats() == null
                                ? null
                                : participant.getStats().getTotalMinionsKilled())))
                .orElseGet(() -> team.stream()
                        .filter(participant -> !used.contains(participant))
                        .max(Comparator.comparingInt(participant -> intValue(participant.getStats() == null
                                ? null
                                : participant.getStats().getTotalMinionsKilled())))
                        .orElse(null));
    }

    private GameDetail.GameParticipant chooseTop(List<GameDetail.GameParticipant> team, Set<GameDetail.GameParticipant> used) {
        return team.stream()
                .filter(participant -> !used.contains(participant))
                .filter(participant -> POSITION_TOP.equals(rawPosition(participant)) || hasTeleport(participant))
                .max(Comparator
                        .comparingInt((GameDetail.GameParticipant participant) -> POSITION_TOP.equals(rawPosition(participant)) ? 1000 : 0)
                        .thenComparingInt(participant -> hasTeleport(participant) ? 200 : 0)
                        .thenComparingInt(participant -> intValue(participant.getStats() == null
                                ? null
                                : participant.getStats().getTotalMinionsKilled())))
                .orElseGet(() -> team.stream()
                        .filter(participant -> !used.contains(participant))
                        .max(Comparator.comparingInt(participant -> intValue(participant.getStats() == null
                                ? null
                                : participant.getStats().getTotalMinionsKilled())))
                        .orElse(null));
    }

    private void applyNormalizedPosition(GameDetail.GameParticipant participant, String position) {
        GameDetail.Timeline timeline = ensureTimeline(participant);
        timeline.setLane(position);
        timeline.setTeamPosition(position);
        timeline.setPositionCn(positionCn(position));
        participant.setTeamPosition(position);
        participant.setIndividualPosition(position);

        if (POSITION_JUNGLE.equals(position)) {
            timeline.setRole("NONE");
        } else if (POSITION_SUPPORT.equals(position)) {
            timeline.setRole("SUPPORT");
        } else if (POSITION_BOTTOM.equals(position)) {
            timeline.setRole("CARRY");
        } else {
            timeline.setRole("SOLO");
        }
    }

    private String resolvePosition(GameDetail.GameParticipant participant) {
        if (participant == null || participant.getTimeline() == null) {
            return null;
        }

        String teamPosition = normalizePosition(participant.getTimeline().getTeamPosition());
        if (isKnownPosition(teamPosition)) {
            return teamPosition;
        }

        String lane = normalizePosition(participant.getTimeline().getLane());
        String role = normalizePosition(participant.getTimeline().getRole());

        if ("JUNGLE".equals(lane)) {
            return POSITION_JUNGLE;
        }
        if ("TOP".equals(lane)) {
            return POSITION_TOP;
        }
        if ("MIDDLE".equals(lane) || "MID".equals(lane)) {
            return POSITION_MIDDLE;
        }
        if ("BOTTOM".equals(lane) || "BOT".equals(lane)) {
            if (role != null && role.contains("SUPPORT")) {
                return POSITION_SUPPORT;
            }
            return POSITION_BOTTOM;
        }
        if ("UTILITY".equals(lane) || "SUPPORT".equals(lane) || (role != null && role.contains("SUPPORT"))) {
            return POSITION_SUPPORT;
        }
        if ("DUO_CARRY".equals(role)) {
            return POSITION_BOTTOM;
        }
        return null;
    }

    private String normalizePosition(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private int getEstimatedCreepScore(GameDetail.Stats stats, String position) {
        if (POSITION_JUNGLE.equals(position)) {
            return intValue(stats.getTotalMinionsKilled()) + intValue(stats.getNeutralMinionsKilled());
        }
        return intValue(stats.getTotalMinionsKilled());
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private Number readNumber(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number;
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Double.parseDouble(text);
                } catch (NumberFormatException ignored) {
                    // Ignore malformed optional fields and continue with the next candidate.
                }
            }
        }
        return null;
    }

    private GameDetail.Timeline ensureTimeline(GameDetail.GameParticipant participant) {
        if (participant.getTimeline() == null) {
            participant.setTimeline(new GameDetail.Timeline());
        }
        return participant.getTimeline();
    }

    private void rememberRawTimeline(GameDetail.GameParticipant participant) {
        GameDetail.Timeline timeline = ensureTimeline(participant);
        if (timeline.getRawLane() == null) {
            timeline.setRawLane(timeline.getLane());
        }
        if (timeline.getRawRole() == null) {
            timeline.setRawRole(timeline.getRole());
        }
    }

    private String rawPosition(GameDetail.GameParticipant participant) {
        if (participant == null || participant.getTimeline() == null) {
            return null;
        }

        String explicitPosition = normalizePosition(participant.getTeamPosition());
        if (!isKnownPosition(explicitPosition)) {
            explicitPosition = normalizePosition(participant.getIndividualPosition());
        }
        if (isKnownPosition(explicitPosition)) {
            return explicitPosition;
        }

        String lane = normalizePosition(participant.getTimeline().getRawLane());
        String role = normalizePosition(participant.getTimeline().getRawRole());

        if ("TOP".equals(lane)) {
            return POSITION_TOP;
        }
        if ("MIDDLE".equals(lane) || "MID".equals(lane)) {
            return POSITION_MIDDLE;
        }
        if ("BOTTOM".equals(lane) || "BOT".equals(lane)) {
            if (role != null && role.contains("SUPPORT")) {
                return POSITION_SUPPORT;
            }
            return POSITION_BOTTOM;
        }
        if ("UTILITY".equals(lane) || "SUPPORT".equals(lane)) {
            return POSITION_SUPPORT;
        }
        return null;
    }

    private String firstUnusedPosition(Map<GameDetail.GameParticipant, String> assignedPositions) {
        for (String position : List.of(POSITION_TOP, POSITION_JUNGLE, POSITION_MIDDLE, POSITION_BOTTOM, POSITION_SUPPORT)) {
            if (!assignedPositions.containsValue(position)) {
                return position;
            }
        }
        return null;
    }

    private boolean hasSmite(GameDetail.GameParticipant participant) {
        return spellEquals(participant, SUMMONER_SPELL_SMITE);
    }

    private boolean hasTeleport(GameDetail.GameParticipant participant) {
        return spellEquals(participant, SUMMONER_SPELL_TELEPORT);
    }

    private boolean hasBottomCarrySpell(GameDetail.GameParticipant participant) {
        return spellEquals(participant, SUMMONER_SPELL_HEAL) || spellEquals(participant, SUMMONER_SPELL_BARRIER);
    }

    private boolean spellEquals(GameDetail.GameParticipant participant, int spellId) {
        return participant != null && (Integer.valueOf(spellId).equals(participant.getSpell1Id())
                || Integer.valueOf(spellId).equals(participant.getSpell2Id()));
    }

    private int supportScore(GameDetail.GameParticipant participant) {
        if (participant == null || participant.getStats() == null) {
            return 0;
        }

        int score = 0;
        if (POSITION_SUPPORT.equals(rawPosition(participant))) {
            score += 1000;
        }
        String role = participant.getTimeline() == null ? null : normalizePosition(participant.getTimeline().getRawRole());
        if (role != null && role.contains("SUPPORT")) {
            score += 300;
        }
        score += intValue(participant.getStats().getVisionScore()) * 4;
        score -= intValue(participant.getStats().getTotalMinionsKilled()) * 2;
        score -= intValue(participant.getStats().getNeutralMinionsKilled()) * 4;
        return score;
    }

    private boolean isKnownPosition(String position) {
        return POSITION_TOP.equals(position)
                || POSITION_JUNGLE.equals(position)
                || POSITION_MIDDLE.equals(position)
                || POSITION_BOTTOM.equals(position)
                || POSITION_SUPPORT.equals(position);
    }

    private String positionCn(String position) {
        return switch (position) {
            case POSITION_TOP -> "上路";
            case POSITION_JUNGLE -> "打野";
            case POSITION_MIDDLE -> "中路";
            case POSITION_BOTTOM -> "下路";
            case POSITION_SUPPORT -> "辅助";
            default -> "未知";
        };
    }

    @FunctionalInterface
    private interface PositionChooser {
        GameDetail.GameParticipant choose(List<GameDetail.GameParticipant> team, Set<GameDetail.GameParticipant> used);
    }

    public void refreshCache(String puuid) {
        matchHistoryCache.invalidate(puuid);
    }

    public void refreshAllCache() {
        matchHistoryCache.invalidateAll();
        gameDetailCache.invalidateAll();
    }

    private String puuidPrefix(String puuid) {
        if (puuid == null || puuid.isBlank()) {
            return "null";
        }
        return puuid.substring(0, Math.min(8, puuid.length()));
    }
}
