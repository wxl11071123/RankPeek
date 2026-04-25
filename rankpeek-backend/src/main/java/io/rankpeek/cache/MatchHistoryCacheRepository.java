package io.rankpeek.cache;

import io.rankpeek.model.GameDetail;
import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.MatchHistoryFetchResult;
import io.rankpeek.model.Rank;
import io.rankpeek.model.Summoner;

import java.util.List;
import java.util.Optional;

public interface MatchHistoryCacheRepository {

    Optional<MatchHistoryFetchResult> findRecentMatchHistory(String puuid, int limit);

    void saveMatchHistory(String puuid, List<MatchHistory> matches);

    Optional<GameDetail> findGameDetail(Long gameId);

    void saveGameDetail(GameDetail detail);

    Optional<Summoner> findSummonerByPuuid(String puuid);

    Optional<Summoner> findSummonerByName(String gameName, String tagLine);

    void saveSummoner(Summoner summoner);

    Optional<Rank> findRank(String puuid);

    void saveRank(String puuid, Rank rank);

    void updatePlayerFetchState(String puuid, List<MatchHistory> matches, String status, String lastError);

    Optional<Long> getMatchUpdatedAt(String puuid);

    void trimPlayerMatchIndex(String puuid, int keepCount);
}
