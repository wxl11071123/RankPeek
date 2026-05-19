package io.rankpeek.server.common;

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
    private final JdbcTemplate jdbcTemplate;

    public ServerDiagnosticsService(ServerProperties properties, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ServerDiagnostics diagnostics() {
        DatabaseDiagnostics database = databaseDiagnostics();
        FlywayDiagnostics flyway = flywayDiagnostics();

        String status = "ok".equals(database.status()) && "ok".equals(flyway.status()) ? "ok" : "degraded";
        return new ServerDiagnostics(status, properties.service(), properties.mode(), properties.version(), database, flyway);
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

    private record FlywayMigration(String version, String description) {
    }
}
