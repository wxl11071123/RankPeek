package io.rankpeek.server.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rankpeek.auth.password-reset-email")
public record PasswordResetEmailProperties(
        boolean enabled,
        String from,
        String resetUrlBase,
        String subject
) {

    public PasswordResetEmailProperties {
        if (subject == null || subject.isBlank()) {
            subject = "RankPeek password reset";
        }
    }

    String requireFrom() {
        return requireConfigured(from, "rankpeek.auth.password-reset-email.from");
    }

    String requireResetUrlBase() {
        return requireConfigured(resetUrlBase, "rankpeek.auth.password-reset-email.reset-url-base");
    }

    private static String requireConfigured(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured when password reset email is enabled");
        }
        return value.trim();
    }
}
