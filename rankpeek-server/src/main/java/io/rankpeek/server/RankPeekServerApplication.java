package io.rankpeek.server;

import io.rankpeek.server.auth.AuthProperties;
import io.rankpeek.server.ai.DeepSeekAiProperties;
import io.rankpeek.server.cnmeta.sync.CnMetaSyncProperties;
import io.rankpeek.server.common.RateLimitProperties;
import io.rankpeek.server.common.ServerProperties;
import io.rankpeek.server.credits.CreditProperties;
import io.rankpeek.server.opgg.OpggCacheProperties;
import io.rankpeek.server.opgg.OpggSourceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        ServerProperties.class,
        AuthProperties.class,
        CreditProperties.class,
        CnMetaSyncProperties.class,
        DeepSeekAiProperties.class,
        RateLimitProperties.class,
        OpggCacheProperties.class,
        OpggSourceProperties.class
})
public class RankPeekServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RankPeekServerApplication.class, args);
    }
}
