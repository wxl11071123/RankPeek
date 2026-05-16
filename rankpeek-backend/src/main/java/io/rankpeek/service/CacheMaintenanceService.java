package io.rankpeek.service;

import io.rankpeek.model.CacheClearResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheMaintenanceService {

    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_MEMORY = "memory";
    private static final String SCOPE_LOCAL_DB = "localDb";
    private static final List<String> LOCAL_CACHE_TABLES = List.of(
            "player_fetch_state",
            "player_match_index",
            "match_participant_cache",
            "game_detail_cache",
            "match_data_scope_cache",
            "match_cache",
            "rank_cache",
            "summoner_cache"
    );

    private final JdbcTemplate jdbcTemplate;
    private final MatchHistoryService matchHistoryService;
    private final RankService rankService;
    private final SummonerService summonerService;

    public CacheClearResult clearCache(String scope, boolean confirm) {
        String normalizedScope = normalizeScope(scope);

        if (!confirm) {
            return result(false, normalizedScope, "confirm=true is required", List.of(), List.of(
                    new CacheClearResult.Failure("confirmation", "confirm=true is required")
            ), 0);
        }

        if (!isSupportedScope(normalizedScope)) {
            String message = "Unsupported cache clear scope: " + normalizedScope;
            return result(false, normalizedScope, message, List.of(), List.of(
                    new CacheClearResult.Failure("scope", message)
            ), 0);
        }

        List<String> cleared = new ArrayList<>();
        List<CacheClearResult.Failure> failed = new ArrayList<>();
        long deletedRows = 0;

        if (SCOPE_MEMORY.equals(normalizedScope) || SCOPE_ALL.equals(normalizedScope)) {
            clearMemoryCache(cleared, failed);
        }

        if (SCOPE_LOCAL_DB.equals(normalizedScope) || SCOPE_ALL.equals(normalizedScope)) {
            deletedRows = clearLocalDatabaseCache(cleared, failed);
        }

        boolean success = failed.isEmpty();
        return result(
                success,
                normalizedScope,
                message(success, cleared, failed),
                List.copyOf(cleared),
                List.copyOf(failed),
                deletedRows
        );
    }

    private void clearMemoryCache(List<String> cleared, List<CacheClearResult.Failure> failed) {
        clearItem("memory.matchHistory", () -> matchHistoryService.refreshAllCache(), cleared, failed);
        clearItem("memory.rank", () -> rankService.refreshAllCache(), cleared, failed);
        clearItem("memory.summoner", () -> summonerService.refreshAllCache(), cleared, failed);
    }

    private long clearLocalDatabaseCache(List<String> cleared, List<CacheClearResult.Failure> failed) {
        long deletedRows = 0;
        for (String tableName : LOCAL_CACHE_TABLES) {
            String itemName = "localDb." + tableName;
            try {
                deletedRows += deleteTable(tableName);
                cleared.add(itemName);
            } catch (Exception e) {
                String message = rootMessage(e);
                log.warn("Failed to clear cache item: name={}, error={}", itemName, message);
                failed.add(new CacheClearResult.Failure(itemName, message));
            }
        }
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

    private void clearItem(
            String name,
            Runnable operation,
            List<String> cleared,
            List<CacheClearResult.Failure> failed) {
        try {
            operation.run();
            cleared.add(name);
        } catch (Exception e) {
            String message = rootMessage(e);
            log.warn("Failed to clear cache item: name={}, error={}", name, message);
            failed.add(new CacheClearResult.Failure(name, message));
        }
    }

    private String rootMessage(Exception e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private String message(
            boolean success,
            List<String> cleared,
            List<CacheClearResult.Failure> failed) {
        if (success) {
            return "cache cleared";
        }
        if (cleared.isEmpty()) {
            return "cache clear failed: " + failedNames(failed);
        }
        return "cache clear completed with failures: " + failedNames(failed);
    }

    private String failedNames(List<CacheClearResult.Failure> failed) {
        return failed.stream()
                .map(CacheClearResult.Failure::getName)
                .reduce((left, right) -> left + ", " + right)
                .orElse("unknown");
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

    private CacheClearResult result(
            boolean success,
            String scope,
            String message,
            List<String> cleared,
            List<CacheClearResult.Failure> failed,
            long deletedRows) {
        return CacheClearResult.builder()
                .success(success)
                .scope(scope)
                .message(message)
                .cleared(cleared)
                .failed(failed)
                .deletedRows(deletedRows)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
