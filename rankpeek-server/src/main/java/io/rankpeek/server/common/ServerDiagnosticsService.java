package io.rankpeek.server.common;

import io.rankpeek.server.ai.DeepSeekAiProperties;
import io.rankpeek.server.auth.AuthProperties;
import io.rankpeek.server.auth.PasswordResetEmailProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.DatabaseMetaData;
import java.util.List;

@Service
public class ServerDiagnosticsService {

    private final ServerProperties properties;
    private final AuthProperties authProperties;
    private final PasswordResetEmailProperties passwordResetEmailProperties;
    private final DeepSeekAiProperties aiProperties;
    private final RateLimitProperties rateLimitProperties;
    private final JdbcTemplate jdbcTemplate;

    public ServerDiagnosticsService(
            ServerProperties properties,
            AuthProperties authProperties,
            PasswordResetEmailProperties passwordResetEmailProperties,
            DeepSeekAiProperties aiProperties,
            RateLimitProperties rateLimitProperties,
            JdbcTemplate jdbcTemplate
    ) {
        this.properties = properties;
        this.authProperties = authProperties;
        this.passwordResetEmailProperties = passwordResetEmailProperties;
        this.aiProperties = aiProperties;
        this.rateLimitProperties = rateLimitProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ServerDiagnostics diagnostics() {
        DatabaseDiagnostics database = databaseDiagnostics();
        FlywayDiagnostics flyway = flywayDiagnostics();
        ConfigurationDiagnostics configuration = configurationDiagnostics();

        String status = "ok".equals(database.status())
                && "ok".equals(flyway.status())
                && "ok".equals(configuration.status())
                ? "ok"
                : "degraded";
        return new ServerDiagnostics(
                status,
                properties.service(),
                properties.mode(),
                properties.version(),
                database,
                flyway,
                configuration
        );
    }

    private DatabaseDiagnostics databaseDiagnostics() {
        try {
            DatabaseDiagnostics diagnostics = jdbcTemplate.execute((ConnectionCallback<DatabaseDiagnostics>) connection -> {
                DatabaseMetaData metadata = connection.getMetaData();
                return new DatabaseDiagnostics("ok", metadata.getDatabaseProductName(),
                        metadata.getDatabaseProductVersion(), null);
            });
            if (diagnostics == null) {
                return new DatabaseDiagnostics("error", null, null, "database_unavailable");
            }
            return diagnostics;
        } catch (DataAccessException exception) {
            return new DatabaseDiagnostics("error", null, null, "database_unavailable");
        }
    }

    private FlywayDiagnostics flywayDiagnostics() {
        try {
            Integer appliedCount = jdbcTemplate.queryForObject(
                    "select count(*) from flyway_schema_history where success = true",
                    Integer.class);
            List<FlywayMigration> latest = jdbcTemplate.query(
                    "select version, description from flyway_schema_history where success = true order by installed_rank desc limit 1",
                    (resultSet, rowNumber) -> new FlywayMigration(
                            resultSet.getString("version"),
                            resultSet.getString("description")));

            if (latest == null || latest.isEmpty()) {
                return new FlywayDiagnostics("missing", null, appliedCount == null ? 0 : appliedCount,
                        null, "flyway_history_missing");
            }

            FlywayMigration migration = latest.getFirst();
            return new FlywayDiagnostics("ok", migration.version(), appliedCount == null ? 0 : appliedCount,
                    migration.description(), null);
        } catch (BadSqlGrammarException exception) {
            return new FlywayDiagnostics("missing", null, 0, null, "flyway_history_missing");
        } catch (DataAccessException exception) {
            return new FlywayDiagnostics("error", null, 0, null, "flyway_query_failed");
        }
    }

    private ConfigurationDiagnostics configurationDiagnostics() {
        boolean publicRegistrationEnabled = Boolean.TRUE.equals(authProperties.publicRegistrationEnabled());
        boolean rateLimitEnabled = Boolean.TRUE.equals(rateLimitProperties.enabled());
        boolean prodMode = "prod".equalsIgnoreCase(properties.mode());
        boolean wildcardCors = properties.cors().allowedOrigins().stream().anyMatch("*"::equals);
        String status = prodMode && (publicRegistrationEnabled || !rateLimitEnabled || wildcardCors)
                ? "degraded"
                : "ok";

        return new ConfigurationDiagnostics(
                status,
                publicRegistrationEnabled,
                passwordResetEmailProperties.enabled(),
                aiProperties.deepSeekEnabled(),
                aiProperties.provider(),
                aiProperties.model(),
                rateLimitEnabled,
                properties.cors().allowedOrigins()
        );
    }

    private record FlywayMigration(String version, String description) {
    }
}
