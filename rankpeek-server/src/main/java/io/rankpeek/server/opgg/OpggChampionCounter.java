package io.rankpeek.server.opgg;

public record OpggChampionCounter(
        int championId,
        long games,
        Long wins
) {
}
