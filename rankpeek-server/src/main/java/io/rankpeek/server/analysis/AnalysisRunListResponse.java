package io.rankpeek.server.analysis;

import java.util.List;

public record AnalysisRunListResponse(
        List<AnalysisRunSummaryResponse> runs,
        long total,
        int limit,
        int offset
) {
}
