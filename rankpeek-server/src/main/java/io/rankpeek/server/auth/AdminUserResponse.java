package io.rankpeek.server.auth;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String email,
        String displayName,
        String role,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt
) {

    public static AdminUserResponse from(AuthUser user) {
        return new AdminUserResponse(
                user.id(),
                user.email(),
                user.displayName(),
                user.role(),
                user.status(),
                user.createdAt(),
                user.updatedAt(),
                user.lastLoginAt()
        );
    }
}
