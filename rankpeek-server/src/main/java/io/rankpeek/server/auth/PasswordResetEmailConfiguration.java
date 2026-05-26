package io.rankpeek.server.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasswordResetEmailConfiguration {

    @Bean
    @ConditionalOnMissingBean(PasswordResetEmailSender.class)
    PasswordResetEmailSender passwordResetEmailSender() {
        return new NoopPasswordResetEmailSender();
    }
}
