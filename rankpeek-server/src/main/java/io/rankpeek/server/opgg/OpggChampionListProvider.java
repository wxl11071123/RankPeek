package io.rankpeek.server.opgg;

@FunctionalInterface
public interface OpggChampionListProvider {
    OpggChampionList getChampionList(OpggChampionListQuery query);
}
