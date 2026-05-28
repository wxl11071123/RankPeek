package io.rankpeek.server.auth;

import java.time.Instant;

public interface EmailVerificationSender {

    void sendRegisterVerificationCode(String email, String code, Instant expiresAt);
}
