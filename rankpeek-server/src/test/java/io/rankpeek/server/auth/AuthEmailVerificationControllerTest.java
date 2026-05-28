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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rankpeek.auth.email-verification.required=true",
        "rankpeek.auth.email-verification.code-ttl-seconds=900",
        "rankpeek.auth.email-verification.resend-cooldown-seconds=0"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthEmailVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CapturingEmailVerificationSender emailSender;

    @BeforeEach
    void clearSender() {
        emailSender.clear();
    }

    @Test
    void registerRequiresEmailVerificationCodeWhenEnabled() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "Secret123!", "RankPeek", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("EMAIL_VERIFICATION_CODE_INVALID"));
    }

    @Test
    void emailVerificationCodeCanBeRequestedAndUsedForRegistration() throws Exception {
        String email = uniqueEmail();

        MvcResult codeResult = mockMvc.perform(post("/api/auth/register/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(900))
                .andReturn();

        assertThat(emailSender.messages()).hasSize(1);
        CapturedEmailVerification message = emailSender.messages().getFirst();
        assertThat(message.email()).isEqualTo(email);
        assertThat(message.code()).matches("\\d{6}");

        Long codeRows = jdbcTemplate.queryForObject(
                "select count(*) from auth_email_verification_codes where email = ?",
                Long.class,
                email
        );
        assertThat(codeRows).isEqualTo(1L);
        String storedHash = jdbcTemplate.queryForObject(
                "select code_hash from auth_email_verification_codes where email = ?",
                String.class,
                email
        );
        assertThat(storedHash).isNotEqualTo(message.code());
        assertThat(codeResult.getResponse().getContentAsString()).doesNotContain(message.code());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "Secret123!", "RankPeek", "000000")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("EMAIL_VERIFICATION_CODE_INVALID"));

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "Secret123!", "RankPeek", message.code())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.accessToken", matchesPattern(".+")))
                .andReturn();

        JsonNode registerData = objectMapper.readTree(registerResult.getResponse().getContentAsString()).path("data");
        assertThat(registerData.path("refreshToken").asText()).isNotBlank();

        Instant consumedAt = jdbcTemplate.queryForObject(
                "select consumed_at from auth_email_verification_codes where email = ?",
                Instant.class,
                email
        );
        assertThat(consumedAt).isNotNull();
    }

    private static String uniqueEmail() {
        return "verify-" + UUID.randomUUID() + "@example.com";
    }

    private static String registerJson(String email, String password, String displayName, String verificationCode) {
        String codeLine = verificationCode == null ? "" : """
                  ,"verificationCode": "%s"
                """.formatted(verificationCode);
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "displayName": "%s"%s
                }
                """.formatted(email, password, displayName, codeLine);
    }

    @TestConfiguration
    static class TestEmailConfiguration {
        @Bean
        @Primary
        CapturingEmailVerificationSender capturingEmailVerificationSender() {
            return new CapturingEmailVerificationSender();
        }
    }

    static class CapturingEmailVerificationSender implements EmailVerificationSender {
        private final List<CapturedEmailVerification> messages = new ArrayList<>();

        @Override
        public void sendRegisterVerificationCode(String email, String code, Instant expiresAt) {
            messages.add(new CapturedEmailVerification(email, code, expiresAt));
        }

        List<CapturedEmailVerification> messages() {
            return messages;
        }

        void clear() {
            messages.clear();
        }
    }

    private record CapturedEmailVerification(String email, String code, Instant expiresAt) {
    }
}
