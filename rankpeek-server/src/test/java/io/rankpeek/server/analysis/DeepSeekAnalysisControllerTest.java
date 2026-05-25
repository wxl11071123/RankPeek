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
