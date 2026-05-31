package io.rankpeek.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.cost.AiCostBreakdown;
import io.rankpeek.cost.AiPricing;
import io.rankpeek.cost.AiPricingCatalog;
import io.rankpeek.cost.CostService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class LocalAiAnalysisService {

    private static final long STREAM_TIMEOUT_MS = 120_000L;

    private final AiProviderSettingsService settingsService;
    private final LocalAiRunRepository runRepository;
    private final LocalAiAnalysisStreamer streamer;
    private final ObjectMapper objectMapper;
    private final CostService costService;

    LocalAiAnalysisService(
            AiProviderSettingsService settingsService,
            LocalAiRunRepository runRepository,
            LocalAiAnalysisStreamer streamer,
            ObjectMapper objectMapper
    ) {
        this(settingsService, runRepository, streamer, objectMapper, null);
    }

    public LocalAiAnalysisService(
            AiProviderSettingsService settingsService,
            LocalAiRunRepository runRepository,
            LocalAiAnalysisStreamer streamer,
            ObjectMapper objectMapper,
            CostService costService
    ) {
        this.settingsService = settingsService;
        this.runRepository = runRepository;
        this.streamer = streamer;
        this.objectMapper = objectMapper;
        this.costService = costService;
    }

    public SseEmitter streamPregame(PregameAnalysisRequest request) {
        return stream("pregame", request, buildPregameMessages(request), "Pregame analysis");
    }

    public SseEmitter streamPostgame(PostgameAnalysisRequest request) {
        return stream("postgame", request, buildPostgameMessages(request), "Postgame analysis");
    }

    public CoachSummaryAnalysisResponse generateCoachSummary(CoachSummaryAnalysisRequest request) {
        StoredAiProviderSettings settings = settingsService.requireRunnableSettings();
        String requestRawJson = writeJson(request);
        long runId = runRepository.createStartedRun(
                "coach-summary",
                settings.providerId(),
                settings.model(),
                sha256(requestRawJson),
                requestRawJson
        );
        try {
            LocalAiAnalysisStreamer.StreamResult result = streamer.streamJson(
                    settings,
                    List.of(
                            new OpenAiChatMessage("system", blankToDefault(request.systemPrompt(), "Return JSON.")),
                            new OpenAiChatMessage("user", blankToDefault(request.userPrompt(), "Analyze this RankPeek snapshot."))
                    )
            );
            Map<String, Object> report = parseJsonObject(result.text());
            runRepository.markSucceeded(runId, writeJson(report), result.usage());
            recordCostIfAvailable(runId, "coach-summary", settings, result.usage());
            return new CoachSummaryAnalysisResponse(report, result.usage());
        } catch (Exception exception) {
            runRepository.markFailed(runId, errorCode(exception), exception.getMessage());
            throw exception;
        }
    }

    public LocalAiRunListResponse listRuns(String endpoint, String status, int limit, int offset) {
        return new LocalAiRunListResponse(runRepository.list(endpoint, status, limit, offset).stream()
                .map(LocalAiRunResponse::from)
                .toList());
    }

    public LocalAiRunResponse getRun(long runId) {
        return runRepository.findById(runId)
                .map(LocalAiRunResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Local AI run not found: " + runId));
    }

    private SseEmitter stream(
            String endpoint,
            Object request,
            List<OpenAiChatMessage> messages,
            String sectionTitle
    ) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        CompletableFuture.runAsync(() -> runStream(endpoint, request, messages, sectionTitle, emitter));
        return emitter;
    }

    private void runStream(
            String endpoint,
            Object request,
            List<OpenAiChatMessage> messages,
            String sectionTitle,
            SseEmitter emitter
    ) {
        Long runId = null;
        try {
            StoredAiProviderSettings settings = settingsService.requireRunnableSettings();
            String requestRawJson = writeJson(request);
            runId = runRepository.createStartedRun(
                    endpoint,
                    settings.providerId(),
                    settings.model(),
                    sha256(requestRawJson),
                    requestRawJson
            );
            LocalAiAnalysisStreamer.StreamResult result =
                    streamer.streamToEmitter(emitter, settings, messages, sectionTitle);
            runRepository.markSucceeded(runId, result.text(), result.usage());
            recordCostIfAvailable(runId, endpoint, settings, result.usage());
            emitter.complete();
        } catch (LocalAiConfigurationException exception) {
            if (runId != null) {
                runRepository.markFailed(runId, LocalAiConfigurationException.CODE, exception.getMessage());
            }
            streamer.sendError(emitter, LocalAiConfigurationException.CODE, exception.getMessage());
            emitter.complete();
        } catch (Exception exception) {
            if (runId != null) {
                runRepository.markFailed(runId, errorCode(exception), exception.getMessage());
            }
            streamer.sendError(emitter, errorCode(exception), exception.getMessage());
            emitter.complete();
        }
    }

    private void recordCostIfAvailable(
            long runId,
            String endpoint,
            StoredAiProviderSettings settings,
            AiTokenUsage usage
    ) {
        if (costService == null || usage == null) {
            return;
        }
        try {
            AiCostBreakdown cost = costService.recordAiAnalysis(runId, endpoint, usage, pricingForSettings(settings));
            runRepository.updateCost(runId, cost);
        } catch (Exception ignored) {
            // Cost tracking should never make an otherwise successful local AI response fail.
        }
    }

    private List<OpenAiChatMessage> buildPregameMessages(PregameAnalysisRequest request) {
        return List.of(
                new OpenAiChatMessage(
                        "system",
                        "You are RankPeek local AI. Give concise League of Legends pregame scouting advice."
                ),
                new OpenAiChatMessage(
                        "user",
                        "Analyze this pregame snapshot and team tags:\n" + writeJson(request)
                )
        );
    }

    private List<OpenAiChatMessage> buildPostgameMessages(PostgameAnalysisRequest request) {
        return List.of(
                new OpenAiChatMessage(
                        "system",
                        "You are RankPeek local AI. Give concise League of Legends postgame review."
                ),
                new OpenAiChatMessage(
                        "user",
                        "Analyze this postgame snapshot:\n" + writeJson(request)
                )
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Local AI request is not serializable", exception);
        }
    }

    private Map<String, Object> parseJsonObject(String text) {
        try {
            return objectMapper.readValue(text, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new AiProviderException("Local AI JSON response is invalid", exception);
        }
    }

    private AiPricing pricingForSettings(StoredAiProviderSettings settings) {
        if (settings.pricingRawJson() != null && !settings.pricingRawJson().isBlank()) {
            try {
                AiProviderPricing pricing = objectMapper.readValue(settings.pricingRawJson(), AiProviderPricing.class);
                return new AiPricing(
                        settings.providerId(),
                        settings.model(),
                        blankToDefault(pricing.currency(), "CNY"),
                        nonNegative(pricing.inputCacheHitCnyPerMillionTokens()),
                        nonNegative(pricing.inputCacheMissCnyPerMillionTokens()),
                        nonNegative(pricing.outputCnyPerMillionTokens())
                );
            } catch (JsonProcessingException ignored) {
                // Fall back to the catalog below.
            }
        }
        return AiPricingCatalog.forModel(settings.providerId(), settings.model()).orElse(null);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String errorCode(Exception exception) {
        if (exception instanceof LocalAiConfigurationException) {
            return LocalAiConfigurationException.CODE;
        }
        return exception instanceof AiProviderException ? "AI_PROVIDER_ERROR" : "LOCAL_AI_ERROR";
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
