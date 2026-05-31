package io.rankpeek.ai;

public record AiProviderSettings(
        boolean enabled,
        String providerId,
        String baseUrl,
        String model,
        boolean apiKeySaved,
        String apiKeyMasked,
        Double temperature,
        int maxTokens,
        AiProviderPricing pricing
) {
}
