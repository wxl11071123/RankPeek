package io.rankpeek.server.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalysisRunsControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void userCanListAndReadOwnSucceededRunWithStoredResponse() throws Exception {
        AuthPayload owner = registerUser();
        AuthPayload other = registerUser();
        seedRun(other.userId(), "SUCCEEDED", "other user summary", null);
        long runId = seedRun(owner.userId(), "SUCCEEDED", "Own run summary", null);

        mockMvc.perform(get("/api/analysis/runs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .param("endpoint", "coach-summary")
                        .param("status", "SUCCEEDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.runs[0].id").value(runId))
                .andExpect(jsonPath("$.data.runs[0].userId").value(owner.userId()))
                .andExpect(jsonPath("$.data.runs[0].endpoint").value("coach-summary"))
                .andExpect(jsonPath("$.data.runs[0].provider").value("deepseek"))
                .andExpect(jsonPath("$.data.runs[0].model").value("deepseek-v4-flash"))
                .andExpect(jsonPath("$.data.runs[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.runs[0].chargedCredits").value(1))
                .andExpect(jsonPath("$.data.runs[0].refundedCredits").value(0))
                .andExpect(jsonPath("$.data.runs[0].promptTokens").value(80))
                .andExpect(jsonPath("$.data.runs[0].completionTokens").value(37))
                .andExpect(jsonPath("$.data.runs[0].totalTokens").value(117))
                .andExpect(jsonPath("$.data.limit").value(20))
                .andExpect(jsonPath("$.data.offset").value(0));

        mockMvc.perform(get("/api/analysis/runs/{runId}", runId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(runId))
                .andExpect(jsonPath("$.data.userId").value(owner.userId()))
                .andExpect(jsonPath("$.data.response.report.title").value("Own run summary"))
                .andExpect(jsonPath("$.data.response.usage.totalTokens").value(117))
                .andExpect(jsonPath("$.data.errorMessage").doesNotExist());
    }

    @Test
    void userDetailReturnsFailureMessageButNotResponseForRefundedRun() throws Exception {
        AuthPayload owner = registerUser();
        long runId = seedRun(owner.userId(), "REFUNDED", null, "DeepSeek request failed with status 500");

        mockMvc.perform(get("/api/analysis/runs/{runId}", runId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(runId))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.errorCode").value("DEEPSEEK_ERROR"))
                .andExpect(jsonPath("$.data.errorMessage").value("DeepSeek request failed with status 500"))
                .andExpect(jsonPath("$.data.response").doesNotExist());
    }

    @Test
    void userCannotReadAnotherUsersRun() throws Exception {
        AuthPayload owner = registerUser();
        AuthPayload other = registerUser();
        long otherRunId = seedRun(other.userId(), "SUCCEEDED", "Other run summary", null);

        mockMvc.perform(get("/api/analysis/runs/{runId}", otherRunId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_RUN_NOT_FOUND"));
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
