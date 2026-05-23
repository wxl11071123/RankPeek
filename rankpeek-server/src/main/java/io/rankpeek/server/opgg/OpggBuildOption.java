package io.rankpeek.server.opgg;

import java.util.List;

public record OpggBuildOption(
        String label,
        List<Integer> ids,
        Long games,
        Double winRate,
        Double pickRate
) {
}
