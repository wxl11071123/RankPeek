package io.rankpeek.ai;

record StoredAiProviderSettings(
        boolean enabled,
        String providerId,
        String baseUrl,
        String model,
        String apiKeyEncrypted,
        String apiKeyMasked,
        Double temperature,
        int maxTokens,
        String pricingRawJson,
        long updatedAt
) {
}
