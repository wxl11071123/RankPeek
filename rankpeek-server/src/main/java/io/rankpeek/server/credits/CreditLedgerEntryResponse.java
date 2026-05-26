package io.rankpeek.server.credits;

import java.time.Instant;

public record CreditLedgerEntryResponse(
        Long id,
        String type,
        int amount,
        int balanceAfter,
        String referenceType,
        String referenceId,
        String reason,
        Instant createdAt
) {
    public static CreditLedgerEntryResponse from(CreditLedgerEntry entry) {
        return new CreditLedgerEntryResponse(
                entry.id(),
                entry.type(),
                entry.amount(),
                entry.balanceAfter(),
                entry.referenceType(),
                entry.referenceId(),
                entry.reason(),
                entry.createdAt()
        );
    }
}
