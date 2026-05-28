package io.rankpeek.server.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class NoopEmailVerificationSender implements EmailVerificationSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoopEmailVerificationSender.class);

    @Override
    public void sendRegisterVerificationCode(String email, String code, Instant expiresAt) {
        LOGGER.warn("Email verification sender is not configured; code was not delivered for email={} expires_at={}",
                email,
                expiresAt);
    }
}
