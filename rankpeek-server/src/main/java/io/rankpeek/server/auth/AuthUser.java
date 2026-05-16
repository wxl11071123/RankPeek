package io.rankpeek.server.auth;

import java.time.Instant;

public record AuthUser(
        Long id,
        String email,
        String displayName,
        String passwordHash,
        String status,
        String role,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt
) {
}
