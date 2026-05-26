package io.rankpeek.server.analysis;

import io.rankpeek.server.credits.AiAnalysisRun;

import java.time.Instant;

public record AnalysisRunSummaryResponse(
        Long id,
        Long userId,
        String endpoint,
        String provider,
        String model,
        String status,
        int chargedCredits,
        int refundedCredits,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        String errorCode,
        Instant createdAt,
        Instant completedAt
) {
    public static AnalysisRunSummaryResponse from(AiAnalysisRun run) {
        return new AnalysisRunSummaryResponse(
                run.id(),
                run.userId(),
                run.endpoint(),
                run.provider(),
                run.model(),
                run.status(),
                run.chargedCredits(),
                run.refundedCredits(),
                run.promptTokens(),
                run.completionTokens(),
                run.totalTokens(),
                run.errorCode(),
                run.createdAt(),
                run.completedAt()
        );
    }
}
