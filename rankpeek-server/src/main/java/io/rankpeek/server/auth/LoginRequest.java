package io.rankpeek.server.auth;

public record LoginRequest(String email, String password) {
}
