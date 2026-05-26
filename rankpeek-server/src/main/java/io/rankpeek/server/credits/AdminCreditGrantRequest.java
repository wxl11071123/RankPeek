package io.rankpeek.server.credits;

public record AdminCreditGrantRequest(Long userId, int amount, String reason) {
}
