package io.rankpeek.server.opgg;

@FunctionalInterface
public interface OpggChampionDetailProvider {
    OpggChampionDetail getChampionDetail(OpggChampionDetailQuery query);
}
