package io.rankpeek.server.opgg;

import java.util.List;

public record OpggChampionListItem(
        int championId,
        Integer tier,
        Integer rank,
        OpggChampionStats stats,
        List<OpggChampionPositionStats> positions
) {
    public OpggChampionListItem {
        positions = positions == null ? List.of() : List.copyOf(positions);
    }
}
