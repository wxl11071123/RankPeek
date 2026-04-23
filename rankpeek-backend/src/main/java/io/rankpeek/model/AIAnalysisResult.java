package io.rankpeek.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIAnalysisResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 分析内容
     */
    private String content;

    /**
     * 错误信息
     */
    private String error;

    public static AIAnalysisResult success(String content) {
        return AIAnalysisResult.builder()
                .success(true)
                .content(content)
                .build();
    }

    public static AIAnalysisResult error(String error) {
        return AIAnalysisResult.builder()
                .success(false)
                .error(error)
                .build();
    }
}
