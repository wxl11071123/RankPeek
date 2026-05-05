package io.rankpeek.server.ai;

import io.rankpeek.server.playstyle.PlaystyleCard;

import java.util.List;

public record AnalysisResult(
        String analysisId,
        String type,
        String summary,
        List<String> patchNotes,
        List<String> cnMetaNotes,
        List<String> lplNotes,
        List<PlaystyleCard> playstyleCards,
        List<String> riskTips,
        AnalysisCost cost
) {
}
