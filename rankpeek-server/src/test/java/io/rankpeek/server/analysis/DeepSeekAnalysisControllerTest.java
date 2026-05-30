package io.rankpeek.server.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.rankpeek.server.auth.AuthRepository;
import io.rankpeek.server.auth.AuthUser;
import io.rankpeek.server.auth.PasswordService;
import io.rankpeek.server.credits.AdminCreditGrantRequest;
import io.rankpeek.server.credits.CreditService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@DirtiesContext
class DeepSeekAnalysisControllerTest {

    private static final String IDEMPOTENCY_HEADER = "X-RankPeek-Idempotency-Key";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static HttpServer server;
    private static volatile CapturedRequest capturedRequest;
    private static final AtomicInteger capturedRequestCount = new AtomicInteger();
    private static volatile FakeResponse nextResponse = new FakeResponse(200, """
            data: {"choices":[{"delta":{"content":"deepseek-stream-advice"}}]}

            data: [DONE]

            """);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private CreditService creditService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void deepSeekProperties(DynamicPropertyRegistry registry) throws IOException {
        ensureServer();
        registry.add("rankpeek.ai.enabled", () -> "true");
        registry.add("rankpeek.ai.provider", () -> "deepseek");
        registry.add("rankpeek.ai.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
        registry.add("rankpeek.ai.model", () -> "deepseek-v4-flash");
        registry.add("rankpeek.ai.api-key", () -> "test-secret");
        registry.add("rankpeek.ai.max-tokens", () -> "128");
        registry.add("rankpeek.ai.temperature", () -> "0.2");
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void resetFakeServer() {
        capturedRequest = null;
        capturedRequestCount.set(0);
        nextResponse = new FakeResponse(200, """
                data: {"choices":[{"delta":{"content":"deepseek-stream-advice"}}]}

                data: [DONE]

                """);
    }

    @Test
    void coachSummaryUsesDeepSeekAndReturnsNormalizedStructuredReport() throws Exception {
        String aiJson = """
                {
                  "schemaVersion": "coach_summary_report.v1",
                  "analysisType": "coach_summary",
                  "inputHash": "ai-placeholder-hash",
                  "title": "Mid game deaths slow the climb",
                  "summary": "Twenty ranked games show a clear mid-game pattern.",
                  "verdict": {
                    "label": "Resource fights need cleaner setup",
                    "score": 72,
                    "confidence": "medium",
                    "summary": "The current sample shows playable lane and champion pool stability, but repeated deaths before major resources are limiting conversion."
                  },
                  "keyFindings": [
                    {
                      "id": "finding-1",
                      "priority": "high",
                      "category": "death",
                      "claim": "Mid-game deaths are the main repeated risk.",
                      "evidence": "m03 and m07 both mention deaths before objectives.",
                      "reasoning": "Those deaths remove tempo before neutral fights.",
                      "advice": "Enter river only after the nearby wave is handled.",
                      "confidence": "medium",
                      "evidenceRefs": ["m03", "m07"]
                    }
                  ],
                  "trainingPlan": [
                    {
                      "focus": "Objective setup",
                      "why": "Most lost tempo appears before neutral resources.",
                      "nextGames": 5,
                      "task": "Before dragon or Baron, reset or push one wave before walking in.",
                      "metricToTrack": "Deaths before neutral objectives",
                      "target": "No more than one in five games",
                      "priority": "high"
                    }
                  ],
                  "championAdvice": [
                    {
                      "championName": "Kindred",
                      "role": "jungle",
                      "recommendation": "keep",
                      "reason": "The sample is large enough to keep using it while cleaning mid-game setup.",
                      "confidence": "medium"
                    }
                  ],
                  "chartBlocks": [],
                  "warnings": [],
                  "finalSummary": "Keep the current champion direction, but make the next block about safer objective setup."
                }
                """;
        nextResponse = new FakeResponse(200, deepSeekContentStream(aiJson));

        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userWithCredits(10)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputHash": "coach-hash-1",
                                  "snapshotSchemaVersion": "coach_summary_input_snapshot.v2",
                                  "promptVersion": "coach_summary.prompt.v2",
                                  "dataQualityConfidence": "medium",
                                  "systemPrompt": "system coach prompt",
                                  "userPrompt": "{\\"currentSnapshotText\\":\\"最近20局走势：资源团前死亡偏多\\",\\"historicalCoachContext\\":\\"无历史报告\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.report.schemaVersion").value("coach_summary_report.v1"))
                .andExpect(jsonPath("$.data.report.analysisType").value("coach_summary"))
                .andExpect(jsonPath("$.data.report.inputHash").value("coach-hash-1"))
                .andExpect(jsonPath("$.data.report.metadata.modelName").value("deepseek-v4-flash"))
                .andExpect(jsonPath("$.data.report.metadata.promptVersion").value("coach_summary.prompt.v2"))
                .andExpect(jsonPath("$.data.report.metadata.snapshotSchemaVersion").value("coach_summary_input_snapshot.v2"))
                .andExpect(jsonPath("$.data.report.metadata.dataQualityConfidence").value("medium"))
                .andExpect(jsonPath("$.data.usage.totalTokens").value(117))
                .andExpect(content().string(not(containsString("test-secret"))));

        assertThat(capturedRequest).isNotNull();
        assertThat(capturedRequest.path()).isEqualTo("/chat/completions");
        assertThat(capturedRequest.authorization()).isEqualTo("Bearer test-secret");
        JsonNode body = OBJECT_MAPPER.readTree(capturedRequest.body());
        assertThat(body.get("stream").asBoolean()).isTrue();
        assertThat(body.get("response_format").get("type").asText()).isEqualTo("json_object");
        JsonNode messagesNode = body.get("messages");
        assertThat(messagesNode.get(0).get("role").asText()).isEqualTo("system");
        assertThat(messagesNode.get(0).get("content").asText()).isEqualTo("system coach prompt");
        assertThat(messagesNode.get(1).get("role").asText()).isEqualTo("user");
        assertThat(messagesNode.get(1).get("content").asText()).contains("最近20局走势");
    }

    @Test
    void coachSummaryNormalizesFrontendParseableSparseReport() throws Exception {
        String aiJson = """
                {
                  "schemaVersion": "coach_summary_report.v1",
                  "analysisType": "coach_summary",
                  "inputHash": "ai-placeholder-hash",
                  "title": "资源团前先站稳",
                  "summary": "最近20局显示资源团前死亡偏多。",
                  "verdict": {
                    "label": "中期资源处理需要收紧",
                    "score": 72,
                    "confidence": "medium",
                    "summary": "你有稳定的英雄池和可用的节奏点，但资源刷新前的死亡会把优势送回去。"
                  }
                }
                """;
        nextResponse = new FakeResponse(200, deepSeekContentStream(aiJson));

        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userWithCredits(10)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputHash": "coach-hash-sparse",
                                  "snapshotSchemaVersion": "coach_summary_input_snapshot.v2",
                                  "promptVersion": "coach_summary.prompt.v2",
                                  "dataQualityConfidence": "low",
                                  "systemPrompt": "system coach prompt",
                                  "userPrompt": "{\\"currentSnapshotText\\":\\"最近20局走势：资源团前死亡偏多\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.report.inputHash").value("coach-hash-sparse"))
                .andExpect(jsonPath("$.data.report.title").value("资源团前先站稳"))
                .andExpect(jsonPath("$.data.report.keyFindings").isArray())
                .andExpect(jsonPath("$.data.report.trainingPlan").isArray())
                .andExpect(jsonPath("$.data.report.championAdvice").isArray())
                .andExpect(jsonPath("$.data.report.chartBlocks").isArray())
                .andExpect(jsonPath("$.data.report.warnings").isArray())
                .andExpect(jsonPath("$.data.report.metadata.dataQualityConfidence").value("low"));
    }

    @Test
    void coachSummaryNormalizesAlternateTitleFieldsInsteadOfFailingReport() throws Exception {
        String aiJson = """
                {
                  "headline": "中期资源团前先站稳",
                  "summary": "最近20局显示资源团前死亡偏多。",
                  "verdict": {
                    "label": "资源团前少掉点",
                    "score": 68,
                    "confidence": "medium",
                    "summary": "你有可用的节奏点，但资源刷新前的死亡会让优势断档。"
                  }
                }
                """;
        nextResponse = new FakeResponse(200, deepSeekContentStream(aiJson));

        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userWithCredits(10)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputHash": "coach-hash-headline",
                                  "snapshotSchemaVersion": "coach_summary_input_snapshot.v2",
                                  "promptVersion": "coach_summary.prompt.v2",
                                  "dataQualityConfidence": "medium",
                                  "systemPrompt": "system coach prompt",
                                  "userPrompt": "{\\"currentSnapshotText\\":\\"最近20局走势：资源团前死亡偏多\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.report.title").value("中期资源团前先站稳"))
                .andExpect(jsonPath("$.data.report.summary").value("最近20局显示资源团前死亡偏多。"))
                .andExpect(jsonPath("$.data.report.inputHash").value("coach-hash-headline"));
    }

    @Test
    void coachSummaryUnwrapsCommonReportEnvelope() throws Exception {
        String aiJson = """
                {
                  "report": {
                    "title": "最近20局先控资源前站位",
                    "summary": "优势建立不错，但资源刷新前站位还要收紧。",
                    "verdict": {
                      "label": "资源前站位",
                      "score": 70,
                      "confidence": "medium",
                      "summary": "报告主体被模型包在 report 字段里时，服务端仍应归一化成前端可读格式。"
                    }
                  }
                }
                """;
        nextResponse = new FakeResponse(200, deepSeekContentStream(aiJson));

        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userWithCredits(10)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputHash": "coach-hash-envelope",
                                  "snapshotSchemaVersion": "coach_summary_input_snapshot.v2",
                                  "promptVersion": "coach_summary.prompt.v2",
                                  "dataQualityConfidence": "medium",
                                  "systemPrompt": "system coach prompt",
                                  "userPrompt": "{\\"currentSnapshotText\\":\\"最近20局走势：资源团前死亡偏多\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.report.title").value("最近20局先控资源前站位"))
                .andExpect(jsonPath("$.data.report.inputHash").value("coach-hash-envelope"));
    }

    @Test
    void coachSummarySynthesizesVerdictWhenModelOmitsIt() throws Exception {
        String aiJson = """
                {
                  "title": "最近20局先控资源前站位",
                  "summary": "优势建立不错，但资源刷新前站位还要收紧。"
                }
                """;
        nextResponse = new FakeResponse(200, deepSeekContentStream(aiJson));

        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userWithCredits(10)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputHash": "coach-hash-no-verdict",
                                  "snapshotSchemaVersion": "coach_summary_input_snapshot.v2",
                                  "promptVersion": "coach_summary.prompt.v2",
                                  "dataQualityConfidence": "low",
                                  "systemPrompt": "system coach prompt",
                                  "userPrompt": "{\\"currentSnapshotText\\":\\"最近20局走势：资源团前死亡偏多\\"}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.report.title").value("最近20局先控资源前站位"))
                .andExpect(jsonPath("$.data.report.verdict.label").value("最近20局先控资源前站位"))
                .andExpect(jsonPath("$.data.report.verdict.confidence").value("low"))
                .andExpect(jsonPath("$.data.report.verdict.summary").value("优势建立不错，但资源刷新前站位还要收紧。"));
    }

    @Test
    void coachSummaryRequiresBearerTokenBeforeCallingDeepSeek() throws Exception {
        capturedRequest = null;

        mockMvc.perform(post("/api/analysis/coach-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(coachSummaryRequest("coach-no-auth")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));

        assertThat(capturedRequest).isNull();
    }

    @Test
    void coachSummaryRejectsWhenCreditBalanceIsInsufficient() throws Exception {
        AuthPayload user = userWithCredits(0);
        capturedRequest = null;

        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header(IDEMPOTENCY_HEADER, "coach-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(coachSummaryRequest("coach-insufficient")))
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_CREDITS"));

        assertThat(capturedRequest).isNull();
    }

    @Test
    void coachSummaryChargesOneCreditAndStoresTokenUsageForSuccessfulDeepSeekCall() throws Exception {
        AuthPayload user = userWithCredits(3);
        String aiJson = """
                {
                  "title": "Objective setup is improving",
                  "summary": "The latest block has enough signal for a coach summary.",
                  "verdict": {
                    "label": "Stable sample",
                    "score": 71,
                    "confidence": "medium",
                    "summary": "The sample is stable enough to produce a short report."
                  }
                }
                """;
        nextResponse = new FakeResponse(200, deepSeekContentStream(aiJson));

        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header(IDEMPOTENCY_HEADER, "coach-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(coachSummaryRequest("coach-charge-success")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usage.totalTokens").value(117));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/credits/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(2));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/credits/ledger")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].type").value("AI_CHARGE"))
                .andExpect(jsonPath("$.data.entries[0].amount").value(-1))
                .andExpect(jsonPath("$.data.entries[0].balanceAfter").value(2));

        AiRunProbe run = readOnlyAiRun(user.userId());
        assertThat(run.status()).isEqualTo("SUCCEEDED");
        assertThat(run.chargedCredits()).isEqualTo(1);
        assertThat(run.refundedCredits()).isZero();
        assertThat(run.totalTokens()).isEqualTo(117);
        assertThat(run.requestHash()).hasSize(64);
        assertThat(run.responseJson()).contains("Objective setup is improving");
        assertThat(run.errorMessage()).isNull();
        assertThat(run.chargeLedgerEntryId()).isNotNull();
        assertThat(run.refundLedgerEntryId()).isNull();
    }

    @Test
    void coachSummaryIdempotencyKeyReplaysSuccessfulResultWithoutCallingDeepSeekOrChargingAgain() throws Exception {
        AuthPayload user = userWithCredits(3);
        String idempotencyKey = "coach-" + UUID.randomUUID();
        String firstAiJson = """
                {
                  "title": "Replay source summary",
                  "summary": "The stored response is returned on the second request.",
                  "verdict": {
                    "label": "Stable",
                    "score": 70,
                    "confidence": "medium",
                    "summary": "The same idempotency key should not call DeepSeek again."
                  }
                }
                """;

        nextResponse = new FakeResponse(200, deepSeekContentStream(firstAiJson));
        String request = coachSummaryRequest("coach-idempotent-1");
        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.report.title").value("Replay source summary"));
        assertThat(capturedRequestCount.get()).isEqualTo(1);

        String unusedAiJson = """
                {
                  "title": "Should not be used",
                  "summary": "A replay must not call the mock server.",
                  "verdict": {
                    "label": "Unused",
                    "score": 10,
                    "confidence": "low",
                    "summary": "This response would prove replay is broken."
                  }
                }
                """;
        nextResponse = new FakeResponse(200, deepSeekContentStream(unusedAiJson));
        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.report.title").value("Replay source summary"));
        assertThat(capturedRequestCount.get()).isEqualTo(1);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/credits/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(2));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/credits/ledger")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].type").value("AI_CHARGE"))
                .andExpect(jsonPath("$.data.entries[1].type").value("ADMIN_ADJUSTMENT"))
                .andExpect(jsonPath("$.data.entries[2]").doesNotExist());
    }

    @Test
    void coachSummaryIdempotencyKeyConflictReturns409WithoutCallingDeepSeekOrChargingAgain() throws Exception {
        AuthPayload user = userWithCredits(3);
        String idempotencyKey = "coach-" + UUID.randomUUID();
        String aiJson = """
                {
                  "title": "Conflict base summary",
                  "summary": "The first request owns the idempotency key.",
                  "verdict": {
                    "label": "Stable",
                    "score": 70,
                    "confidence": "medium",
                    "summary": "A different request body must conflict."
                  }
                }
                """;
        nextResponse = new FakeResponse(200, deepSeekContentStream(aiJson));

        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(coachSummaryRequest("coach-conflict-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(capturedRequestCount.get()).isEqualTo(1);

        nextResponse = new FakeResponse(200, deepSeekContentStream(aiJson));
        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(coachSummaryRequest("coach-conflict-b")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_CONFLICT"));
        assertThat(capturedRequestCount.get()).isEqualTo(1);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/credits/ledger")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].type").value("AI_CHARGE"))
                .andExpect(jsonPath("$.data.entries[1].type").value("ADMIN_ADJUSTMENT"))
                .andExpect(jsonPath("$.data.entries[2]").doesNotExist());
    }

    @Test
    void coachSummaryReservedIdempotencyKeyReturnsInProgressWithoutCallingDeepSeek() throws Exception {
        AuthPayload user = userWithCredits(3);
        String idempotencyKey = "coach-" + UUID.randomUUID();
        String request = coachSummaryRequest("coach-in-progress");
        insertReservedAiRun(user.userId(), idempotencyKey, request);

        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_RUN_IN_PROGRESS"));
        assertThat(capturedRequestCount.get()).isZero();
    }

    @Test
    void coachSummaryRefundedRunReplaysFailureWithoutCallingDeepSeekOrChargingAgain() throws Exception {
        AuthPayload user = userWithCredits(3);
        String idempotencyKey = "coach-" + UUID.randomUUID();
        nextResponse = new FakeResponse(500, "{\"error\":{\"message\":\"bad upstream secret text\"}}");
        String request = coachSummaryRequest("coach-refund");

        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DEEPSEEK_ERROR"));
        assertThat(capturedRequestCount.get()).isEqualTo(1);

        nextResponse = new FakeResponse(200, deepSeekContentStream("""
                {
                  "title": "Should not recover under same key",
                  "summary": "Refunded runs are replayed as failures.",
                  "verdict": {
                    "label": "Unused",
                    "score": 10,
                    "confidence": "low",
                    "summary": "This response should not be requested."
                  }
                }
                """));
        mockMvc.perform(post("/api/analysis/coach-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DEEPSEEK_ERROR"));
        assertThat(capturedRequestCount.get()).isEqualTo(1);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/credits/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(3));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/credits/ledger")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].type").value("AI_REFUND"))
                .andExpect(jsonPath("$.data.entries[0].amount").value(1))
                .andExpect(jsonPath("$.data.entries[1].type").value("AI_CHARGE"))
                .andExpect(jsonPath("$.data.entries[1].amount").value(-1));

        AiRunProbe run = readOnlyAiRun(user.userId());
        assertThat(run.status()).isEqualTo("REFUNDED");
        assertThat(run.chargedCredits()).isEqualTo(1);
        assertThat(run.refundedCredits()).isEqualTo(1);
        assertThat(run.requestHash()).hasSize(64);
        assertThat(run.responseJson()).isNull();
        assertThat(run.errorMessage()).contains("DeepSeek request failed");
        assertThat(run.chargeLedgerEntryId()).isNotNull();
        assertThat(run.refundLedgerEntryId()).isNotNull();
    }

    @Test
    void pregameStreamRequiresBearerTokenBeforeCallingDeepSeek() throws Exception {
        capturedRequest = null;

        mockMvc.perform(post("/api/analysis/pregame/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(pregameStreamRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));

        assertThat(capturedRequest).isNull();
    }

    @Test
    void postgameStreamRejectsWhenCreditBalanceIsInsufficient() throws Exception {
        AuthPayload user = userWithCredits(0);
        capturedRequest = null;

        mockMvc.perform(post("/api/analysis/postgame/stream")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(postgameStreamRequest("review")))
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_CREDITS"));

        assertThat(capturedRequest).isNull();
    }

    @Test
    void postgameStreamChargesOneCreditAndStoresTokenUsageForSuccessfulDeepSeekCall() throws Exception {
        AuthPayload user = userWithCredits(3);
        nextResponse = new FakeResponse(200, """
                data: {"choices":[{"delta":{"content":"structured-json"}}],"model":"deepseek-v4-flash","usage":null}

                data: {"choices":[],"model":"deepseek-v4-flash","usage":{"prompt_tokens":2100,"completion_tokens":140,"total_tokens":2240,"prompt_cache_hit_tokens":0,"prompt_cache_miss_tokens":2100}}

                data: [DONE]

                """);

        MvcResult result = mockMvc.perform(post("/api/analysis/postgame/stream")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(postgameStreamRequest("review")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:usage")))
                .andExpect(content().string(containsString("\"totalTokens\":2240")))
                .andExpect(content().string(containsString("event:done")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/credits/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(2));

        AiRunProbe run = readOnlyAiRun(user.userId());
        assertThat(run.endpoint()).isEqualTo("postgame-stream");
        assertThat(run.status()).isEqualTo("SUCCEEDED");
        assertThat(run.chargedCredits()).isEqualTo(1);
        assertThat(run.refundedCredits()).isZero();
        assertThat(run.totalTokens()).isEqualTo(2240);
        assertThat(run.requestHash()).hasSize(64);
        assertThat(run.responseJson()).isNull();
        assertThat(run.errorMessage()).isNull();
        assertThat(run.chargeLedgerEntryId()).isNotNull();
        assertThat(run.refundLedgerEntryId()).isNull();
    }

    @Test
    void postgameStreamRefundsCreditWhenDeepSeekFails() throws Exception {
        AuthPayload user = userWithCredits(3);
        nextResponse = new FakeResponse(500, "{\"error\":{\"message\":\"bad upstream secret text\"}}");

        MvcResult result = mockMvc.perform(post("/api/analysis/postgame/stream")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(postgameStreamRequest("review")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString("HTTP 500")))
                .andExpect(content().string(not(containsString("bad upstream secret text"))));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/credits/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(3));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/credits/ledger")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].type").value("AI_REFUND"))
                .andExpect(jsonPath("$.data.entries[0].amount").value(1))
                .andExpect(jsonPath("$.data.entries[1].type").value("AI_CHARGE"))
                .andExpect(jsonPath("$.data.entries[1].amount").value(-1));

        AiRunProbe run = readOnlyAiRun(user.userId());
        assertThat(run.endpoint()).isEqualTo("postgame-stream");
        assertThat(run.status()).isEqualTo("REFUNDED");
        assertThat(run.chargedCredits()).isEqualTo(1);
        assertThat(run.refundedCredits()).isEqualTo(1);
        assertThat(run.requestHash()).hasSize(64);
        assertThat(run.responseJson()).isNull();
        assertThat(run.errorMessage()).contains("DeepSeek request failed");
        assertThat(run.chargeLedgerEntryId()).isNotNull();
        assertThat(run.refundLedgerEntryId()).isNotNull();
    }

    @Test
    void pregameStreamUsesDeepSeekWhenEnabled() throws Exception {
        AuthPayload user = userWithCredits(3);
        nextResponse = new FakeResponse(200, """
                data: {"choices":[{"delta":{"content":"{\\"playerKey\\":\\"puuid:ally-puuid\\",\\"label\\":\\"self\\","}}]}

                data: {"choices":[{"delta":{"content":"\\"tone\\":\\"stable\\",\\"text\\":\\"jungler-state-ready early-path-mid-bot\\"}\\n"}}]}

                data: [DONE]

                """);
        String request = """
                {
                  "mode": "teammate",
                  "queueId": 420,
                  "allyTeamTags": ["当前snapshot时间：2026-05-22T13:00:00.000Z。模式：单双排。用户ID：W#1234。阵营：我方。\\n\\nW#1234（用户） 战绩状态：正常。当前位置：打野，tag：高胜率，场均击杀/死亡/助攻：7.0/3.0/8.0，平均KDA：3.1，胜率：55.0%，伤转：152.3%，样本数：20，参团率：12.5%，最近对局：德邦总管 打野 胜 7/3/8。"],
                  "enemyTeamTags": [],
                  "snapshotSchemaVersion": "gaming_ai_input_snapshot.v2",
                  "snapshot": {
                    "schemaVersion": "gaming_ai_input_snapshot.v2",
                    "mode": "teammate",
                    "teammateSnapshot": {
                      "side": "ally",
                      "players": [{"key": "puuid:ally-puuid", "isSelf": true}]
                    },
                    "opponentSnapshot": {
                      "side": "enemy",
                      "players": []
                    }
                  }
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/analysis/pregame/stream")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(request))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:start")))
                .andExpect(content().string(containsString("event:player_insight")))
                .andExpect(content().string(containsString("puuid:ally-puuid")))
                .andExpect(content().string(containsString("jungler-state-ready")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(not(containsString("test-secret"))));

        assertThat(capturedRequest).isNotNull();
        assertThat(capturedRequest.path()).isEqualTo("/chat/completions");
        assertThat(capturedRequest.authorization()).isEqualTo("Bearer test-secret");
        JsonNode body = OBJECT_MAPPER.readTree(capturedRequest.body());
        assertThat(body.get("model").asText()).isEqualTo("deepseek-v4-flash");
        assertThat(body.get("stream").asBoolean()).isTrue();
        assertThat(body.get("stream_options").get("include_usage").asBoolean()).isTrue();
        JsonNode messagesNode = body.get("messages");
        String messages = messagesNode.toString();
        String userMessage = messagesNode.get(1).get("content").asText();
        assertThat(messages).contains("gaming_ai_input_snapshot.v2");
        assertThat(messages).contains("player_insight_result.v1");
        assertThat(messages).contains("NDJSON");
        assertThat(messages).contains("allowedPlayerKeys");
        assertThat(messages).contains("puuid:ally-puuid");
        assertThat(messages).contains("当前snapshot时间");
        assertThat(messages).contains("W#1234（用户）");
        assertThat(messages).doesNotContain("snapshotJson");
        assertThat(messages).doesNotContain("teammateSnapshot");
        assertThat(userMessage).contains("队友模式");
        assertThat(userMessage).contains("用户本人");
        assertThat(userMessage).contains("非用户队友只做状态总结");
        assertThat(userMessage).contains("不给操作建议、不写配合/规避方案");
        assertThat(userMessage).contains("用户本人写状态总结 + 一句轻量本局思路提醒");
        assertThat(userMessage).contains("label 只能从：上等马、中等马、下等马、？？？马");
        assertThat(userMessage).contains("上等马 -> carry");
        assertThat(userMessage).contains("？？？马 -> unknown");
        assertThat(userMessage).doesNotContain("普通队友写状态与配合/规避建议");
        assertThat(userMessage).doesNotContain("保守配合建议");
        assertThat(userMessage).doesNotContain("对手模式");
        assertThat(userMessage).doesNotContain("威胁点或破绽");
        assertThat(userMessage).doesNotContain("代中代");
        assertThat(userMessage).doesNotContain("突破口");
    }

    @Test
    void pregameOpponentPromptUsesOpponentOnlyRules() throws Exception {
        AuthPayload user = userWithCredits(3);
        nextResponse = new FakeResponse(200, """
                data: {"choices":[{"delta":{"content":"{\\"playerKey\\":\\"puuid:enemy-puuid\\",\\"label\\":\\"threat\\","}}]}

                data: {"choices":[{"delta":{"content":"\\"tone\\":\\"risk\\",\\"text\\":\\"watch-level-three-pathing\\"}\\n"}}]}

                data: [DONE]

                """);
        String request = """
                {
                  "mode": "opponent",
                  "queueId": 420,
                  "allyTeamTags": ["ally snapshot should not be selected"],
                  "enemyTeamTags": ["当前snapshot时间：2026-05-22T13:00:00.000Z。模式：单双排。用户ID：W#1234。阵营：敌方。\\n\\nEnemy#1234 战绩状态：正常。当前位置：打野，tag：高胜率。"],
                  "snapshotSchemaVersion": "gaming_ai_input_snapshot.v2",
                  "snapshot": {
                    "schemaVersion": "gaming_ai_input_snapshot.v2",
                    "mode": "opponent",
                    "teammateSnapshot": {
                      "side": "ally",
                      "players": [{"key": "puuid:ally-puuid", "isSelf": true}]
                    },
                    "opponentSnapshot": {
                      "side": "enemy",
                      "players": [{"key": "puuid:enemy-puuid", "isSelf": false}]
                    }
                  }
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/analysis/pregame/stream")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(request))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:player_insight")))
                .andExpect(content().string(containsString("puuid:enemy-puuid")))
                .andExpect(content().string(containsString("watch-level-three-pathing")))
                .andExpect(content().string(not(containsString("test-secret"))));

        JsonNode body = OBJECT_MAPPER.readTree(capturedRequest.body());
        JsonNode messagesNode = body.get("messages");
        String messages = messagesNode.toString();
        String userMessage = messagesNode.get(1).get("content").asText();
        assertThat(messages).contains("player_insight_result.v1");
        assertThat(messages).contains("puuid:enemy-puuid");
        assertThat(messages).contains("Enemy#1234");
        assertThat(messages).doesNotContain("puuid:ally-puuid");
        assertThat(messages).doesNotContain("ally snapshot should not be selected");
        assertThat(userMessage).contains("对手模式");
        assertThat(userMessage).contains("每个敌方玩家只做状态总结");
        assertThat(userMessage).contains("不给用户操作建议");
        assertThat(userMessage).contains("不写前期注意点、针对方案");
        assertThat(userMessage).contains("label 只能从：代中代、小代、npc、突破口、？？？");
        assertThat(userMessage).contains("代中代 -> carry");
        assertThat(userMessage).contains("突破口 -> weak");
        assertThat(userMessage).contains("？？？ -> unknown");
        assertThat(userMessage).doesNotContain("威胁点或破绽，以及前期注意点");
        assertThat(userMessage).doesNotContain("保守注意点");
        assertThat(userMessage).doesNotContain("队友模式");
        assertThat(userMessage).doesNotContain("用户本人");
        assertThat(userMessage).doesNotContain("上等马");
        assertThat(userMessage).doesNotContain("？？？马");
    }

    @Test
    void postgameStreamUsesDeepSeekWhenEnabled() throws Exception {
        AuthPayload user = userWithCredits(3);
        String request = """
                {
                  "mode": "review",
                  "snapshotSchemaVersion": "postgame_ai_input_snapshot.v3",
                  "snapshot": {
                    "schemaVersion": "postgame_ai_input_snapshot.v3",
                    "analysisType": "postgame",
                    "analysisBrief": {
                      "schemaVersion": "postgame_analysis_brief.v1",
                      "language": "zh-CN",
                      "matchFacts": ["模式：单双排位；时间：24:55；结果：我方获胜。"],
                      "teamFacts": ["我方击杀 28，敌方击杀 24。"],
                      "playerFacts": [
                        "【你｜我方打野｜凯隐】7/5/11，KDA 3.6。",
                        "【我方上单｜盖伦】2/8/4。",
                        "【我方中单｜阿狸】8/3/9。",
                        "【我方下路｜金克丝】6/4/7。",
                        "【我方辅助｜洛】1/4/17。",
                        "【敌方上单｜奎桑提】5/4/8。",
                        "【敌方打野｜盲僧】4/6/7。",
                        "【敌方中单｜维克托】7/5/6。",
                        "【敌方下路｜伊泽瑞尔】6/6/4。",
                        "【敌方辅助｜璐璐】2/7/13。"
                      ],
                      "timelineFacts": [
                        "15:30 我方拿下小龙。"
                      ],
                      "dataQualityFacts": []
                    }
                  }
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/analysis/postgame/stream")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(request))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("deepseek-stream-advice")))
                .andExpect(content().string(containsString("event:done")));

        JsonNode body = OBJECT_MAPPER.readTree(capturedRequest.body());
        JsonNode messagesNode = body.get("messages");
        String messages = messagesNode.toString();
        String systemMessage = messagesNode.get(0).get("content").asText();
        String userMessage = messagesNode.get(1).get("content").asText();
        assertThat(messages).contains("postgame_ai_input_snapshot.v3");
        assertThat(messages).contains("postgame_review_result.v1");
        assertThat(messages).contains("夯", "顶级", "人上人", "NPC", "拉完了");
        assertThat(messages).contains("levels", "summary", "playerRef", "phrase");
        assertThat(messages).contains("只输出 JSON");
        assertThat(systemMessage).contains("RP指数", "0-10", "当前 postgame snapshot 只提供每名玩家的终局 RP");
        assertThat(systemMessage).doesNotContain("每3分钟", "praise 模式", "postgame_praise_result.v1");
        assertThat(systemMessage).doesNotContain("RP标签");
        assertThat(userMessage).contains("snapshotText:");
        assertThat(userMessage).contains("对局信息：");
        assertThat(userMessage).contains("- 模式：单双排位；时间：24:55；结果：我方获胜。");
        assertThat(userMessage).contains("时间轴与 RP：");
        assertThat(userMessage).doesNotContain("每3分钟");
        assertThat(userMessage).doesNotContain("数据质量：");
        assertThat(userMessage).doesNotContain("snapshotJson:");
        assertThat(userMessage).doesNotContain("\"analysisBrief\"");
    }

    @Test
    void postgamePraisePromptProtectsCurrentPlayerWithoutBlindPraise() throws Exception {
        AuthPayload user = userWithCredits(3);
        String request = """
                {
                  "mode": "praise",
                  "snapshotSchemaVersion": "postgame_ai_input_snapshot.v3",
                  "snapshot": {
                    "schemaVersion": "postgame_ai_input_snapshot.v3",
                    "analysisType": "postgame",
                    "analysisBrief": {
                      "schemaVersion": "postgame_analysis_brief.v1",
                      "language": "zh-CN",
                      "matchFacts": ["本局为单双排排位，我方失败。"],
                      "teamFacts": ["我方击杀 24，敌方击杀 28。"],
                      "playerFacts": [
                        "【你｜我方打野｜凯隐】7/5/11，KDA 3.6，参团率75%，最终装备：黑切、死亡之舞，符文：主宰/精密，主系：电刑、猛然冲击、眼球收集器、寻宝猎人，副系：凯旋、致命一击。",
                        "【我方上单｜盖伦】2/8/4。",
                        "【我方中单｜阿狸】8/3/9。",
                        "【我方下路｜金克丝】6/4/7。",
                        "【我方辅助｜洛】1/4/17。",
                        "【敌方上单｜奎桑提】5/4/8。",
                        "【敌方打野｜盲僧】4/6/7。",
                        "【敌方中单｜维克托】7/5/6。",
                        "【敌方下路｜伊泽瑞尔】6/6/4。",
                        "【敌方辅助｜璐璐】2/7/13。"
                      ],
                      "timelineFacts": ["16:30 我方拿下小龙。"],
                      "dataQualityFacts": ["timeline 可用。"]
                    }
                  }
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/analysis/postgame/stream")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(request))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("deepseek-stream-advice")))
                .andExpect(content().string(containsString("event:done")));

        JsonNode body = OBJECT_MAPPER.readTree(capturedRequest.body());
        JsonNode messagesNode = body.get("messages");
        String messages = messagesNode.toString();
        String systemMessage = messagesNode.get(0).get("content").asText();
        String userMessage = messagesNode.get(1).get("content").asText();
        assertThat(messages).contains("postgame_praise_result.v1");
        assertThat(systemMessage).contains("RP指数", "0-10", "当前 postgame snapshot 只提供每名玩家的终局 RP");
        assertThat(systemMessage).doesNotContain("每3分钟", "review 模式", "postgame_review_result.v1");
        assertThat(messages).contains("headline", "paragraphs");
        assertThat(messages).contains("只围绕当前用户");
        assertThat(messages).contains("带有【你｜");
        assertThat(messages).contains("赛后爽文嘴替");
        assertThat(messages).contains("不是教练、不是分析师、不是熟人聊天");
        assertThat(messages).contains("可以夸张、护短、有情绪");
        assertThat(messages).contains("每一句夸都必须能被 snapshot 中的事实支撑");
        assertThat(messages).contains("不要写成数据报告");
        assertThat(messages).contains("不要机械罗列 KDA、伤害、参团率");
        assertThat(messages).contains("只挑最能撑起情绪的关键事实");
        assertThat(messages).contains("不要频繁使用“兄弟”“哥们”“牛逼”“赢麻了”");
        assertThat(messages).contains("像赛后弹幕精选 + 嘴替短文");
        assertThat(messages).contains("正文最多两段");
        assertThat(messages).contains("不能有小标题、列表、编号、项目符号");
        assertThat(messages).contains("不要写“总结来说”“从数据看”“首先/其次/最后”");
        assertThat(messages).contains("赢局：大力吹");
        assertThat(messages).contains("用户是胜利核心、节奏发动机、关键输出/承伤/牵制点");
        assertThat(messages).contains("输局：强力护短");
        assertThat(messages).contains("局势、阵容、节奏、资源交换多因素结果");
        assertThat(messages).contains("可以点名队友或对手的英雄和位置");
        assertThat(messages).contains("不要暴露召唤师名");
        assertThat(messages).contains("不能把百分比自动写成全队最高、第一、MVP");
        assertThat(messages).contains("除非 playerFacts、teamFacts、timelineFacts 或 snapshot 明确给出排名");
        assertThat(messages).contains("例如只有伤害占比 19%");
        assertThat(messages).contains("优先引用 playerFacts 里的“排名：”和“高光：”事实");
        assertThat(messages).contains("个人镀层");
        assertThat(messages).contains("优先围绕【你】所选英雄和位置来解释");
        assertThat(messages).contains("这把你就是主角");
        assertThat(messages).contains("你已经把能做的打满了");
        assertThat(messages).contains("AI 自拟");
        assertThat(messages).contains("headline 像一句赛后爽文标题");
        assertThat(messages).contains("短、有劲、贴英雄和胜负");
        assertThat(messages).contains("headline 不要使用固定模板“这把真不能全怪你”");
        assertThat(messages).contains("赢局不要用“不背锅”“不能怪你”类标题");
        assertThat(messages).contains("paragraphs 必须是 1 到 2 段正文");
        assertThat(messages).contains("不设硬性字数");
        assertThat(messages).contains("不要为了凑字灌水，也不要因为怕长只写成几句");
        assertThat(messages).contains("必须结合【你】的英雄、位置、胜负");
        assertThat(messages).contains("不要输出教学建议");
        assertThat(messages).contains("不要辱骂");
        assertThat(messages).doesNotContain("像一个真的懂英雄联盟、也懂玩家心态的老玩家朋友");
        assertThat(messages).doesNotContain("必须结合【你】的英雄、位置、胜负、KDA、参团率、伤害/经济/承伤占比、视野、补刀、最终装备、详细符文");
        assertThat(messages).doesNotContain("判断方向：");
        assertThat(messages).doesNotContain("总长 160 到 300 个中文字符");
        assertThat(messages).doesNotContain("不要输出 JSON");
        assertThat(messages).doesNotContain("\"headline\": \"这把真不能全怪你\"");
        assertThat(userMessage).doesNotContain("postgame_review_result.v1");
    }

    @Test
    void postgameStreamEmitsTokenUsageWhenDeepSeekIncludesUsageChunk() throws Exception {
        AuthPayload user = userWithCredits(3);
        nextResponse = new FakeResponse(200, """
                data: {"choices":[{"delta":{"content":"structured-json"}}],"model":"deepseek-v4-flash","usage":null}

                data: {"choices":[],"model":"deepseek-v4-flash","usage":{"prompt_tokens":2100,"completion_tokens":140,"total_tokens":2240,"prompt_cache_hit_tokens":0,"prompt_cache_miss_tokens":2100}}

                data: [DONE]

                """);

        MvcResult result = mockMvc.perform(post("/api/analysis/postgame/stream")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "mode": "review",
                                  "snapshotSchemaVersion": "postgame_ai_input_snapshot.v3",
                                  "snapshot": {
                                    "schemaVersion": "postgame_ai_input_snapshot.v3",
                                    "analysisType": "postgame",
                                    "analysisBrief": {
                                      "schemaVersion": "postgame_analysis_brief.v1",
                                      "language": "zh-CN",
                                      "matchFacts": ["ranked postgame"],
                                      "teamFacts": [],
                                      "playerFacts": [],
                                      "timelineFacts": [],
                                      "dataQualityFacts": []
                                    }
                                  }
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:usage")))
                .andExpect(content().string(containsString("\"model\":\"deepseek-v4-flash\"")))
                .andExpect(content().string(containsString("\"promptTokens\":2100")))
                .andExpect(content().string(containsString("\"completionTokens\":140")))
                .andExpect(content().string(containsString("\"promptCacheHitTokens\":0")))
                .andExpect(content().string(containsString("\"promptCacheMissTokens\":2100")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(not(containsString("test-secret"))));
    }

    @Test
    void deepSeekHttpErrorReturnsErrorSseWithoutProviderBody() throws Exception {
        AuthPayload user = userWithCredits(3);
        nextResponse = new FakeResponse(500, "{\"error\":{\"message\":\"bad upstream secret text\"}}");

        MvcResult result = mockMvc.perform(post("/api/analysis/postgame/stream")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "mode": "review",
                                  "snapshotSchemaVersion": "postgame_ai_input_snapshot.v1",
                                  "snapshot": {}
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString("HTTP 500")))
                .andExpect(content().string(not(containsString("bad upstream secret text"))))
                .andExpect(content().string(not(containsString("test-secret"))));
    }

    private static void ensureServer() throws IOException {
        if (server != null) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", DeepSeekAnalysisControllerTest::handleChatCompletions);
        server.start();
    }

    private static void handleChatCompletions(HttpExchange exchange) throws IOException {
        capturedRequestCount.incrementAndGet();
        capturedRequest = new CapturedRequest(
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
        );
        byte[] bytes = nextResponse.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", nextResponse.status() == 200 ? "text/event-stream" : "application/json");
        exchange.sendResponseHeaders(nextResponse.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String deepSeekContentStream(String content) throws IOException {
        String deltaChunk = OBJECT_MAPPER.writeValueAsString(Map.of(
                "choices", List.of(Map.of("delta", Map.of("content", content))),
                "model", "deepseek-v4-flash"
        ));
        String usageChunk = OBJECT_MAPPER.writeValueAsString(Map.of(
                "choices", List.of(),
                "model", "deepseek-v4-flash",
                "usage", Map.of(
                        "prompt_tokens", 80,
                        "completion_tokens", 37,
                        "total_tokens", 117,
                        "prompt_cache_hit_tokens", 0,
                        "prompt_cache_miss_tokens", 80
                )
        ));
        return "data: " + deltaChunk + "\n\n"
                + "data: " + usageChunk + "\n\n"
                + "data: [DONE]\n\n";
    }

    private AuthPayload userWithCredits(int credits) throws Exception {
        AuthPayload user = registerUser();
        if (credits > 0) {
            AuthUser admin = createAdminUser();
            creditService.adjustByAdmin(
                    admin,
                    new AdminCreditGrantRequest(user.userId(), credits, "test credits"),
                    "grant-" + UUID.randomUUID()
            );
        }
        return user;
    }

    private AuthPayload registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Secret123!",
                                  "displayName": "RankPeek User"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).get("data");
        return new AuthPayload(data.get("user").get("id").asLong(), data.get("accessToken").asText());
    }

    private AuthUser createAdminUser() {
        return authRepository.upsertInitialAdmin(
                "admin-" + UUID.randomUUID() + "@example.com",
                "RankPeek Admin",
                passwordService.hash("Admin123!"),
                Instant.now()
        );
    }

    private AiRunProbe readOnlyAiRun(long userId) {
        return jdbcTemplate.queryForObject(
                """
                        select endpoint, status, charged_credits, refunded_credits, total_tokens,
                            request_hash, response_json, error_message,
                            charge_ledger_entry_id, refund_ledger_entry_id
                        from ai_analysis_runs
                        where user_id = ?
                        order by id desc
                        limit 1
                        """,
                (rs, rowNum) -> new AiRunProbe(
                        rs.getString("endpoint"),
                        rs.getString("status"),
                        rs.getInt("charged_credits"),
                        rs.getInt("refunded_credits"),
                        rs.getLong("total_tokens"),
                        rs.getString("request_hash"),
                        rs.getString("response_json"),
                        rs.getString("error_message"),
                        nullableLong(rs.getObject("charge_ledger_entry_id"), rs.getLong("charge_ledger_entry_id")),
                        nullableLong(rs.getObject("refund_ledger_entry_id"), rs.getLong("refund_ledger_entry_id"))
                ),
                userId
        );
    }

    private void insertReservedAiRun(long userId, String idempotencyKey, String request) {
        jdbcTemplate.update(
                """
                        insert into ai_analysis_runs (
                            user_id, endpoint, provider, model, status, idempotency_key,
                            charged_credits, request_hash, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                        """,
                userId,
                "coach-summary",
                "deepseek",
                "deepseek-v4-flash",
                "RESERVED",
                idempotencyKey,
                1,
                requestHash(request)
        );
    }

    private static String bearer(AuthPayload user) {
        return "Bearer " + user.accessToken();
    }

    private static String coachSummaryRequest(String inputHash) {
        return """
                {
                  "inputHash": "%s",
                  "snapshotSchemaVersion": "coach_summary_input_snapshot.v2",
                  "promptVersion": "coach_summary.prompt.v2",
                  "dataQualityConfidence": "medium",
                  "systemPrompt": "system coach prompt",
                  "userPrompt": "{\\"currentSnapshotText\\":\\"ranked sample facts\\"}"
                }
                """.formatted(inputHash);
    }

    private static String pregameStreamRequest() {
        return """
                {
                  "mode": "teammate",
                  "queueId": 420,
                  "allyTeamTags": ["ally test facts"],
                  "enemyTeamTags": [],
                  "snapshotSchemaVersion": "gaming_ai_input_snapshot.v2",
                  "snapshot": {
                    "schemaVersion": "gaming_ai_input_snapshot.v2",
                    "mode": "teammate",
                    "teammateSnapshot": {
                      "side": "ally",
                      "players": [{"key": "puuid:ally-puuid", "isSelf": true}]
                    },
                    "opponentSnapshot": {
                      "side": "enemy",
                      "players": []
                    }
                  }
                }
                """;
    }

    private static String postgameStreamRequest(String mode) {
        return """
                {
                  "mode": "%s",
                  "snapshotSchemaVersion": "postgame_ai_input_snapshot.v3",
                  "snapshot": {
                    "schemaVersion": "postgame_ai_input_snapshot.v3",
                    "analysisType": "postgame",
                    "analysisBrief": {
                      "schemaVersion": "postgame_analysis_brief.v1",
                      "language": "zh-CN",
                      "matchFacts": ["ranked postgame"],
                      "teamFacts": [],
                      "playerFacts": [],
                      "timelineFacts": [],
                      "dataQualityFacts": []
                    }
                  }
                }
                """.formatted(mode);
    }

    private static String requestHash(String request) {
        try {
            String canonical = OBJECT_MAPPER.writeValueAsString(OBJECT_MAPPER.readTree(request));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash request", exception);
        }
    }

    private static Long nullableLong(Object marker, long value) {
        return marker == null ? null : value;
    }

    private record CapturedRequest(String path, String authorization, String body) {
    }

    private record FakeResponse(int status, String body) {
    }

    private record AuthPayload(long userId, String accessToken) {
    }

    private record AiRunProbe(
            String endpoint,
            String status,
            int chargedCredits,
            int refundedCredits,
            long totalTokens,
            String requestHash,
            String responseJson,
            String errorMessage,
            Long chargeLedgerEntryId,
            Long refundLedgerEntryId
    ) {
    }
}
