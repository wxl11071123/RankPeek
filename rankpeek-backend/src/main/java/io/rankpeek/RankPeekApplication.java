package io.rankpeek;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * RankPeek backend entry point.
 */
@SpringBootApplication
@EnableAsync
public class RankPeekApplication {

    public static void main(String[] args) {
        SpringApplication.run(RankPeekApplication.class, args);
    }
}