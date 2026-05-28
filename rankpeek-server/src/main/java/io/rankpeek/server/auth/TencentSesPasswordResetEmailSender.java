package io.rankpeek.server.auth;

import java.time.Instant;
import java.util.Map;

public class TencentSesPasswordResetEmailSender implements PasswordResetEmailSender {

    private final TencentSesTemplateEmailSender templateEmailSender;
    private final TencentSesEmailProperties properties;
    private final AuthProperties authProperties;

    public TencentSesPasswordResetEmailSender(
            TencentSesTemplateEmailSender templateEmailSender,
            TencentSesEmailProperties properties,
            AuthProperties authProperties
    ) {
        this.templateEmailSender = templateEmailSender;
        this.properties = properties;
        this.authProperties = authProperties;
    }

    @Override
    public void sendPasswordResetEmail(AuthUser user, String resetToken, Instant expiresAt) {
        templateEmailSender.sendTemplateEmail(
                user.email(),
                properties.requirePasswordResetTemplateId(),
                properties.passwordResetSubject(),
                Map.of(
                        "token", resetToken,
                        "expire_minutes", String.valueOf(Math.max(1, authProperties.passwordResetTokenTtlSeconds() / 60))
                )
        );
    }
}
