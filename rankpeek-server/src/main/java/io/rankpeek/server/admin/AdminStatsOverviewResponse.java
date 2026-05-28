package io.rankpeek.server.admin;

import java.time.Instant;
import java.util.List;

public record AdminStatsOverviewResponse(
        String date,
        String timeZone,
        Instant generatedAt,
        ActiveUsers activeUsers,
        UserStats users,
        AiStats ai,
        CreditStats credits,
        List<DailyStats> daily
) {
    public record ActiveUsers(
            long last1Hour,
            long last3Hours,
            long last24Hours
    ) {
    }

    public record UserStats(
            long total,
            long active,
            long disabled,
            long admins,
            long registeredToday
    ) {
    }

    public record AiStats(
            long requestsToday,
            long succeededToday,
            long failedToday,
            long reservedToday,
            long promptTokensToday,
            long completionTokensToday,
            long totalTokensToday,
            long chargedCreditsToday,
            long refundedCreditsToday,
            double successRate
    ) {
    }

    public record CreditStats(
            long adminGrantedToday,
            long aiChargedToday,
            long aiRefundedToday,
            long netAiCreditsToday,
            long outstandingBalance
    ) {
    }

    public record DailyStats(
            String date,
            long registeredUsers,
            long aiRequests,
            long aiSucceeded,
            long aiFailed,
            long chargedCredits,
            long refundedCredits,
            long totalTokens
    ) {
    }
}
