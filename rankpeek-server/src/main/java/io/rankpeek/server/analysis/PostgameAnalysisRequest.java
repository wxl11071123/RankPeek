package io.rankpeek.server.analysis;

import java.util.Map;

public record PostgameAnalysisRequest(
        String mode,
        String snapshotSchemaVersion,
        Map<String, Object> snapshot
) {
}
