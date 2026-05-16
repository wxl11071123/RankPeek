package io.rankpeek.server.cnmeta.sync;

import io.rankpeek.server.common.ApiException;
import io.rankpeek.server.common.ApiResponse;
import io.rankpeek.server.cnmeta.CnMetaRoles;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/cn-meta/sync")
public class CnMetaSyncController {

    private static final Set<String> ALLOWED_ROLES = Set.of("TOP", "JUNGLE", "MID", "ADC", "SUPPORT");

    private final CnMetaSyncProperties properties;
    private final CnMetaSyncService syncService;

    public CnMetaSyncController(
            CnMetaSyncProperties properties,
            CnMetaSyncService syncService
    ) {
        this.properties = properties;
        this.syncService = syncService;
    }

    @PostMapping("/mock-once")
    public ApiResponse<CnMetaSyncResult> mockOnce(
            @RequestParam String patchKey,
            @RequestParam(defaultValue = "420") Integer queueId,
            @RequestParam String tierScope,
            @RequestParam String role
    ) {
        SyncRequest request = validateSyncRequest(patchKey, queueId, tierScope, role);
        return ApiResponse.success(syncService.syncOnceWithSource(
                "mock",
                request.patchKey(),
                request.queueId(),
                request.tierScope(),
                request.role()
        ));
    }

    @PostMapping("/real-once")
    public ApiResponse<CnMetaSyncResult> realOnce(
            @RequestParam String patchKey,
            @RequestParam(defaultValue = "420") Integer queueId,
            @RequestParam String tierScope,
            @RequestParam String role
    ) {
        if (!properties.realSourceEnabled()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "CN_META_REAL_SOURCE_DISABLED",
                    "Real 101 CN meta source is disabled"
            );
        }
        SyncRequest request = validateSyncRequest(patchKey, queueId, tierScope, role);
        return ApiResponse.success(syncService.syncOnceWithSource(
                "real",
                request.patchKey(),
                request.queueId(),
                request.tierScope(),
                CnMetaRoles.ALL
        ));
    }

    @PostMapping("/configured-matrix")
    public ApiResponse<List<CnMetaSyncResult>> configuredMatrix(@RequestParam String patchKey) {
        String normalizedPatchKey = validatePatchKey(patchKey);
        if (!properties.enabled() && !properties.allowManual()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "CN_META_SYNC_DISABLED",
                    "CN meta configured matrix sync is disabled"
            );
        }
        if (!"mock".equalsIgnoreCase(properties.source())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CN_META_REAL_SOURCE_DISABLED",
                    "Configured matrix sync only allows the mock source in this foundation"
            );
        }
        return ApiResponse.success(syncService.syncConfiguredMatrix(normalizedPatchKey));
    }

    @GetMapping("/jobs")
    public ApiResponse<List<CnMetaSyncJob>> jobs(@RequestParam(defaultValue = "20") Integer limit) {
        return ApiResponse.success(syncService.findRecentJobs(limit));
    }

    private SyncRequest validateSyncRequest(String patchKey, Integer queueId, String tierScope, String role) {
        String normalizedPatchKey = validatePatchKey(patchKey);
        if (queueId == null || queueId <= 0) {
            throw new IllegalArgumentException("queueId must be greater than 0");
        }
        String normalizedTier = normalizeUpper(tierScope, "tierScope");
        if (!properties.tiers().contains(normalizedTier)) {
            throw new IllegalArgumentException("tierScope is not allowed");
        }
        String normalizedRole = normalizeUpper(role, "role");
        if (!ALLOWED_ROLES.contains(normalizedRole)) {
            throw new IllegalArgumentException("role is not allowed");
        }
        return new SyncRequest(normalizedPatchKey, queueId, normalizedTier, normalizedRole);
    }

    private static String validatePatchKey(String patchKey) {
        String normalized = normalize(patchKey, "patchKey");
        if (normalized.length() > 32) {
            throw new IllegalArgumentException("patchKey must be 32 characters or fewer");
        }
        return normalized;
    }

    private static String normalizeUpper(String value, String field) {
        return normalize(value, field).toUpperCase(Locale.ROOT);
    }

    private static String normalize(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private record SyncRequest(String patchKey, Integer queueId, String tierScope, String role) {
    }
}
