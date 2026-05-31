package io.rankpeek.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LocalAiAnalysisStreamer {

    private static final MediaType APPLICATION_JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    private final OpenAiCompatibleChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LocalAiAnalysisStreamer(OpenAiCompatibleChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    public StreamResult streamToEmitter(
            SseEmitter emitter,
            StoredAiProviderSettings settings,
            List<OpenAiChatMessage> messages,
            String sectionTitle
    ) {
        StringBuilder responseText = new StringBuilder();
        AtomicReference<AiTokenUsage> usage = new AtomicReference<>();
        sendEvent(emitter, "start", Map.of("type", "start", "title", "RankPeek local AI stream started"));
        sendEvent(emitter, "section", Map.of("type", "section", "title", sectionTitle));
        chatClient.streamChat(
                chatOptions(settings, false),
                messages,
                delta -> {
                    responseText.append(delta);
                    sendEvent(emitter, "delta", Map.of("type", "delta", "text", delta));
                },
                value -> {
                    usage.set(value);
                    sendEvent(emitter, "usage", Map.of("type", "usage", "usage", value));
                }
        );
        sendEvent(emitter, "done", Map.of("type", "done"));
        return new StreamResult(responseText.toString(), usage.get());
    }

    public StreamResult streamJsonToEmitter(
            SseEmitter emitter,
            StoredAiProviderSettings settings,
            List<OpenAiChatMessage> messages,
            String sectionTitle
    ) {
        StringBuilder responseText = new StringBuilder();
        AtomicReference<AiTokenUsage> usage = new AtomicReference<>();
        sendEvent(emitter, "start", Map.of("type", "start", "title", "RankPeek local AI stream started"));
        sendEvent(emitter, "section", Map.of("type", "section", "title", sectionTitle));
        chatClient.streamJsonChat(
                chatOptions(settings, true),
                messages,
                delta -> {
                    responseText.append(delta);
                    sendEvent(emitter, "delta", Map.of("type", "delta", "text", delta));
                },
                value -> {
                    usage.set(value);
                    sendEvent(emitter, "usage", Map.of("type", "usage", "usage", value));
                }
        );
        sendEvent(emitter, "done", Map.of("type", "done"));
        return new StreamResult(responseText.toString(), usage.get());
    }

    public StreamResult streamPregameInsightsToEmitter(
            SseEmitter emitter,
            StoredAiProviderSettings settings,
            List<OpenAiChatMessage> messages,
            String sectionTitle,
            Set<String> allowedPlayerKeys
    ) {
        StringBuilder responseText = new StringBuilder();
        StringBuilder lineBuffer = new StringBuilder();
        AtomicReference<AiTokenUsage> usage = new AtomicReference<>();
        sendEvent(emitter, "start", Map.of("type", "start", "title", "RankPeek local AI stream started"));
        sendEvent(emitter, "section", Map.of("type", "section", "title", sectionTitle));
        chatClient.streamChat(
                chatOptions(settings, false),
                messages,
                delta -> {
                    responseText.append(delta);
                    consumePregameDelta(emitter, lineBuffer, allowedPlayerKeys, delta);
                },
                value -> {
                    usage.set(value);
                    sendEvent(emitter, "usage", Map.of("type", "usage", "usage", value));
                }
        );
        flushPregameBuffer(emitter, lineBuffer, allowedPlayerKeys);
        sendEvent(emitter, "done", Map.of("type", "done"));
        return new StreamResult(responseText.toString(), usage.get());
    }

    public StreamResult streamJson(
            StoredAiProviderSettings settings,
            List<OpenAiChatMessage> messages
    ) {
        StringBuilder responseText = new StringBuilder();
        AtomicReference<AiTokenUsage> usage = new AtomicReference<>();
        chatClient.streamJsonChat(
                chatOptions(settings, true),
                messages,
                responseText::append,
                usage::set
        );
        return new StreamResult(responseText.toString(), usage.get());
    }

    public void sendError(SseEmitter emitter, String code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("code", code);
        payload.put("message", message);
        sendEvent(emitter, "error", payload);
    }

    private OpenAiCompatibleChatClient.ChatOptions chatOptions(
            StoredAiProviderSettings settings,
            boolean jsonObjectResponse
    ) {
        return new OpenAiCompatibleChatClient.ChatOptions(
                settings.providerId(),
                settings.baseUrl(),
                settings.model(),
                settings.apiKeyEncrypted(),
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                settings.webSearchEnabled(),
                settings.deepThinkingEnabled(),
                jsonObjectResponse
        );
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload, APPLICATION_JSON_UTF8));
        } catch (Exception exception) {
            throw new AiProviderException("Local AI stream delivery failed", exception);
        }
    }

    private void consumePregameDelta(
            SseEmitter emitter,
            StringBuilder buffer,
            Set<String> allowedPlayerKeys,
            String delta
    ) {
        buffer.append(delta);
        int newlineIndex = buffer.indexOf("\n");
        while (newlineIndex >= 0) {
            String line = buffer.substring(0, newlineIndex);
            buffer.delete(0, newlineIndex + 1);
            sendPregameInsightLine(emitter, allowedPlayerKeys, line);
            newlineIndex = buffer.indexOf("\n");
        }
    }

    private void flushPregameBuffer(SseEmitter emitter, StringBuilder buffer, Set<String> allowedPlayerKeys) {
        if (buffer.toString().trim().isEmpty()) {
            return;
        }
        sendPregameInsightLine(emitter, allowedPlayerKeys, buffer.toString());
        buffer.setLength(0);
    }

    private void sendPregameInsightLine(SseEmitter emitter, Set<String> allowedPlayerKeys, String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(trimmed);
        } catch (JsonProcessingException exception) {
            throw new AiProviderException("Local AI pregame insight is not valid NDJSON", exception);
        }

        String playerKey = readText(node, "playerKey");
        String label = readText(node, "label");
        String text = readText(node, "text");
        if (playerKey.isBlank() || label.isBlank() || text.isBlank()) {
            throw new AiProviderException("Local AI pregame insight missing required fields");
        }
        if (allowedPlayerKeys != null && !allowedPlayerKeys.isEmpty() && !allowedPlayerKeys.contains(playerKey)) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "player_insight");
        payload.put("playerKey", playerKey);
        payload.put("label", label);
        payload.put("tone", normalizeTone(readText(node, "tone")));
        payload.put("text", text);
        sendEvent(emitter, "player_insight", payload);
    }

    private static String readText(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        return field.isTextual() ? field.asText().trim() : "";
    }

    private static String normalizeTone(String tone) {
        return switch (tone == null ? "" : tone.trim()) {
            case "carry", "stable", "risk", "weak", "unknown" -> tone.trim();
            default -> "unknown";
        };
    }

    public record StreamResult(
            String text,
            AiTokenUsage usage
    ) {
    }
}
