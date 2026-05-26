package io.rankpeek.server.credits;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rankpeek.credits")
public record CreditProperties(int coachSummaryChargeCredits) {

    public CreditProperties {
        if (coachSummaryChargeCredits <= 0) {
            coachSummaryChargeCredits = 1;
        }
    }
}
