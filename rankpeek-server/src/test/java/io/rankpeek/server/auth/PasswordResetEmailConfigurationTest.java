package io.rankpeek.server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetEmailConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(PasswordResetEmailConfiguration.class);

    @Test
    void disabledPasswordResetEmailUsesNoopSender() {
        contextRunner
                .withPropertyValues("rankpeek.auth.password-reset-email.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(PasswordResetEmailSender.class);
                    assertThat(context.getBean(PasswordResetEmailSender.class))
                            .isInstanceOf(NoopPasswordResetEmailSender.class);
                });
    }

    @Test
    void enabledPasswordResetEmailRequiresResetUrlBaseAndFromAddress() {
        contextRunner
                .withBean(JavaMailSender.class, CapturingJavaMailSender::new)
                .withPropertyValues(
                        "rankpeek.auth.password-reset-email.enabled=true",
                        "rankpeek.auth.password-reset-email.from=no-reply@rankpeek.local"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()).getMessage())
                            .contains("rankpeek.auth.password-reset-email.reset-url-base");
                });
    }

    @Test
    void enabledPasswordResetEmailSendsResetLinkThroughJavaMail() throws Exception {
        contextRunner
                .withBean(JavaMailSender.class, CapturingJavaMailSender::new)
                .withPropertyValues(
                        "rankpeek.auth.password-reset-email.enabled=true",
                        "rankpeek.auth.password-reset-email.from=no-reply@rankpeek.local",
                        "rankpeek.auth.password-reset-email.reset-url-base=https://rankpeek.example.com/password-reset"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PasswordResetEmailSender.class);
                    PasswordResetEmailSender sender = context.getBean(PasswordResetEmailSender.class);
                    assertThat(sender).isInstanceOf(SmtpPasswordResetEmailSender.class);

                    sender.sendPasswordResetEmail(
                            activeUser("user@example.com"),
                            "reset-token+value",
                            Instant.parse("2026-05-26T04:00:00Z")
                    );

                    CapturingJavaMailSender mailSender = (CapturingJavaMailSender) context.getBean(JavaMailSender.class);
                    assertThat(mailSender.sentMessages()).hasSize(1);
                    MimeMessage message = mailSender.sentMessages().getFirst();
                    Address[] recipients = message.getAllRecipients();
                    assertThat(recipients).hasSize(1);
                    assertThat(recipients[0].toString()).isEqualTo("user@example.com");
                    assertThat(message.getFrom()[0].toString()).isEqualTo("no-reply@rankpeek.local");
                    assertThat(message.getSubject()).contains("RankPeek");
                    assertThat((String) message.getContent())
                            .contains("https://rankpeek.example.com/password-reset?token=reset-token%2Bvalue")
                            .contains("2026-05-26T04:00:00Z");
                });
    }

    @Test
    void customPasswordResetEmailSenderOverridesSmtpSender() {
        PasswordResetEmailSender customSender = (user, resetToken, expiresAt) -> {
        };

        contextRunner
                .withBean(PasswordResetEmailSender.class, () -> customSender)
                .withBean(JavaMailSender.class, CapturingJavaMailSender::new)
                .withPropertyValues(
                        "rankpeek.auth.password-reset-email.enabled=true",
                        "rankpeek.auth.password-reset-email.from=no-reply@rankpeek.local",
                        "rankpeek.auth.password-reset-email.reset-url-base=https://rankpeek.example.com/password-reset"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PasswordResetEmailSender.class);
                    assertThat(context.getBean(PasswordResetEmailSender.class)).isSameAs(customSender);
                });
    }

    private static AuthUser activeUser(String email) {
        Instant now = Instant.parse("2026-05-26T03:50:00Z");
        return new AuthUser(42L, email, "RankPeek User", "hash", "ACTIVE", "USER", now, now, null);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    static class CapturingJavaMailSender implements JavaMailSender {
        private final List<MimeMessage> sentMessages = new ArrayList<>();

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            try {
                return new MimeMessage(Session.getInstance(new Properties()), contentStream);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to create test mime message", exception);
            }
        }

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            sentMessages.add(mimeMessage);
        }

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
            sentMessages.addAll(List.of(mimeMessages));
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
            MimeMessage message = createMimeMessage();
            try {
                mimeMessagePreparator.prepare(message);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to prepare test mime message", exception);
            }
            send(message);
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
            for (MimeMessagePreparator preparator : mimeMessagePreparators) {
                send(preparator);
            }
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            throw new UnsupportedOperationException("SimpleMailMessage is not used by password reset email");
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
            throw new UnsupportedOperationException("SimpleMailMessage is not used by password reset email");
        }

        List<MimeMessage> sentMessages() {
            return sentMessages;
        }
    }
}
