package io.rankpeek.server.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.rankpeek.server.ai.DeepSeekAiException;
import io.rankpeek.server.ai.DeepSeekAiProperties;
import io.rankpeek.server.ai.DeepSeekChatClient;
import io.rankpeek.server.ai.DeepSeekChatMessage;
import io.rankpeek.server.ai.DeepSeekTokenUsage;
import io.rankpeek.server.analysis.coachsummary.CoachSummaryReport;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
        return streamPregame(request, DeepSeekStreamCallbacks.NOOP);
    }

    public SseEmitter streamPregame(PregameAnalysisRequest request, DeepSeekStreamCallbacks callbacks) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Thread.ofVirtual().start(() -> streamPregameStructured(emitter, request, callbacks));
        return emitter;
    }

    public SseEmitter streamPostgame(PostgameAnalysisRequest request) {
        return streamPostgame(request, DeepSeekStreamCallbacks.NOOP);
    }

    public SseEmitter streamPostgame(PostgameAnalysisRequest request, DeepSeekStreamCallbacks callbacks) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Thread.ofVirtual().start(() -> stream(emitter, buildPostgameMessages(request), callbacks));
        return emitter;
    }

    public CoachSummaryAnalysisResponse generateCoachSummary(CoachSummaryAnalysisRequest request) {
        if (isBlank(request.systemPrompt()) || isBlank(request.userPrompt())) {
            throw new DeepSeekAiException("Coach summary prompt is empty");
        }

        StringBuilder content = new StringBuilder();
        AtomicReference<DeepSeekTokenUsage> usage = new AtomicReference<>();
        chatClient.streamJsonChat(
                properties,
                List.of(
                        new DeepSeekChatMessage("system", request.systemPrompt()),
                        new DeepSeekChatMessage("user", request.userPrompt())
                ),
                content::append,
                usage::set
        );

        return new CoachSummaryAnalysisResponse(
                parseCoachSummaryReport(content.toString(), request, usage.get()),
                usage.get()
        );
    }

    private void stream(SseEmitter emitter, List<DeepSeekChatMessage> messages, DeepSeekStreamCallbacks callbacks) {
        AtomicReference<DeepSeekTokenUsage> usageReference = new AtomicReference<>();
        try {
            pauseBeforeFirstSend();
            sendEvent(emitter, "start", "RankPeek DeepSeek stream started");
            sendEvent(emitter, "section", "DeepSeek 分析");
            chatClient.streamChat(
                    properties,
                    messages,
                    delta -> sendDelta(emitter, delta),
                    usage -> {
                        usageReference.set(usage);
                        sendUsage(emitter, usage);
                    }
            );
            sendEvent(emitter, "done", "done");
            callbacks.onSucceeded(usageReference.get());
            emitter.complete();
        } catch (DeepSeekAiException exception) {
            callbacks.onFailed("DEEPSEEK_ERROR", exception.getMessage());
            sendError(emitter, exception.getMessage());
        } catch (Exception exception) {
            callbacks.onFailed("DEEPSEEK_ERROR", "DeepSeek stream failed");
            sendError(emitter, "DeepSeek stream failed");
        }
    }

    private void streamPregameStructured(
            SseEmitter emitter,
            PregameAnalysisRequest request,
            DeepSeekStreamCallbacks callbacks
    ) {
        StringBuilder buffer = new StringBuilder();
        Set<String> allowedPlayerKeys = readSelectedPlayerKeys(request);
        AtomicReference<DeepSeekTokenUsage> usageReference = new AtomicReference<>();
        try {
            pauseBeforeFirstSend();
            sendEvent(emitter, "start", "RankPeek DeepSeek stream started");
            chatClient.streamChat(
                    properties,
                    buildPregameMessages(request),
                    delta -> consumePregameDelta(emitter, buffer, allowedPlayerKeys, delta),
                    usage -> {
                        usageReference.set(usage);
                        sendUsage(emitter, usage);
                    }
            );
            flushPregameBuffer(emitter, buffer, allowedPlayerKeys);
            sendEvent(emitter, "done", "done");
            callbacks.onSucceeded(usageReference.get());
            emitter.complete();
        } catch (DeepSeekAiException exception) {
            callbacks.onFailed("DEEPSEEK_ERROR", exception.getMessage());
            sendError(emitter, exception.getMessage());
        } catch (Exception exception) {
            callbacks.onFailed("DEEPSEEK_ERROR", "DeepSeek stream failed");
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

    private static void pauseBeforeFirstSend() throws InterruptedException {
        // Let Spring initialize SseEmitter's handler before the writer thread sends.
        Thread.sleep(10L);
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
                          "headline": "AI 自拟一句赛后爽文标题",
                          "paragraphs": [
                            "第一段正文",
                            "第二段正文，可省略"
                          ]
                        }
                        核心定位：夸夸机是赛后爽文嘴替，不是教练、不是分析师、不是熟人聊天。像赛后弹幕精选 + 嘴替短文，可以夸张、护短、有情绪，但每一句夸都必须能被 snapshot 中的事实支撑。
                        目标：
                        - 只围绕当前用户，也就是 playerFacts 中带有【你｜...】的玩家进行分析。
                        - 必须结合【你】的英雄、位置、胜负；数据只挑最能撑起情绪的关键事实，不要写成数据报告，不要机械罗列 KDA、伤害、参团率。
                        - 优先围绕【你】所选英雄和位置来解释这局为什么爽、为什么不该背锅、为什么有价值。
                        - 输出中文，语气要像爽文嘴替，不像 AI 助手、客服、教练报告、战术复盘或普通聊天。
                        - 标题和正文都要有情绪、有画面感，但不能编造不存在的数据。
                        - 可以点名队友或对手的英雄和位置，用来描述局势或帮用户说话；不要暴露召唤师名，不要把某个玩家钉成战犯，也不要辱骂。
                        - 不能把百分比自动写成全队最高、第一、MVP、最强、唯一支柱等排名结论，除非 playerFacts、teamFacts、timelineFacts 或 snapshot 明确给出排名。
                        - 例如只有伤害占比 19%、经济转化 20% 这类百分比时，只能说“有输出/经济贡献”，不能说“全队最高”或“队内第一”。
                        - 优先引用 playerFacts 里的“排名：”和“高光：”事实；如果出现“个人镀层”，可以按个人镀层贡献描述，不要写成全队镀层。
                        情绪方向：
                        - 赢局：大力吹，强调用户是胜利核心、节奏发动机、关键输出/承伤/牵制点；可以写“这把你就是主角”，但必须用 snapshot 事实撑住。
                        - 输局：强力护短，强调“你已经把能做的打满了”，把失败写成局势、阵容、节奏、资源交换多因素结果；可以替用户挡锅，但不能辱骂或攻击队友。
                        - 数据好看时直接把最亮的 2 到 4 个事实写成爽点；数据不好看时，从英雄职责、承伤、牵制、开团、视野、资源让渡、装备/符文思路里找合理贡献。
                        - 不要频繁使用“兄弟”“哥们”“牛逼”“赢麻了”等生硬网感词；偶尔可以有劲，但不要像在装熟。
                        输出规则：
                        - headline 像一句赛后爽文标题，短、有劲、贴英雄和胜负。
                        - headline 不要使用固定模板“这把真不能全怪你”；赢局不要用“不背锅”“不能怪你”类标题，必须按英雄、位置、胜负和表现自拟。
                        - paragraphs 必须是 1 到 2 段正文，正文最多两段；不设硬性字数，内容多少跟着这局事实走，事实够多就大胆展开，事实少就短一点；不要为了凑字灌水，也不要因为怕长只写成几句。
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

    private Map<String, Object> parseCoachSummaryReport(
            String rawContent,
            CoachSummaryAnalysisRequest request,
            DeepSeekTokenUsage usage
    ) {
        String json = extractJsonObject(rawContent);
        ObjectNode root;
        try {
            JsonNode node = objectMapper.readTree(json);
            node = unwrapCoachSummaryEnvelope(node);
            if (!node.isObject()) {
                throw new DeepSeekAiException("DeepSeek coach summary report is not a JSON object");
            }
            root = (ObjectNode) node;
        } catch (JsonProcessingException exception) {
            throw new DeepSeekAiException("DeepSeek coach summary report is not valid JSON", exception);
        }

        root.put("schemaVersion", CoachSummaryReport.SCHEMA_VERSION);
        root.put("analysisType", CoachSummaryReport.ANALYSIS_TYPE);
        root.put("inputHash", requireNonBlank(request.inputHash(), "Coach summary inputHash is empty"));
        normalizeCoachSummaryTextFields(root);

        ObjectNode metadata = readOrCreateObject(root, "metadata");
        metadata.put("modelName", usage == null ? properties.model() : usage.model());
        metadata.put("promptVersion", blankToDefault(request.promptVersion(), "coach_summary.prompt.v2"));
        metadata.put("generatedAt", Instant.now().toString());
        metadata.put("snapshotSchemaVersion", blankToDefault(request.snapshotSchemaVersion(), "coach_summary_input_snapshot.v1"));
        metadata.put("dataQualityConfidence", normalizeConfidence(request.dataQualityConfidence()));

        requireTextField(root, "title");
        requireTextField(root, "summary");
        normalizeCoachSummaryVerdict(root, request);
        if (!root.path("verdict").isObject()) {
            throw new DeepSeekAiException("DeepSeek coach summary report missing verdict");
        }
        ensureArrayField(root, "keyFindings");
        ensureArrayField(root, "trainingPlan");
        ensureArrayField(root, "championAdvice");
        ensureArrayField(root, "chartBlocks");
        ensureArrayField(root, "warnings");

        return objectMapper.convertValue(root, new TypeReference<>() {
        });
    }

    private static JsonNode unwrapCoachSummaryEnvelope(JsonNode node) {
        if (!node.isObject() || hasCoachSummaryShape(node)) {
            return node;
        }
        for (String fieldName : List.of("report", "result", "data")) {
            JsonNode child = node.path(fieldName);
            if (child.isObject() && hasCoachSummaryShape(child)) {
                return child;
            }
        }
        return node;
    }

    private static boolean hasCoachSummaryShape(JsonNode node) {
        return !readFirstText(node, "title", "headline", "reportTitle", "summary").isBlank()
                || node.path("verdict").isObject();
    }

    private static void normalizeCoachSummaryTextFields(ObjectNode root) {
        putTextIfMissing(root, "title",
                readFirstText(root, "headline", "reportTitle", "name", "topic"));
        if (isBlank(readText(root, "title"))) {
            JsonNode verdict = root.path("verdict");
            putTextIfMissing(root, "title", readFirstText(verdict, "label", "title"));
        }
        putTextIfMissing(root, "summary",
                readFirstText(root, "overview", "abstract", "finalSummary"));
        if (isBlank(readText(root, "summary"))) {
            JsonNode verdict = root.path("verdict");
            putTextIfMissing(root, "summary", readFirstText(verdict, "summary", "label"));
        }
    }

    private static void putTextIfMissing(ObjectNode root, String fieldName, String value) {
        if (!isBlank(readText(root, fieldName)) || isBlank(value)) {
            return;
        }
        root.put(fieldName, value.trim());
    }

    private static void normalizeCoachSummaryVerdict(ObjectNode root, CoachSummaryAnalysisRequest request) {
        ObjectNode verdict = readOrCreateObject(root, "verdict");
        putTextIfMissing(verdict, "label", readText(root, "title"));
        if (!verdict.path("score").canConvertToInt()) {
            verdict.put("score", 50);
        }
        String confidence = normalizeConfidence(readText(verdict, "confidence"));
        if (confidence.equals("medium") && isBlank(readText(verdict, "confidence"))) {
            confidence = normalizeConfidence(request.dataQualityConfidence());
        }
        verdict.put("confidence", confidence);
        putTextIfMissing(verdict, "summary", readText(root, "summary"));
    }

    private static String readFirstText(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        for (String fieldName : fieldNames) {
            String value = readText(node, fieldName);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static ObjectNode readOrCreateObject(ObjectNode root, String fieldName) {
        JsonNode existing = root.get(fieldName);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        return root.putObject(fieldName);
    }

    private static void ensureArrayField(ObjectNode root, String fieldName) {
        if (!root.path(fieldName).isArray()) {
            root.putArray(fieldName);
        }
    }

    private static void requireTextField(ObjectNode root, String fieldName) {
        if (!root.path(fieldName).isTextual() || root.path(fieldName).asText().isBlank()) {
            throw new DeepSeekAiException("DeepSeek coach summary report missing " + fieldName);
        }
    }

    private static String extractJsonObject(String rawContent) {
        String trimmed = nullToEmpty(rawContent);
        if (trimmed.isBlank()) {
            throw new DeepSeekAiException("DeepSeek coach summary report is empty");
        }

        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                trimmed = trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new DeepSeekAiException("DeepSeek coach summary report does not contain JSON");
        }
        return trimmed.substring(start, end + 1);
    }

    private static String normalizeConfidence(String value) {
        return switch (nullToEmpty(value).toLowerCase()) {
            case "high", "medium", "low" -> nullToEmpty(value).toLowerCase();
            default -> "medium";
        };
    }

    private static String requireNonBlank(String value, String message) {
        if (isBlank(value)) {
            throw new DeepSeekAiException(message);
        }
        return value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
