package io.rankpeek.cost;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManualCostRequest(
        String label,
        String category,
        BigDecimal amountCny,
        String cadence,
        LocalDate effectiveDate,
        String note,
        Boolean active
) {
}
