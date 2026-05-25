package io.rankpeek.server.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class DeepSeekChatClient {

    private final ObjectMapper objectMapper;

    public DeepSeekChatClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void streamChat(
            DeepSeekAiProperties properties,
            List<DeepSeekChatMessage> messages,
            Consumer<String> onDelta
    ) {
        streamChat(properties, messages, onDelta, ignored -> {
        });
    }

    public void streamChat(
            DeepSeekAiProperties properties,
            List<DeepSeekChatMessage> messages,
            Consumer<String> onDelta,
            Consumer<DeepSeekTokenUsage> onUsage
    ) {
        streamChat(properties, messages, onDelta, onUsage, false);
    }

    public void streamJsonChat(
            DeepSeekAiProperties properties,
            List<DeepSeekChatMessage> messages,
            Consumer<String> onDelta,
            Consumer<DeepSeekTokenUsage> onUsage
    ) {
        streamChat(properties, messages, onDelta, onUsage, true);
    }

    private void streamChat(
            DeepSeekAiProperties properties,
            List<DeepSeekChatMessage> messages,
            Consumer<String> onDelta,
            Consumer<DeepSeekTokenUsage> onUsage,
            boolean jsonObjectResponse
    ) {
        if (properties.apiKey().isBlank()) {
            throw new DeepSeekAiException("DeepSeek API key is not configured");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.chatCompletionsUrl()))
                .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildRequestBody(properties, messages, jsonObjectResponse),
                        StandardCharsets.UTF_8
                ))
                .build();

        HttpResponse<java.io.InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DeepSeekAiException("DeepSeek request was interrupted", exception);
        } catch (HttpTimeoutException exception) {
            throw new DeepSeekAiException("DeepSeek request timed out", exception);
        } catch (IOException exception) {
            throw new DeepSeekAiException("DeepSeek request failed", exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DeepSeekAiException("DeepSeek request failed: HTTP " + response.statusCode());
        }

        parseSseResponse(response, properties.model(), onDelta, onUsage);
    }

    private String buildRequestBody(
            DeepSeekAiProperties properties,
            List<DeepSeekChatMessage> messages,
            boolean jsonObjectResponse
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("stream", true);
        body.put("thinking", Map.of("type", "disabled"));
        body.put("stream_options", Map.of("include_usage", true));
        body.put("max_tokens", properties.maxTokens());
        body.put("temperature", properties.temperature());
        if (jsonObjectResponse) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        body.put("messages", messages.stream()
                .map(message -> Map.of(
                        "role", message.role(),
                        "content", message.content()
                ))
                .toList());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new DeepSeekAiException("DeepSeek request body serialization failed", exception);
        }
    }

    private void parseSseResponse(
            HttpResponse<java.io.InputStream> response,
            String fallbackModel,
            Consumer<String> onDelta,
            Consumer<DeepSeekTokenUsage> onUsage
    ) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("data:")) {
                    continue;
                }

                String data = trimmed.substring("data:".length()).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    return;
                }

                JsonNode root = readStreamChunk(data);
                JsonNode usage = root.get("usage");
                if (usage != null && usage.isObject()) {
                    onUsage.accept(readTokenUsage(root, usage, fallbackModel));
                }

                String content = readDeltaContent(root);
                if (!content.isEmpty()) {
                    onDelta.accept(content);
                }
            }
        } catch (IOException exception) {
            throw new DeepSeekAiException("DeepSeek invalid stream response", exception);
        }
    }

    private JsonNode readStreamChunk(String data) {
        try {
            return objectMapper.readTree(data);
        } catch (JsonProcessingException exception) {
            throw new DeepSeekAiException("DeepSeek invalid stream response", exception);
        }
    }

    private String readDeltaContent(JsonNode root) {
        JsonNode content = root.path("choices").path(0).path("delta").path("content");
        return content.isTextual() ? content.asText() : "";
    }

    private static DeepSeekTokenUsage readTokenUsage(JsonNode root, JsonNode usage, String fallbackModel) {
        long promptTokens = readLong(usage, "prompt_tokens");
        long cacheHitTokens = readLong(usage, "prompt_cache_hit_tokens");
        long cacheMissTokens = usage.has("prompt_cache_miss_tokens")
                ? readLong(usage, "prompt_cache_miss_tokens")
                : Math.max(0, promptTokens - cacheHitTokens);
        return new DeepSeekTokenUsage(
                "deepseek",
                readModel(root, fallbackModel),
                promptTokens,
                readLong(usage, "completion_tokens"),
                readLong(usage, "total_tokens"),
                cacheHitTokens,
                cacheMissTokens
        );
    }

    private static String readModel(JsonNode root, String fallbackModel) {
        JsonNode model = root.path("model");
        return model.isTextual() && !model.asText().isBlank() ? model.asText() : fallbackModel;
    }

    private static long readLong(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.canConvertToLong() ? value.asLong() : 0L;
    }
}
