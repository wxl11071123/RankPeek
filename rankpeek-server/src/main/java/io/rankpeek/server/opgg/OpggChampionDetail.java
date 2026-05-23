package io.rankpeek.server.opgg;

import java.time.Instant;
import java.util.List;

public record OpggChampionDetail(
        int championId,
        String championName,
        String mode,
        String region,
        String tier,
        String position,
        String version,
        Instant updatedAt,
        OpggChampionStats stats,
        List<OpggBuildOption> summonerSpells,
        List<OpggBuildOption> runes,
        List<OpggBuildOption> skillOrders,
        List<OpggBuildOption> starterItems,
        List<OpggBuildOption> boots,
        List<OpggBuildOption> coreItems
) {
}
