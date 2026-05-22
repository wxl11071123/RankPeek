package io.rankpeek.server.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.server.ai.DeepSeekAiException;
import io.rankpeek.server.ai.DeepSeekAiProperties;
import io.rankpeek.server.ai.DeepSeekChatClient;
import io.rankpeek.server.ai.DeepSeekChatMessage;
import io.rankpeek.server.ai.DeepSeekTokenUsage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        Thread.ofVirtual().start(() -> streamPregameStructured(emitter, request));
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

    private void streamPregameStructured(SseEmitter emitter, PregameAnalysisRequest request) {
        StringBuilder buffer = new StringBuilder();
        Set<String> allowedPlayerKeys = readSelectedPlayerKeys(request);
        try {
            sendEvent(emitter, "start", "RankPeek DeepSeek stream started");
            chatClient.streamChat(
                    properties,
                    buildPregameMessages(request),
                    delta -> consumePregameDelta(emitter, buffer, allowedPlayerKeys, delta),
                    usage -> sendUsage(emitter, usage)
            );
            flushPregameBuffer(emitter, buffer, allowedPlayerKeys);
            sendEvent(emitter, "done", "done");
            emitter.complete();
        } catch (DeepSeekAiException exception) {
            sendError(emitter, exception.getMessage());
        } catch (Exception exception) {
            sendError(emitter, "DeepSeek stream failed");
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
            throw new DeepSeekAiException("DeepSeek pregame insight is not valid NDJSON", exception);
        }

        String playerKey = readText(node, "playerKey");
        String label = readText(node, "label");
        String text = readText(node, "text");
        if (playerKey.isBlank() || label.isBlank() || text.isBlank()) {
            throw new DeepSeekAiException("DeepSeek pregame insight missing required fields");
        }
        if (!allowedPlayerKeys.isEmpty() && !allowedPlayerKeys.contains(playerKey)) {
            return;
        }

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("playerKey", playerKey);
        payload.put("label", label);
        payload.put("tone", normalizeTone(readText(node, "tone")));
        payload.put("text", text);
        try {
            sendEvent(emitter, "player_insight", objectMapper.writeValueAsString(payload));
        } catch (IOException exception) {
            throw new DeepSeekAiException("DeepSeek stream delivery failed", exception);
        }
    }

    private List<DeepSeekChatMessage> buildPregameMessages(PregameAnalysisRequest request) {
        String mode = nullToEmpty(request.mode());
        String modeRules = buildPregameModeRules(mode);
        return List.of(
                new DeepSeekChatMessage(
                        "system",
                        """
                                你是 RankPeek 的英雄联盟赛前分析助手。只根据用户提供的 snapshot 分析，不准编造外部战绩。
                                你必须输出 player_insight_result.v1 的 NDJSON：每一行都是一个完整 JSON 对象，不要 Markdown、不要代码块、不要总评、不要额外解释。
                                每个对象字段固定为 playerKey、label、tone、text。tone 只能是 carry、stable、risk、weak、unknown。
                                """
                ),
                new DeepSeekChatMessage(
                        "user",
                        """
                                请做赛前结构化分析。
                                schema: player_insight_result.v1
                                mode: %s
                                snapshotSchemaVersion: %s
                                allowedPlayerKeys:
                                %s
                                共同输出规则：
                                - 只输出 NDJSON，每行一个 JSON 对象。
                                - 必须为 allowedPlayerKeys 里的每个 playerKey 输出一行，不能输出其他 playerKey。
                                - label 必须使用当前模式允许的固定标签，不准自创。
                                - text 必须是 1 到 2 句话，不能有小标题、列表、编号或换行。
                                %s
                                snapshotText:
                                %s
                                """.formatted(
                                mode,
                                nullToEmpty(request.snapshotSchemaVersion()),
                                formatLines(readSelectedPlayerKeys(request).stream().toList()),
                                modeRules,
                                formatPregameSnapshotText(request)
                        )
                )
        );
    }

    private static String buildPregameModeRules(String mode) {
        if ("opponent".equalsIgnoreCase(mode)) {
            return """
                    对手模式规则：
                    - 只分析敌方阵营，不评价我方。
                    - label 只能从：代中代、小代、npc、突破口、？？？。
                    - tone 必须按 label 映射：代中代 -> carry，小代 -> risk，npc -> stable，突破口 -> weak，？？？ -> unknown。
                    - 每个敌方玩家只做状态总结：根据位置、近期状态、tag 和关键数据概括威胁程度或可突破程度。
                    - 不给用户操作建议，不写前期注意点、针对方案、gank/入侵/控资源建议。
                    - 查不到战绩、读取失败、隐藏战绩或样本不足时优先使用“？？？”和 unknown，只总结为信息有限。
                    """;
        }

        return """
                队友模式规则：
                - 只分析我方阵营，不评价敌方玩家。
                - label 只能从：上等马、中等马、下等马、？？？马。
                - tone 必须按 label 映射：上等马 -> carry，中等马 -> stable，下等马 -> risk，？？？马 -> unknown。
                - 非用户队友只做状态总结：根据位置、近期状态、tag 和关键数据概括这个人的可靠程度或风险，不给操作建议、不写配合/规避方案。
                - 用户本人写状态总结 + 一句轻量本局思路提醒；提醒只基于用户当前位置和队友整体状态，不要展开成教学或路线清单。
                - 查不到战绩、读取失败、隐藏战绩或样本不足时优先使用“？？？马”和 unknown，只总结为信息有限，不给操作建议。
                """;
    }

    private static String formatPregameSnapshotText(PregameAnalysisRequest request) {
        boolean opponentMode = "opponent".equalsIgnoreCase(nullToEmpty(request.mode()));
        List<String> primary = opponentMode ? request.enemyTeamTags() : request.allyTeamTags();
        List<String> fallback = opponentMode ? request.allyTeamTags() : request.enemyTeamTags();
        return formatLines(primary == null || primary.isEmpty() ? fallback : primary);
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

    private static Set<String> readSelectedPlayerKeys(PregameAnalysisRequest request) {
        Set<String> playerKeys = new LinkedHashSet<>();
        List<Map<String, Object>> players = readSelectedSnapshotPlayers(request);
        for (int index = 0; index < players.size(); index += 1) {
            String key = readPlayerKey(players.get(index), index);
            if (!key.isBlank()) {
                playerKeys.add(key);
            }
        }
        return playerKeys;
    }

    private static List<Map<String, Object>> readSelectedSnapshotPlayers(PregameAnalysisRequest request) {
        Map<String, Object> snapshot = readMap(request.snapshot());
        if (snapshot.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> selectedPlayers = toPlayerMaps(snapshot.get("selectedPlayers"));
        if (!selectedPlayers.isEmpty()) {
            return selectedPlayers;
        }

        String mode = nullToEmpty(request.mode()).toLowerCase();
        if ("opponent".equals(mode)) {
            List<Map<String, Object>> opponentSnapshotPlayers = readTeamSnapshotPlayers(snapshot, "opponentSnapshot");
            if (!opponentSnapshotPlayers.isEmpty()) {
                return opponentSnapshotPlayers;
            }
            return toPlayerMaps(snapshot.get("enemyTeam"));
        }

        List<Map<String, Object>> teammateSnapshotPlayers = readTeamSnapshotPlayers(snapshot, "teammateSnapshot");
        if (!teammateSnapshotPlayers.isEmpty()) {
            return teammateSnapshotPlayers;
        }
        return toPlayerMaps(snapshot.get("allyTeam"));
    }

    private static List<Map<String, Object>> readTeamSnapshotPlayers(Map<String, Object> snapshot, String key) {
        Object value = snapshot.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            return List.of();
        }
        return toPlayerMaps(map.get("players"));
    }

    private static List<Map<String, Object>> toPlayerMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<Map<String, Object>> players = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> player = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        player.put(key, entry.getValue());
                    }
                }
                players.add(player);
            }
        }
        return players;
    }

    private static String readPlayerKey(Map<String, Object> player, int index) {
        String key = readString(player.get("key"));
        if (!key.isBlank()) {
            return key;
        }

        String puuid = readString(player.get("puuid"));
        if (!puuid.isBlank()) {
            return "puuid:" + puuid;
        }

        String displayName = readString(player.get("displayName"));
        if (!displayName.isBlank()) {
            return "name:" + displayName + ":" + readChampionKeyPart(player.get("championId"));
        }

        return "player:" + index;
    }

    private static String readChampionKeyPart(Object value) {
        if (value instanceof Number number) {
            return String.valueOf(number.intValue());
        }
        String text = readString(value);
        return text.isBlank() ? "unknown" : text;
    }

    private static String readString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String readText(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        return field.isTextual() ? field.asText().trim() : "";
    }

    private static String normalizeTone(String tone) {
        String normalized = nullToEmpty(tone);
        return switch (normalized) {
            case "carry", "stable", "risk", "weak", "unknown" -> normalized;
            default -> "unknown";
        };
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
