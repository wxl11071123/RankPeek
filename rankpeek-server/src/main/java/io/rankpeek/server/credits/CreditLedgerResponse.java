package io.rankpeek.server.credits;

import java.util.List;

public record CreditLedgerResponse(List<CreditLedgerEntryResponse> entries) {
}
