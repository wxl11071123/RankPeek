package io.rankpeek.server.analysis;

import io.rankpeek.server.ai.AiProvider;
import io.rankpeek.server.ai.AnalysisResult;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@Service
public class AnalysisService {

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

    private static String summarizeTags(List<String> tags) {
        return String.join("；", tags.stream().limit(3).toList());
    }

    private static void pauseBriefly() throws InterruptedException {
        Thread.sleep(10L);
    }
}
