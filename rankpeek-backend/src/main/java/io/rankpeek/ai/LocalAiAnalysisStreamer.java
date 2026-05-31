package io.rankpeek.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LocalAiAnalysisStreamer {

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
                settings.maxTokens(),
                settings.temperature(),
                jsonObjectResponse
        );
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(toJson(payload), MediaType.APPLICATION_JSON));
        } catch (Exception exception) {
            throw new AiProviderException("Local AI stream delivery failed", exception);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new AiProviderException("Local AI stream payload serialization failed", exception);
        }
    }

    public record StreamResult(
            String text,
            AiTokenUsage usage
    ) {
    }
}
