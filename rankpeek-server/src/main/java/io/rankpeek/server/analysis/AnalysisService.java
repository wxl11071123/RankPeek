package io.rankpeek.server.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.server.ai.AiProvider;
import io.rankpeek.server.ai.AnalysisResult;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PromptContextService promptContextService;
    private final AiProvider aiProvider;

    public AnalysisService(PromptContextService promptContextService, AiProvider aiProvider) {
        this.promptContextService = promptContextService;
        this.aiProvider = aiProvider;
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

    public SseEmitter streamPregameMock(PregameAnalysisRequest request) {
        SseEmitter emitter = new SseEmitter(30_000L);

        Thread.ofVirtual().start(() -> {
            try {
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

    private static void sendPlayerVerdicts(SseEmitter emitter, PregameAnalysisRequest request) throws IOException {
        List<Map<String, Object>> players = readSelectedSnapshotPlayers(request);
        for (int i = 0; i < players.size(); i++) {
            sendEvent(emitter, "player_verdict", buildPlayerVerdictJson(request, players.get(i), i));
        }
    }

    private static String buildPlayerVerdictJson(
            PregameAnalysisRequest request,
            Map<String, Object> player,
            int index
    ) throws JsonProcessingException {
        boolean opponentMode = "opponent".equalsIgnoreCase(request.mode() == null ? "" : request.mode().trim());
        String label = opponentMode
                ? (index % 2 == 0 ? "高威胁" : "可突破")
                : (index % 2 == 0 ? "稳定队友" : "风险队友");
        String tone = opponentMode
                ? (index % 2 == 0 ? "carry" : "weak")
                : (index % 2 == 0 ? "stable" : "risk");
        String reason = opponentMode
                ? "rankpeek-server mock 仅基于本次请求里的对手标签生成。"
                : "rankpeek-server mock 仅基于本次请求里的队友标签生成。";

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("playerKey", readPlayerKey(player, index));
        payload.put("label", label);
        payload.put("tone", tone);
        payload.put("reason", reason);
        return OBJECT_MAPPER.writeValueAsString(payload);
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
            return toPlayerMaps(snapshot.get("enemyTeam"));
        }
        return toPlayerMaps(snapshot.get("allyTeam"));
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

    private static String summarizeTags(List<String> tags) {
        return String.join("；", tags.stream().limit(3).toList());
    }

    private static void pauseBriefly() throws InterruptedException {
        Thread.sleep(10L);
    }
}
