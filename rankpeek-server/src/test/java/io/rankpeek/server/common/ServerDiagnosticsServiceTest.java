package io.rankpeek.server.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
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
                jdbcTemplate);

        ServerDiagnostics diagnostics = service.diagnostics();

        assertThat(diagnostics.status()).isEqualTo("degraded");
        assertThat(diagnostics.database().status()).isEqualTo("error");
        assertThat(diagnostics.database().error()).isEqualTo("database_unavailable");
    }
}
