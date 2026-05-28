package io.rankpeek.server.auth;

import java.time.Instant;
import java.util.Map;

public class TencentSesEmailVerificationSender implements EmailVerificationSender {

    private final TencentSesTemplateEmailSender templateEmailSender;
    private final TencentSesEmailProperties properties;
    private final AuthProperties authProperties;

    public TencentSesEmailVerificationSender(
            TencentSesTemplateEmailSender templateEmailSender,
            TencentSesEmailProperties properties,
            AuthProperties authProperties
    ) {
        this.templateEmailSender = templateEmailSender;
        this.properties = properties;
        this.authProperties = authProperties;
    }

    @Override
    public void sendRegisterVerificationCode(String email, String code, Instant expiresAt) {
        templateEmailSender.sendTemplateEmail(
                email,
                properties.requireRegisterTemplateId(),
                properties.registerSubject(),
                Map.of(
                        "code", code,
                        "expire_minutes", String.valueOf(Math.max(1, authProperties.emailVerification().codeTtlSeconds() / 60))
                )
        );
    }
}
