package io.rankpeek.server.opgg;

public interface OpggSourceClient {
    OpggChampionDetail fetchChampionDetail(OpggChampionDetailQuery query);

    OpggChampionList fetchChampionList(OpggChampionListQuery query);
}
