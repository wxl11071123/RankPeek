package io.rankpeek.server.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

@ConfigurationProperties(prefix = "rankpeek.ai")
public record DeepSeekAiProperties(
        boolean enabled,
        String provider,
        String baseUrl,
        String model,
        String apiKey,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxTokens,
        Double temperature
) {

    public DeepSeekAiProperties {
        provider = normalizeProvider(provider);
        baseUrl = normalizeBaseUrl(baseUrl);
        model = blankToDefault(model, "deepseek-v4-flash");
        apiKey = apiKey == null ? "" : apiKey.trim();
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 5_000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 30_000;
        }
        if (maxTokens <= 0) {
            maxTokens = 1600;
        }
        if (temperature == null || temperature < 0 || temperature > 2) {
            temperature = 0.4;
        }
    }

    public boolean deepSeekEnabled() {
        return enabled && "deepseek".equals(provider);
    }

    public String chatCompletionsUrl() {
        return baseUrl + "/chat/completions";
    }

    private static String normalizeProvider(String value) {
        return blankToDefault(value, "mock").toLowerCase(Locale.ROOT);
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = blankToDefault(value, "https://api.deepseek.com");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
