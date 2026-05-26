package io.rankpeek.server.auth;

import io.rankpeek.server.common.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
    private final AuthProperties authProperties;

    public AuthService(
            AuthRepository authRepository,
            PasswordService passwordService,
            TokenService tokenService,
            AuthProperties authProperties
    ) {
        this.authRepository = authRepository;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.authProperties = authProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String userAgent) {
        if (!Boolean.TRUE.equals(authProperties.publicRegistrationEnabled())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PUBLIC_REGISTRATION_DISABLED",
                    "Public registration is disabled"
            );
        }
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
        Instant now = Instant.now();
        StoredRefreshToken refreshToken = requireValidRefreshToken(request.refreshToken(), now);
        AuthUser user = authRepository.findUserById(refreshToken.userId()).orElseThrow(AuthService::invalidRefreshToken);
        ensureActive(user);

        authRepository.markRefreshTokenUsed(refreshToken.id(), now);
        authRepository.revokeRefreshToken(refreshToken.id(), now);
        String rotatedRefreshToken = tokenService.createRefreshToken();
        authRepository.insertRefreshToken(
                user.id(),
                tokenService.hashRefreshToken(rotatedRefreshToken),
                now.plusSeconds(tokenService.refreshTokenTtlSeconds()),
                now,
                refreshToken.userAgent()
        );
        return new RefreshResponse(
                tokenService.createAccessToken(UserResponse.from(user)),
                rotatedRefreshToken,
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
        return UserResponse.from(requireCurrentUser(authorizationHeader));
    }

    public AuthUser requireCurrentUser(String authorizationHeader) {
        String accessToken = requireBearerToken(authorizationHeader);
        AccessTokenClaims claims = tokenService.verifyAccessToken(accessToken);
        AuthUser user = authRepository.findUserById(claims.userId()).orElseThrow(AuthService::invalidAccessToken);
        ensureActive(user);
        return user;
    }

    public AuthUser requireAdmin(String authorizationHeader) {
        AuthUser user = requireCurrentUser(authorizationHeader);
        if (!"ADMIN".equals(user.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Admin role is required");
        }
        return user;
    }

    public AdminUserListResponse listUsers(
            AuthUser admin,
            String query,
            String status,
            String role,
            int limit,
            int offset
    ) {
        ensureAdmin(admin);
        int normalizedLimit = normalizeLimit(limit);
        int normalizedOffset = normalizeOffset(offset);
        String normalizedStatus = normalizeOptionalStatus(status);
        String normalizedRole = normalizeOptionalRole(role);
        String normalizedQuery = normalizeOptionalQuery(query);

        List<AdminUserResponse> users = authRepository.findUsers(
                        normalizedQuery,
                        normalizedStatus,
                        normalizedRole,
                        normalizedLimit,
                        normalizedOffset
                ).stream()
                .map(AdminUserResponse::from)
                .toList();
        long total = authRepository.countUsers(normalizedQuery, normalizedStatus, normalizedRole);
        return new AdminUserListResponse(users, total, normalizedLimit, normalizedOffset);
    }

    @Transactional
    public AdminUserResponse updateUserByAdmin(AuthUser admin, Long userId, AdminUserUpdateRequest request) {
        ensureAdmin(admin);
        if (userId == null) {
            throw new IllegalArgumentException("User id is required");
        }
        AuthUser target = authRepository.findUserById(userId).orElseThrow(AuthService::userNotFound);
        String status = request == null ? null : request.status();
        String role = request == null ? null : request.role();
        String normalizedStatus = status == null ? target.status() : normalizeRequiredStatus(status);
        String normalizedRole = role == null ? target.role() : normalizeRequiredRole(role);

        if (admin.id().equals(userId) && (!"ACTIVE".equals(normalizedStatus) || !"ADMIN".equals(normalizedRole))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_MODIFY_SELF", "Admin cannot disable or demote self");
        }

        AuthUser updated = authRepository.updateUserStatusAndRole(
                target.id(),
                normalizedStatus,
                normalizedRole,
                Instant.now()
        );
        if ("DISABLED".equals(updated.status())) {
            authRepository.revokeRefreshTokensForUser(updated.id(), Instant.now());
        }
        return AdminUserResponse.from(updated);
    }

    @Transactional
    public AdminUserSessionRevokeResponse revokeUserSessionsByAdmin(AuthUser admin, Long userId) {
        ensureAdmin(admin);
        if (userId == null) {
            throw new IllegalArgumentException("User id is required");
        }
        AuthUser target = authRepository.findUserById(userId).orElseThrow(AuthService::userNotFound);
        int revokedCount = authRepository.revokeRefreshTokensForUser(target.id(), Instant.now());
        return new AdminUserSessionRevokeResponse(target.id(), revokedCount);
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

    private static String normalizeOptionalQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String normalized = query.trim();
        if (normalized.length() > 320) {
            throw new IllegalArgumentException("Query must be 320 characters or fewer");
        }
        return normalized;
    }

    private static String normalizeOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return normalizeRequiredStatus(status);
    }

    private static String normalizeRequiredStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new IllegalArgumentException("Status must be ACTIVE or DISABLED");
        }
        return normalized;
    }

    private static String normalizeOptionalRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return normalizeRequiredRole(role);
    }

    private static String normalizeRequiredRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!"USER".equals(normalized) && !"ADMIN".equals(normalized)) {
            throw new IllegalArgumentException("Role must be USER or ADMIN");
        }
        return normalized;
    }

    private static int normalizeLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        return limit;
    }

    private static int normalizeOffset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be zero or greater");
        }
        return offset;
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

    private static void ensureAdmin(AuthUser user) {
        if (!"ADMIN".equals(user.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Admin role is required");
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

    private static ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found");
    }
}
