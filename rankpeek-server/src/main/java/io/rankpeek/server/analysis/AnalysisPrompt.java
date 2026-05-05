package io.rankpeek.server.analysis;

import java.util.List;

public record AnalysisPrompt(
        PromptContext context,
        List<String> allyTeamTags,
        List<String> enemyTeamTags
) {
}
