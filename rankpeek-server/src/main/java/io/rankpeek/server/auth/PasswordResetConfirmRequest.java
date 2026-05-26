package io.rankpeek.server.auth;

public record PasswordResetConfirmRequest(String token, String newPassword) {
}
