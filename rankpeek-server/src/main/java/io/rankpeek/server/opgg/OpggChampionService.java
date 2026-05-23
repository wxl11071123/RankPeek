package io.rankpeek.server.opgg;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OpggChampionService implements OpggChampionDetailProvider {
    private final OpggSourceClient sourceClient;
    private final Duration cacheTtl;
    private final Clock clock;
    private final OpggCacheProperties cacheProperties;
    private final OpggChampionCacheRepository cacheRepository;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public OpggChampionService(
            OpggSourceClient sourceClient,
            OpggSourceProperties properties,
            OpggCacheProperties cacheProperties,
            OpggChampionCacheRepository cacheRepository
    ) {
        this(
                sourceClient,
                Duration.ofSeconds(properties.cacheTtlSeconds()),
                Clock.systemUTC(),
                cacheProperties,
                cacheRepository
        );
    }

    OpggChampionService(OpggSourceClient sourceClient, Duration cacheTtl, Clock clock) {
        this(
                sourceClient,
                cacheTtl,
                clock,
                new OpggCacheProperties(false, "Asia/Shanghai", false, "0 20 4 * * *"),
                null
        );
    }

    OpggChampionService(
            OpggSourceClient sourceClient,
            Duration cacheTtl,
            Clock clock,
            OpggCacheProperties cacheProperties,
            OpggChampionCacheRepository cacheRepository
    ) {
        this.sourceClient = sourceClient;
        this.cacheTtl = cacheTtl;
        this.clock = clock;
        this.cacheProperties = cacheProperties;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public OpggChampionDetail getChampionDetail(OpggChampionDetailQuery query) {
        String cacheKey = query.cacheKey();
        Instant now = clock.instant();
        LocalDate cacheDate = cacheDate(now);
        if (databaseCacheEnabled()) {
            Optional<OpggChampionDetail> stored = cacheRepository.findToday(query, cacheDate);
            if (stored.isPresent()) {
                OpggChampionDetail detail = stored.get();
                cache.put(cacheKey, new CacheEntry(detail, now.plus(cacheTtl), cacheDate));
                return detail;
            }
        }

        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && now.isBefore(cached.expiresAt()) && cacheDate.equals(cached.cacheDate())) {
            return cached.detail();
        }

        OpggChampionDetail detail = sourceClient.fetchChampionDetail(query);
        cache.put(cacheKey, new CacheEntry(detail, now.plus(cacheTtl), cacheDate));
        if (databaseCacheEnabled()) {
            cacheRepository.upsertToday(query, cacheDate, detail, now);
        }
        return detail;
    }

    private boolean databaseCacheEnabled() {
        return cacheRepository != null
                && cacheProperties != null
                && Boolean.TRUE.equals(cacheProperties.cacheEnabled());
    }

    private LocalDate cacheDate(Instant now) {
        String zone = cacheProperties == null ? "Asia/Shanghai" : cacheProperties.cacheZone();
        return LocalDate.ofInstant(now, ZoneId.of(zone));
    }

    private record CacheEntry(OpggChampionDetail detail, Instant expiresAt, LocalDate cacheDate) {
    }
}
