package io.rankpeek.server.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.server.ai.AiProvider;
import io.rankpeek.server.ai.AnalysisResult;
import io.rankpeek.server.ai.DeepSeekAiException;
import io.rankpeek.server.ai.DeepSeekAiProperties;
import io.rankpeek.server.ai.DeepSeekTokenUsage;
import io.rankpeek.server.auth.AuthUser;
import io.rankpeek.server.common.ApiException;
import io.rankpeek.server.common.ApiResponse;
import io.rankpeek.server.credits.AiAnalysisRun;
import io.rankpeek.server.credits.AiCreditReservation;
import io.rankpeek.server.credits.CreditProperties;
import io.rankpeek.server.credits.CreditService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class AnalysisService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), true);

    private final PromptContextService promptContextService;
    private final AiProvider aiProvider;
    private final DeepSeekAnalysisStreamer deepSeekAnalysisStreamer;
    private final DeepSeekAiProperties deepSeekAiProperties;
    private final CreditService creditService;
    private final CreditProperties creditProperties;

    public AnalysisService(
            PromptContextService promptContextService,
            AiProvider aiProvider,
            DeepSeekAnalysisStreamer deepSeekAnalysisStreamer,
            DeepSeekAiProperties deepSeekAiProperties,
            CreditService creditService,
            CreditProperties creditProperties
    ) {
        this.promptContextService = promptContextService;
        this.aiProvider = aiProvider;
        this.deepSeekAnalysisStreamer = deepSeekAnalysisStreamer;
        this.deepSeekAiProperties = deepSeekAiProperties;
        this.creditService = creditService;
        this.creditProperties = creditProperties;
    }

    public AnalysisResult generatePregameMock(PregameAnalysisRequest request) {
        PromptContext context = promptContextService.buildContext(
                request.championId(),
                request.role(),
                request.patchKey(),
                request.queueId()
        );
        AnalysisPrompt prompt = new AnalysisPrompt(
                context,
                nullToEmpty(request.allyTeamTags()),
                nullToEmpty(request.enemyTeamTags())
        );
        return aiProvider.generateAnalysis(prompt);
    }

    public boolean deepSeekEnabled() {
        return deepSeekAnalysisStreamer.isEnabled();
    }

    public SseEmitter streamPregame(PregameAnalysisRequest request, AuthUser user) {
        if (deepSeekAnalysisStreamer.isEnabled()) {
            return streamBillableAiRun(
                    user,
                    "pregame-stream",
                    requestHash(request),
                    callbacks -> deepSeekAnalysisStreamer.streamPregame(request, callbacks)
            );
        }
        return streamPregameMock(request);
    }

    public SseEmitter streamPostgame(PostgameAnalysisRequest request, AuthUser user) {
        if (deepSeekAnalysisStreamer.isEnabled()) {
            return streamBillableAiRun(
                    user,
                    "postgame-stream",
                    requestHash(request),
                    callbacks -> deepSeekAnalysisStreamer.streamPostgame(request, callbacks)
            );
        }
        return streamPostgameMock(request);
    }

    public SseEmitter streamPregameMock(PregameAnalysisRequest request) {
        SseEmitter emitter = new SseEmitter(30_000L);

        Thread.ofVirtual().start(() -> {
            try {
                pauseBeforeFirstSend();
                sendEvent(emitter, "start", "RankPeek mock stream started");
                pauseBriefly();
                sendEvent(emitter, "section", "概览");
                pauseBriefly();
                sendEvent(emitter, "delta", buildOverviewDelta(request));
                pauseBriefly();
                sendPlayerVerdicts(emitter, request);
                pauseBriefly();
                sendEvent(emitter, "section", "风险点");
                pauseBriefly();
                sendEvent(emitter, "delta", buildRiskDelta(request));
                pauseBriefly();
                sendEvent(emitter, "section", "建议");
                pauseBriefly();
                sendEvent(emitter, "delta", "这是一段本地 mock 分析：先按已加载标签做轻量判断，进入游戏后继续以真实对线、视野和资源交换为准。");
                pauseBriefly();
                sendEvent(emitter, "done", "done");
                emitter.complete();
            } catch (Exception e) {
                try {
                    sendEvent(emitter, "error", "mock stream failed");
                } catch (IOException ignored) {
                    // The client may have already disconnected.
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    public SseEmitter streamPostgameMock(PostgameAnalysisRequest request) {
        SseEmitter emitter = new SseEmitter(30_000L);

        Thread.ofVirtual().start(() -> {
            try {
                pauseBeforeFirstSend();
                sendEvent(emitter, "start", "RankPeek postgame mock stream started");
                pauseBriefly();
                sendEvent(emitter, "delta", buildStructuredPostgameMockDelta(request));
                pauseBriefly();
                sendEvent(emitter, "done", "done");
                emitter.complete();
            } catch (Exception e) {
                try {
                    sendEvent(emitter, "error", "postgame mock stream failed");
                } catch (IOException ignored) {
                    // The client may have already disconnected.
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private SseEmitter streamBillableAiRun(
            AuthUser user,
            String endpoint,
            String requestHash,
            Function<DeepSeekStreamCallbacks, SseEmitter> streamFactory
    ) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCESS_TOKEN_INVALID", "Invalid or expired access token");
        }

        AiCreditReservation reservation = creditService.reserveAiRun(
                user,
                endpoint,
                deepSeekAiProperties.provider(),
                deepSeekAiProperties.model(),
                creditProperties.aiStreamChargeCredits(),
                requestHash,
                null
        );
        return streamFactory.apply(new DeepSeekStreamCallbacks() {
            @Override
            public void onSucceeded(DeepSeekTokenUsage usage) {
                creditService.completeAiRun(reservation, usage, null);
            }

            @Override
            public void onFailed(String errorCode, String errorMessage) {
                creditService.refundAiRun(reservation, errorCode, errorMessage);
            }
        });
    }

    public ApiResponse<CoachSummaryAnalysisResponse> generateCoachSummary(
            CoachSummaryAnalysisRequest request,
            AuthUser user,
            String idempotencyKey
    ) {
        if (!deepSeekAnalysisStreamer.isEnabled()) {
            return ApiResponse.failure("AI_SERVER_DISABLED", "DeepSeek AI is not enabled");
        }

        String requestHash = requestHash(request);
        var existingRun = creditService.findAiRunByIdempotencyKey(user.id(), idempotencyKey);
        if (existingRun.isPresent()) {
            return handleExistingCoachSummaryRun(existingRun.get(), requestHash);
        }

        AiCreditReservation reservation;
        try {
            reservation = creditService.reserveAiRun(
                    user,
                    "coach-summary",
                    deepSeekAiProperties.provider(),
                    deepSeekAiProperties.model(),
                    creditProperties.coachSummaryChargeCredits(),
                    requestHash,
                    idempotencyKey
            );
        } catch (DuplicateKeyException exception) {
            return creditService.findAiRunByIdempotencyKey(user.id(), idempotencyKey)
                    .map(run -> handleExistingCoachSummaryRun(run, requestHash))
                    .orElseThrow(() -> exception);
        }

        try {
            CoachSummaryAnalysisResponse response = deepSeekAnalysisStreamer.generateCoachSummary(request);
            creditService.completeAiRun(reservation, response.usage(), writeJson(response));
            return ApiResponse.success(response);
        } catch (DeepSeekAiException exception) {
            creditService.refundAiRun(reservation, "DEEPSEEK_ERROR", exception.getMessage());
            return ApiResponse.failure("DEEPSEEK_ERROR", exception.getMessage());
        }
    }

    public AnalysisRunListResponse listUserRuns(AuthUser user, String endpoint, String status, int limit, int offset) {
        int normalizedLimit = normalizeLimit(limit);
        int normalizedOffset = normalizeOffset(offset);
        return new AnalysisRunListResponse(
                creditService.listAiRuns(user.id(), endpoint, status, normalizedLimit, normalizedOffset).stream()
                        .map(AnalysisRunSummaryResponse::from)
                        .toList(),
                creditService.countAiRuns(user.id(), endpoint, status),
                normalizedLimit,
                normalizedOffset
        );
    }

    public AnalysisRunDetailResponse getUserRun(AuthUser user, Long runId) {
        AiAnalysisRun run = creditService.findAiRunById(runId)
                .filter(candidate -> user.id().equals(candidate.userId()))
                .orElseThrow(AnalysisService::aiRunNotFound);
        return AnalysisRunDetailResponse.from(run, responseForUserDetail(run));
    }

    public AdminAnalysisRunListResponse listAdminRuns(Long userId, String endpoint, String status, int limit, int offset) {
        int normalizedLimit = normalizeLimit(limit);
        int normalizedOffset = normalizeOffset(offset);
        return new AdminAnalysisRunListResponse(
                creditService.listAiRunsForAdmin(userId, endpoint, status, normalizedLimit, normalizedOffset).stream()
                        .map(AdminAnalysisRunSummaryResponse::from)
                        .toList(),
                creditService.countAiRunsForAdmin(userId, endpoint, status),
                normalizedLimit,
                normalizedOffset
        );
    }

    public AdminAnalysisRunSummaryResponse getAdminRun(Long runId) {
        AiAnalysisRun run = creditService.findAiRunById(runId).orElseThrow(AnalysisService::aiRunNotFound);
        return AdminAnalysisRunSummaryResponse.from(run);
    }

    private ApiResponse<CoachSummaryAnalysisResponse> handleExistingCoachSummaryRun(AiAnalysisRun run, String requestHash) {
        if (run.requestHash() == null || !run.requestHash().equals(requestHash)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "Idempotency key was already used for a different AI request");
        }

        return switch (run.status()) {
            case "RESERVED" -> throw new ApiException(HttpStatus.CONFLICT, "AI_RUN_IN_PROGRESS", "AI analysis run is still in progress");
            case "SUCCEEDED" -> ApiResponse.success(readCoachSummaryResponse(run.responseJson()));
            case "FAILED", "REFUNDED" -> ApiResponse.failure(
                    run.errorCode() == null || run.errorCode().isBlank() ? "AI_RUN_FAILED" : run.errorCode(),
                    run.errorMessage() == null || run.errorMessage().isBlank() ? "AI analysis run failed" : run.errorMessage()
            );
            default -> throw new ApiException(HttpStatus.CONFLICT, "AI_RUN_IN_PROGRESS", "AI analysis run is not replayable yet");
        };
    }

    private Object responseForUserDetail(AiAnalysisRun run) {
        if (!"SUCCEEDED".equals(run.status())) {
            return null;
        }
        return readCoachSummaryResponse(run.responseJson());
    }

    private static CoachSummaryAnalysisResponse readCoachSummaryResponse(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_RUN_REPLAY_UNAVAILABLE", "Stored AI analysis response is unavailable");
        }
        try {
            return OBJECT_MAPPER.readValue(responseJson, CoachSummaryAnalysisResponse.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_RUN_REPLAY_UNAVAILABLE", "Stored AI analysis response is invalid");
        }
    }

    private static String requestHash(Object request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(writeJson(request).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "JSON_SERIALIZATION_FAILED", "Unable to serialize AI analysis payload");
        }
    }

    private static int normalizeLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        return limit;
    }

    private static int normalizeOffset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be zero or greater");
        }
        return offset;
    }

    private static ApiException aiRunNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "AI_RUN_NOT_FOUND", "AI analysis run was not found");
    }

    private static List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static void sendEvent(SseEmitter emitter, String eventName, String data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private static String buildOverviewDelta(PregameAnalysisRequest request) {
        List<String> allyTags = nullToEmpty(request.allyTeamTags());
        List<String> enemyTags = nullToEmpty(request.enemyTeamTags());
        String allySummary = allyTags.isEmpty() ? "己方暂无可用标签" : "己方样本：" + summarizeTags(allyTags);
        String enemySummary = enemyTags.isEmpty() ? "敌方暂无可用标签" : "敌方样本：" + summarizeTags(enemyTags);
        return allySummary + "。" + enemySummary + "。当前内容来自 rankpeek-server mock stream，不调用真实 AI。";
    }

    private static String buildRiskDelta(PregameAnalysisRequest request) {
        String mode = request.mode() == null ? "" : request.mode().trim().toLowerCase();
        if ("opponent".equals(mode)) {
            return "对手侧优先关注高胜率、高 KDA 或战绩隐藏目标；若标签样本不足，不从 snapshot 反查补数据。";
        }
        return "队友侧优先识别低样本、战绩隐藏和波动标签；本轮只使用前端提交的临时 snapshot。";
    }

    private static String normalizePostgameMode(String mode) {
        String value = mode == null ? "" : mode.trim().toLowerCase();
        return "praise".equals(value) ? "praise" : "review";
    }

    private static String buildStructuredPostgameMockDelta(PostgameAnalysisRequest request) throws JsonProcessingException {
        if ("praise".equals(normalizePostgameMode(request.mode()))) {
            return buildPostgamePraiseMockJson(request);
        }
        return buildPostgameReviewMockJson(request);
    }

    private static String buildPostgamePraiseMockJson(PostgameAnalysisRequest request) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "postgame_praise_result.v1");
        payload.put("headline", "这局你有东西的");

        String firstFact = readFirstAnalysisBriefFact(request, "playerFacts");
        String paragraphOne = "rankpeek-server mock：这局先按赛后 snapshot 给你撑腰，能进结算说明关键数据已经收到了。"
                + (firstFact.isBlank() ? "" : " 你的核心表现会参考：" + firstFact);
        String paragraphTwo = "真实 DeepSeek 打开后会结合完整对局细节输出更像老玩家的夸夸；当前 mock 只用于验证前端结构化展示链路。";
        payload.put("paragraphs", List.of(paragraphOne, paragraphTwo));
        payload.put("body", paragraphOne + "\n\n" + paragraphTwo);
        return OBJECT_MAPPER.writeValueAsString(payload);
    }

    private static String buildPostgameReviewMockJson(PostgameAnalysisRequest request) throws JsonProcessingException {
        List<Map<String, Object>> players = buildPostgameReviewMockPlayers(request);
        Map<String, List<Map<String, Object>>> playersByLevel = new LinkedHashMap<>();
        for (String level : List.of("\u592f", "\u9876\u7ea7", "\u4eba\u4e0a\u4eba", "NPC", "\u62c9\u5b8c\u4e86")) {
            playersByLevel.put(level, new ArrayList<>());
        }
        for (Map<String, Object> player : players) {
            String level = readString(player.get("level"));
            playersByLevel.computeIfAbsent(level, ignored -> new ArrayList<>()).add(player);
        }

        List<Map<String, Object>> levels = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : playersByLevel.entrySet()) {
            Map<String, Object> level = new LinkedHashMap<>();
            level.put("label", entry.getKey());
            level.put("players", entry.getValue());
            levels.add(level);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "postgame_review_result.v1");
        payload.put("levels", levels);
        payload.put("summary", "rankpeek-server mock：已返回 postgame_review_result.v1 结构化结果，用于验证赛后复盘表格渲染。真实 DeepSeek 开启后会基于同一份赛后 snapshot 给出完整客观总结。");
        return OBJECT_MAPPER.writeValueAsString(payload);
    }

    private static List<Map<String, Object>> buildPostgameReviewMockPlayers(PostgameAnalysisRequest request) {
        List<String> playerFacts = readAnalysisBriefFacts(request, "playerFacts");
        String[] fallbackChampions = {
                "\u94c1\u8840\u72fc\u6bcd", "\u76f2\u50e7", "\u5965\u672f\u5148\u9a71", "\u7206\u7834\u9b3c\u624d", "\u653e\u9010\u4e4b\u5203",
                "\u5361\u724c\u5927\u5e08", "\u865a\u7a7a\u6398\u5730\u517d", "\u9006\u7fbd", "\u6df1\u6d77\u6cf0\u5766", "\u75db\u82e6\u4e4b\u62e5"
        };
        String[] levels = {
                "\u592f", "\u9876\u7ea7", "\u9876\u7ea7", "\u4eba\u4e0a\u4eba", "\u4eba\u4e0a\u4eba",
                "NPC", "NPC", "\u62c9\u5b8c\u4e86", "\u62c9\u5b8c\u4e86", "\u62c9\u5b8c\u4e86"
        };

        List<Map<String, Object>> players = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            String fact = index < playerFacts.size() ? playerFacts.get(index) : "";
            PostgameMockPlayerIdentity identity = parsePostgameMockPlayerIdentity(fact, fallbackChampions[index]);
            Map<String, Object> player = new LinkedHashMap<>();
            player.put("level", levels[index]);
            player.put("playerRef", "mock:" + (index + 1));
            player.put("championName", identity.championName());
            player.put("side", identity.side());
            player.put("role", identity.role());
            player.put("phrase", buildPostgameMockPhrase(levels[index], fact));
            players.add(player);
        }
        return players;
    }

    private static PostgameMockPlayerIdentity parsePostgameMockPlayerIdentity(String fact, String fallbackChampion) {
        String label = extractBetween(fact, '\u3010', '\u3011');
        String[] parts = label.split("\uFF5C");
        String ownerRole = parts.length >= 2 ? parts[parts.length - 2] : "";
        String championName = parts.length >= 1 ? parts[parts.length - 1].trim() : "";
        if (championName.isBlank()) {
            championName = fallbackChampion;
        }

        String side = ownerRole.contains("\u6211\u65b9") ? "ally"
                : ownerRole.contains("\u654c\u65b9") ? "enemy"
                : ownerRole.contains("\u84dd\u65b9") ? "blue"
                : ownerRole.contains("\u7ea2\u65b9") ? "red"
                : "";
        String role = ownerRole
                .replace("\u4f60", "")
                .replace("\u6211\u65b9", "")
                .replace("\u654c\u65b9", "")
                .replace("\u84dd\u65b9", "")
                .replace("\u7ea2\u65b9", "")
                .trim();
        return new PostgameMockPlayerIdentity(championName, side, role);
    }

    private static String buildPostgameMockPhrase(String level, String fact) {
        String kda = extractFirstKda(fact);
        String metric = kda.isBlank() ? "" : kda + "\uff0c";
        return switch (level) {
            case "\u592f" -> metric + "mock 最高档，说明这名玩家在关键指标上最显眼。";
            case "\u9876\u7ea7" -> metric + "mock 高档，整体表现靠前。";
            case "\u4eba\u4e0a\u4eba" -> metric + "mock 中上档，能稳定交作业。";
            case "NPC" -> metric + "mock 普通档，存在感不算特别突出。";
            default -> metric + "mock 低档，本局数据相对吃亏。";
        };
    }

    private static String extractFirstKda(String fact) {
        if (fact == null || fact.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+)/(\\d+)/(\\d+)")
                .matcher(fact);
        return matcher.find() ? matcher.group(0) : "";
    }

    private static String extractBetween(String value, char startChar, char endChar) {
        if (value == null) {
            return "";
        }
        int start = value.indexOf(startChar);
        if (start < 0) {
            return "";
        }
        int end = value.indexOf(endChar, start + 1);
        if (end <= start) {
            return "";
        }
        return value.substring(start + 1, end).trim();
    }

    private static String readFirstAnalysisBriefFact(PostgameAnalysisRequest request, String key) {
        List<String> facts = readAnalysisBriefFacts(request, key);
        return facts.isEmpty() ? "" : facts.get(0);
    }

    private static List<String> readAnalysisBriefFacts(PostgameAnalysisRequest request, String key) {
        Map<String, Object> analysisBrief = readMap(readMap(request.snapshot()).get("analysisBrief"));
        Object value = analysisBrief.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<String> facts = new ArrayList<>();
        for (Object item : list) {
            String fact = readString(item);
            if (!fact.isBlank()) {
                facts.add(fact);
            }
        }
        return facts;
    }

    private record PostgameMockPlayerIdentity(String championName, String side, String role) {
    }

    private static void sendPlayerVerdicts(SseEmitter emitter, PregameAnalysisRequest request) throws IOException {
        List<Map<String, Object>> players = readSelectedSnapshotPlayers(request);
        for (int i = 0; i < players.size(); i++) {
            sendEvent(emitter, "player_insight", buildPlayerInsightJson(request, players.get(i), i));
            sendEvent(emitter, "player_verdict", buildPlayerVerdictJson(request, players.get(i), i));
        }
    }

    private static String buildPlayerInsightJson(
            PregameAnalysisRequest request,
            Map<String, Object> player,
            int index
    ) throws JsonProcessingException {
        Map<String, String> verdict = buildPlayerPayload(request, player, index);
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("playerKey", verdict.get("playerKey"));
        payload.put("label", verdict.get("label"));
        payload.put("tone", verdict.get("tone"));
        payload.put("text", verdict.get("reason"));
        return OBJECT_MAPPER.writeValueAsString(payload);
    }

    private static String buildPlayerVerdictJson(
            PregameAnalysisRequest request,
            Map<String, Object> player,
            int index
    ) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(buildPlayerPayload(request, player, index));
    }

    private static Map<String, String> buildPlayerPayload(
            PregameAnalysisRequest request,
            Map<String, Object> player,
            int index
    ) {
        boolean opponentMode = "opponent".equalsIgnoreCase(request.mode() == null ? "" : request.mode().trim());
        boolean selfPlayer = readBoolean(player.get("isSelf"));
        String label = opponentMode
                ? (index % 2 == 0 ? "\u4ee3\u4e2d\u4ee3" : "\u7a81\u7834\u53e3")
                : selfPlayer
                        ? "\u4e2d\u7b49\u9a6c"
                        : (index % 2 == 0 ? "\u4e0a\u7b49\u9a6c" : "\u4e0b\u7b49\u9a6c");
        String tone = opponentMode
                ? (index % 2 == 0 ? "carry" : "weak")
                : selfPlayer
                        ? "stable"
                        : (index % 2 == 0 ? "carry" : "risk");
        String reason = selfPlayer
                ? "rankpeek-server mock \u5f53\u524d\u7528\u6237\u6309\u4e2d\u7b49\u9a6c\u5c55\u793a\uff0c\u771f\u5b9e\u5206\u6790\u4f1a\u7ed3\u5408\u961f\u53cb\u72b6\u6001\u7ed9\u524d\u671f\u63d0\u9192\u3002"
                : opponentMode
                        ? "rankpeek-server mock 仅基于本次请求里的对手标签生成。"
                        : "rankpeek-server mock 仅基于本次请求里的队友标签生成。";

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("playerKey", readPlayerKey(player, index));
        payload.put("label", label);
        payload.put("tone", tone);
        payload.put("reason", reason);
        return payload;
    }

    private static List<Map<String, Object>> readSelectedSnapshotPlayers(PregameAnalysisRequest request) {
        Map<String, Object> snapshot = request.snapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> selectedPlayers = toPlayerMaps(snapshot.get("selectedPlayers"));
        if (!selectedPlayers.isEmpty()) {
            return selectedPlayers;
        }

        String mode = request.mode() == null ? "" : request.mode().trim().toLowerCase();
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
        return text.isBlank() ? "0" : text;
    }

    private static String readString(Object value) {
        return value instanceof String text ? text.trim() : "";
    }

    private static boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(readString(value));
    }

    private static int readInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(readString(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Map<String, Object> readMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private static Map<String, Object> readMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private static String summarizeTags(List<String> tags) {
        return String.join("；", tags.stream().limit(3).toList());
    }

    private static void pauseBeforeFirstSend() throws InterruptedException {
        // Let Spring initialize SseEmitter's handler before the writer thread sends.
        pauseBriefly();
    }

    private static void pauseBriefly() throws InterruptedException {
        Thread.sleep(10L);
    }
}
