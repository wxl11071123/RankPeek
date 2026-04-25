package io.rankpeek.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class MatchHistoryPrewarmService {

    private static final long PREWARM_DEDUPE_MILLIS = TimeUnit.MINUTES.toMillis(2);
    private static final int MAX_CONCURRENT_PREWARMS = 4;

    private final SummonerService summonerService;
    private final RankService rankService;
    private final MatchHistoryService matchHistoryService;
    private final Executor dataLoaderExecutor;
    private final Map<String, Long> prewarmSubmittedAt = new ConcurrentHashMap<>();
    private final Semaphore prewarmSemaphore = new Semaphore(MAX_CONCURRENT_PREWARMS);

    @Autowired
    MatchHistoryPrewarmService(SummonerService summonerService,
                               RankService rankService,
                               MatchHistoryService matchHistoryService,
                               @Qualifier("dataLoaderExecutor") Executor dataLoaderExecutor) {
        this.summonerService = summonerService;
        this.rankService = rankService;
        this.matchHistoryService = matchHistoryService;
        this.dataLoaderExecutor = dataLoaderExecutor;
    }

    public void prewarmPlayers(Collection<String> puuids, String reason) {
        Set<String> normalizedPuuids = normalizePuuids(puuids);
        if (normalizedPuuids.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (String puuid : normalizedPuuids) {
            if (!markSubmittedIfNeeded(puuid, now)) {
                log.debug("Skipping duplicate match-history prewarm: puuid={}, reason={}", puuidPrefix(puuid), reason);
                continue;
            }
            try {
                dataLoaderExecutor.execute(() -> prewarmPlayer(puuid, reason));
            } catch (Exception e) {
                log.debug("Failed to submit match-history prewarm task: puuid={}, reason={}, error={}",
                        puuidPrefix(puuid), reason, e.getMessage());
            }
        }
    }

    private void prewarmPlayer(String puuid, String reason) {
        if (!prewarmSemaphore.tryAcquire()) {
            log.debug("Skipping match-history prewarm because concurrency limit was reached: puuid={}, reason={}",
                    puuidPrefix(puuid), reason);
            return;
        }

        try {
            summonerService.getSummonerByPuuid(puuid);
            rankService.getRankByPuuid(puuid);
            matchHistoryService.getMatchHistoryFetchResult(puuid, false);
            log.debug("Prewarmed visible session player caches: puuid={}, reason={}", puuidPrefix(puuid), reason);
        } catch (Exception e) {
            log.debug("Failed to prewarm visible session player caches: puuid={}, reason={}, error={}",
                    puuidPrefix(puuid), reason, e.getMessage());
        } finally {
            prewarmSemaphore.release();
        }
    }

    private boolean markSubmittedIfNeeded(String puuid, long now) {
        AtomicBoolean shouldSubmit = new AtomicBoolean(false);
        prewarmSubmittedAt.compute(puuid, (key, previous) -> {
            if (previous == null || now - previous >= PREWARM_DEDUPE_MILLIS) {
                shouldSubmit.set(true);
                return now;
            }
            return previous;
        });
        return shouldSubmit.get();
    }

    private Set<String> normalizePuuids(Collection<String> puuids) {
        if (puuids == null || puuids.isEmpty()) {
            return Set.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        puuids.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(puuid -> !puuid.isBlank())
                .forEach(normalized::add);
        return normalized;
    }

    private String puuidPrefix(String puuid) {
        if (puuid == null || puuid.isBlank()) {
            return "null";
        }
        return puuid.substring(0, Math.min(8, puuid.length()));
    }
}
