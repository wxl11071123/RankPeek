package io.rankpeek.server.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.server.common.ApiException;
import io.rankpeek.server.common.ServerProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TokenService {

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec accessTokenKey;

    public TokenService(AuthProperties authProperties, ServerProperties serverProperties, ObjectMapper objectMapper) {
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
        validateSecret(serverProperties);
        this.accessTokenKey = new SecretKeySpec(
                authProperties.accessTokenSecret().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
    }

    public long accessTokenTtlSeconds() {
        return authProperties.accessTokenTtlSeconds();
    }

    public long refreshTokenTtlSeconds() {
        return authProperties.refreshTokenTtlSeconds();
    }

    public String createAccessToken(UserResponse user) {
        Instant now = Instant.now();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(user.id()));
        payload.put("email", user.email());
        payload.put("role", user.role());
        payload.put("status", user.status());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plusSeconds(accessTokenTtlSeconds()).getEpochSecond());

        String headerPart = encodeJson(header);
        String payloadPart = encodeJson(payload);
        String signedPart = headerPart + "." + payloadPart;
        return signedPart + "." + sign(signedPart);
    }

    public AccessTokenClaims verifyAccessToken(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length != 3) {
                throw invalidAccessToken();
            }
            String signedPart = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(signedPart).getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw invalidAccessToken();
            }

            JsonNode payload = objectMapper.readTree(BASE64_URL_DECODER.decode(parts[1]));
            long expiresAt = payload.path("exp").asLong(0);
            if (expiresAt <= Instant.now().getEpochSecond()) {
                throw invalidAccessToken();
            }
            Long userId = Long.valueOf(payload.path("sub").asText());
            return new AccessTokenClaims(
                    userId,
                    payload.path("email").asText(),
                    payload.path("role").asText(),
                    payload.path("status").asText()
            );
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidAccessToken();
        }
    }

    public String createRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }

    public String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash refresh token", exception);
        }
    }

    private void validateSecret(ServerProperties serverProperties) {
        String mode = serverProperties.mode();
        boolean devMode = "local-dev".equalsIgnoreCase(mode) || "test".equalsIgnoreCase(mode);
        String secret = authProperties.accessTokenSecret();
        boolean unsafeSecret = secret == null
                || secret.isBlank()
                || AuthProperties.DEFAULT_DEV_SECRET.equals(secret)
                || secret.contains("change-me");
        if (!devMode && unsafeSecret) {
            throw new IllegalStateException("rankpeek.auth.access-token-secret must be configured for non-dev mode");
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create token", exception);
        }
    }

    private String sign(String signedPart) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(accessTokenKey);
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(signedPart.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign token", exception);
        }
    }

    private static ApiException invalidAccessToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "ACCESS_TOKEN_INVALID", "Invalid or expired access token");
    }
}
