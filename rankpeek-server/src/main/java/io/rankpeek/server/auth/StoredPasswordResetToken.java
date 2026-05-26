package io.rankpeek.server.auth;

import java.time.Instant;

public record StoredPasswordResetToken(
        Long id,
        Long userId,
        String tokenHash,
        Instant expiresAt,
        Instant usedAt,
        Instant createdAt
) {
}
