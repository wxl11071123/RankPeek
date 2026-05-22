package io.rankpeek.server.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class DeepSeekAnalysisControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static HttpServer server;
    private static volatile CapturedRequest capturedRequest;
    private static volatile FakeResponse nextResponse = new FakeResponse(200, """
            data: {"choices":[{"delta":{"content":"deepseek-stream-advice"}}]}

            data: [DONE]

            """);

    @Autowired
    private MockMvc mockMvc;

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
        nextResponse = new FakeResponse(200, """
                data: {"choices":[{"delta":{"content":"deepseek-stream-advice"}}]}

                data: [DONE]

                """);
    }

    @Test
    void pregameStreamUsesDeepSeekWhenEnabled() throws Exception {
        String request = """
                {
                  "mode": "teammate",
                  "queueId": 420,
                  "allyTeamTags": ["ally | W#1234 | champion=141 | status=NORMAL | sample=20"],
                  "enemyTeamTags": ["enemy | Hidden#CN1 | champion=64 | status=PRIVATE"],
                  "snapshotSchemaVersion": "gaming_ai_input_snapshot.v1",
                  "snapshot": {
                    "schemaVersion": "gaming_ai_input_snapshot.v1",
                    "mode": "teammate",
                    "allyTeam": [{"key": "puuid:ally-puuid", "displayName": "W#1234"}],
                    "enemyTeam": [{"key": "name:Hidden#CN1:64", "displayName": "Hidden#CN1"}]
                  }
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/analysis/pregame/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(request))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:start")))
                .andExpect(content().string(containsString("event:section")))
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("deepseek-stream-advice")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(not(containsString("test-secret"))));

        assertThat(capturedRequest).isNotNull();
        assertThat(capturedRequest.path()).isEqualTo("/chat/completions");
        assertThat(capturedRequest.authorization()).isEqualTo("Bearer test-secret");
        JsonNode body = OBJECT_MAPPER.readTree(capturedRequest.body());
        assertThat(body.get("model").asText()).isEqualTo("deepseek-v4-flash");
        assertThat(body.get("stream").asBoolean()).isTrue();
        assertThat(body.get("stream_options").get("include_usage").asBoolean()).isTrue();
        assertThat(body.get("messages").toString()).contains("gaming_ai_input_snapshot.v1");
        assertThat(body.get("messages").toString()).contains("ally | W#1234");
    }

    @Test
    void postgameStreamUsesDeepSeekWhenEnabled() throws Exception {
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
                      "matchFacts": ["本局为单双排排位。"],
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
                      "timelineFacts": ["15:30 我方拿下小龙。"],
                      "dataQualityFacts": ["timeline 可用。"]
                    }
                  }
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/analysis/postgame/stream")
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
        String messages = body.get("messages").toString();
        assertThat(messages).contains("postgame_ai_input_snapshot.v3");
        assertThat(messages).contains("postgame_review_result.v1");
        assertThat(messages).contains("夯", "顶级", "人上人", "NPC", "拉完了");
        assertThat(messages).contains("levels", "summary", "playerRef", "phrase");
        assertThat(messages).contains("只输出 JSON");
    }

    @Test
    void postgamePraisePromptProtectsCurrentPlayerWithoutBlindPraise() throws Exception {
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
        String userMessage = messagesNode.get(1).get("content").asText();
        assertThat(messages).contains("postgame_praise_result.v1");
        assertThat(messages).contains("headline", "paragraphs");
        assertThat(messages).contains("只围绕当前用户");
        assertThat(messages).contains("带有【你｜");
        assertThat(messages).contains("像一个真的懂英雄联盟、也懂玩家心态的老玩家朋友");
        assertThat(messages).contains("正文最多两段");
        assertThat(messages).contains("不能有小标题、列表、编号、项目符号");
        assertThat(messages).contains("不要写“总结来说”“从数据看”“首先/其次/最后”");
        assertThat(messages).contains("赢了要从多个角度大力吹");
        assertThat(messages).contains("输了要尽量帮玩家合理甩锅");
        assertThat(messages).contains("可以点名队友或对手的英雄和位置");
        assertThat(messages).contains("不要暴露召唤师名");
        assertThat(messages).contains("不能把百分比自动写成全队最高、第一、MVP");
        assertThat(messages).contains("除非 playerFacts、teamFacts、timelineFacts 或 snapshot 明确给出排名");
        assertThat(messages).contains("例如只有伤害占比 19%");
        assertThat(messages).contains("优先引用 playerFacts 里的“排名：”和“高光：”事实");
        assertThat(messages).contains("个人镀层");
        assertThat(messages).contains("优先围绕【你】所选英雄和位置来解释");
        assertThat(messages).contains("大方鼓励和吹嘘，可以写得爽一点");
        assertThat(messages).contains("从其他英雄、其他位置、团队节奏、资源交换或阵容执行里找原因");
        assertThat(messages).contains("明确表达玩家已经尽力，不让用户背锅");
        assertThat(messages).contains("往胜利的基石、关键拼图、关键人物方向写");
        assertThat(messages).contains("帮用户甩锅，不让用户吃一点压力");
        assertThat(messages).contains("AI 自拟");
        assertThat(messages).contains("headline 像一句醒目的夸夸标题");
        assertThat(messages).contains("headline 不要使用固定模板“这把真不能全怪你”");
        assertThat(messages).contains("赢局不要用“不背锅”“不能怪你”类标题");
        assertThat(messages).contains("paragraphs 必须是 1 到 2 段正文");
        assertThat(messages).contains("表现好并且赢了");
        assertThat(messages).contains("不是混赢");
        assertThat(messages).contains("表现好但输了");
        assertThat(messages).contains("数据不好看但赢了");
        assertThat(messages).contains("数据不好看且输了");
        assertThat(messages).contains("必须结合【你】的英雄、位置、胜负、KDA");
        assertThat(messages).contains("不要输出教学建议");
        assertThat(messages).contains("不要辱骂");
        assertThat(messages).doesNotContain("不要输出 JSON");
        assertThat(messages).doesNotContain("\"headline\": \"这把真不能全怪你\"");
        assertThat(userMessage).doesNotContain("postgame_review_result.v1");
    }

    @Test
    void postgameStreamEmitsTokenUsageWhenDeepSeekIncludesUsageChunk() throws Exception {
        nextResponse = new FakeResponse(200, """
                data: {"choices":[{"delta":{"content":"structured-json"}}],"model":"deepseek-v4-flash","usage":null}

                data: {"choices":[],"model":"deepseek-v4-flash","usage":{"prompt_tokens":2100,"completion_tokens":140,"total_tokens":2240,"prompt_cache_hit_tokens":0,"prompt_cache_miss_tokens":2100}}

                data: [DONE]

                """);

        MvcResult result = mockMvc.perform(post("/api/analysis/postgame/stream")
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
        nextResponse = new FakeResponse(500, "{\"error\":{\"message\":\"bad upstream secret text\"}}");

        MvcResult result = mockMvc.perform(post("/api/analysis/postgame/stream")
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

    private record CapturedRequest(String path, String authorization, String body) {
    }

    private record FakeResponse(int status, String body) {
    }
}
