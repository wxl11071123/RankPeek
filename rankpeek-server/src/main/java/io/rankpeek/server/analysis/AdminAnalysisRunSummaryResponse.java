package io.rankpeek.server.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.rankpeek.server.credits.AiAnalysisRun;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminAnalysisRunSummaryResponse(
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
        String errorMessage,
        Long chargeLedgerEntryId,
        Long refundLedgerEntryId,
        Instant createdAt,
        Instant completedAt
) {
    public static AdminAnalysisRunSummaryResponse from(AiAnalysisRun run) {
        return new AdminAnalysisRunSummaryResponse(
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
                run.errorMessage(),
                run.chargeLedgerEntryId(),
                run.refundLedgerEntryId(),
                run.createdAt(),
                run.completedAt()
        );
    }
}
