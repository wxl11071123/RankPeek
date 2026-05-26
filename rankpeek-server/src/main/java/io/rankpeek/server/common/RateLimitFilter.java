package io.rankpeek.server.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final RateLimitProperties properties;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            RateLimitProperties properties,
            RateLimitService rateLimitService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        RateLimitRule rule = ruleFor(request);
        if (rule == null || !Boolean.TRUE.equals(properties.enabled())) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitService.RateLimitDecision decision = rateLimitService.consume(
                rule.bucket(),
                subject(request, rule),
                rule.maxRequests(),
                properties.window()
        );
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure("RATE_LIMIT_EXCEEDED", "Too many requests; retry later")
        );
    }

    private RateLimitRule ruleFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if (path.equals("/api/auth/register")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/password-reset/request")
                || path.equals("/api/auth/password-reset/confirm")) {
            return new RateLimitRule("auth", properties.auth().maxRequests(), false);
        }
        if (path.equals("/api/analysis/coach-summary")
                || path.equals("/api/analysis/pregame/stream")
                || path.equals("/api/analysis/postgame/stream")) {
            return new RateLimitRule("ai", properties.ai().maxRequests(), true);
        }
        return null;
    }

    private static String subject(HttpServletRequest request, RateLimitRule rule) {
        if (rule.preferAuthorization()) {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()) {
                return "token:" + sha256(authorization.trim());
            }
        }
        return "ip:" + clientAddress(request);
    }

    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash rate limit subject", exception);
        }
    }

    private record RateLimitRule(String bucket, int maxRequests, boolean preferAuthorization) {
    }
}
