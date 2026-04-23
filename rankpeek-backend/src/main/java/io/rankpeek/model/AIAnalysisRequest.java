package io.rankpeek.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 分析请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIAnalysisRequest {

    /**
     * 分析模式：overview（整局总览）、player（单人复盘）
     */
    private String mode;

    /**
     * 玩家 ID（单人复盘时使用）
     */
    private Integer participantId;

    /**
     * 游戏ID
     */
    private Long gameId;
}
