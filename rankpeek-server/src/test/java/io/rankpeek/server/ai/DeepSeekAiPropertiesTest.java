package io.rankpeek.server.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekAiPropertiesTest {

    @Test
    void defaultsKeepRealAiDisabledAndUseDeepSeekDefaults() {
        DeepSeekAiProperties properties = new DeepSeekAiProperties(
                false,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                null
        );

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.deepSeekEnabled()).isFalse();
        assertThat(properties.provider()).isEqualTo("mock");
        assertThat(properties.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(properties.model()).isEqualTo("deepseek-v4-flash");
        assertThat(properties.connectTimeoutMs()).isEqualTo(5_000);
        assertThat(properties.readTimeoutMs()).isEqualTo(30_000);
        assertThat(properties.maxTokens()).isEqualTo(1600);
        assertThat(properties.temperature()).isEqualTo(0.4);
    }

    @Test
    void enablesDeepSeekOnlyWhenFlagAndProviderMatch() {
        DeepSeekAiProperties properties = new DeepSeekAiProperties(
                true,
                " DeepSeek ",
                " https://api.deepseek.com/ ",
                " deepseek-v4-pro ",
                "secret-key",
                1000,
                2000,
                300,
                0.2
        );

        assertThat(properties.deepSeekEnabled()).isTrue();
        assertThat(properties.provider()).isEqualTo("deepseek");
        assertThat(properties.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(properties.model()).isEqualTo("deepseek-v4-pro");
        assertThat(properties.apiKey()).isEqualTo("secret-key");
        assertThat(properties.temperature()).isEqualTo(0.2);
    }
}
