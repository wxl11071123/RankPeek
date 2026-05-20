package io.rankpeek.server;

import io.rankpeek.server.auth.AuthProperties;
import io.rankpeek.server.ai.DeepSeekAiProperties;
import io.rankpeek.server.cnmeta.sync.CnMetaSyncProperties;
import io.rankpeek.server.common.ServerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        ServerProperties.class,
        AuthProperties.class,
        CnMetaSyncProperties.class,
        DeepSeekAiProperties.class
})
public class RankPeekServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RankPeekServerApplication.class, args);
    }
}
