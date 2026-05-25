package io.rankpeek.server.analysis;

import io.rankpeek.server.ai.DeepSeekTokenUsage;

import java.util.Map;

public record CoachSummaryAnalysisResponse(
        Map<String, Object> report,
        DeepSeekTokenUsage usage
) {
}
