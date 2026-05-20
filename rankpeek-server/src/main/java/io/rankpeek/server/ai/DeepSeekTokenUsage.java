package io.rankpeek.server.ai;

public record DeepSeekTokenUsage(
        String provider,
        String model,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long promptCacheHitTokens,
        long promptCacheMissTokens
) {
    public DeepSeekTokenUsage {
        provider = blankToDefault(provider, "deepseek");
        model = blankToDefault(model, "unknown");
        promptTokens = nonNegative(promptTokens);
        completionTokens = nonNegative(completionTokens);
        totalTokens = nonNegative(totalTokens);
        promptCacheHitTokens = nonNegative(promptCacheHitTokens);
        promptCacheMissTokens = nonNegative(promptCacheMissTokens);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static long nonNegative(long value) {
        return Math.max(0, value);
    }
}
