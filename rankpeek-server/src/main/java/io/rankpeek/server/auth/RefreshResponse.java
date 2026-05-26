package io.rankpeek.server.auth;

public record RefreshResponse(String accessToken, String refreshToken, long expiresInSeconds) {
}
