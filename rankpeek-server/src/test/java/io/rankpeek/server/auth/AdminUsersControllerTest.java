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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "rankpeek.auth.public-registration-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUsersControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private PasswordService passwordService;

    @Test
    void adminCanCreateUserForClosedRegistrationMvp() throws Exception {
        AuthPayload admin = createAdmin();
        String email = "created-" + UUID.randomUUID() + "@example.com";

        MvcResult createResult = mockMvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Created123!",
                                  "displayName": "Created User"
                                }
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.displayName").value("Created User"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.lastLoginAt").value(nullValue()))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        assertThat(created.get("id").asLong()).isPositive();

        auth(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "Created123!"
                        }
                        """.formatted(email)));
    }

    @Test
    void nonAdminCannotCreateUser() throws Exception {
        AuthPayload user = registerUser();
        String email = "blocked-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Created123!",
                                  "displayName": "Blocked User"
                                }
                                """.formatted(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ADMIN_REQUIRED"));
    }

    @Test
    void adminCreateUserRejectsDuplicateEmail() throws Exception {
        AuthPayload admin = createAdmin();
        AuthPayload existing = registerUser();

        mockMvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Created123!",
                                  "displayName": "Duplicate User"
                                }
                                """.formatted(existing.email().toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void adminCanListAndDisableUserRevokingRefreshTokens() throws Exception {
        AuthPayload user = registerUser();
        AuthPayload admin = createAdmin();

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("query", user.email()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.users[0].id").value(user.userId()))
                .andExpect(jsonPath("$.data.users[0].email").value(user.email()))
                .andExpect(jsonPath("$.data.users[0].role").value("USER"))
                .andExpect(jsonPath("$.data.users[0].status").value("ACTIVE"));

        mockMvc.perform(patch("/api/admin/users/{userId}", user.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLED",
                                  "role": "USER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(user.userId()))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.role").value("USER"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(user.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_UNAVAILABLE"));
    }

    @Test
    void adminCanPromoteUserAndRevokeSessions() throws Exception {
        AuthPayload user = registerUser();
        AuthPayload admin = createAdmin();

        mockMvc.perform(patch("/api/admin/users/{userId}", user.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ACTIVE",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/admin/users/{userId}/sessions/revoke", user.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(user.userId()))
                .andExpect(jsonPath("$.data.revokedCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(user.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void adminUsersRequiresAdminRole() throws Exception {
        AuthPayload user = registerUser();

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ADMIN_REQUIRED"));
    }

    @Test
    void adminCannotDisableOrDemoteSelf() throws Exception {
        AuthPayload admin = createAdmin();

        mockMvc.perform(patch("/api/admin/users/{userId}", admin.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLED",
                                  "role": "USER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CANNOT_MODIFY_SELF"));
    }

    private AuthPayload registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "Secret123!";
        authRepository.insertUser(
                email,
                "RankPeek User",
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
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return new AuthPayload(
                data.get("user").get("id").asLong(),
                data.get("user").get("email").asText(),
                data.get("accessToken").asText(),
                data.get("refreshToken").asText()
        );
    }

    private static String bearer(AuthPayload payload) {
        return "Bearer " + payload.accessToken();
    }

    private static String refreshJson(String refreshToken) {
        return """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);
    }

    private record AuthPayload(long userId, String email, String accessToken, String refreshToken) {
    }
}
