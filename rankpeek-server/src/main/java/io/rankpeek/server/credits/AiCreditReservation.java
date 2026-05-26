package io.rankpeek.server.credits;

public record AiCreditReservation(
        Long runId,
        Long userId,
        int chargedCredits,
        boolean chargeApplied
) {
}
