package io.rankpeek.server.common;

import java.util.List;

public record ConfigurationDiagnostics(
        String status,
        boolean publicRegistrationEnabled,
        boolean passwordResetEmailEnabled,
        boolean aiEnabled,
        String aiProvider,
        String aiModel,
        boolean rateLimitEnabled,
        List<String> corsAllowedOrigins
) {
}
