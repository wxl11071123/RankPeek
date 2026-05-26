package io.rankpeek.server.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.server.auth.AuthRepository;
import io.rankpeek.server.auth.PasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAnalysisRunsControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private PasswordService passwordService;

    @Test
    void adminCanListAndReadRunMetadataWithoutResponseBody() throws Exception {
        AuthPayload user = registerUser();
        AuthPayload other = registerUser();
        AuthPayload admin = createAdmin();
        seedRun(user.userId(), "REFUNDED", "Filtered out", "DeepSeek request failed with status 500");
        seedRun(other.userId(), "SUCCEEDED", "Other user summary", null);
        long runId = seedRun(user.userId(), "SUCCEEDED", "Admin visible metadata", null);

        mockMvc.perform(get("/api/admin/analysis/runs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("userId", String.valueOf(user.userId()))
                        .param("endpoint", "coach-summary")
                        .param("status", "SUCCEEDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.runs[0].id").value(runId))
                .andExpect(jsonPath("$.data.runs[0].userId").value(user.userId()))
                .andExpect(jsonPath("$.data.runs[0].endpoint").value("coach-summary"))
                .andExpect(jsonPath("$.data.runs[0].provider").value("deepseek"))
                .andExpect(jsonPath("$.data.runs[0].model").value("deepseek-v4-flash"))
                .andExpect(jsonPath("$.data.runs[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.runs[0].chargedCredits").value(1))
                .andExpect(jsonPath("$.data.runs[0].refundedCredits").value(0))
                .andExpect(jsonPath("$.data.runs[0].totalTokens").value(117))
                .andExpect(jsonPath("$.data.runs[0].response").doesNotExist());

        mockMvc.perform(get("/api/admin/analysis/runs/{runId}", runId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(runId))
                .andExpect(jsonPath("$.data.userId").value(user.userId()))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.response").doesNotExist())
                .andExpect(jsonPath("$.data.errorMessage").doesNotExist());
    }

    @Test
    void adminAnalysisRunsRequiresAdminRole() throws Exception {
        AuthPayload user = registerUser();

        mockMvc.perform(get("/api/admin/analysis/runs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ADMIN_REQUIRED"));
    }

    private long seedRun(long userId, String status, String title, String errorMessage) {
        String idempotencyKey = "seed-" + UUID.randomUUID();
        String responseJson = title == null ? null : """
                {
                  "report": {
                    "title": "%s",
                    "inputHash": "seed-input-hash"
                  },
                  "usage": {
                    "provider": "deepseek",
                    "model": "deepseek-v4-flash",
                    "promptTokens": 80,
                    "completionTokens": 37,
                    "totalTokens": 117,
                    "promptCacheHitTokens": 0,
                    "promptCacheMissTokens": 80
                  }
                }
                """.formatted(title);
        jdbcTemplate.update(
                """
                        insert into ai_analysis_runs (
                            user_id, endpoint, provider, model, status, idempotency_key,
                            request_hash, response_json, error_message, charged_credits,
                            refunded_credits, prompt_tokens, completion_tokens, total_tokens,
                            error_code, created_at, updated_at, completed_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp, current_timestamp)
                        """,
                userId,
                "coach-summary",
                "deepseek",
                "deepseek-v4-flash",
                status,
                idempotencyKey,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                responseJson,
                errorMessage,
                1,
                "REFUNDED".equals(status) ? 1 : 0,
                80,
                37,
                117,
                "REFUNDED".equals(status) ? "DEEPSEEK_ERROR" : null
        );
        return jdbcTemplate.queryForObject(
                "select id from ai_analysis_runs where user_id = ? and idempotency_key = ?",
                Long.class,
                userId,
                idempotencyKey
        );
    }

    private AuthPayload registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        return auth(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "Secret123!",
                          "displayName": "RankPeek User"
                        }
                        """.formatted(email)));
    }

    private AuthPayload createAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        String password = "Admin123!";
        authRepository.upsertInitialAdmin(
                email,
                "RankPeek Admin",
                passwordService.hash(password),
                Instant.now()
        );
        return auth(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(email, password)));
    }

    private AuthPayload auth(org.springframework.test.web.servlet.RequestBuilder requestBuilder) throws Exception {
        MvcResult result = mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn();
        JsonNode data = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).get("data");
        return new AuthPayload(data.get("user").get("id").asLong(), data.get("accessToken").asText());
    }

    private static String bearer(AuthPayload user) {
        return "Bearer " + user.accessToken();
    }

    private record AuthPayload(long userId, String accessToken) {
    }
}
