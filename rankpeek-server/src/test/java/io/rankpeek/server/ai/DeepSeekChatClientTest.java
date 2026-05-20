package io.rankpeek.server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekChatClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;
    private CapturedRequest capturedRequest;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void streamsChatCompletionDeltasAndSendsDeepSeekRequestShape() throws Exception {
        startServer(exchange -> {
            capturedRequest = capture(exchange);
            respond(exchange, 200, "text/event-stream", """
                    data: {"choices":[{"delta":{"content":"第一段"}}]}

                    data: {"choices":[{"delta":{"content":"，第二段"}}]}

                    data: [DONE]

                    """);
        });

        DeepSeekChatClient client = new DeepSeekChatClient(OBJECT_MAPPER);
        DeepSeekAiProperties properties = new DeepSeekAiProperties(
                true,
                "deepseek",
                baseUrl(),
                "deepseek-v4-flash",
                "test-secret",
                1000,
                5000,
                256,
                0.3
        );
        List<String> deltas = new ArrayList<>();

        client.streamChat(properties, List.of(
                new DeepSeekChatMessage("system", "You are RankPeek."),
                new DeepSeekChatMessage("user", "Analyze this game.")
        ), deltas::add);

        assertThat(deltas).containsExactly("第一段", "，第二段");
        assertThat(capturedRequest.path()).isEqualTo("/chat/completions");
        assertThat(capturedRequest.authorization()).isEqualTo("Bearer test-secret");
        assertThat(capturedRequest.contentType()).contains("application/json");

        JsonNode body = OBJECT_MAPPER.readTree(capturedRequest.body());
        assertThat(body.get("model").asText()).isEqualTo("deepseek-v4-flash");
        assertThat(body.get("stream").asBoolean()).isTrue();
        assertThat(body.get("stream_options").get("include_usage").asBoolean()).isTrue();
        assertThat(body.get("thinking").get("type").asText()).isEqualTo("disabled");
        assertThat(body.get("max_tokens").asInt()).isEqualTo(256);
        assertThat(body.get("temperature").asDouble()).isEqualTo(0.3);
        assertThat(body.get("messages")).hasSize(2);
        assertThat(body.get("messages").get(0).get("role").asText()).isEqualTo("system");
    }

    @Test
    void streamsTokenUsageFromDeepSeekUsageChunk() throws Exception {
        startServer(exchange -> {
            capturedRequest = capture(exchange);
            respond(exchange, 200, "text/event-stream", """
                    data: {"choices":[{"delta":{"content":"first"}}],"model":"deepseek-v4-flash","usage":null}

                    data: {"choices":[],"model":"deepseek-v4-flash","usage":{"prompt_tokens":1200,"completion_tokens":80,"total_tokens":1280,"prompt_cache_hit_tokens":0,"prompt_cache_miss_tokens":1200}}

                    data: [DONE]

                    """);
        });

        DeepSeekChatClient client = new DeepSeekChatClient(OBJECT_MAPPER);
        DeepSeekAiProperties properties = new DeepSeekAiProperties(
                true,
                "deepseek",
                baseUrl(),
                "deepseek-v4-flash",
                "test-secret",
                1000,
                5000,
                256,
                0.3
        );
        List<String> deltas = new ArrayList<>();
        List<DeepSeekTokenUsage> usages = new ArrayList<>();

        client.streamChat(properties, List.of(
                new DeepSeekChatMessage("system", "You are RankPeek."),
                new DeepSeekChatMessage("user", "Analyze this game.")
        ), deltas::add, usages::add);

        assertThat(deltas).containsExactly("first");
        assertThat(usages).containsExactly(new DeepSeekTokenUsage(
                "deepseek",
                "deepseek-v4-flash",
                1200,
                80,
                1280,
                0,
                1200
        ));
    }

    @Test
    void reportsHttpErrorsWithoutLeakingApiKey() throws Exception {
        startServer(exchange -> {
            capturedRequest = capture(exchange);
            respond(exchange, 401, "application/json", "{\"error\":{\"message\":\"bad key\"}}");
        });

        DeepSeekChatClient client = new DeepSeekChatClient(OBJECT_MAPPER);
        DeepSeekAiProperties properties = new DeepSeekAiProperties(
                true,
                "deepseek",
                baseUrl(),
                "deepseek-v4-flash",
                "super-secret-key",
                1000,
                5000,
                256,
                0.3
        );

        assertThatThrownBy(() -> client.streamChat(
                properties,
                List.of(new DeepSeekChatMessage("user", "Analyze this game.")),
                ignored -> {
                }
        ))
                .isInstanceOf(DeepSeekAiException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageNotContaining("super-secret-key")
                .hasMessageNotContaining("bad key");
    }

    @Test
    void rejectsMissingApiKeyBeforeCallingProvider() throws Exception {
        startServer(exchange -> {
            capturedRequest = capture(exchange);
            respond(exchange, 200, "text/event-stream", "data: [DONE]\n\n");
        });

        DeepSeekChatClient client = new DeepSeekChatClient(OBJECT_MAPPER);
        DeepSeekAiProperties properties = new DeepSeekAiProperties(
                true,
                "deepseek",
                baseUrl(),
                "deepseek-v4-flash",
                "",
                1000,
                5000,
                256,
                0.3
        );

        assertThatThrownBy(() -> client.streamChat(
                properties,
                List.of(new DeepSeekChatMessage("user", "Analyze this game.")),
                ignored -> {
                }
        ))
                .isInstanceOf(DeepSeekAiException.class)
                .hasMessageContaining("API key is not configured");
        assertThat(capturedRequest).isNull();
    }

    @Test
    void rejectsMalformedSseWithoutLeakingApiKey() throws Exception {
        startServer(exchange -> {
            capturedRequest = capture(exchange);
            respond(exchange, 200, "text/event-stream", """
                    data: {"choices":[{"delta":{"content":"ok"}}]}

                    data: {"choices":

                    """);
        });

        DeepSeekChatClient client = new DeepSeekChatClient(OBJECT_MAPPER);
        DeepSeekAiProperties properties = new DeepSeekAiProperties(
                true,
                "deepseek",
                baseUrl(),
                "deepseek-v4-flash",
                "another-secret",
                1000,
                5000,
                256,
                0.3
        );

        assertThatThrownBy(() -> client.streamChat(
                properties,
                List.of(new DeepSeekChatMessage("user", "Analyze this game.")),
                ignored -> {
                }
        ))
                .isInstanceOf(DeepSeekAiException.class)
                .hasMessageContaining("invalid stream")
                .hasMessageNotContaining("another-secret");
    }

    @Test
    void reportsTimeoutWithoutLeakingApiKey() throws Exception {
        startServer(exchange -> {
            capturedRequest = capture(exchange);
            Thread.sleep(300);
            respond(exchange, 200, "text/event-stream", "data: [DONE]\n\n");
        });

        DeepSeekChatClient client = new DeepSeekChatClient(OBJECT_MAPPER);
        DeepSeekAiProperties properties = new DeepSeekAiProperties(
                true,
                "deepseek",
                baseUrl(),
                "deepseek-v4-flash",
                "timeout-secret",
                1000,
                50,
                256,
                0.3
        );

        assertThatThrownBy(() -> client.streamChat(
                properties,
                List.of(new DeepSeekChatMessage("user", "Analyze this game.")),
                ignored -> {
                }
        ))
                .isInstanceOf(DeepSeekAiException.class)
                .hasMessageContaining("timed out")
                .hasMessageNotContaining("timeout-secret");
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception exception) {
                respond(exchange, 500, "text/plain", exception.getMessage());
            }
        });
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static CapturedRequest capture(HttpExchange exchange) throws IOException {
        return new CapturedRequest(
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
        );
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record CapturedRequest(
            String path,
            String authorization,
            String contentType,
            String body
    ) {
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
