package io.rankpeek.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;

@Slf4j
@Configuration
public class LocalDatabaseConfig {

    @Bean
    @ConditionalOnMissingBean
    public DataSource dataSource(LocalDataPathService localDataPathService) {
        Path databasePath = localDataPathService.getCacheDatabasePath().toAbsolutePath();
        String normalizedPath = databasePath.toString().replace('\\', '/');
        String url = "jdbc:h2:file:" + normalizedPath + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false";

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        log.info("Local cache database configured at {}", databasePath);
        return dataSource;
    }
}
