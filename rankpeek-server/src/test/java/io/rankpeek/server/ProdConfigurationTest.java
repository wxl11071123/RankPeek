package io.rankpeek.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ProdConfigurationTest {

    @Test
    void applicationProdUsesPostgresAndSafeServerDefaults() throws IOException {
        PropertySource<?> properties = loadProdProperties();

        assertThat(properties.getProperty("server.address")).isEqualTo("${RANKPEEK_SERVER_ADDRESS:127.0.0.1}");
        assertThat(properties.getProperty("server.port")).isEqualTo("${RANKPEEK_SERVER_PORT:18080}");
        assertThat(properties.getProperty("spring.datasource.url"))
                .isEqualTo("${RANKPEEK_SERVER_DB_URL:jdbc:postgresql://127.0.0.1:5432/rankpeek_server}");
        assertThat(properties.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("org.postgresql.Driver");
        assertThat(properties.getProperty("rankpeek.server.mode")).isEqualTo("prod");
        assertThat(properties.getProperty("rankpeek.server.cors.allowed-origins"))
                .isEqualTo("${RANKPEEK_CORS_ALLOWED_ORIGINS:http://localhost:5173}");
        assertThat(properties.getProperty("rankpeek.auth.access-token-secret"))
                .isEqualTo("${RANKPEEK_AUTH_ACCESS_TOKEN_SECRET}");
        assertThat(properties.getProperty("rankpeek.auth.public-registration-enabled"))
                .isEqualTo("${RANKPEEK_PUBLIC_REGISTRATION_ENABLED:false}");
        assertThat(properties.getProperty("rankpeek.auth.password-reset-email.enabled"))
                .isEqualTo("${RANKPEEK_PASSWORD_RESET_EMAIL_ENABLED:false}");
        assertThat(properties.getProperty("rankpeek.auth.password-reset-email.from"))
                .isEqualTo("${RANKPEEK_PASSWORD_RESET_EMAIL_FROM:}");
        assertThat(properties.getProperty("rankpeek.auth.password-reset-email.reset-url-base"))
                .isEqualTo("${RANKPEEK_PASSWORD_RESET_URL_BASE:}");
        assertThat(properties.getProperty("rankpeek.auth.password-reset-email.subject"))
                .isEqualTo("${RANKPEEK_PASSWORD_RESET_EMAIL_SUBJECT:RankPeek password reset}");
        assertThat(properties.getProperty("rankpeek.rate-limit.enabled"))
                .isEqualTo("${RANKPEEK_RATE_LIMIT_ENABLED:true}");
        assertThat(properties.getProperty("rankpeek.rate-limit.auth.max-requests"))
                .isEqualTo("${RANKPEEK_RATE_LIMIT_AUTH_MAX_REQUESTS:20}");
        assertThat(properties.getProperty("rankpeek.rate-limit.ai.max-requests"))
                .isEqualTo("${RANKPEEK_RATE_LIMIT_AI_MAX_REQUESTS:10}");
        assertThat(properties.getProperty("rankpeek.cn-meta.sync.enabled"))
                .isEqualTo("${RANKPEEK_CN_META_SYNC_ENABLED:false}");
        assertThat(properties.getProperty("rankpeek.cn-meta.sync.source"))
                .isEqualTo("${RANKPEEK_CN_META_SYNC_SOURCE:mock}");
        assertThat(properties.getProperty("rankpeek.cn-meta.sync.roles[0]")).isEqualTo("ALL");
        assertThat(properties.getProperty("rankpeek.cn-meta.sync.real-source-enabled"))
                .isEqualTo("${RANKPEEK_CN_META_REAL_SOURCE_ENABLED:false}");
        assertThat(properties.getProperty("rankpeek.cn-meta.sync.real-endpoint-template"))
                .isEqualTo("${RANKPEEK_CN_META_REAL_ENDPOINT_TEMPLATE:}");
    }

    private static PropertySource<?> loadProdProperties() throws IOException {
        ClassPathResource resource = new ClassPathResource("application-prod.yml");
        assertThat(resource.exists()).isTrue();
        return new YamlPropertySourceLoader().load("application-prod", resource).getFirst();
    }
}
