package io.rankpeek.server;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServerHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private PasswordService passwordService;

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/server/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.service").value("rankpeek-server"))
                .andExpect(jsonPath("$.data.mode").value("test"));
    }

    @Test
    void healthEchoesProvidedRequestId() throws Exception {
        mockMvc.perform(get("/api/server/health")
                        .header("X-Request-Id", "rankpeek-test-request"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "rankpeek-test-request"));
    }

    @Test
    void healthGeneratesRequestIdWhenMissing() throws Exception {
        mockMvc.perform(get("/api/server/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", not(blankOrNullString())));
    }

    @Test
    void versionReturnsConfiguredVersion() throws Exception {
        mockMvc.perform(get("/api/server/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value("0.1.0"))
                .andExpect(jsonPath("$.data.service").value("rankpeek-server"));
    }

    @Test
    void diagnosticsReturnsDatabaseAndFlywayStatus() throws Exception {
        mockMvc.perform(get("/api/server/diagnostics")
                        .header(HttpHeaders.AUTHORIZATION, bearer(createAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.service").value("rankpeek-server"))
                .andExpect(jsonPath("$.data.mode").value("test"))
                .andExpect(jsonPath("$.data.database.status").value("ok"))
                .andExpect(jsonPath("$.data.flyway.status").value("ok"))
                .andExpect(jsonPath("$.data.flyway.currentVersion").value("10"))
                .andExpect(jsonPath("$.data.flyway.appliedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(10)))
                .andExpect(jsonPath("$.data.configuration.status").value("ok"))
                .andExpect(jsonPath("$.data.configuration.publicRegistrationEnabled").value(true))
                .andExpect(jsonPath("$.data.configuration.initialAdminEnabled").value(false))
                .andExpect(jsonPath("$.data.configuration.passwordResetEmailEnabled").value(false))
                .andExpect(jsonPath("$.data.configuration.aiEnabled").value(false))
                .andExpect(jsonPath("$.data.configuration.aiProvider").value("mock"))
                .andExpect(jsonPath("$.data.configuration.aiModel").value("deepseek-v4-flash"))
                .andExpect(jsonPath("$.data.configuration.rateLimitEnabled").value(false))
                .andExpect(jsonPath("$.data.configuration.corsAllowedOrigins[0]").value("*"));
    }

    @Test
    void healthAllowsRendererOrigin() throws Exception {
        mockMvc.perform(get("/api/server/health").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void unknownApiRouteReturnsNotFoundResponse() throws Exception {
        mockMvc.perform(get("/api/server/not-a-real-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void unsupportedHttpMethodReturnsMethodNotAllowedResponse() throws Exception {
        mockMvc.perform(post("/api/server/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedContentTypeReturnsUnsupportedMediaTypeResponse() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("email=user@example.com&password=Secret123!"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void malformedJsonReturnsBadRequestResponse() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void missingRequiredQueryParamReturnsBadRequestResponse() throws Exception {
        mockMvc.perform(get("/api/cn-meta/champions/266/latest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void invalidPathVariableTypeReturnsBadRequestResponse() throws Exception {
        mockMvc.perform(get("/api/cn-meta/champions/not-a-number/latest")
                        .param("tierScope", "PLATINUM_PLUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
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
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return new AuthPayload(data.get("accessToken").asText());
    }

    private static String bearer(AuthPayload payload) {
        return "Bearer " + payload.accessToken();
    }

    private record AuthPayload(String accessToken) {
    }
}
