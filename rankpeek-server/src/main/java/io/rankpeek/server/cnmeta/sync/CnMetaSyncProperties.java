package io.rankpeek.server.cnmeta.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ConfigurationProperties(prefix = "rankpeek.cn-meta.sync")
public record CnMetaSyncProperties(
        boolean enabled,
        boolean allowManual,
        String source,
        String cron,
        String zone,
        long requestDelayMs,
        int maxRetries,
        List<Integer> stopOnHttpStatus,
        int defaultQueueId,
        List<String> tiers,
        List<String> roles,
        boolean realSourceEnabled,
        String realEndpointTemplate,
        int realTimeType,
        int realChampionId,
        int realDataDateOffsetDays,
        Map<String, String> realTierCodeMap,
        String realUserAgent,
        int realConnectTimeoutMs,
        int realReadTimeoutMs,
        int realMaxResponseBytes
) {

    public CnMetaSyncProperties {
        source = blankToDefault(source, "mock");
        cron = blankToDefault(cron, "0 30 4 * * *");
        zone = blankToDefault(zone, "Asia/Shanghai");
        if (requestDelayMs < 0) {
            requestDelayMs = 3000;
        }
        if (maxRetries < 0) {
            maxRetries = 2;
        }
        if (stopOnHttpStatus == null || stopOnHttpStatus.isEmpty()) {
            stopOnHttpStatus = List.of(401, 403, 429);
        }
        if (defaultQueueId <= 0) {
            defaultQueueId = 420;
        }
        if (tiers == null || tiers.isEmpty()) {
            tiers = List.of("IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND", "MASTER_PLUS");
        }
        if (roles == null || roles.isEmpty()) {
            roles = List.of("TOP", "JUNGLE", "MID", "ADC", "SUPPORT");
        }
        realEndpointTemplate = blankToDefault(realEndpointTemplate, "");
        if (realTimeType <= 0) {
            realTimeType = 1;
        }
        if (realChampionId <= 0) {
            realChampionId = 666;
        }
        if (realDataDateOffsetDays < 0) {
            realDataDateOffsetDays = 1;
        }
        realTierCodeMap = normalizeTierCodeMap(realTierCodeMap);
        realUserAgent = blankToDefault(realUserAgent, "RankPeek/dev-public-aggregate-client");
        if (realConnectTimeoutMs <= 0) {
            realConnectTimeoutMs = 5000;
        }
        if (realReadTimeoutMs <= 0) {
            realReadTimeoutMs = 10000;
        }
        if (realMaxResponseBytes <= 0) {
            realMaxResponseBytes = 1_000_000;
        }
    }

    private static String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static Map<String, String> normalizeTierCodeMap(Map<String, String> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : value.entrySet()) {
            String key = entry.getKey();
            String tierCode = entry.getValue();
            if (key == null || key.isBlank() || tierCode == null || tierCode.isBlank()) {
                continue;
            }
            normalized.put(key.trim().toUpperCase(Locale.ROOT), tierCode.trim());
        }
        return Map.copyOf(normalized);
    }
}
