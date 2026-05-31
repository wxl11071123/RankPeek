package io.rankpeek.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Service
public class AiProviderSettingsService {

    private static final String DEFAULT_PROVIDER_ID = "deepseek";
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final double DEFAULT_TEMPERATURE = 0.4d;
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final String CONFIGURATION_REQUIRED_MESSAGE = "Please configure AI provider and API key first.";

    private final AiProviderSettingsRepository repository;
    private final ObjectMapper objectMapper;
    private final OpenAiCompatibleChatClient chatClient;

    @Autowired
    public AiProviderSettingsService(
            AiProviderSettingsRepository repository,
            OpenAiCompatibleChatClient chatClient
    ) {
        this(repository, new ObjectMapper(), chatClient);
    }

    AiProviderSettingsService(AiProviderSettingsRepository repository) {
        this(repository, new ObjectMapper(), null);
    }

    AiProviderSettingsService(AiProviderSettingsRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, null);
    }

    AiProviderSettingsService(
            AiProviderSettingsRepository repository,
            ObjectMapper objectMapper,
            OpenAiCompatibleChatClient chatClient
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.chatClient = chatClient;
    }

    public static List<AiProviderProfile> defaultProviderProfiles() {
        return List.of(
                new AiProviderProfile(
                        "deepseek",
                        "DeepSeek",
                        "openai-compatible",
                        DEFAULT_BASE_URL,
                        List.of("deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner"),
                        true
                ),
                new AiProviderProfile(
                        "custom-openai-compatible",
                        "Custom OpenAI-compatible",
                        "openai-compatible",
                        "",
                        List.of(),
                        false
                )
        );
    }

    public List<AiProviderProfile> listProviderProfiles() {
        return defaultProviderProfiles();
    }

    public AiProviderSettings getSettings() {
        return repository.findDefault()
                .map(this::toPublicSettings)
                .orElseGet(this::defaultSettings);
    }

    StoredAiProviderSettings requireRunnableSettings() {
        StoredAiProviderSettings settings = repository.findDefault()
                .orElseThrow(() -> new LocalAiConfigurationException("Please configure AI provider and API key first."));
        if (!settings.enabled()
                || isBlank(settings.providerId())
                || isBlank(settings.baseUrl())
                || isBlank(settings.model())
                || isBlank(settings.apiKeyEncrypted())) {
            throw new LocalAiConfigurationException("Please configure AI provider and API key first.");
        }
        return settings;
    }

    public AiProviderSettings saveSettings(AiProviderSettingsSaveRequest request) {
        StoredAiProviderSettings existing = repository.findDefault().orElse(null);
        String providerId = normalizeProviderId(request.providerId());
        String baseUrl = normalizeBaseUrl(request.baseUrl(), providerId, request.enabled());
        String model = normalizeModel(request.model(), request.enabled());
        Double temperature = normalizeTemperature(request.temperature());
        int maxTokens = request.maxTokens() > 0 ? request.maxTokens() : DEFAULT_MAX_TOKENS;
        ApiKeyStorage apiKeyStorage = resolveApiKeyStorage(request, existing);
        AiProviderPricing pricing = normalizePricing(request.pricing(), model);
        StoredAiProviderSettings stored = new StoredAiProviderSettings(
                request.enabled(),
                providerId,
                baseUrl,
                model,
                apiKeyStorage.rawKey(),
                apiKeyStorage.maskedKey(),
                temperature,
                maxTokens,
                writePricing(pricing),
                System.currentTimeMillis()
        );
        repository.saveDefault(stored);
        return toPublicSettings(stored);
    }

    public AiProviderTestResponse testProvider(AiProviderTestRequest request) {
        AiProviderTestConfiguration configuration = resolveTestConfiguration(request);
        if (!configuration.configured()) {
            return new AiProviderTestResponse(
                    false,
                    configuration.providerId(),
                    configuration.model(),
                    CONFIGURATION_REQUIRED_MESSAGE
            );
        }

        try {
            effectiveChatClient().streamChat(
                    new OpenAiCompatibleChatClient.ChatOptions(
                            configuration.providerId(),
                            configuration.baseUrl(),
                            configuration.model(),
                            configuration.apiKey(),
                            Duration.ofSeconds(5),
                            Duration.ofSeconds(15),
                            32,
                            0d,
                            false
                    ),
                    List.of(
                            new OpenAiChatMessage(
                                    "system",
                                    "You are RankPeek's AI provider connection tester. Reply with ok."
                            ),
                            new OpenAiChatMessage("user", "Reply with ok.")
                    ),
                    ignored -> {
                    },
                    ignored -> {
                    }
            );
            return new AiProviderTestResponse(
                    true,
                    configuration.providerId(),
                    configuration.model(),
                    "AI provider connection succeeded."
            );
        } catch (AiProviderException | IllegalArgumentException exception) {
            return new AiProviderTestResponse(
                    false,
                    configuration.providerId(),
                    configuration.model(),
                    exception.getMessage()
            );
        }
    }

    private AiProviderTestConfiguration resolveTestConfiguration(AiProviderTestRequest request) {
        StoredAiProviderSettings existing = repository.findDefault().orElse(null);
        String providerId = normalizeProviderId(firstNonBlank(
                request == null ? null : request.providerId(),
                existing == null ? null : existing.providerId(),
                DEFAULT_PROVIDER_ID
        ));
        String baseUrl = normalizeBaseUrlForTest(firstNonBlank(
                request == null ? null : request.baseUrl(),
                existing == null ? null : existing.baseUrl(),
                "deepseek".equals(providerId) ? DEFAULT_BASE_URL : ""
        ));
        String model = blankToDefault(firstNonBlank(
                request == null ? null : request.model(),
                existing == null ? null : existing.model(),
                DEFAULT_MODEL
        ), DEFAULT_MODEL);
        String apiKey = trimToEmpty(firstNonBlank(
                request == null ? null : request.apiKey(),
                existing == null ? null : existing.apiKeyEncrypted()
        ));
        return new AiProviderTestConfiguration(
                providerId,
                baseUrl,
                model,
                apiKey,
                !baseUrl.isBlank() && !model.isBlank() && !apiKey.isBlank()
        );
    }

    private OpenAiCompatibleChatClient effectiveChatClient() {
        return chatClient == null ? new OpenAiCompatibleChatClient(objectMapper) : chatClient;
    }

    private AiProviderSettings defaultSettings() {
        return new AiProviderSettings(
                false,
                DEFAULT_PROVIDER_ID,
                DEFAULT_BASE_URL,
                DEFAULT_MODEL,
                false,
                null,
                DEFAULT_TEMPERATURE,
                DEFAULT_MAX_TOKENS,
                defaultPricing(DEFAULT_MODEL)
        );
    }

    private AiProviderSettings toPublicSettings(StoredAiProviderSettings settings) {
        return new AiProviderSettings(
                settings.enabled(),
                blankToDefault(settings.providerId(), DEFAULT_PROVIDER_ID),
                blankToDefault(settings.baseUrl(), DEFAULT_BASE_URL),
                blankToDefault(settings.model(), DEFAULT_MODEL),
                !isBlank(settings.apiKeyEncrypted()),
                isBlank(settings.apiKeyEncrypted()) ? null : settings.apiKeyMasked(),
                settings.temperature() == null ? DEFAULT_TEMPERATURE : settings.temperature(),
                settings.maxTokens() > 0 ? settings.maxTokens() : DEFAULT_MAX_TOKENS,
                readPricing(settings.pricingRawJson(), settings.model())
        );
    }

    private String normalizeProviderId(String providerId) {
        return blankToDefault(providerId, DEFAULT_PROVIDER_ID).toLowerCase(Locale.ROOT);
    }

    private String normalizeBaseUrl(String baseUrl, String providerId, boolean enabled) {
        String normalized = blankToDefault(baseUrl, "deepseek".equals(providerId) ? DEFAULT_BASE_URL : "");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (enabled && normalized.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required when AI is enabled");
        }
        return normalized;
    }

    private String normalizeBaseUrlForTest(String baseUrl) {
        String normalized = trimToEmpty(baseUrl);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeModel(String model, boolean enabled) {
        String normalized = enabled ? trimToEmpty(model) : blankToDefault(model, DEFAULT_MODEL);
        if (enabled && normalized.isBlank()) {
            throw new IllegalArgumentException("model is required when AI is enabled");
        }
        return blankToDefault(normalized, DEFAULT_MODEL);
    }

    private Double normalizeTemperature(Double temperature) {
        if (temperature == null || temperature < 0 || temperature > 2) {
            return DEFAULT_TEMPERATURE;
        }
        return temperature;
    }

    private ApiKeyStorage resolveApiKeyStorage(
            AiProviderSettingsSaveRequest request,
            StoredAiProviderSettings existing) {
        String requestKey = trimToEmpty(request.apiKey());
        if (!requestKey.isBlank()) {
            return new ApiKeyStorage(requestKey, maskApiKey(requestKey));
        }
        if (request.saveApiKey() && existing != null && !isBlank(existing.apiKeyEncrypted())) {
            return new ApiKeyStorage(existing.apiKeyEncrypted(), existing.apiKeyMasked());
        }
        return new ApiKeyStorage(null, null);
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 3) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private AiProviderPricing normalizePricing(AiProviderPricing pricing, String model) {
        if (pricing == null) {
            return defaultPricing(model);
        }
        return new AiProviderPricing(
                blankToDefault(pricing.currency(), "CNY"),
                positiveOrZero(pricing.inputCacheHitCnyPerMillionTokens()),
                positiveOrZero(pricing.inputCacheMissCnyPerMillionTokens()),
                positiveOrZero(pricing.outputCnyPerMillionTokens())
        );
    }

    private BigDecimal positiveOrZero(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private AiProviderPricing defaultPricing(String model) {
        String normalizedModel = blankToDefault(model, DEFAULT_MODEL).toLowerCase(Locale.ROOT);
        return switch (normalizedModel) {
            case "deepseek-v4-pro" -> new AiProviderPricing(
                    "CNY",
                    new BigDecimal("0.025"),
                    new BigDecimal("3"),
                    new BigDecimal("6")
            );
            case "deepseek-reasoner" -> new AiProviderPricing(
                    "CNY",
                    BigDecimal.ONE,
                    new BigDecimal("4"),
                    new BigDecimal("16")
            );
            default -> new AiProviderPricing(
                    "CNY",
                    new BigDecimal("0.02"),
                    BigDecimal.ONE,
                    new BigDecimal("2")
            );
        };
    }

    private String writePricing(AiProviderPricing pricing) {
        try {
            return objectMapper.writeValueAsString(pricing);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("pricing is not serializable", exception);
        }
    }

    private AiProviderPricing readPricing(String rawJson, String model) {
        if (isBlank(rawJson)) {
            return defaultPricing(model);
        }
        try {
            return normalizePricing(objectMapper.readValue(rawJson, AiProviderPricing.class), model);
        } catch (JsonProcessingException exception) {
            return defaultPricing(model);
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ApiKeyStorage(String rawKey, String maskedKey) {
    }

    private record AiProviderTestConfiguration(
            String providerId,
            String baseUrl,
            String model,
            String apiKey,
            boolean configured
    ) {
    }
}
