package io.rankpeek.server.analysis;

import java.util.List;

public record PregameAnalysisRequest(
        String patchKey,
        Integer queueId,
        Integer championId,
        String role,
        List<String> allyTeamTags,
        List<String> enemyTeamTags
) {
}
