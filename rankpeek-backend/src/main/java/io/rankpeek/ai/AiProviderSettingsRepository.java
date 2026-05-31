package io.rankpeek.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class AiProviderSettingsRepository {

    private static final String DEFAULT_SETTINGS_ID = "default";

    private final JdbcTemplate jdbcTemplate;

    public AiProviderSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<StoredAiProviderSettings> findDefault() {
        return jdbcTemplate.query("""
                SELECT enabled,
                       provider_id,
                       base_url,
                       model,
                       api_key_encrypted,
                       api_key_masked,
                       temperature,
                       max_tokens,
                       pricing_raw_json,
                       updated_at
                FROM ai_provider_settings
                WHERE id = ?
                """, this::mapSettings, DEFAULT_SETTINGS_ID).stream().findFirst();
    }

    public void saveDefault(StoredAiProviderSettings settings) {
        jdbcTemplate.update("""
                MERGE INTO ai_provider_settings (
                    id,
                    enabled,
                    provider_id,
                    base_url,
                    model,
                    api_key_encrypted,
                    api_key_masked,
                    temperature,
                    max_tokens,
                    pricing_raw_json,
                    updated_at
                ) KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                DEFAULT_SETTINGS_ID,
                settings.enabled(),
                settings.providerId(),
                settings.baseUrl(),
                settings.model(),
                settings.apiKeyEncrypted(),
                settings.apiKeyMasked(),
                settings.temperature(),
                settings.maxTokens(),
                settings.pricingRawJson(),
                settings.updatedAt());
    }

    private StoredAiProviderSettings mapSettings(ResultSet rs, int rowNum) throws SQLException {
        return new StoredAiProviderSettings(
                rs.getBoolean("enabled"),
                rs.getString("provider_id"),
                rs.getString("base_url"),
                rs.getString("model"),
                rs.getString("api_key_encrypted"),
                rs.getString("api_key_masked"),
                readNullableDouble(rs, "temperature"),
                rs.getInt("max_tokens"),
                rs.getString("pricing_raw_json"),
                rs.getLong("updated_at")
        );
    }

    private Double readNullableDouble(ResultSet rs, String columnName) throws SQLException {
        double value = rs.getDouble(columnName);
        return rs.wasNull() ? null : value;
    }
}
