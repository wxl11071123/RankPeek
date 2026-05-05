package io.rankpeek.server.analysis;

import io.rankpeek.server.ai.AiProvider;
import io.rankpeek.server.ai.AnalysisResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalysisService {

    private final PromptContextService promptContextService;
    private final AiProvider aiProvider;

    public AnalysisService(PromptContextService promptContextService, AiProvider aiProvider) {
        this.promptContextService = promptContextService;
        this.aiProvider = aiProvider;
    }

    public AnalysisResult generatePregameMock(PregameAnalysisRequest request) {
        PromptContext context = promptContextService.buildContext(
                request.championId(),
                request.role(),
                request.patchKey(),
                request.queueId()
        );
        AnalysisPrompt prompt = new AnalysisPrompt(
                context,
                nullToEmpty(request.allyTeamTags()),
                nullToEmpty(request.enemyTeamTags())
        );
        return aiProvider.generateAnalysis(prompt);
    }

    private static List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
