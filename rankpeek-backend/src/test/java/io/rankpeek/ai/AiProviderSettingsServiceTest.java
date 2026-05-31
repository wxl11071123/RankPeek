package io.rankpeek.ai;

import io.rankpeek.cache.LocalCacheSchemaInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderSettingsServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AiProviderSettingsService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:rankpeek-ai-provider-" + System.nanoTime()
                        + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        new LocalCacheSchemaInitializer(jdbcTemplate).initializeSchema();
        service = new AiProviderSettingsService(new AiProviderSettingsRepository(jdbcTemplate));
    }

    @Test
    void initializeSchema_createsAiProviderSettingsTable() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'ai_provider_settings'
                """, String.class);

        assertThat(columns).contains(
                "id",
                "enabled",
                "provider_id",
                "base_url",
                "model",
                "api_key_encrypted",
                "api_key_masked",
                "temperature",
                "max_tokens",
                "pricing_raw_json",
                "updated_at"
        );
    }

    @Test
    void getSettings_returnsDeepSeekDefaultsWithoutRawApiKey() {
        AiProviderSettings settings = service.getSettings();

        assertThat(settings.enabled()).isFalse();
        assertThat(settings.providerId()).isEqualTo("deepseek");
        assertThat(settings.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(settings.model()).isEqualTo("deepseek-v4-flash");
        assertThat(settings.apiKeySaved()).isFalse();
        assertThat(settings.apiKeyMasked()).isNull();
        assertThat(settings.temperature()).isEqualTo(0.4d);
        assertThat(settings.maxTokens()).isEqualTo(4096);
        assertThat(settings.pricing().currency()).isEqualTo("CNY");
        assertThat(settings.pricing().inputCacheHitCnyPerMillionTokens()).isEqualByComparingTo("0.02");
        assertThat(settings.pricing().inputCacheMissCnyPerMillionTokens()).isEqualByComparingTo("1");
        assertThat(settings.pricing().outputCnyPerMillionTokens()).isEqualByComparingTo("2");
    }

    @Test
    void saveSettings_normalizesBaseUrlMasksApiKeyAndPersistsPricing() {
        AiProviderSettings settings = service.saveSettings(new AiProviderSettingsSaveRequest(
                true,
                "deepseek",
                " https://api.deepseek.com/// ",
                "deepseek-v4-pro",
                "sk-1234567890abcdef",
                true,
                0.7d,
                2048,
                new AiProviderPricing(
                        "CNY",
                        new BigDecimal("0.025"),
                        new BigDecimal("3"),
                        new BigDecimal("6")
                )
        ));

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(settings.model()).isEqualTo("deepseek-v4-pro");
        assertThat(settings.apiKeySaved()).isTrue();
        assertThat(settings.apiKeyMasked()).isEqualTo("sk-...cdef");
        assertThat(settings.temperature()).isEqualTo(0.7d);
        assertThat(settings.maxTokens()).isEqualTo(2048);
        assertThat(settings.pricing().inputCacheHitCnyPerMillionTokens()).isEqualByComparingTo("0.025");

        String storedKey = jdbcTemplate.queryForObject(
                "SELECT api_key_encrypted FROM ai_provider_settings WHERE id = 'default'",
                String.class
        );
        assertThat(storedKey).isEqualTo("sk-1234567890abcdef");
        assertThat(service.getSettings().apiKeyMasked()).isEqualTo("sk-...cdef");
    }

    @Test
    void saveSettings_rejectsBlankModelWhenEnabled() {
        assertThatThrownBy(() -> service.saveSettings(new AiProviderSettingsSaveRequest(
                true,
                "deepseek",
                "https://api.deepseek.com",
                " ",
                "sk-test",
                true,
                0.4d,
                4096,
                null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
    }
}
