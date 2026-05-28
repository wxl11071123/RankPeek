package io.rankpeek.server.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rankpeek.email.tencent-ses")
public record TencentSesEmailProperties(
        boolean enabled,
        String secretId,
        String secretKey,
        String region,
        String endpoint,
        String fromEmailAddress,
        String registerSubject,
        Long registerTemplateId,
        String passwordResetSubject,
        Long passwordResetTemplateId,
        int connectTimeoutMs,
        int readTimeoutMs
) {
    public TencentSesEmailProperties {
        region = blankToDefault(region, "ap-hongkong");
        endpoint = blankToDefault(endpoint, "https://ses.tencentcloudapi.com");
        registerSubject = blankToDefault(registerSubject, "RankPeek 注册验证码");
        passwordResetSubject = blankToDefault(passwordResetSubject, "RankPeek 密码重置");
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 5000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 10000;
        }
    }

    public String requireSecretId() {
        return requireConfigured(secretId, "rankpeek.email.tencent-ses.secret-id");
    }

    public String requireSecretKey() {
        return requireConfigured(secretKey, "rankpeek.email.tencent-ses.secret-key");
    }

    public String requireFromEmailAddress() {
        return requireConfigured(fromEmailAddress, "rankpeek.email.tencent-ses.from-email-address");
    }

    public long requireRegisterTemplateId() {
        if (registerTemplateId == null || registerTemplateId <= 0) {
            throw new IllegalStateException("rankpeek.email.tencent-ses.register-template-id must be configured");
        }
        return registerTemplateId;
    }

    public long requirePasswordResetTemplateId() {
        if (passwordResetTemplateId == null || passwordResetTemplateId <= 0) {
            throw new IllegalStateException("rankpeek.email.tencent-ses.password-reset-template-id must be configured");
        }
        return passwordResetTemplateId;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String requireConfigured(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured when Tencent Cloud SES email is enabled");
        }
        return value.trim();
    }
}
