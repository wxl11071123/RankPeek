package io.rankpeek.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.rankpeek.cache.LocalCacheSchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocalAiControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JdbcTemplate jdbcTemplate;
    private AiProviderSettingsService settingsService;
    private LocalAiRunRepository runRepository;
    private MockMvc mockMvc;
    private HttpServer aiServer;
    private AtomicInteger aiRequestCount;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:rankpeek-local-ai-controller-" + System.nanoTime()
                        + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        new LocalCacheSchemaInitializer(jdbcTemplate).initializeSchema();
        AiProviderSettingsRepository settingsRepository = new AiProviderSettingsRepository(jdbcTemplate);
        settingsService = new AiProviderSettingsService(settingsRepository);
        runRepository = new LocalAiRunRepository(jdbcTemplate);
        LocalAiAnalysisService service = new LocalAiAnalysisService(
                settingsService,
                runRepository,
                new LocalAiAnalysisStreamer(new OpenAiCompatibleChatClient(objectMapper), objectMapper),
                objectMapper
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new LocalAiController(service)).build();
    }

    @AfterEach
    void tearDown() {
        if (aiServer != null) {
            aiServer.stop(0);
        }
    }

    @Test
    void pregameStreamWithoutAuthorizationReturnsProviderConfigurationErrorWhenMissingSettings() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ai/pregame/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "mode": "teammate",
                                  "snapshotSchemaVersion": "gaming_ai_input_snapshot.v1",
                                  "snapshot": {}
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString("AI_PROVIDER_NOT_CONFIGURED")))
                .andExpect(content().string(not(containsString("Authorization"))));
    }

    @Test
    void successfulPregameStreamEmitsStartDeltaUsageAndDoneWithoutAuthorization() throws Exception {
        configureSuccessfulProvider();

        MvcResult result = mockMvc.perform(post("/api/v1/ai/pregame/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "mode": "teammate",
                                  "queueId": 420,
                                  "allyTeamTags": ["ally | W#1234 | champion=141"],
                                  "enemyTeamTags": [],
                                  "snapshotSchemaVersion": "gaming_ai_input_snapshot.v1",
                                  "snapshot": {"mode": "teammate"}
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:start")))
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("RankPeek local AI")))
                .andExpect(content().string(containsString("event:usage")))
                .andExpect(content().string(containsString("promptCacheHitTokens")))
                .andExpect(content().string(containsString("event:done")));

        assertThat(aiRequestCount.get()).isEqualTo(1);
        assertThat(runRepository.list("pregame", "succeeded", 20, 0)).hasSize(1);
    }

    @Test
    void postgameStreamDoesNotRequireAuthorization() throws Exception {
        configureSuccessfulProvider();

        MvcResult result = mockMvc.perform(post("/api/v1/ai/postgame/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "mode": "review",
                                  "snapshotSchemaVersion": "postgame_ai_input_snapshot.v1",
                                  "snapshot": {"mode": "review"}
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:start")))
                .andExpect(content().string(containsString("event:done")));
    }

    @Test
    void coachSummaryDoesNotRequireAuthorizationAndReturnsReport() throws Exception {
        configureSuccessfulProvider("""
                data: {"model":"deepseek-v4-flash","choices":[{"delta":{"content":"{\\\"verdict\\\":{\\\"label\\\":\\\"B+\\\"}}"}}]}

                data: {"model":"deepseek-v4-flash","usage":{"prompt_tokens":10,"prompt_cache_hit_tokens":4,"prompt_cache_miss_tokens":6,"completion_tokens":3,"total_tokens":13},"choices":[{"delta":{}}]}

                data: [DONE]

                """);

        mockMvc.perform(post("/api/v1/ai/coach-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputHash": "hash-1",
                                  "snapshotSchemaVersion": "coach_summary_input_snapshot.v1",
                                  "promptVersion": "coach_summary_prompt.v1",
                                  "dataQualityConfidence": "high",
                                  "systemPrompt": "Return JSON",
                                  "userPrompt": "Analyze"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.report.verdict.label").value("B+"))
                .andExpect(jsonPath("$.data.usage.promptTokens").value(10));
    }

    @Test
    void runsCanBeListedAndFetchedThroughController() throws Exception {
        long runId = runRepository.createStartedRun("pregame", "deepseek", "deepseek-v4-flash", "hash", "{}");
        runRepository.markFailed(runId, "TEST_ERROR", "failed for test");

        mockMvc.perform(get("/api/v1/ai/runs?status=failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(runId))
                .andExpect(jsonPath("$.data.items[0].status").value("failed"));

        mockMvc.perform(get("/api/v1/ai/runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId))
                .andExpect(jsonPath("$.data.errorCode").value("TEST_ERROR"));
    }

    private void configureSuccessfulProvider() throws Exception {
        configureSuccessfulProvider("""
                data: {"model":"deepseek-v4-flash","choices":[{"delta":{"content":"RankPeek local AI response"}}]}

                data: {"model":"deepseek-v4-flash","usage":{"prompt_tokens":10,"prompt_cache_hit_tokens":4,"prompt_cache_miss_tokens":6,"completion_tokens":3,"total_tokens":13},"choices":[{"delta":{}}]}

                data: [DONE]

                """);
    }

    private void configureSuccessfulProvider(String sseBody) throws Exception {
        aiRequestCount = new AtomicInteger();
        aiServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        aiServer.createContext("/chat/completions", exchange -> respondWithSse(exchange, sseBody));
        aiServer.start();
        settingsService.saveSettings(new AiProviderSettingsSaveRequest(
                true,
                "deepseek",
                "http://127.0.0.1:" + aiServer.getAddress().getPort(),
                "deepseek-v4-flash",
                "sk-test",
                true,
                0.4d,
                4096,
                new AiProviderPricing(
                        "CNY",
                        new BigDecimal("0.02"),
                        new BigDecimal("1"),
                        new BigDecimal("2")
                )
        ));
    }

    private void respondWithSse(HttpExchange exchange, String sseBody) throws IOException {
        aiRequestCount.incrementAndGet();
        JsonNode request = objectMapper.readTree(exchange.getRequestBody());
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer sk-test");
        assertThat(request.path("stream").asBoolean()).isTrue();
        byte[] body = sseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
