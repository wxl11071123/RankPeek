package io.rankpeek.config;

import lombok.extern.slf4j.Slf4j;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Slf4j
@Configuration
public class LocalDatabaseConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public DataSource dataSource(LocalDataPathService localDataPathService) {
        Path databasePath = localDataPathService.getCacheDatabasePath().toAbsolutePath();
        String normalizedPath = databasePath.toString().replace('\\', '/');
        String url = "jdbc:h2:file:" + normalizedPath + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false";

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.h2.Driver");
        config.setJdbcUrl(url);
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setPoolName("rankpeek-local-cache");
        config.setConnectionTimeout(1_000);
        config.setInitializationFailTimeout(-1);
        logStartupDiagnostics(localDataPathService, databasePath, url);
        return new HikariDataSource(config);
    }

    private void logStartupDiagnostics(LocalDataPathService localDataPathService, Path databasePath, String jdbcUrl) {
        log.info("RankPeek backend startup diagnostics: pid={}, localDataRoot={}, h2DatabasePath={}, h2JdbcUrl={}",
                ProcessHandle.current().pid(),
                localDataPathService.getLocalDataRoot().toAbsolutePath(),
                databasePath,
                jdbcUrl);
        logCacheFileState(databasePath.resolveSibling(databasePath.getFileName() + ".mv.db"));
        logCacheFileState(databasePath.resolveSibling(databasePath.getFileName() + ".lock.db"));
        logCacheFileState(databasePath.resolveSibling(databasePath.getFileName() + ".trace.db"));
    }

    private void logCacheFileState(Path path) {
        try {
            boolean exists = Files.exists(path);
            long size = exists ? Files.size(path) : 0;
            Instant lastModified = exists ? Files.getLastModifiedTime(path).toInstant() : null;
            log.info("RankPeek H2 cache file state: file={}, exists={}, sizeBytes={}, lastModified={}",
                    path,
                    exists,
                    size,
                    lastModified);
        } catch (Exception e) {
            log.warn("Failed to inspect RankPeek H2 cache file: file={}, rootCause={}",
                    path,
                    rootCauseSummary(e),
                    e);
        }
    }

    private String rootCauseSummary(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getMessage();
        }
        return current.getClass().getSimpleName() + ": " + message;
    }
}
