package io.rankpeek.server.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "rankpeek.rate-limit")
public record RateLimitProperties(
        Boolean enabled,
        Long windowSeconds,
        Limit auth,
        Limit ai
) {

    public RateLimitProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (windowSeconds == null || windowSeconds <= 0) {
            windowSeconds = 60L;
        }
        if (auth == null) {
            auth = new Limit(20);
        }
        if (ai == null) {
            ai = new Limit(10);
        }
    }

    public Duration window() {
        return Duration.ofSeconds(windowSeconds);
    }

    public record Limit(Integer maxRequests) {
        public Limit {
            if (maxRequests == null || maxRequests <= 0) {
                maxRequests = 1;
            }
        }
    }
}
