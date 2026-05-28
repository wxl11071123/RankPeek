package io.rankpeek.server.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

@Configuration
@EnableConfigurationProperties({PasswordResetEmailProperties.class, TencentSesEmailProperties.class, AuthProperties.class})
public class PasswordResetEmailConfiguration {

    @Bean
    @ConditionalOnMissingBean(PasswordResetEmailSender.class)
    PasswordResetEmailSender passwordResetEmailSender(
            Optional<TencentSesTemplateEmailSender> tencentSesTemplateEmailSender,
            TencentSesEmailProperties tencentSesEmailProperties,
            Optional<JavaMailSender> mailSender,
            PasswordResetEmailProperties passwordResetEmailProperties,
            AuthProperties authProperties
    ) {
        if (tencentSesEmailProperties.enabled()) {
            return new TencentSesPasswordResetEmailSender(
                    tencentSesTemplateEmailSender.orElseThrow(),
                    tencentSesEmailProperties,
                    authProperties
            );
        }
        if (passwordResetEmailProperties.enabled()) {
            return new SmtpPasswordResetEmailSender(mailSender.orElseThrow(), passwordResetEmailProperties);
        }
        return new NoopPasswordResetEmailSender();
    }

    @Bean
    @ConditionalOnMissingBean(EmailVerificationSender.class)
    EmailVerificationSender emailVerificationSender(
            Optional<TencentSesTemplateEmailSender> tencentSesTemplateEmailSender,
            TencentSesEmailProperties tencentSesEmailProperties,
            AuthProperties authProperties
    ) {
        if (tencentSesEmailProperties.enabled()) {
            return new TencentSesEmailVerificationSender(
                    tencentSesTemplateEmailSender.orElseThrow(),
                    tencentSesEmailProperties,
                    authProperties
            );
        }
        return new NoopEmailVerificationSender();
    }

    @Bean
    @ConditionalOnMissingBean(TencentSesTemplateEmailSender.class)
    TencentSesTemplateEmailSender tencentSesTemplateEmailSender(
            ObjectMapper objectMapper,
            TencentSesEmailProperties properties
    ) {
        if (properties.enabled()) {
            properties.requireSecretId();
            properties.requireSecretKey();
            properties.requireFromEmailAddress();
            properties.requireRegisterTemplateId();
            properties.requirePasswordResetTemplateId();
        }
        return new TencentSesTemplateEmailSender(objectMapper, properties);
    }
}
