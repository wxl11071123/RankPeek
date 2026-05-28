package io.rankpeek.server.auth;

public record EmailVerificationResponse(boolean accepted, long expiresInSeconds) {
}
