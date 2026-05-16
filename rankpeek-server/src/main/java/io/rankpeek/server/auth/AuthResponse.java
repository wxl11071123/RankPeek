package io.rankpeek.server.auth;

public record AuthResponse(
        UserResponse user,
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
}
