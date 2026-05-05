package io.rankpeek.server.ai;

import io.rankpeek.server.analysis.AnalysisPrompt;

public interface AiProvider {

    AnalysisResult generateAnalysis(AnalysisPrompt prompt);
}
