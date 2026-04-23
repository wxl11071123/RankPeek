package io.rankpeek.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Match-history service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchHistoryService {

    private final LcuHttpClient lcuHttpClient;

    private Cache<String, MatchHistoryFetchResult> matchHistoryCache;

    @PostConstruct
    public void init() {
        this.matchHistoryCache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
        log.info("战绩服务初始化完成");
    }

    /**
     * Returns the cached raw fetch result for a player.
     */
    public MatchHistoryFetchResult getMatchHistoryFetchResult(String puuid) {
        return matchHistoryCache.get(puuid, this::fetchMatchHistoryResult);
    }

    /**
     * Fetch visible match history.
     */
    public List<MatchHistory> getMatchHistory(String puuid, int begIndex, int endIndex) {
        List<MatchHistory> matches = getMatchHistoryFetchResult(puuid).getMatches();
        return sliceMatches(matches, begIndex, endIndex);
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
        String uri = String.format("lol-match-history/v1/products/lol/%s/matches?begIndex=%d&endIndex=%d",
                puuid, 0, 49);

        JsonNode response = lcuHttpClient.get(uri, JsonNode.class);
        JsonNode gamesNode = extractGamesNode(response);
        List<MatchHistory> matches = new ArrayList<>();

        if (gamesNode != null && gamesNode.isArray()) {
            for (JsonNode game : gamesNode) {
                matches.add(lcuHttpClient.getObjectMapper().convertValue(game, MatchHistory.class));
            }
        }

        return MatchHistoryFetchResult.builder()
                .matches(matches)
                .rawEmpty(matches.isEmpty())
                .build();
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
        String uri = String.format("lol-match-history/v1/games/%d", gameId);
        return lcuHttpClient.get(uri, GameDetail.class);
    }

    /**
     * Fetch filtered match history.
     */
    public List<MatchHistory> getFilteredMatchHistory(String puuid, int begIndex, int endIndex,
                                                      Integer queueId, Integer championId, int maxResults) {
        List<MatchHistory> allMatches = getMatchHistoryFetchResult(puuid).getMatches();
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
            return new ArrayList<>(sliced.subList(0, maxResults));
        }
        return sliced;
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

    public void refreshCache(String puuid) {
        matchHistoryCache.invalidate(puuid);
    }

    public void refreshAllCache() {
        matchHistoryCache.invalidateAll();
    }
}
