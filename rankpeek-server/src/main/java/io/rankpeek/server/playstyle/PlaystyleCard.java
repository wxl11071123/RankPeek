package io.rankpeek.server.playstyle;

import java.math.BigDecimal;
import java.time.Instant;

public record PlaystyleCard(
        Long id,
        String patchKey,
        Integer championId,
        String role,
        String title,
        String cardType,
        String summary,
        String whenToUse,
        String whenNotToUse,
        String coreItemsJson,
        String runesJson,
        String skillOrder,
        String sourceTier,
        BigDecimal confidence,
        String freshnessStatus,
        String status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
    public PlaystyleCard withFreshnessStatus(String nextFreshnessStatus) {
        return new PlaystyleCard(
                id,
                patchKey,
                championId,
                role,
                title,
                cardType,
                summary,
                whenToUse,
                whenNotToUse,
                coreItemsJson,
                runesJson,
                skillOrder,
                sourceTier,
                confidence,
                nextFreshnessStatus,
                status,
                expiresAt,
                createdAt,
                updatedAt
        );
    }
}
