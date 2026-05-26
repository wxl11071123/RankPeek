package io.rankpeek.server.credits;

import java.time.Instant;

public record CreditLedgerEntry(
        Long id,
        Long userId,
        Long actorUserId,
        String type,
        int amount,
        int balanceAfter,
        String idempotencyKey,
        String referenceType,
        String referenceId,
        String reason,
        Instant createdAt
) {
}
