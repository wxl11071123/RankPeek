package io.rankpeek.server.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@EnableConfigurationProperties(PasswordResetEmailProperties.class)
public class PasswordResetEmailConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "rankpeek.auth.password-reset-email",
            name = "enabled",
            havingValue = "true"
    )
    @ConditionalOnMissingBean(PasswordResetEmailSender.class)
    PasswordResetEmailSender smtpPasswordResetEmailSender(
            JavaMailSender mailSender,
            PasswordResetEmailProperties properties
    ) {
        return new SmtpPasswordResetEmailSender(mailSender, properties);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "rankpeek.auth.password-reset-email",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    @ConditionalOnMissingBean(PasswordResetEmailSender.class)
    PasswordResetEmailSender passwordResetEmailSender() {
        return new NoopPasswordResetEmailSender();
    }
}
