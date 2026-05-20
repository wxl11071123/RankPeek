package io.rankpeek.server.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class AuthAdminBootstrap implements ApplicationRunner {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_DISPLAY_NAME_LENGTH = 64;

    private final AuthProperties authProperties;
    private final AuthRepository authRepository;
    private final PasswordService passwordService;

    public AuthAdminBootstrap(
            AuthProperties authProperties,
            AuthRepository authRepository,
            PasswordService passwordService
    ) {
        this.authProperties = authProperties;
        this.authRepository = authRepository;
        this.passwordService = passwordService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AuthProperties.InitialAdmin initialAdmin = authProperties.initialAdmin();
        if (initialAdmin == null || !initialAdmin.enabled()) {
            return;
        }

        authRepository.upsertInitialAdmin(
                normalizeEmail(initialAdmin.email()),
                normalizeDisplayName(initialAdmin.displayName()),
                hashPassword(initialAdmin.password()),
                Instant.now()
        );
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalStateException("rankpeek.auth.initial-admin.email must be configured");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalStateException("rankpeek.auth.initial-admin.email must be a valid email");
        }
        return normalized;
    }

    private static String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String normalized = displayName.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalStateException("rankpeek.auth.initial-admin.display-name must be 64 characters or fewer");
        }
        return normalized;
    }

    private String hashPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("rankpeek.auth.initial-admin.password must be at least 8 characters");
        }
        return passwordService.hash(password);
    }
}
