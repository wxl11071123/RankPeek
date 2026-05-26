package io.rankpeek.server.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthPasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CapturingPasswordResetEmailSender emailSender;

    @BeforeEach
    void clearMail() {
        emailSender.clear();
    }

    @Test
    void passwordResetRequestAcceptsExistingAndMissingEmailWithoutAccountLeak() throws Exception {
        String existingEmail = uniqueEmail();
        register(existingEmail, "Secret123!");

        MvcResult existingResult = mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordResetRequestJson("  " + existingEmail.toUpperCase() + "  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andReturn();

        MvcResult missingResult = mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordResetRequestJson("missing-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andReturn();

        assertThat(missingResult.getResponse().getContentAsString())
                .isEqualTo(existingResult.getResponse().getContentAsString());
        assertThat(emailSender.messages()).hasSize(1);
        CapturedPasswordResetEmail message = emailSender.messages().getFirst();
        assertThat(message.email()).isEqualTo(existingEmail);
        assertThat(message.resetToken()).isNotBlank();
        assertThat(message.expiresAt()).isAfter(Instant.now());
        assertThat(message.tokenVisibleWhenEmailWasSent()).isTrue();

        String storedTokenHash = jdbcTemplate.queryForObject(
                """
                        select token_hash
                        from auth_password_reset_tokens
                        where user_id = (select id from users where email = ?)
                        """,
                String.class,
                existingEmail
        );
        assertThat(storedTokenHash)
                .hasSize(64)
                .isNotEqualTo(message.resetToken());

        Integer tokenCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from auth_password_reset_tokens
                        where user_id = (select id from users where email = ?)
                        """,
                Integer.class,
                existingEmail
        );
        assertThat(tokenCount).isEqualTo(1);
    }

    @Test
    void passwordResetConfirmUpdatesPasswordRevokesSessionsAndConsumesToken() throws Exception {
        String email = uniqueEmail();
        AuthPayload auth = register(email, "Secret123!");

        requestPasswordReset(email);
        String resetToken = emailSender.messages().getLast().resetToken();

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordResetConfirmJson(resetToken, "NewSecret123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reset").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "Secret123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "NewSecret123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(auth.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordResetConfirmJson(resetToken, "Another123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PASSWORD_RESET_TOKEN_INVALID"));
    }

    @Test
    void passwordResetConfirmRejectsInvalidTokenWithoutChangingPassword() throws Exception {
        String email = uniqueEmail();
        register(email, "Secret123!");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordResetConfirmJson("not-a-valid-token", "NewSecret123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PASSWORD_RESET_TOKEN_INVALID"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "Secret123!")))
                .andExpect(status().isOk());
    }

    private void requestPasswordReset(String email) throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordResetRequestJson(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));
    }

    private AuthPayload register(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "displayName": "RankPeek User"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return new AuthPayload(data.get("refreshToken").asText());
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String passwordResetRequestJson(String email) {
        return """
                {
                  "email": "%s"
                }
                """.formatted(email);
    }

    private static String passwordResetConfirmJson(String token, String newPassword) {
        return """
                {
                  "token": "%s",
                  "newPassword": "%s"
                }
                """.formatted(token, newPassword);
    }

    private static String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private static String refreshJson(String refreshToken) {
        return """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);
    }

    @TestConfiguration
    static class PasswordResetMailTestConfiguration {
        @Bean
        @Primary
        CapturingPasswordResetEmailSender capturingPasswordResetEmailSender(
                DataSource dataSource,
                TokenService tokenService
        ) {
            return new CapturingPasswordResetEmailSender(dataSource, tokenService);
        }
    }

    static class CapturingPasswordResetEmailSender implements PasswordResetEmailSender {
        private final DataSource dataSource;
        private final TokenService tokenService;
        private final List<CapturedPasswordResetEmail> messages = new ArrayList<>();

        CapturingPasswordResetEmailSender(DataSource dataSource, TokenService tokenService) {
            this.dataSource = dataSource;
            this.tokenService = tokenService;
        }

        @Override
        public void sendPasswordResetEmail(AuthUser user, String resetToken, Instant expiresAt) {
            messages.add(new CapturedPasswordResetEmail(
                    user.email(),
                    resetToken,
                    expiresAt,
                    tokenVisibleFromSeparateConnection(resetToken)
            ));
        }

        void clear() {
            messages.clear();
        }

        List<CapturedPasswordResetEmail> messages() {
            return messages;
        }

        private boolean tokenVisibleFromSeparateConnection(String resetToken) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "select count(*) from auth_password_reset_tokens where token_hash = ?"
                 )) {
                statement.setString(1, tokenService.hashRefreshToken(resetToken));
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getInt(1) == 1;
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to verify password reset token visibility", exception);
            }
        }
    }

    private record CapturedPasswordResetEmail(
            String email,
            String resetToken,
            Instant expiresAt,
            boolean tokenVisibleWhenEmailWasSent
    ) {
    }

    private record AuthPayload(String refreshToken) {
    }
}
