package io.rankpeek.server.auth;

public record AdminUserCreateRequest(String email, String password, String displayName) {
}
