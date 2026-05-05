package io.rankpeek.server.cnmeta;

import java.math.BigDecimal;

public record CnChampionMeta(
        String source,
        String patchKey,
        Integer queueId,
        String tierScope,
        Integer championId,
        String role,
        BigDecimal winRate,
        BigDecimal pickRate,
        BigDecimal banRate,
        BigDecimal avgKda,
        Integer rankIndex,
        String sampleNote
) {
}
