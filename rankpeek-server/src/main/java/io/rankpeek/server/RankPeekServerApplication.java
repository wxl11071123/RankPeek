package io.rankpeek.server;

import io.rankpeek.server.common.ServerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ServerProperties.class)
public class RankPeekServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RankPeekServerApplication.class, args);
    }
}
