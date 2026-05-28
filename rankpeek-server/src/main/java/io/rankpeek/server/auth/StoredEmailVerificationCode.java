package io.rankpeek.server.auth;

import java.time.Instant;

public record StoredEmailVerificationCode(
        Long id,
        String email,
        String purpose,
        String codeHash,
        Instant expiresAt,
        Instant consumedAt,
        Instant createdAt
) {
}
