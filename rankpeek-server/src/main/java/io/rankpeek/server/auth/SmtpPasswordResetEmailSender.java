package io.rankpeek.server.auth;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class SmtpPasswordResetEmailSender implements PasswordResetEmailSender {

    private final JavaMailSender mailSender;
    private final String from;
    private final String resetUrlBase;
    private final String subject;

    public SmtpPasswordResetEmailSender(JavaMailSender mailSender, PasswordResetEmailProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.requireFrom();
        this.resetUrlBase = properties.requireResetUrlBase();
        this.subject = properties.subject();
    }

    @Override
    public void sendPasswordResetEmail(AuthUser user, String resetToken, Instant expiresAt) {
        mailSender.send(message -> {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(user.email());
            helper.setSubject(subject);
            helper.setText(buildBody(user, resetToken, expiresAt), false);
        });
    }

    private String buildBody(AuthUser user, String resetToken, Instant expiresAt) {
        String displayName = user.displayName() == null || user.displayName().isBlank()
                ? "RankPeek user"
                : user.displayName();
        return """
                Hi %s,

                Use this link to reset your RankPeek password:

                %s

                This link expires at %s.

                If you did not request a password reset, you can ignore this email.
                """.formatted(displayName, resetLink(resetToken), expiresAt);
    }

    private String resetLink(String resetToken) {
        String separator;
        if (resetUrlBase.endsWith("?") || resetUrlBase.endsWith("&")) {
            separator = "";
        } else if (resetUrlBase.contains("?")) {
            separator = "&";
        } else {
            separator = "?";
        }
        return resetUrlBase + separator + "token=" + URLEncoder.encode(resetToken, StandardCharsets.UTF_8);
    }
}
