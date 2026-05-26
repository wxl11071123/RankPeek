package io.rankpeek.server.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RateLimitPropertiesTest {

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private Environment environment;

    @Test
    void testProfileDisablesRateLimitByDefault() {
        assertThat(environment.getProperty("rankpeek.server.mode")).isEqualTo("test");
        assertThat(environment.getProperty("rankpeek.rate-limit.enabled")).isEqualTo("false");
        assertThat(properties.enabled()).isFalse();
    }
}
