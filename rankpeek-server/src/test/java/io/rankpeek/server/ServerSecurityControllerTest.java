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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rankpeek.server.cors.allowed-origins=http://localhost:5173",
        "rankpeek.auth.public-registration-enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext
class ServerSecurityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private PasswordService passwordService;

    @Test
    void corsUsesConfiguredOriginAllowlist() throws Exception {
        mockMvc.perform(get("/api/server/health")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(get("/api/server/health")
                        .header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void diagnosticsRequiresAdminBearerToken() throws Exception {
        mockMvc.perform(get("/api/server/diagnostics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));

        mockMvc.perform(get("/api/server/diagnostics")
                        .header(HttpHeaders.AUTHORIZATION, bearer(createAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.database.status").value("ok"));
    }

    @Test
    void playstyleMockSeedRequiresAdminBearerToken() throws Exception {
        mockMvc.perform(post("/api/playstyles/cards/mock-seed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));

        mockMvc.perform(post("/api/playstyles/cards/mock-seed")
                        .header(HttpHeaders.AUTHORIZATION, bearer(createAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceTier").value("MOCK_REVIEWED"));
    }

    @Test
    void publicRegistrationCanBeDisabled() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "disabled-%s@example.com",
                                  "password": "Secret123!",
                                  "displayName": "Disabled Registration"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PUBLIC_REGISTRATION_DISABLED"));
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
