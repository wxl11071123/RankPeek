package io.rankpeek.server.credits;

import java.time.Instant;

public record AiAnalysisRun(
        Long id,
        Long userId,
        String endpoint,
        String provider,
        String model,
        String status,
        String idempotencyKey,
        String requestHash,
        String responseJson,
        String errorMessage,
        int chargedCredits,
        int refundedCredits,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        String errorCode,
        Long chargeLedgerEntryId,
        Long refundLedgerEntryId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
