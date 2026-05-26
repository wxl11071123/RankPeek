package io.rankpeek.server.credits;

public record AdminCreditGrantResponse(
        Long userId,
        int balance,
        boolean duplicate,
        CreditLedgerEntryResponse entry
) {
}
