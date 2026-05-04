package io.rankpeek.config;

import lombok.extern.slf4j.Slf4j;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Path;

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
        log.info("Local cache database configured at {}", databasePath);
        return new HikariDataSource(config);
    }
}
