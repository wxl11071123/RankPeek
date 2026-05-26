package io.rankpeek.server.common;

public record ServerDiagnostics(String status, String service, String mode, String version,
                                DatabaseDiagnostics database, FlywayDiagnostics flyway,
                                ConfigurationDiagnostics configuration) {
}
