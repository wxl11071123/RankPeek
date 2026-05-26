package io.rankpeek.server;

import io.rankpeek.server.auth.AuthRepository;
import io.rankpeek.server.auth.PasswordService;
import io.rankpeek.server.auth.TokenService;
import io.rankpeek.server.auth.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rankpeek.rate-limit.enabled=true",
        "rankpeek.rate-limit.window-seconds=60",
        "rankpeek.rate-limit.auth.max-requests=2",
        "rankpeek.rate-limit.ai.max-requests=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class ServerRateLimitControllerTest {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private TokenService tokenService;

    @Test
    void registrationIsRateLimitedByClientAddress() throws Exception {
        String clientIp = "203.0.113.10";

        registerAttempt(clientIp, uniqueEmail()).andExpect(status().isOk());
        registerAttempt(clientIp, uniqueEmail()).andExpect(status().isOk());

        registerAttempt(clientIp, uniqueEmail())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void loginFailuresAreRateLimitedByClientAddress() throws Exception {
        String clientIp = "203.0.113.11";
        String email = createUser();

        loginAttempt(clientIp, email, "Wrong123!").andExpect(status().isUnauthorized());
        loginAttempt(clientIp, email, "Wrong123!").andExpect(status().isUnauthorized());

        loginAttempt(clientIp, email, "Wrong123!")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void coachSummaryIsRateLimitedBeforeAiWork() throws Exception {
        String accessToken = accessTokenFor(createUser());

        coachSummaryAttempt(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_SERVER_DISABLED"));

        coachSummaryAttempt(accessToken)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private org.springframework.test.web.servlet.ResultActions registerAttempt(String clientIp, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .header(X_FORWARDED_FOR, clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "Secret123!",
                          "displayName": "Rate Limited"
                        }
                        """.formatted(email)));
    }

    private org.springframework.test.web.servlet.ResultActions loginAttempt(String clientIp, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .header(X_FORWARDED_FOR, clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(email, password)));
    }

    private org.springframework.test.web.servlet.ResultActions coachSummaryAttempt(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/analysis/coach-summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-RankPeek-Idempotency-Key", "rate-limit-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "inputHash": "rate-limit-input",
                          "snapshotSchemaVersion": "coach_summary_input_snapshot.v2",
                          "promptVersion": "coach_summary.prompt.v2",
                          "dataQualityConfidence": "medium",
                          "systemPrompt": "system prompt",
                          "userPrompt": "{\\"currentSnapshotText\\":\\"sample\\"}"
                        }
                        """));
    }

    private String createUser() {
        String email = uniqueEmail();
        authRepository.insertUser(email, "Rate Limited", passwordService.hash("Secret123!"), Instant.now());
        return email;
    }

    private String accessTokenFor(String email) {
        var user = authRepository.findUserByEmail(email).orElseThrow();
        return tokenService.createAccessToken(UserResponse.from(user));
    }

    private static String uniqueEmail() {
        return "rate-" + UUID.randomUUID() + "@example.com";
    }
}
