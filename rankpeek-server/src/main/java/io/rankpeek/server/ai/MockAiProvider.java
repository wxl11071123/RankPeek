package io.rankpeek.server.ai;

import io.rankpeek.server.analysis.AnalysisPrompt;
import io.rankpeek.server.analysis.PromptContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockAiProvider implements AiProvider {

    @Override
    public AnalysisResult generateAnalysis(AnalysisPrompt prompt) {
        PromptContext context = prompt.context();
        String analysisId = "mock-pregame-"
                + context.patchKey() + "-"
                + context.championId() + "-"
                + context.role() + "-"
                + context.queueId();
        return new AnalysisResult(
                analysisId,
                "PREGAME",
                "Mock pregame analysis only. No real AI provider is called.",
                context.patchNotes(),
                context.cnMetaNotes(),
                context.lplNotes(),
                context.playstyleCards(),
                List.of("Mock result: review live matchup context manually before acting."),
                new AnalysisCost(0, 0, true)
        );
    }

    public boolean requiresApiKey() {
        return false;
    }
}
