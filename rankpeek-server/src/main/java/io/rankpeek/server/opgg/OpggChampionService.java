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
public class OpggChampionService implements OpggChampionDetailProvider, OpggChampionListProvider {
    private final OpggSourceClient sourceClient;
    private final Duration cacheTtl;
    private final Clock clock;
    private final OpggCacheProperties cacheProperties;
    private final OpggChampionCacheRepository cacheRepository;
    private final Map<String, DetailCacheEntry> detailCache = new ConcurrentHashMap<>();
    private final Map<String, ListCacheEntry> listCache = new ConcurrentHashMap<>();

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
                if (isUsableCachedDetail(query, detail)) {
                    detailCache.put(cacheKey, new DetailCacheEntry(detail, now.plus(cacheTtl), cacheDate));
                    return detail;
                }
            }
        }

        DetailCacheEntry cached = detailCache.get(cacheKey);
        if (cached != null
                && now.isBefore(cached.expiresAt())
                && cacheDate.equals(cached.cacheDate())
                && isUsableCachedDetail(query, cached.detail())) {
            return cached.detail();
        }

        OpggChampionDetail detail = sourceClient.fetchChampionDetail(query);
        detailCache.put(cacheKey, new DetailCacheEntry(detail, now.plus(cacheTtl), cacheDate));
        if (databaseCacheEnabled()) {
            cacheRepository.upsertToday(query, cacheDate, detail, now);
        }
        return detail;
    }

    @Override
    public OpggChampionList getChampionList(OpggChampionListQuery query) {
        String cacheKey = query.cacheKey();
        Instant now = clock.instant();
        LocalDate cacheDate = cacheDate(now);
        if (databaseCacheEnabled()) {
            Optional<OpggChampionList> stored = cacheRepository.findToday(query, cacheDate);
            if (stored.isPresent()) {
                OpggChampionList list = stored.get();
                listCache.put(cacheKey, new ListCacheEntry(list, now.plus(cacheTtl), cacheDate));
                return list;
            }
        }

        ListCacheEntry cached = listCache.get(cacheKey);
        if (cached != null && now.isBefore(cached.expiresAt()) && cacheDate.equals(cached.cacheDate())) {
            return cached.list();
        }

        OpggChampionList list = sourceClient.fetchChampionList(query);
        listCache.put(cacheKey, new ListCacheEntry(list, now.plus(cacheTtl), cacheDate));
        if (databaseCacheEnabled()) {
            cacheRepository.upsertToday(query, cacheDate, list, now);
        }
        return list;
    }

    private boolean databaseCacheEnabled() {
        return cacheRepository != null
                && cacheProperties != null
                && Boolean.TRUE.equals(cacheProperties.cacheEnabled());
    }

    private boolean isUsableCachedDetail(OpggChampionDetailQuery query, OpggChampionDetail detail) {
        if (!"ranked".equals(query.mode())) {
            return true;
        }
        return detail.skillOrders() != null && !detail.skillOrders().isEmpty();
    }

    private LocalDate cacheDate(Instant now) {
        String zone = cacheProperties == null ? "Asia/Shanghai" : cacheProperties.cacheZone();
        return LocalDate.ofInstant(now, ZoneId.of(zone));
    }

    private record DetailCacheEntry(OpggChampionDetail detail, Instant expiresAt, LocalDate cacheDate) {
    }

    private record ListCacheEntry(OpggChampionList list, Instant expiresAt, LocalDate cacheDate) {
    }
}
