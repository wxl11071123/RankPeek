package io.rankpeek.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheClearResult {

    private boolean cleared;
    private String scope;
    private String message;
    private long deletedRows;
    private long timestamp;
}
