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
        assertThat(properties.getProperty("rankpeek.auth.access-token-secret"))
                .isEqualTo("${RANKPEEK_AUTH_ACCESS_TOKEN_SECRET}");
        assertThat(properties.getProperty("rankpeek.cn-meta.sync.real-source-enabled")).isEqualTo(false);
    }

    private static PropertySource<?> loadProdProperties() throws IOException {
        ClassPathResource resource = new ClassPathResource("application-prod.yml");
        assertThat(resource.exists()).isTrue();
        return new YamlPropertySourceLoader().load("application-prod", resource).getFirst();
    }
}
