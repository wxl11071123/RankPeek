package io.rankpeek.server.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rankpeek.auth")
public record AuthProperties(
        String accessTokenSecret,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds
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
    }
}
