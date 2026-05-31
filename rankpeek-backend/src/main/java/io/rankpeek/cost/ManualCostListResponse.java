package io.rankpeek.cost;

import java.util.List;

public record ManualCostListResponse(
        List<ManualCostItem> items
) {
}
