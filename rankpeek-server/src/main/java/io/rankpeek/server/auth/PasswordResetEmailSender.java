package io.rankpeek.server.auth;

import java.time.Instant;

public interface PasswordResetEmailSender {

    void sendPasswordResetEmail(AuthUser user, String resetToken, Instant expiresAt);
}
