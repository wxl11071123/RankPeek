package io.rankpeek.service;

import io.rankpeek.model.CacheClearResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheMaintenanceService {

    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_MEMORY = "memory";
    private static final String SCOPE_LOCAL_DB = "localDb";

    private final JdbcTemplate jdbcTemplate;
    private final MatchHistoryService matchHistoryService;
    private final RankService rankService;
    private final SummonerService summonerService;

    public CacheClearResult clearCache(String scope, boolean confirm) {
        String normalizedScope = normalizeScope(scope);

        if (!confirm) {
            return result(false, normalizedScope, "confirm=true is required", 0);
        }

        if (!isSupportedScope(normalizedScope)) {
            return result(false, normalizedScope, "Unsupported cache clear scope: " + normalizedScope, 0);
        }

        try {
            return switch (normalizedScope) {
                case SCOPE_MEMORY -> {
                    clearMemoryCache();
                    yield result(true, normalizedScope, "memory cache cleared", 0);
                }
                case SCOPE_LOCAL_DB -> {
                    long deletedRows = clearLocalDatabaseCache();
                    yield result(true, normalizedScope, "local database cache cleared", deletedRows);
                }
                case SCOPE_ALL -> {
                    clearMemoryCache();
                    long deletedRows = clearLocalDatabaseCache();
                    yield result(true, normalizedScope, "memory and local database cache cleared", deletedRows);
                }
                default -> result(false, normalizedScope, "Unsupported cache clear scope: " + normalizedScope, 0);
            };
        } catch (Exception e) {
            log.warn("Failed to clear cache: scope={}, error={}", normalizedScope, e.getMessage());
            return result(false, normalizedScope, "Failed to clear cache: " + e.getMessage(), 0);
        }
    }

    private void clearMemoryCache() {
        matchHistoryService.refreshAllCache();
        rankService.refreshAllCache();
        summonerService.refreshAllCache();
    }

    private long clearLocalDatabaseCache() {
        long deletedRows = 0;
        deletedRows += deleteTable("player_fetch_state");
        deletedRows += deleteTable("player_match_index");
        deletedRows += deleteTable("match_participant_cache");
        deletedRows += deleteTable("game_detail_cache");
        deletedRows += deleteTable("match_cache");
        deletedRows += deleteTable("rank_cache");
        deletedRows += deleteTable("summoner_cache");
        return deletedRows;
    }

    private long deleteTable(String tableName) {
        try {
            return jdbcTemplate.update("DELETE FROM " + tableName);
        } catch (Exception e) {
            log.warn("Failed to clear local cache table: table={}, error={}", tableName, e.getMessage());
            throw new IllegalStateException("failed to clear local database table " + tableName, e);
        }
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return SCOPE_ALL;
        }

        String trimmed = scope.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case SCOPE_ALL -> SCOPE_ALL;
            case SCOPE_MEMORY -> SCOPE_MEMORY;
            case "localdb" -> SCOPE_LOCAL_DB;
            default -> trimmed;
        };
    }

    private boolean isSupportedScope(String scope) {
        return SCOPE_ALL.equals(scope) || SCOPE_MEMORY.equals(scope) || SCOPE_LOCAL_DB.equals(scope);
    }

    private CacheClearResult result(boolean cleared, String scope, String message, long deletedRows) {
        return CacheClearResult.builder()
                .cleared(cleared)
                .scope(scope)
                .message(message)
                .deletedRows(deletedRows)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
