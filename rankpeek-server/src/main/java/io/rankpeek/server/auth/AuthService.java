package io.rankpeek.server.auth;

import io.rankpeek.server.common.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_DISPLAY_NAME_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final AuthRepository authRepository;
    private final PasswordService passwordService;
    private final TokenService tokenService;

    public AuthService(AuthRepository authRepository, PasswordService passwordService, TokenService tokenService) {
        this.authRepository = authRepository;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String userAgent) {
        String email = normalizeEmail(request.email());
        String displayName = normalizeDisplayName(request.displayName());
        validatePassword(request.password());

        if (authRepository.findUserByEmail(email).isPresent()) {
            throw emailAlreadyRegistered();
        }

        AuthUser user;
        try {
            user = authRepository.insertUser(email, displayName, passwordService.hash(request.password()), Instant.now());
        } catch (DuplicateKeyException exception) {
            throw emailAlreadyRegistered();
        }

        return issueAuthResponse(user, userAgent);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent) {
        String email = normalizeEmail(request.email());
        if (request.password() == null || request.password().isBlank()) {
            throw invalidCredentials();
        }

        AuthUser user = authRepository.findUserByEmail(email).orElseThrow(AuthService::invalidCredentials);
        if (!passwordService.matches(request.password(), user.passwordHash())) {
            throw invalidCredentials();
        }
        ensureActive(user);

        Instant now = Instant.now();
        authRepository.updateLastLoginAt(user.id(), now);
        AuthUser updated = authRepository.findUserById(user.id()).orElse(user);
        return issueAuthResponse(updated, userAgent);
    }

    @Transactional
    public RefreshResponse refresh(RefreshTokenRequest request) {
        StoredRefreshToken refreshToken = requireValidRefreshToken(request.refreshToken(), Instant.now());
        AuthUser user = authRepository.findUserById(refreshToken.userId()).orElseThrow(AuthService::invalidRefreshToken);
        ensureActive(user);

        authRepository.markRefreshTokenUsed(refreshToken.id(), Instant.now());
        return new RefreshResponse(
                tokenService.createAccessToken(UserResponse.from(user)),
                tokenService.accessTokenTtlSeconds()
        );
    }

    @Transactional
    public LogoutResponse logout(RefreshTokenRequest request) {
        StoredRefreshToken refreshToken = requireValidRefreshToken(request.refreshToken(), Instant.now());
        boolean revoked = authRepository.revokeRefreshToken(refreshToken.id(), Instant.now());
        return new LogoutResponse(revoked);
    }

    public UserResponse currentUser(String authorizationHeader) {
        String accessToken = requireBearerToken(authorizationHeader);
        AccessTokenClaims claims = tokenService.verifyAccessToken(accessToken);
        AuthUser user = authRepository.findUserById(claims.userId()).orElseThrow(AuthService::invalidAccessToken);
        ensureActive(user);
        return UserResponse.from(user);
    }

    private AuthResponse issueAuthResponse(AuthUser user, String userAgent) {
        UserResponse responseUser = UserResponse.from(user);
        String accessToken = tokenService.createAccessToken(responseUser);
        String refreshToken = tokenService.createRefreshToken();
        Instant now = Instant.now();
        authRepository.insertRefreshToken(
                user.id(),
                tokenService.hashRefreshToken(refreshToken),
                now.plusSeconds(tokenService.refreshTokenTtlSeconds()),
                now,
                normalizeUserAgent(userAgent)
        );
        return new AuthResponse(responseUser, accessToken, refreshToken, tokenService.accessTokenTtlSeconds());
    }

    private StoredRefreshToken requireValidRefreshToken(String refreshToken, Instant now) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidRefreshToken();
        }
        StoredRefreshToken stored = authRepository.findRefreshTokenByHash(tokenService.hashRefreshToken(refreshToken))
                .orElseThrow(AuthService::invalidRefreshToken);
        if (stored.revokedAt() != null || !stored.expiresAt().isAfter(now)) {
            throw invalidRefreshToken();
        }
        return stored;
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email is required");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Email must be valid");
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
            throw new IllegalArgumentException("Display name must be 64 characters or fewer");
        }
        return normalized;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
    }

    private static String normalizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String normalized = userAgent.trim();
        if (normalized.length() <= MAX_USER_AGENT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_USER_AGENT_LENGTH);
    }

    private static String requireBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw invalidAccessToken();
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw invalidAccessToken();
        }
        return token;
    }

    private static void ensureActive(AuthUser user) {
        if (!"ACTIVE".equals(user.status())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "Account is not active");
        }
    }

    private static ApiException emailAlreadyRegistered() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }

    private static ApiException invalidRefreshToken() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "REFRESH_TOKEN_INVALID",
                "Refresh token is invalid, expired, or revoked"
        );
    }

    private static ApiException invalidAccessToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "ACCESS_TOKEN_INVALID", "Invalid or expired access token");
    }
}
