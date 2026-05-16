package io.rankpeek.server.auth;

public record AccessTokenClaims(Long userId, String email, String role, String status) {
}
