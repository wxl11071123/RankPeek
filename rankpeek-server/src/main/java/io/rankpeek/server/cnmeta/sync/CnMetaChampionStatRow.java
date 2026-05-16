package io.rankpeek.server.cnmeta.sync;

import java.math.BigDecimal;

public record CnMetaChampionStatRow(
        Integer championId,
        String role,
        String tierScope,
        BigDecimal winRate,
        BigDecimal pickRate,
        BigDecimal banRate,
        BigDecimal avgKda,
        BigDecimal avgGold,
        BigDecimal avgDamageShare,
        BigDecimal avgDamageTakenShare,
        Integer rankIndex,
        String sampleNote,
        BigDecimal avgDamage,
        BigDecimal avgDamageTaken,
        BigDecimal avgHeal,
        Integer avgDurationSeconds,
        BigDecimal avgKills,
        BigDecimal avgAssists,
        String dataSourceNote
) {
    public CnMetaChampionStatRow(
            Integer championId,
            String role,
            String tierScope,
            BigDecimal winRate,
            BigDecimal pickRate,
            BigDecimal banRate,
            BigDecimal avgKda,
            BigDecimal avgGold,
            BigDecimal avgDamageShare,
            BigDecimal avgDamageTakenShare,
            Integer rankIndex,
            String sampleNote
    ) {
        this(
                championId,
                role,
                tierScope,
                winRate,
                pickRate,
                banRate,
                avgKda,
                avgGold,
                avgDamageShare,
                avgDamageTakenShare,
                rankIndex,
                sampleNote,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
