package io.rankpeek.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Cached match-history fetch result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchHistoryFetchResult {

    @Builder.Default
    private List<MatchHistory> matches = new ArrayList<>();

    private boolean rawEmpty;
}
