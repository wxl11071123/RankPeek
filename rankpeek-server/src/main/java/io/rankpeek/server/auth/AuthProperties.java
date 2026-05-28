package io.rankpeek.server.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rankpeek.auth")
public record AuthProperties(
        String accessTokenSecret,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds,
        long passwordResetTokenTtlSeconds,
        EmailVerification emailVerification,
        Boolean publicRegistrationEnabled,
        InitialAdmin initialAdmin
) {
    public static final String DEFAULT_DEV_SECRET = "rankpeek-local-dev-access-token-secret-change-me";

    public AuthProperties {
        if (accessTokenSecret == null || accessTokenSecret.isBlank()) {
            accessTokenSecret = DEFAULT_DEV_SECRET;
        }
        if (accessTokenTtlSeconds <= 0) {
            accessTokenTtlSeconds = 3600;
        }
        if (refreshTokenTtlSeconds <= 0) {
            refreshTokenTtlSeconds = 2_592_000;
        }
        if (passwordResetTokenTtlSeconds <= 0) {
            passwordResetTokenTtlSeconds = 900;
        }
        if (emailVerification == null) {
            emailVerification = new EmailVerification(false, 900, 60);
        }
        if (publicRegistrationEnabled == null) {
            publicRegistrationEnabled = true;
        }
        if (initialAdmin == null) {
            initialAdmin = new InitialAdmin(false, null, null, null);
        }
    }

    public record InitialAdmin(
            boolean enabled,
            String email,
            String password,
            String displayName
    ) {
    }

    public record EmailVerification(
            boolean required,
            long codeTtlSeconds,
            long resendCooldownSeconds
    ) {
        public EmailVerification {
            if (codeTtlSeconds <= 0) {
                codeTtlSeconds = 900;
            }
            if (resendCooldownSeconds < 0) {
                resendCooldownSeconds = 60;
            }
        }
    }
}
