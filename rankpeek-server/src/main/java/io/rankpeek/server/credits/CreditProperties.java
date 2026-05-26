package io.rankpeek.server.credits;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rankpeek.credits")
public record CreditProperties(int coachSummaryChargeCredits, int aiStreamChargeCredits) {

    public CreditProperties {
        if (coachSummaryChargeCredits <= 0) {
            coachSummaryChargeCredits = 1;
        }
        if (aiStreamChargeCredits <= 0) {
            aiStreamChargeCredits = 1;
        }
    }
}
