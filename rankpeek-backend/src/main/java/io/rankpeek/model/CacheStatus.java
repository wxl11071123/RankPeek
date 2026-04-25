package io.rankpeek.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheStatus {

    private boolean enabled;
    private String databasePath;
    private long databaseSizeBytes;

    private long summonerCount;
    private long rankCount;
    private long matchCount;
    private long gameDetailCount;
    private long participantCount;
    private long playerMatchIndexCount;
    private long trackedPlayerCount;

    private Long latestMatchCreation;
}
