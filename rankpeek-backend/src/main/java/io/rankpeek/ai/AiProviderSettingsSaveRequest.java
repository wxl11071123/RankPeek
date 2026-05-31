package io.rankpeek.ai;

public record AiProviderSettingsSaveRequest(
        boolean enabled,
        String providerId,
        String baseUrl,
        String model,
        String apiKey,
        boolean saveApiKey,
        Double temperature,
        int maxTokens,
        AiProviderPricing pricing
) {
}
