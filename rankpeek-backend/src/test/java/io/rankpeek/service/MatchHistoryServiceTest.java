package io.rankpeek.service;

import io.rankpeek.cache.MatchHistoryCacheRepository;
import io.rankpeek.model.MatchDataScopeCache;
import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.MatchHistoryFetchResult;
import io.rankpeek.model.MatchHistoryPageResponse;
import io.rankpeek.model.MatchTimeline;
import io.rankpeek.model.MatchTimelineFetchResult;
import io.rankpeek.model.Rank;
import io.rankpeek.model.RecordStatus;
import io.rankpeek.service.matchhistory.MatchHistoryProvider;
import io.rankpeek.service.matchhistory.MatchHistoryQueryOptions;
import io.rankpeek.service.matchhistory.MatchHistorySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.after;

@ExtendWith(MockitoExtension.class)
class MatchHistoryServiceTest {

    @Mock
    private MatchHistoryProvider matchHistoryProvider;
    @Mock
    private MatchHistoryProvider sgpMatchHistoryProvider;
    @Mock
    private MatchHistoryCacheRepository cacheRepository;

    private MatchHistoryService matchHistoryService;

    @BeforeEach
    void setUp() {
        matchHistoryService = new MatchHistoryService(matchHistoryProvider);
        matchHistoryService.init();
    }

    @Test
    void getMatchHistoryFetchResult_usesProviderAndCachesOnRepeatedReads() {
        when(matchHistoryProvider.fetchMatchHistory(any(String.class), any(MatchHistoryQueryOptions.class)))
                .thenReturn(resultWithMatch(1L));

        MatchHistoryFetchResult first = matchHistoryService.getMatchHistoryFetchResult("puuid-1");
        MatchHistoryFetchResult second = matchHistoryService.getMatchHistoryFetchResult("puuid-1");

        assertThat(first.getMatches()).hasSize(1);
        assertThat(second.getMatches()).hasSize(1);
        verify(matchHistoryProvider, times(1))
                .fetchMatchHistory("puuid-1", options(MatchHistorySource.AUTO, false));
    }

    @Test
    void getMatchHistoryFetchResult_forceRefreshInvalidatesCachedEntryAndPassesRefreshOption() {
        when(matchHistoryProvider.fetchMatchHistory(any(String.class), any(MatchHistoryQueryOptions.class)))
                .thenReturn(resultWithMatch(1L), resultWithMatch(2L));

        MatchHistoryFetchResult cached = matchHistoryService.getMatchHistoryFetchResult("puuid-1");
        MatchHistoryFetchResult stillCached = matchHistoryService.getMatchHistoryFetchResult("puuid-1");
        MatchHistoryFetchResult refreshed = matchHistoryService.getMatchHistoryFetchResult("puuid-1", true);

        assertThat(cached.getMatches()).extracting(MatchHistory::getGameId).containsExactly(1L);
        assertThat(stillCached.getMatches()).extracting(MatchHistory::getGameId).containsExactly(1L);
        assertThat(refreshed.getMatches()).extracting(MatchHistory::getGameId).containsExactly(2L);
        verify(matchHistoryProvider).fetchMatchHistory("puuid-1", options(MatchHistorySource.AUTO, false));
        verify(matchHistoryProvider).fetchMatchHistory("puuid-1", options(MatchHistorySource.AUTO, true));
    }

    @Test
    void getMatchHistoryFetchResult_sourceLcuUsesLcuProvider() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(matchHistoryProvider.fetchMatchHistory("puuid-1", options(MatchHistorySource.LCU, false)))
                .thenReturn(resultWithMatch(1L));

        MatchHistoryFetchResult result = sourceAwareService.getMatchHistoryFetchResult(
                "puuid-1",
                false,
                MatchHistorySource.LCU
        );

        assertThat(result.getMatches()).extracting(MatchHistory::getGameId).containsExactly(1L);
        verify(matchHistoryProvider).fetchMatchHistory("puuid-1", options(MatchHistorySource.LCU, false));
        verify(sgpMatchHistoryProvider, never())
                .fetchMatchHistory(any(String.class), any(MatchHistoryQueryOptions.class));
    }

    @Test
    void getMatchHistoryFetchResult_sourceSgpUsesSgpProvider() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", options(MatchHistorySource.SGP, false)))
                .thenReturn(resultWithMatch(2L));

        MatchHistoryFetchResult result = sourceAwareService.getMatchHistoryFetchResult(
                "puuid-1",
                false,
                MatchHistorySource.SGP
        );

        assertThat(result.getMatches()).extracting(MatchHistory::getGameId).containsExactly(2L);
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", options(MatchHistorySource.SGP, false));
        verify(matchHistoryProvider, never()).fetchMatchHistory(any(String.class), any(MatchHistoryQueryOptions.class));
    }

    @Test
    void getMatchHistoryFetchResult_sourceAutoUsesSgpWhenReady() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.supports(options(MatchHistorySource.AUTO, false))).thenReturn(true);
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", options(MatchHistorySource.AUTO, false)))
                .thenReturn(resultWithMatch(3L));

        MatchHistoryFetchResult result = sourceAwareService.getMatchHistoryFetchResult(
                "puuid-1",
                false,
                MatchHistorySource.AUTO
        );

        assertThat(result.getMatches()).extracting(MatchHistory::getGameId).containsExactly(3L);
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", options(MatchHistorySource.AUTO, false));
        verify(matchHistoryProvider, never()).fetchMatchHistory(any(String.class), any(MatchHistoryQueryOptions.class));
    }

    @Test
    void getMatchHistoryFetchResult_defaultSourcePrefersSgpWhenReady() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.supports(options(MatchHistorySource.AUTO, false))).thenReturn(true);
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", options(MatchHistorySource.AUTO, false)))
                .thenReturn(resultWithMatch(33L));

        MatchHistoryFetchResult result = sourceAwareService.getMatchHistoryFetchResult("puuid-1");

        assertThat(result.getMatches()).extracting(MatchHistory::getGameId).containsExactly(33L);
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", options(MatchHistorySource.AUTO, false));
        verify(matchHistoryProvider, never()).fetchMatchHistory(any(String.class), any(MatchHistoryQueryOptions.class));
    }

    @Test
    void getMatchHistoryFetchResult_sourceAutoFallsBackToLcuWhenSgpUnavailable() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.supports(options(MatchHistorySource.AUTO, false))).thenReturn(false);
        when(matchHistoryProvider.fetchMatchHistory("puuid-1", options(MatchHistorySource.AUTO, false)))
                .thenReturn(resultWithMatch(4L));

        MatchHistoryFetchResult result = sourceAwareService.getMatchHistoryFetchResult(
                "puuid-1",
                false,
                MatchHistorySource.AUTO
        );

        assertThat(result.getMatches()).extracting(MatchHistory::getGameId).containsExactly(4L);
        verify(matchHistoryProvider).fetchMatchHistory("puuid-1", options(MatchHistorySource.AUTO, false));
        verify(sgpMatchHistoryProvider, never())
                .fetchMatchHistory(any(String.class), any(MatchHistoryQueryOptions.class));
    }

    @Test
    void getMatchHistoryFetchResult_cacheSeparatesLcuAndSgpSources() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(matchHistoryProvider.fetchMatchHistory("puuid-1", options(MatchHistorySource.LCU, false)))
                .thenReturn(resultWithMatch(5L));
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", options(MatchHistorySource.SGP, false)))
                .thenReturn(resultWithMatch(6L));

        MatchHistoryFetchResult lcu = sourceAwareService.getMatchHistoryFetchResult(
                "puuid-1",
                false,
                MatchHistorySource.LCU
        );
        MatchHistoryFetchResult sgp = sourceAwareService.getMatchHistoryFetchResult(
                "puuid-1",
                false,
                MatchHistorySource.SGP
        );

        assertThat(lcu.getMatches()).extracting(MatchHistory::getGameId).containsExactly(5L);
        assertThat(sgp.getMatches()).extracting(MatchHistory::getGameId).containsExactly(6L);
        verify(matchHistoryProvider).fetchMatchHistory("puuid-1", options(MatchHistorySource.LCU, false));
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", options(MatchHistorySource.SGP, false));
    }

    @Test
    void getMatchHistoryWithFetchLimit_prefersSgpAutoSource() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.supports(limitOptions(MatchHistorySource.AUTO, 50))).thenReturn(true);
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", limitOptions(MatchHistorySource.AUTO, 50)))
                .thenReturn(resultWithMatches(
                        match(7L, 420, "puuid-1", 11),
                        match(8L, 420, "puuid-1", 12)
                ));

        List<MatchHistory> matches = sourceAwareService.getMatchHistory("puuid-1", 0, 49, 50);

        assertThat(matches).extracting(MatchHistory::getGameId).containsExactly(7L, 8L);
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", limitOptions(MatchHistorySource.AUTO, 50));
        verify(matchHistoryProvider, never()).fetchMatchHistory(any(String.class), any(MatchHistoryQueryOptions.class));
    }

    @Test
    void getMatchHistoryWithFetchLimitFallsBackToLcuWhenSgpFetchFails() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.supports(limitOptions(MatchHistorySource.AUTO, 50))).thenReturn(true);
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", limitOptions(MatchHistorySource.AUTO, 50)))
                .thenThrow(new RuntimeException("sgp down"));
        when(matchHistoryProvider.fetchMatchHistory("puuid-1", limitOptions(MatchHistorySource.LCU, 50)))
                .thenReturn(resultWithMatches(match(9L, 420, "puuid-1", 11)));

        List<MatchHistory> matches = sourceAwareService.getMatchHistory("puuid-1", 0, 49, 50);

        assertThat(matches).extracting(MatchHistory::getGameId).containsExactly(9L);
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", limitOptions(MatchHistorySource.AUTO, 50));
        verify(matchHistoryProvider).fetchMatchHistory("puuid-1", limitOptions(MatchHistorySource.LCU, 50));
    }

    @Test
    void getGameDetailById_sourceSgpUsesSgpProvider() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        var detail = renderableGameDetail(99L);
        when(sgpMatchHistoryProvider.fetchGameDetail(99L, options(MatchHistorySource.SGP, false)))
                .thenReturn(detail);

        assertThat(sourceAwareService.getGameDetailById(99L, MatchHistorySource.SGP)).isSameAs(detail);
        verify(sgpMatchHistoryProvider).fetchGameDetail(99L, options(MatchHistorySource.SGP, false));
        verify(matchHistoryProvider, never()).fetchGameDetail(any(Long.class), any(MatchHistoryQueryOptions.class));
    }

    @Test
    void getGameDetailById_defaultSourcePrefersSgpWhenReady() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        var detail = renderableGameDetail(100L);
        when(sgpMatchHistoryProvider.supports(options(MatchHistorySource.AUTO, false))).thenReturn(true);
        when(sgpMatchHistoryProvider.fetchGameDetail(100L, options(MatchHistorySource.AUTO, false)))
                .thenReturn(detail);

        assertThat(sourceAwareService.getGameDetailById(100L)).isSameAs(detail);
        verify(sgpMatchHistoryProvider).fetchGameDetail(100L, options(MatchHistorySource.AUTO, false));
        verify(matchHistoryProvider, never()).fetchGameDetail(any(Long.class), any(MatchHistoryQueryOptions.class));
    }

    @Test
    void getGameDetailById_defaultSourceFallsBackToLcuWhenSgpReturnsHollowDetail() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        var hollowDetail = hollowGameDetail(101L);
        var lcuDetail = renderableGameDetail(101L);
        when(sgpMatchHistoryProvider.supports(options(MatchHistorySource.AUTO, false))).thenReturn(true);
        when(sgpMatchHistoryProvider.fetchGameDetail(101L, options(MatchHistorySource.AUTO, false)))
                .thenReturn(hollowDetail);
        when(matchHistoryProvider.fetchGameDetail(101L, options(MatchHistorySource.LCU, false)))
                .thenReturn(lcuDetail);

        assertThat(sourceAwareService.getGameDetailById(101L)).isSameAs(lcuDetail);
        verify(sgpMatchHistoryProvider).fetchGameDetail(101L, options(MatchHistorySource.AUTO, false));
        verify(matchHistoryProvider).fetchGameDetail(101L, options(MatchHistorySource.LCU, false));
    }

    @Test
    void getGameDetailById_sourceCacheReturnsCachedDetailWithoutProvider() {
        MatchHistoryService sourceAwareService = sourceAwareServiceWithCacheRepository();
        var cachedDetail = renderableGameDetail(102L);
        when(cacheRepository.findGameDetail(102L)).thenReturn(Optional.of(cachedDetail));

        assertThat(sourceAwareService.getGameDetailById(102L, MatchHistorySource.CACHE)).isSameAs(cachedDetail);
        verify(cacheRepository).findGameDetail(102L);
        verify(matchHistoryProvider, never()).fetchGameDetail(any(Long.class), any(MatchHistoryQueryOptions.class));
        verify(sgpMatchHistoryProvider, never()).fetchGameDetail(any(Long.class), any(MatchHistoryQueryOptions.class));
    }

    @Test
    void getMatchHistoryPage_sourceAutoUsesSgpAndCalculatesHasNext() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.supports(pageOptions(MatchHistorySource.AUTO, false, 3))).thenReturn(true);
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.AUTO, false, 3)))
                .thenReturn(resultWithMatches(
                        match(1L, 420, "puuid-1", 11),
                        match(2L, 420, "puuid-1", 12),
                        match(3L, 420, "puuid-1", 13)
                ));

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                2,
                "auto",
                null,
                null,
                false,
                null
        );

        assertThat(response.getMatches()).extracting(MatchHistory::getGameId).containsExactly(1L, 2L);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(2);
        assertThat(response.isHasNext()).isTrue();
        assertThat(response.getSource()).isEqualTo("sgp");
        assertThat(response.getRecordStatus()).isEqualTo(RecordStatus.NORMAL);
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.AUTO, false, 3));
    }

    @Test
    void getMatchHistoryPage_fetchesOnlyEnoughRowsForRequestedFirstPage() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.SGP, false, 21)))
                .thenReturn(resultWithMatches(
                        match(1L, 420, "puuid-1", 11),
                        match(2L, 420, "puuid-1", 12)
                ));

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                20,
                "sgp",
                null,
                null,
                false,
                null
        );

        assertThat(response.getMatches()).extracting(MatchHistory::getGameId).containsExactly(1L, 2L);
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.SGP, false, 21));
    }

    @Test
    void getMatchHistoryPage_sourceSgpDoesNotHydrateDetailsWhenCurrentParticipantIsRenderable() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.SGP, false, 21)))
                .thenReturn(resultWithMatches(
                        renderableCurrentOnlyMatch(77L, 420, "puuid-1", 11)
                ));

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                20,
                "sgp",
                null,
                null,
                false,
                null
        );

        assertThat(response.getMatches()).extracting(MatchHistory::getGameId).containsExactly(77L);
        verify(sgpMatchHistoryProvider, never())
                .fetchGameDetail(any(Long.class), any(MatchHistoryQueryOptions.class));
    }

    @Test
    void getMatchHistoryPage_sourceSgpSavesRawSummaryAndBackfillsTimelineAsync() {
        MatchHistoryService sourceAwareService = sourceAwareServiceWithCacheRepository();
        MatchHistory match = renderableCurrentOnlyMatch(78L, 420, "puuid-1", 11);
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.SGP, false, 21)))
                .thenReturn(MatchHistoryFetchResult.builder()
                        .matches(List.of(match))
                        .rawSummaryJsonByGameId(Map.of(78L, "{\"gameId\":78}"))
                        .build());
        MatchTimeline timeline = new MatchTimeline();
        timeline.setGameId(78L);
        when(sgpMatchHistoryProvider.fetchGameTimeline(78L, pageOptions(MatchHistorySource.SGP, false, 21)))
                .thenReturn(MatchTimelineFetchResult.builder()
                        .gameId(78L)
                        .timeline(timeline)
                        .rawDetailJson("{\"json\":{\"gameId\":78,\"frames\":[]}}")
                        .rawTimelineJson("{\"json\":{\"gameId\":78,\"frames\":[]}}")
                        .status("FETCHED")
                        .build());

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                20,
                "sgp",
                null,
                null,
                false,
                null
        );

        assertThat(response.getMatches()).extracting(MatchHistory::getGameId).containsExactly(78L);
        verify(sgpMatchHistoryProvider, never())
                .fetchGameDetail(any(Long.class), any(MatchHistoryQueryOptions.class));
        verify(cacheRepository, timeout(1000)).saveSgpRawSummaries(Map.of(78L, "{\"gameId\":78}"));
        verify(sgpMatchHistoryProvider, timeout(1000))
                .fetchGameTimeline(78L, pageOptions(MatchHistorySource.SGP, false, 21));
        verify(cacheRepository, timeout(1000))
                .saveSgpRawDetail(78L, "{\"json\":{\"gameId\":78,\"frames\":[]}}", "FETCHED", null);
        verify(cacheRepository, timeout(1000))
                .saveSgpTimeline(78L, timeline, "{\"json\":{\"gameId\":78,\"frames\":[]}}", "FETCHED", null);
    }

    @Test
    void getMatchHistoryPage_sourceSgpSkipsTimelineBackfillWhenScopeIsTerminal() {
        MatchHistoryService sourceAwareService = sourceAwareServiceWithCacheRepository();
        MatchHistory match = renderableCurrentOnlyMatch(78L, 420, "puuid-1", 11);
        MatchDataScopeCache scope = new MatchDataScopeCache();
        scope.setGameId(78L);
        scope.setDetailStatus("FETCHED");
        scope.setTimelineStatus("FETCHED");
        when(cacheRepository.findMatchDataScope(78L)).thenReturn(Optional.of(scope));
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.SGP, false, 21)))
                .thenReturn(MatchHistoryFetchResult.builder()
                        .matches(List.of(match))
                        .rawSummaryJsonByGameId(Map.of(78L, "{\"gameId\":78}"))
                        .build());

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                20,
                "sgp",
                null,
                null,
                false,
                null
        );

        assertThat(response.getMatches()).extracting(MatchHistory::getGameId).containsExactly(78L);
        verify(sgpMatchHistoryProvider, after(300).never())
                .fetchGameTimeline(78L, pageOptions(MatchHistorySource.SGP, false, 21));
    }

    @Test
    void getMatchHistoryPage_sourceCacheReadsRequestedPageSizeFromLocalCache() {
        MatchHistoryService sourceAwareService = sourceAwareServiceWithCacheRepository();
        List<MatchHistory> matches = new java.util.ArrayList<>();
        for (long gameId = 1; gameId <= 200; gameId++) {
            matches.add(match(gameId, 420, "puuid-1", 11));
        }
        when(cacheRepository.findRecentMatchHistory("puuid-1", 200))
                .thenReturn(Optional.of(MatchHistoryFetchResult.builder()
                        .matches(matches)
                        .build()));

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                200,
                "cache",
                null,
                null,
                false,
                null
        );

        assertThat(response.getMatches()).hasSize(200);
        assertThat(response.getSource()).isEqualTo("cache");
        verify(cacheRepository).findRecentMatchHistory("puuid-1", 200);
    }

    @Test
    void getMatchHistoryPage_sourceSgpReusesLocalCacheBeforeProviderWhenNotForceRefreshing() {
        MatchHistoryService sourceAwareService = sourceAwareServiceWithCacheRepository();
        List<MatchHistory> matches = new java.util.ArrayList<>();
        for (long gameId = 1; gameId <= 200; gameId++) {
            matches.add(match(gameId, 420, "puuid-1", 11));
        }
        when(cacheRepository.findRecentMatchHistory("puuid-1", 200))
                .thenReturn(Optional.of(MatchHistoryFetchResult.builder()
                        .matches(matches)
                        .build()));

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                200,
                "sgp",
                null,
                null,
                false,
                null
        );

        assertThat(response.getMatches()).hasSize(200);
        assertThat(response.getSource()).isEqualTo("sgp");
        verify(cacheRepository).findRecentMatchHistory("puuid-1", 200);
        verify(sgpMatchHistoryProvider, never())
                .fetchMatchHistory(any(String.class), any(MatchHistoryQueryOptions.class));
    }

    @Test
    void getMatchHistoryPage_sourceAutoFallsBackToLcuWhenSgpFetchFails() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.supports(pageOptions(MatchHistorySource.AUTO, false, 11))).thenReturn(true);
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.AUTO, false, 11)))
                .thenThrow(new RuntimeException("sgp down"));
        when(matchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.LCU, false, 11)))
                .thenReturn(resultWithMatches(match(44L, 420, "puuid-1", 11)));

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                10,
                "auto",
                null,
                null,
                false,
                null
        );

        assertThat(response.getMatches()).extracting(MatchHistory::getGameId).containsExactly(44L);
        assertThat(response.getSource()).isEqualTo("lcu");
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.AUTO, false, 11));
        verify(matchHistoryProvider).fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.LCU, false, 11));
    }

    @Test
    void getMatchHistoryPage_sourceAutoReportsCacheWhenSgpFetchFallsBackToDatabaseCache() {
        MatchHistoryService sourceAwareService = sourceAwareServiceWithCacheRepository();
        when(sgpMatchHistoryProvider.supports(pageOptions(MatchHistorySource.AUTO, true, 11))).thenReturn(true);
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.AUTO, true, 11)))
                .thenThrow(new RuntimeException("sgp timeout"));
        when(cacheRepository.findRecentMatchHistory("puuid-1", 11))
                .thenReturn(Optional.of(resultWithMatches(match(55L, 420, "puuid-1", 11))));

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                10,
                "auto",
                null,
                null,
                true,
                null
        );

        assertThat(response.getMatches()).extracting(MatchHistory::getGameId).containsExactly(55L);
        assertThat(response.getSource()).isEqualTo("cache");
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.AUTO, true, 11));
        verify(matchHistoryProvider, never())
                .fetchMatchHistory(any(String.class), any(MatchHistoryQueryOptions.class));
    }

    @Test
    void getMatchHistoryPage_forceRefreshRejectsSingleShortSgpResultAndUsesCacheFallback() {
        MatchHistoryService sourceAwareService = sourceAwareServiceWithCacheRepository();
        List<MatchHistory> cachedMatches = new java.util.ArrayList<>();
        for (long gameId = 1001; gameId <= 1020; gameId++) {
            cachedMatches.add(match(gameId, 420, "puuid-1", 11));
        }
        MatchHistory shortMatch = renderableCurrentOnlyMatch(77L, 420, "puuid-1", 11);
        shortMatch.setGameDuration(180);
        shortMatch.setRemake(true);
        when(sgpMatchHistoryProvider.supports(pageOptions(MatchHistorySource.AUTO, true, 21))).thenReturn(true);
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.AUTO, true, 21)))
                .thenReturn(resultWithMatches(shortMatch));
        when(cacheRepository.findRecentMatchHistory("puuid-1", 21))
                .thenReturn(Optional.of(MatchHistoryFetchResult.builder()
                        .matches(cachedMatches)
                        .build()));

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                20,
                "auto",
                null,
                null,
                true,
                null
        );

        assertThat(response.getMatches()).extracting(MatchHistory::getGameId)
                .containsExactlyElementsOf(cachedMatches.stream().map(MatchHistory::getGameId).toList());
        assertThat(response.getSource()).isEqualTo("cache");
        verify(cacheRepository).findRecentMatchHistory("puuid-1", 21);
        verify(cacheRepository, after(300).never())
                .saveMatchHistory(eq("puuid-1"), argThat(matches -> matches.stream()
                        .anyMatch(match -> match != null && Long.valueOf(77L).equals(match.getGameId()))));
    }

    @Test
    void getMatchHistoryPage_forceRefreshSavesAndReturnsEnoughRenderableSgpMatches() {
        MatchHistoryService sourceAwareService = sourceAwareServiceWithCacheRepository();
        List<MatchHistory> sgpMatches = new java.util.ArrayList<>();
        for (long gameId = 2001; gameId <= 2020; gameId++) {
            sgpMatches.add(renderableCurrentOnlyMatch(gameId, 420, "puuid-1", 11));
        }
        MatchDataScopeCache terminalScope = new MatchDataScopeCache();
        terminalScope.setDetailStatus("FETCHED");
        terminalScope.setTimelineStatus("FETCHED");
        when(cacheRepository.findMatchDataScope(any(Long.class))).thenReturn(Optional.of(terminalScope));
        when(sgpMatchHistoryProvider.supports(pageOptions(MatchHistorySource.AUTO, true, 21))).thenReturn(true);
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.AUTO, true, 21)))
                .thenReturn(resultWithMatches(sgpMatches.toArray(MatchHistory[]::new)));

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                20,
                "auto",
                null,
                null,
                true,
                null
        );

        assertThat(response.getMatches()).hasSize(20);
        assertThat(response.getSource()).isEqualTo("sgp");
        verify(cacheRepository, timeout(1000)).saveMatchHistory(eq("puuid-1"), argThat(matches ->
                matches.size() == 20
                        && matches.stream().allMatch(match -> match != null && match.getGameId() >= 2001L)));
    }

    @Test
    void getMatchHistoryPage_sourceAutoFallsBackToLcuWhenSgpPageIsNotRenderable() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.supports(pageOptions(MatchHistorySource.AUTO, true, 11))).thenReturn(true);
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.AUTO, true, 11)))
                .thenReturn(resultWithMatches(nonRenderableCurrentOnlyMatch(77L, 420)));
        when(matchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.LCU, true, 11)))
                .thenReturn(resultWithMatches(match(44L, 420, "puuid-1", 11)));

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                10,
                "auto",
                null,
                null,
                true,
                null
        );

        assertThat(response.getMatches()).extracting(MatchHistory::getGameId).containsExactly(44L);
        assertThat(response.getSource()).isEqualTo("lcu");
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.AUTO, true, 11));
        verify(matchHistoryProvider).fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.LCU, true, 11));
        verify(sgpMatchHistoryProvider, never())
                .fetchGameDetail(any(Long.class), any(MatchHistoryQueryOptions.class));
    }

    @Test
    void getMatchHistoryPage_sourceSgpPaginatesSecondPage() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.SGP, false, 5)))
                .thenReturn(resultWithMatches(
                        match(1L, 420, "puuid-1", 11),
                        match(2L, 420, "puuid-1", 12),
                        match(3L, 420, "puuid-1", 13)
                ));

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                2,
                2,
                "sgp",
                null,
                null,
                false,
                null
        );

        assertThat(response.getMatches()).extracting(MatchHistory::getGameId).containsExactly(3L);
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.getSource()).isEqualTo("sgp");
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.SGP, false, 5));
    }

    @Test
    void getMatchHistoryPage_normalizesUnsafePageSizes() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.SGP, false, 21)))
                .thenReturn(resultWithMatches(
                        match(1L, 420, "puuid-1", 11),
                        match(2L, 420, "puuid-1", 12),
                        match(3L, 420, "puuid-1", 13)
                ));
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.SGP, false, 200)))
                .thenReturn(resultWithMatches(
                        match(1L, 420, "puuid-1", 11),
                        match(2L, 420, "puuid-1", 12),
                        match(3L, 420, "puuid-1", 13)
                ));

        MatchHistoryPageResponse fallbackResponse = sourceAwareService.getMatchHistoryPage(
                "puuid-1", 1, 0, "sgp", null, null, false, null);
        MatchHistoryPageResponse cappedResponse = sourceAwareService.getMatchHistoryPage(
                "puuid-1", 1, 500, "sgp", null, null, false, null);

        assertThat(fallbackResponse.getPageSize()).isEqualTo(20);
        assertThat(cappedResponse.getPageSize()).isEqualTo(200);
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.SGP, false, 21));
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.SGP, false, 200));
    }

    @Test
    void getMatchHistoryPage_sourceSgpSupportsPageTwentyWithinLimit() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        List<MatchHistory> matches = new java.util.ArrayList<>();
        for (long gameId = 1; gameId <= 201; gameId++) {
            matches.add(match(gameId, 420, "puuid-1", 11));
        }
        when(sgpMatchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.SGP, false)))
                .thenReturn(MatchHistoryFetchResult.builder()
                        .matches(matches)
                        .build());

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                20,
                10,
                "sgp",
                null,
                null,
                false,
                null
        );

        assertThat(response.getMatches()).extracting(MatchHistory::getGameId)
                .containsExactly(191L, 192L, 193L, 194L, 195L, 196L, 197L, 198L, 199L, 200L);
        assertThat(response.getPage()).isEqualTo(20);
        assertThat(response.getPageSize()).isEqualTo(10);
        assertThat(response.isHasNext()).isFalse();

        MatchHistoryPageResponse overflowResponse = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                21,
                10,
                "sgp",
                null,
                null,
                false,
                null
        );

        assertThat(overflowResponse.getMatches()).isEmpty();
        assertThat(overflowResponse.isHasNext()).isFalse();
        verify(sgpMatchHistoryProvider).fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.SGP, false));
    }

    @Test
    void getMatchHistoryPage_filtersByQueueAndChampion() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        when(sgpMatchHistoryProvider.supports(pageOptions(MatchHistorySource.AUTO, false, 11))).thenReturn(false);
        when(matchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.AUTO, false, 11)))
                .thenReturn(resultWithMatches(
                        match(1L, 420, "puuid-1", 11),
                        match(2L, 440, "puuid-1", 22),
                        match(3L, 420, "puuid-1", 22)
                ));

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                10,
                "auto",
                420,
                22,
                false,
                null
        );

        assertThat(response.getMatches()).extracting(MatchHistory::getGameId).containsExactly(3L);
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.getSource()).isEqualTo("lcu");
    }

    @Test
    void getMatchHistoryPage_reusesRecordStatusForPrivateRecords() {
        MatchHistoryService sourceAwareService = sourceAwareService();
        Rank rankWithGames = rankWithGames();
        when(sgpMatchHistoryProvider.supports(pageOptions(MatchHistorySource.AUTO, false, 11))).thenReturn(false);
        when(matchHistoryProvider.fetchMatchHistory("puuid-1", pageOptions(MatchHistorySource.AUTO, false, 11)))
                .thenReturn(MatchHistoryFetchResult.builder()
                        .matches(List.of())
                        .rawEmpty(true)
                        .build());

        MatchHistoryPageResponse response = sourceAwareService.getMatchHistoryPage(
                "puuid-1",
                1,
                10,
                "auto",
                null,
                null,
                false,
                rankWithGames
        );

        assertThat(response.getMatches()).isEmpty();
        assertThat(response.getRecordStatus()).isEqualTo(RecordStatus.PRIVATE);
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

    private MatchHistoryFetchResult resultWithMatch(long gameId) {
        MatchHistory match = new MatchHistory();
        match.setGameId(gameId);
        return MatchHistoryFetchResult.builder()
                .matches(List.of(match))
                .rawEmpty(false)
                .build();
    }

    private MatchHistoryFetchResult resultWithMatches(MatchHistory... matches) {
        return MatchHistoryFetchResult.builder()
                .matches(List.of(matches))
                .rawEmpty(matches.length == 0)
                .build();
    }

    private io.rankpeek.model.GameDetail renderableGameDetail(long gameId) {
        io.rankpeek.model.GameDetail detail = new io.rankpeek.model.GameDetail();
        detail.setGameId(gameId);

        io.rankpeek.model.GameDetail.GameParticipant participant = new io.rankpeek.model.GameDetail.GameParticipant();
        participant.setParticipantId(1);
        participant.setTeamId(100);
        participant.setChampionId(11);
        io.rankpeek.model.GameDetail.Stats stats = new io.rankpeek.model.GameDetail.Stats();
        stats.setWin(true);
        stats.setKills(8);
        stats.setDeaths(2);
        stats.setAssists(10);
        participant.setStats(stats);
        detail.setParticipants(List.of(participant));

        io.rankpeek.model.GameDetail.ParticipantIdentity identity = new io.rankpeek.model.GameDetail.ParticipantIdentity();
        identity.setParticipantId(1);
        io.rankpeek.model.GameDetail.Player player = new io.rankpeek.model.GameDetail.Player();
        player.setPuuid("puuid-1");
        identity.setPlayer(player);
        detail.setParticipantIdentities(List.of(identity));
        return detail;
    }

    private io.rankpeek.model.GameDetail hollowGameDetail(long gameId) {
        io.rankpeek.model.GameDetail detail = new io.rankpeek.model.GameDetail();
        detail.setGameId(gameId);

        io.rankpeek.model.GameDetail.GameParticipant participant = new io.rankpeek.model.GameDetail.GameParticipant();
        participant.setParticipantId(1);
        participant.setStats(new io.rankpeek.model.GameDetail.Stats());
        detail.setParticipants(List.of(participant));

        io.rankpeek.model.GameDetail.ParticipantIdentity identity = new io.rankpeek.model.GameDetail.ParticipantIdentity();
        identity.setParticipantId(1);
        detail.setParticipantIdentities(List.of(identity));
        return detail;
    }

    private MatchHistory match(long gameId, int queueId, String puuid, int championId) {
        MatchHistory match = new MatchHistory();
        match.setGameId(gameId);
        match.setQueueId(queueId);
        List<MatchHistory.Participant> participants = new java.util.ArrayList<>();
        List<MatchHistory.ParticipantIdentity> identities = new java.util.ArrayList<>();
        for (int participantId = 1; participantId <= 10; participantId++) {
            MatchHistory.Participant participant = new MatchHistory.Participant();
            participant.setParticipantId(participantId);
            participant.setChampionId(participantId == 1 ? championId : 100 + participantId);
            MatchHistory.Stats stats = new MatchHistory.Stats();
            stats.setWin(participantId <= 5);
            stats.setKills(participantId);
            stats.setDeaths(2);
            stats.setAssists(3);
            participant.setStats(stats);
            participants.add(participant);

            MatchHistory.ParticipantIdentity identity = new MatchHistory.ParticipantIdentity();
            identity.setParticipantId(participantId);
            MatchHistory.Player player = new MatchHistory.Player();
            player.setPuuid(participantId == 1 ? puuid : "other-puuid-" + participantId);
            identity.setPlayer(player);
            identities.add(identity);
        }
        match.setParticipants(participants);
        match.setParticipantIdentities(identities);
        return match;
    }

    private MatchHistory nonRenderableCurrentOnlyMatch(long gameId, int queueId) {
        MatchHistory match = new MatchHistory();
        match.setGameId(gameId);
        match.setQueueId(queueId);

        MatchHistory.Participant participant = new MatchHistory.Participant();
        participant.setParticipantId(1);
        participant.setChampionId(11);
        MatchHistory.Stats stats = new MatchHistory.Stats();
        stats.setWin(true);
        stats.setKills(5);
        stats.setDeaths(1);
        stats.setAssists(7);
        participant.setStats(stats);
        match.setParticipants(List.of(participant));

        return match;
    }

    private MatchHistory renderableCurrentOnlyMatch(long gameId, int queueId, String puuid, int championId) {
        MatchHistory match = new MatchHistory();
        match.setGameId(gameId);
        match.setQueueId(queueId);

        MatchHistory.Participant participant = new MatchHistory.Participant();
        participant.setParticipantId(1);
        participant.setChampionId(championId);
        MatchHistory.Stats stats = new MatchHistory.Stats();
        stats.setWin(true);
        stats.setKills(5);
        stats.setDeaths(1);
        stats.setAssists(7);
        participant.setStats(stats);
        match.setParticipants(List.of(participant));

        MatchHistory.ParticipantIdentity identity = new MatchHistory.ParticipantIdentity();
        identity.setParticipantId(1);
        MatchHistory.Player player = new MatchHistory.Player();
        player.setPuuid(puuid);
        identity.setPlayer(player);
        match.setParticipantIdentities(List.of(identity));

        return match;
    }

    private Rank rankWithGames() {
        Rank rank = new Rank();
        Rank.QueueMap queueMap = new Rank.QueueMap();
        Rank.QueueInfo solo = new Rank.QueueInfo();
        solo.setWins(6);
        solo.setLosses(4);
        queueMap.setRankedSolo5x5(solo);
        rank.setQueueMap(queueMap);
        return rank;
    }

    private MatchHistoryService sourceAwareService() {
        when(matchHistoryProvider.source()).thenReturn(MatchHistorySource.LCU);
        when(sgpMatchHistoryProvider.source()).thenReturn(MatchHistorySource.SGP);
        MatchHistoryService service = new MatchHistoryService(List.of(matchHistoryProvider, sgpMatchHistoryProvider));
        service.init();
        return service;
    }

    private MatchHistoryService sourceAwareServiceWithCacheRepository() {
        when(matchHistoryProvider.source()).thenReturn(MatchHistorySource.LCU);
        when(sgpMatchHistoryProvider.source()).thenReturn(MatchHistorySource.SGP);
        MatchHistoryService service = new MatchHistoryService(
                List.of(matchHistoryProvider, sgpMatchHistoryProvider),
                cacheRepository
        );
        service.init();
        return service;
    }

    private MatchHistoryQueryOptions options(MatchHistorySource source, boolean forceRefresh) {
        return new MatchHistoryQueryOptions(
                0,
                99,
                null,
                null,
                50,
                forceRefresh,
                source,
                null,
                null
        );
    }

    private MatchHistoryQueryOptions pageOptions(MatchHistorySource source, boolean forceRefresh) {
        return pageOptions(source, forceRefresh, 200);
    }

    private MatchHistoryQueryOptions pageOptions(MatchHistorySource source, boolean forceRefresh, int limit) {
        return new MatchHistoryQueryOptions(
                0,
                limit - 1,
                null,
                null,
                limit,
                forceRefresh,
                source,
                null,
                null
        );
    }

    private MatchHistoryQueryOptions limitOptions(MatchHistorySource source, int limit) {
        return new MatchHistoryQueryOptions(
                0,
                limit - 1,
                null,
                null,
                limit,
                false,
                source,
                null,
                null
        );
    }
}
