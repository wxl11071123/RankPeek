package io.rankpeek.server.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class NoopPasswordResetEmailSender implements PasswordResetEmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoopPasswordResetEmailSender.class);

    @Override
    public void sendPasswordResetEmail(AuthUser user, String resetToken, Instant expiresAt) {
        LOGGER.warn(
                "Password reset email sender is not configured; reset token was not delivered for user_id={} expires_at={}",
                user.id(),
                expiresAt
        );
    }
}
