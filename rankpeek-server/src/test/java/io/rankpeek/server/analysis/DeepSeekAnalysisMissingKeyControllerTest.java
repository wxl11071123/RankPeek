package io.rankpeek.server.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.server.auth.AuthRepository;
import io.rankpeek.server.auth.AuthUser;
import io.rankpeek.server.auth.PasswordService;
import io.rankpeek.server.credits.AdminCreditGrantRequest;
import io.rankpeek.server.credits.CreditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rankpeek.ai.enabled=true",
        "rankpeek.ai.provider=deepseek",
        "rankpeek.ai.base-url=http://127.0.0.1:1",
        "rankpeek.ai.api-key="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class DeepSeekAnalysisMissingKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private CreditService creditService;

    @Test
    void missingDeepSeekKeyReturnsErrorSse() throws Exception {
        AuthPayload user = userWithCredits(3);

        MvcResult result = mockMvc.perform(post("/api/analysis/postgame/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "mode": "review",
                                  "snapshotSchemaVersion": "postgame_ai_input_snapshot.v1",
                                  "snapshot": {}
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString("API key is not configured")));
    }

    private AuthPayload userWithCredits(int credits) throws Exception {
        AuthPayload user = registerUser();
        AuthUser admin = authRepository.upsertInitialAdmin(
                "admin-" + UUID.randomUUID() + "@example.com",
                "RankPeek Admin",
                passwordService.hash("Admin123!"),
                Instant.now()
        );
        creditService.adjustByAdmin(
                admin,
                new AdminCreditGrantRequest(user.userId(), credits, "test credits"),
                "grant-" + UUID.randomUUID()
        );
        return user;
    }

    private AuthPayload registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Secret123!",
                                  "displayName": "RankPeek User"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return new AuthPayload(data.get("user").get("id").asLong(), data.get("accessToken").asText());
    }

    private record AuthPayload(long userId, String accessToken) {
    }
}
