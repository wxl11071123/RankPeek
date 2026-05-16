package io.rankpeek.server.auth;

public record RefreshResponse(String accessToken, long expiresInSeconds) {
}
