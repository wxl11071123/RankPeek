package io.rankpeek.server.common;

public record FlywayDiagnostics(String status, String currentVersion, Integer appliedCount, String latestDescription,
                                String error) {
}
