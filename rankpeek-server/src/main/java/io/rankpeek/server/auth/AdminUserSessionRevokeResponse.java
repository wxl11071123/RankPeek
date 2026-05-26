package io.rankpeek.server.auth;

public record AdminUserSessionRevokeResponse(Long userId, int revokedCount) {
}
