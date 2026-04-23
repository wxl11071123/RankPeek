package io.rankpeek.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.rankpeek.model.AIAnalysisRequest;
import io.rankpeek.model.AIAnalysisResult;
import io.rankpeek.model.ApiResponse;
import io.rankpeek.model.SessionData;
import io.rankpeek.service.AiAnalysisService;
import io.rankpeek.service.SessionAnalysisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAnalysisService aiAnalysisService;
    private final SessionAnalysisService sessionAnalysisService;

    /**
     * 分析对局详情（异步）
     * 
     * @param request 分析请求
     * @return 分析结果
     */
    @PostMapping("/analyze")
    public CompletableFuture<ResponseEntity<ApiResponse<AIAnalysisResult>>> analyzeGameDetail(
            @RequestBody AIAnalysisRequest request) {
        log.info("AI 分析请求：gameId={}, mode={}, participantId={}",
                request.getGameId(), request.getMode(), request.getParticipantId());

        String mode = request.getMode() != null ? request.getMode() : "overview";

        return aiAnalysisService.analyzeGameDetailAsync(
                request.getGameId(),
                mode,
                request.getParticipantId()).thenApply(result -> {
                    log.info("AI 分析完成：gameId={}, success={}", request.getGameId(), result.isSuccess());
                    return ResponseEntity.ok(ApiResponse.success(result));
                }).exceptionally(ex -> {
                    log.error("AI 分析异常：gameId={}, error={}", request.getGameId(), ex.getMessage());
                    AIAnalysisResult errorResult = AIAnalysisResult.error("分析失败: " + ex.getMessage());
                    return ResponseEntity.ok(ApiResponse.success(errorResult));
                });
    }

    /**
     * 分析房间会话数据（组队阶段）
     * 
     * @param analysisMode 分析模式：team（队伍分析）、player（单人分析）
     * @param queueMode    队列模式（可选）
     * @return 分析结果
     */
    @PostMapping("/analyze-session")
    public ApiResponse<AIAnalysisResult> analyzeSession(
            @RequestParam(value = "analysisMode", required = false, defaultValue = "team") String analysisMode,
            @RequestParam(value = "queueMode", required = false) Integer queueMode) {
        log.info("AI 房间分析请求：analysisMode={}, queueMode={}", analysisMode, queueMode);

        SessionData sessionData = sessionAnalysisService.getSessionData(queueMode);
        AIAnalysisResult result = aiAnalysisService.analyzeSessionData(sessionData, analysisMode);

        return ApiResponse.success(result);
    }

    /**
     * 清除分析缓存
     */
    @DeleteMapping("/cache")
    public ApiResponse<Void> clearCache() {
        log.info("清除 AI 分析缓存");
        // 缓存会自动过期，这里可以添加手动清除逻辑
        return ApiResponse.success();
    }
}
