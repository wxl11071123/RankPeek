package io.rankpeek.server.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.server.ai.DeepSeekAiException;
import io.rankpeek.server.ai.DeepSeekAiProperties;
import io.rankpeek.server.ai.DeepSeekChatClient;
import io.rankpeek.server.ai.DeepSeekChatMessage;
import io.rankpeek.server.ai.DeepSeekTokenUsage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekAnalysisStreamer {

    private static final long STREAM_TIMEOUT_MS = 60_000L;

    private final DeepSeekAiProperties properties;
    private final DeepSeekChatClient chatClient;
    private final ObjectMapper objectMapper;

    public DeepSeekAnalysisStreamer(
            DeepSeekAiProperties properties,
            DeepSeekChatClient chatClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.deepSeekEnabled();
    }

    public SseEmitter streamPregame(PregameAnalysisRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Thread.ofVirtual().start(() -> stream(emitter, buildPregameMessages(request)));
        return emitter;
    }

    public SseEmitter streamPostgame(PostgameAnalysisRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Thread.ofVirtual().start(() -> stream(emitter, buildPostgameMessages(request)));
        return emitter;
    }

    private void stream(SseEmitter emitter, List<DeepSeekChatMessage> messages) {
        try {
            sendEvent(emitter, "start", "RankPeek DeepSeek stream started");
            sendEvent(emitter, "section", "DeepSeek 分析");
            chatClient.streamChat(
                    properties,
                    messages,
                    delta -> sendDelta(emitter, delta),
                    usage -> sendUsage(emitter, usage)
            );
            sendEvent(emitter, "done", "done");
            emitter.complete();
        } catch (DeepSeekAiException exception) {
            sendError(emitter, exception.getMessage());
        } catch (Exception exception) {
            sendError(emitter, "DeepSeek stream failed");
        }
    }

    private List<DeepSeekChatMessage> buildPregameMessages(PregameAnalysisRequest request) {
        return List.of(
                new DeepSeekChatMessage(
                        "system",
                        """
                                你是 RankPeek 的英雄联盟赛前分析助手。只根据用户提供的 snapshot 和标签分析，不臆造外部战绩。
                                用中文输出，保持简洁、可执行，重点说明风险、配合点和本局建议。
                                """
                ),
                new DeepSeekChatMessage(
                        "user",
                        """
                                请做赛前分析。
                                mode: %s
                                snapshotSchemaVersion: %s
                                allyTeamTags:
                                %s
                                enemyTeamTags:
                                %s
                                snapshotJson:
                                %s
                                """.formatted(
                                nullToEmpty(request.mode()),
                                nullToEmpty(request.snapshotSchemaVersion()),
                                formatLines(request.allyTeamTags()),
                                formatLines(request.enemyTeamTags()),
                                toJson(request.snapshot())
                        )
                )
        );
    }

    private List<DeepSeekChatMessage> buildPostgameMessages(PostgameAnalysisRequest request) {
        String mode = "praise".equalsIgnoreCase(nullToEmpty(request.mode())) ? "praise" : "review";
        String task = "praise".equals(mode)
                ? """
                        请只输出 JSON，不要 Markdown、代码块或额外解释。JSON schemaVersion 固定为 postgame_praise_result.v1。
                        输出结构：
                        {
                          "schemaVersion": "postgame_praise_result.v1",
                          "headline": "AI 自拟一句醒目的夸夸标题",
                          "paragraphs": [
                            "第一段正文",
                            "第二段正文，可省略"
                          ]
                        }
                        核心原则：像一个真的懂英雄联盟、也懂玩家心态的老玩家朋友，护短但不造假，安慰但不要无脑吹。
                        目标：
                        - 只围绕当前用户，也就是 playerFacts 中带有【你｜...】的玩家进行分析。
                        - 必须结合【你】的英雄、位置、胜负、KDA、参团率、伤害/经济/承伤占比、视野、补刀、最终装备、详细符文、15 分钟经济差和 timeline 关键事实。
                        - 优先围绕【你】所选英雄和位置来解释表现，比如这个英雄在这个位置承担什么职责、这局为什么难打或为什么有价值。
                        - 输出中文，语气像懂你的老玩家，不像 AI 助手、客服、教练报告或战术复盘。
                        - 标题和正文都要自然、有情绪、有玩家感，但不能编造不存在的数据。
                        - 可以点名队友或对手的英雄和位置，用来描述局势或帮用户说话；不要暴露召唤师名，不要把某个玩家钉成战犯，也不要辱骂。
                        - 不能把百分比自动写成全队最高、第一、MVP、最强、唯一支柱等排名结论，除非 playerFacts、teamFacts、timelineFacts 或 snapshot 明确给出排名。
                        - 例如只有伤害占比 19%、经济转化 20% 这类百分比时，只能说“有输出/经济贡献”，不能说“全队最高”或“队内第一”。
                        - 优先引用 playerFacts 里的“排名：”和“高光：”事实；如果出现“个人镀层”，可以按个人镀层贡献描述，不要写成全队镀层。
                        判断方向：
                        1. 如果【你】表现好并且赢了：赢了要从多个角度大力吹，大方鼓励和吹嘘，可以写得爽一点；强调这是有明确贡献的胜利，不是混赢。
                        2. 如果【你】表现好但输了：明确表达玩家已经尽力，不让用户背锅；从其他英雄、其他位置、团队节奏、资源交换或阵容执行里找原因，说明这局不是用户一个人的问题。
                        3. 如果【你】数据不好看但赢了：不要无脑吹成全场最强，要结合英雄和位置职责，从承伤、牵制、开团、视野、资源让渡、装备/符文思路等角度找合理贡献；往胜利的基石、关键拼图、关键人物方向写。
                        4. 如果【你】数据不好看且输了：先安慰并降低压力，输了要尽量帮玩家合理甩锅，帮用户甩锅，不让用户吃一点压力；把失败解释成多因素结果，可以从阵容、队友位置节奏、对手强势点或资源交换里找外部原因，但不要辱骂、阴阳怪气或攻击其他玩家。
                        输出规则：
                        - headline 像一句醒目的夸夸标题。
                        - headline 不要使用固定模板“这把真不能全怪你”；赢局不要用“不背锅”“不能怪你”类标题，必须按英雄、位置、胜负和表现自拟。
                        - paragraphs 必须是 1 到 2 段正文，正文最多两段，总长 220 到 450 个中文字符。
                        - 不能有小标题、列表、编号、项目符号；paragraphs 里的每一项只能是自然正文。
                        - 不要写“总结来说”“从数据看”“首先/其次/最后”。
                        - 必须提到【你】的英雄和位置。
                        - 不要输出教学建议，不要给下局任务，不要制造压力。
                        - 不要出现召唤师名。
                        - 如果数据不足，就明确说“这局数据不完整，但从已有信息看...”。
                        """
                : """
                        请只输出 JSON，不要 Markdown、代码块或额外解释。JSON schemaVersion 固定为 postgame_review_result.v1。
                        输出结构：
                        {
                          "schemaVersion": "postgame_review_result.v1",
                          "levels": [
                            {"label": "夯", "players": [{"playerRef": "你｜我方打野｜凯隐", "championName": "凯隐", "phrase": "短语说明"}]},
                            {"label": "顶级", "players": []},
                            {"label": "人上人", "players": []},
                            {"label": "NPC", "players": []},
                            {"label": "拉完了", "players": []}
                          ],
                          "summary": "一段根据对局内容写出的客观总结。"
                        }
                        规则：
                        - levels 必须且只能包含五档：夯、顶级、人上人、NPC、拉完了。
                        - 必须覆盖 playerFacts 里的全部 10 个玩家，每个玩家只能出现一次。
                        - playerRef 直接使用 playerFacts 中【】内的标识，不要编造召唤师名。
                        - championName 使用 playerRef 里的英雄名。
                        - phrase 是每个玩家的一句短语说明，尽量客观，避免脏话。
                        - summary 放在前端生成的图片里，写一段客观复盘总结，不要重复列流水账。
                        - 可以引用 playerFacts 里的“排名：”“高光：”“个人镀层”事实；没有明确排名标签时，不要自行推断“全队最高、第一、MVP”。
                        """;

        return List.of(
                new DeepSeekChatMessage(
                        "system",
                        """
                                你是 RankPeek 的英雄联盟赛后复盘助手。只根据用户提供的 postgame snapshot 分析，不臆造不存在的数据。
                                review 模式必须输出可被 JSON.parse 解析的 postgame_review_result.v1；praise 模式必须输出可被 JSON.parse 解析的 postgame_praise_result.v1。
                                """
                ),
                new DeepSeekChatMessage(
                        "user",
                        """
                                %s
                                mode: %s
                                snapshotSchemaVersion: %s
                                snapshotJson:
                                %s
                                """.formatted(
                                task,
                                mode,
                                nullToEmpty(request.snapshotSchemaVersion()),
                                toJson(request.snapshot())
                        )
                )
        );
    }

    private void sendDelta(SseEmitter emitter, String delta) {
        try {
            sendEvent(emitter, "delta", delta);
        } catch (IOException exception) {
            throw new DeepSeekAiException("DeepSeek stream delivery failed", exception);
        }
    }

    private void sendUsage(SseEmitter emitter, DeepSeekTokenUsage usage) {
        try {
            sendEvent(emitter, "usage", objectMapper.writeValueAsString(usage));
        } catch (IOException exception) {
            throw new DeepSeekAiException("DeepSeek stream delivery failed", exception);
        }
    }

    private static void sendEvent(SseEmitter emitter, String eventName, String data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private static void sendError(SseEmitter emitter, String message) {
        try {
            sendEvent(emitter, "error", message == null || message.isBlank() ? "DeepSeek stream failed" : message);
        } catch (IOException ignored) {
            // The client may have already disconnected.
        } finally {
            emitter.complete();
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private static String formatLines(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "- none";
        }
        return values.stream()
                .map(value -> "- " + nullToEmpty(value))
                .toList()
                .stream()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- none");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, Object> readMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }
}
