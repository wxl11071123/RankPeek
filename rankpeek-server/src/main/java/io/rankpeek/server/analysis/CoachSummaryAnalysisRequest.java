package io.rankpeek.server.analysis;

public record CoachSummaryAnalysisRequest(
        String inputHash,
        String snapshotSchemaVersion,
        String promptVersion,
        String dataQualityConfidence,
        String systemPrompt,
        String userPrompt
) {
}
