package io.rankpeek.server.analysis;

import java.util.List;

public record AdminAnalysisRunListResponse(
        List<AdminAnalysisRunSummaryResponse> runs,
        long total,
        int limit,
        int offset
) {
}
