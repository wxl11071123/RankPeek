package io.rankpeek.server.common;

import io.rankpeek.server.ai.DeepSeekAiProperties;
import io.rankpeek.server.auth.AuthProperties;
import io.rankpeek.server.auth.PasswordResetEmailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerDiagnosticsServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void diagnosticsDegradesWhenDatabaseCheckFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.execute(any(ConnectionCallback.class)))
                .thenThrow(new DataAccessResourceFailureException("connection failed"));

        ServerDiagnosticsService service = new ServerDiagnosticsService(
                new ServerProperties("rankpeek-server", "test", "0.1.0", null),
                new AuthProperties("test-secret", 3600, 2592000, 900, null, true, null),
                new PasswordResetEmailProperties(false, null, null, null),
                new DeepSeekAiProperties(false, "mock", "https://api.deepseek.com", "deepseek-v4-flash", "", 5000, 30000, 4096, 0.4),
                new RateLimitProperties(false, 60L, new RateLimitProperties.Limit(20), new RateLimitProperties.Limit(10)),
                jdbcTemplate);

        ServerDiagnostics diagnostics = service.diagnostics();

        assertThat(diagnostics.status()).isEqualTo("degraded");
        assertThat(diagnostics.database().status()).isEqualTo("error");
        assertThat(diagnostics.database().error()).isEqualTo("database_unavailable");
        assertThat(diagnostics.configuration().status()).isEqualTo("ok");
        assertThat(diagnostics.configuration().initialAdminEnabled()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void diagnosticsDegradesProductionConfigurationWhenUnsafeSwitchesAreEnabled() throws Exception {
        JdbcTemplate jdbcTemplate = okJdbcTemplate();

        ServerDiagnosticsService service = new ServerDiagnosticsService(
                new ServerProperties("rankpeek-server", "prod", "0.1.0", new ServerProperties.Cors(List.of("*"))),
                new AuthProperties("prod-secret", 3600, 2592000, 900, null, true,
                        new AuthProperties.InitialAdmin(true, "admin@example.com", "password", "Admin")),
                new PasswordResetEmailProperties(false, null, null, null),
                new DeepSeekAiProperties(false, "mock", "https://api.deepseek.com", "deepseek-v4-flash", "", 5000, 30000, 4096, 0.4),
                new RateLimitProperties(false, 60L, new RateLimitProperties.Limit(20), new RateLimitProperties.Limit(10)),
                jdbcTemplate);

        ServerDiagnostics diagnostics = service.diagnostics();

        assertThat(diagnostics.status()).isEqualTo("degraded");
        assertThat(diagnostics.database().status()).isEqualTo("ok");
        assertThat(diagnostics.flyway().status()).isEqualTo("ok");
        assertThat(diagnostics.configuration().status()).isEqualTo("degraded");
        assertThat(diagnostics.configuration().publicRegistrationEnabled()).isTrue();
        assertThat(diagnostics.configuration().initialAdminEnabled()).isTrue();
        assertThat(diagnostics.configuration().rateLimitEnabled()).isFalse();
        assertThat(diagnostics.configuration().corsAllowedOrigins()).containsExactly("*");
    }

    @SuppressWarnings("unchecked")
    private static JdbcTemplate okJdbcTemplate() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(metadata.getDatabaseProductName()).thenReturn("H2");
        when(metadata.getDatabaseProductVersion()).thenReturn("2.3");
        when(connection.getMetaData()).thenReturn(metadata);
        when(jdbcTemplate.execute(any(ConnectionCallback.class)))
                .thenAnswer(invocation -> ((ConnectionCallback<?>) invocation.getArgument(0)).doInConnection(connection));
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Integer.class))).thenReturn(9);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenAnswer(invocation -> {
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("version")).thenReturn("9");
                    when(resultSet.getString("description")).thenReturn("password reset tokens");
                    org.springframework.jdbc.core.RowMapper<?> rowMapper = invocation.getArgument(1);
                    return List.of(rowMapper.mapRow(resultSet, 0));
                });
        return jdbcTemplate;
    }
}
