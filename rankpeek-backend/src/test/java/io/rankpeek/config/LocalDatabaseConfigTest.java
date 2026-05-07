package io.rankpeek.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDatabaseConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void createsSingleConnectionPoolForEmbeddedH2Cache() {
        LocalDataPathService pathService = new LocalDataPathService() {
            @Override
            public Path getCacheDatabasePath() {
                return tempDir.resolve("rankpeek-cache");
            }
        };

        DataSource dataSource = new LocalDatabaseConfig().dataSource(pathService);

        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        try {
            assertThat(hikariDataSource.getMaximumPoolSize()).isEqualTo(1);
            assertThat(hikariDataSource.getMinimumIdle()).isZero();
            assertThat(hikariDataSource.getInitializationFailTimeout()).isLessThan(0);
            assertThat(hikariDataSource.getConnectionTimeout()).isEqualTo(1_000);
            assertThat(hikariDataSource.getJdbcUrl()).startsWith("jdbc:h2:file:");
        } finally {
            hikariDataSource.close();
        }
    }

    @Test
    void usesSimulatorIsolatedCachePathForEmbeddedH2Url() {
        String originalTmpDir = System.getProperty("java.io.tmpdir");
        try {
            System.setProperty("java.io.tmpdir", tempDir.toString());
            LocalDataPathService pathService = new LocalDataPathService("", true);

            DataSource dataSource = new LocalDatabaseConfig().dataSource(pathService);

            assertThat(dataSource).isInstanceOf(HikariDataSource.class);
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            try {
                String expectedPath = tempDir.resolve("RankPeek")
                        .resolve("simulator")
                        .resolve("cache")
                        .resolve("rankpeek-cache")
                        .toAbsolutePath()
                        .toString()
                        .replace('\\', '/');
                assertThat(hikariDataSource.getJdbcUrl())
                        .startsWith("jdbc:h2:file:")
                        .contains(expectedPath);
                String appData = System.getenv("APPDATA");
                if (appData != null && !appData.isBlank()) {
                    assertThat(hikariDataSource.getJdbcUrl())
                            .doesNotContain(Path.of(appData, "RankPeek").toString().replace('\\', '/'));
                }
            } finally {
                hikariDataSource.close();
            }
        } finally {
            System.setProperty("java.io.tmpdir", originalTmpDir);
        }
    }
}
