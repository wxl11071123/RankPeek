package io.rankpeek.cost;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManualCostItem(
        long id,
        String label,
        String category,
        BigDecimal amountCny,
        String cadence,
        LocalDate effectiveDate,
        String note,
        boolean active,
        long createdAt,
        long updatedAt
) {
}
