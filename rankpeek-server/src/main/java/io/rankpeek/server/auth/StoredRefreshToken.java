package io.rankpeek.server.auth;

import java.time.Instant;

public record StoredRefreshToken(
        Long id,
        Long userId,
        String tokenHash,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        Instant lastUsedAt,
        String userAgent
) {
}
