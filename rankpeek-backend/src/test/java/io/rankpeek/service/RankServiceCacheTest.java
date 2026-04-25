package io.rankpeek.service;

import io.rankpeek.cache.MatchHistoryCacheRepository;
import io.rankpeek.model.Rank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankServiceCacheTest {

    @Mock
    private LcuHttpClient lcuHttpClient;
    @Mock
    private MatchHistoryCacheRepository repository;

    private RankService service;

    @BeforeEach
    void setUp() {
        service = new RankService(lcuHttpClient, repository);
        service.init();
    }

    @Test
    void getRankByPuuid_usesDatabaseOnCacheMiss() {
        Rank cached = createRank("GOLD");
        when(repository.findRank("puuid-1")).thenReturn(Optional.of(cached));

        assertThat(service.getRankByPuuid("puuid-1")).isSameAs(cached);
    }

    @Test
    void getRankByPuuid_savesLcuResultAndFallsBackToDatabaseOnFailure() {
        Rank fetched = createRank("PLATINUM");
        when(repository.findRank("puuid-1")).thenReturn(Optional.empty());
        when(lcuHttpClient.get("lol-ranked/v1/ranked-stats/puuid-1", Rank.class)).thenReturn(fetched);

        assertThat(service.getRankByPuuid("puuid-1")).isSameAs(fetched);
        verify(repository).saveRank("puuid-1", fetched);

        Rank stale = createRank("GOLD");
        when(repository.findRank("puuid-2")).thenReturn(Optional.empty(), Optional.of(stale));
        when(lcuHttpClient.get("lol-ranked/v1/ranked-stats/puuid-2", Rank.class))
                .thenThrow(new RuntimeException("LCU down"));

        assertThat(service.getRankByPuuid("puuid-2")).isSameAs(stale);
    }

    private Rank createRank(String tier) {
        Rank rank = new Rank();
        Rank.QueueMap queueMap = new Rank.QueueMap();
        Rank.QueueInfo solo = new Rank.QueueInfo();
        solo.setTier(tier);
        solo.setWins(10);
        solo.setLosses(5);
        queueMap.setRankedSolo5x5(solo);
        rank.setQueueMap(queueMap);
        return rank;
    }
}
