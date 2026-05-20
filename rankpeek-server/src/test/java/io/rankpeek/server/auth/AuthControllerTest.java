package io.rankpeek.server.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerCreatesUserAndReturnsTokens() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "Secret123!", "  Rank Peek  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.displayName").value("Rank Peek"))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(3600));
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String email = uniqueEmail();
        register(email, "Secret123!", "RankPeek");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email.toUpperCase(), "Another123!", "Other")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void loginReturnsTokensForValidPassword() throws Exception {
        String email = uniqueEmail();
        register(email, "Secret123!", "RankPeek");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email.toUpperCase(), "Secret123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())));
    }

    @Test
    void loginAllowsRendererOrigin() throws Exception {
        String email = uniqueEmail();
        register(email, "Secret123!", "RankPeek");

        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "Secret123!")))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void loginRejectsWrongPasswordWithoutUserLeak() throws Exception {
        String email = uniqueEmail();
        register(email, "Secret123!", "RankPeek");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "Wrong123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("Invalid email or password"));
    }

    @Test
    void refreshReturnsNewAccessToken() throws Exception {
        AuthPayload auth = register(uniqueEmail(), "Secret123!", "RankPeek");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(auth.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(3600));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        AuthPayload auth = register(uniqueEmail(), "Secret123!", "RankPeek");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(auth.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.revoked").value(true));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(auth.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void meReturnsCurrentUserForAccessToken() throws Exception {
        AuthPayload auth = register(uniqueEmail(), "Secret123!", "RankPeek");

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(auth.email()))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void serverHealthAndVersionRemainPublic() throws Exception {
        mockMvc.perform(get("/api/server/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ok"));

        mockMvc.perform(get("/api/server/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value("0.1.0"));
    }

    private AuthPayload register(String email, String password, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, password, displayName)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.get("accessToken").asText()).isNotBlank();
        assertThat(data.get("refreshToken").asText()).isNotBlank();
        return new AuthPayload(
                data.get("user").get("email").asText(),
                data.get("accessToken").asText(),
                data.get("refreshToken").asText()
        );
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String registerJson(String email, String password, String displayName) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "displayName": "%s"
                }
                """.formatted(email, password, displayName);
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

    private record AuthPayload(String email, String accessToken, String refreshToken) {
    }
}
