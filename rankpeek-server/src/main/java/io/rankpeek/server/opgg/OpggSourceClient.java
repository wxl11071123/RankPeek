package io.rankpeek.server.opgg;

@FunctionalInterface
public interface OpggSourceClient {
    OpggChampionDetail fetchChampionDetail(OpggChampionDetailQuery query);
}
