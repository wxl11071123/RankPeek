package io.rankpeek.server.credits;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreditsControllerTest {

    private static final String IDEMPOTENCY_HEADER = "X-RankPeek-Idempotency-Key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private PasswordService passwordService;

    @Test
    void userCanReadBalanceAndLedgerAfterAdminGrant() throws Exception {
        AuthPayload user = registerUser();
        AuthPayload admin = createAdmin();
        String idempotencyKey = "grant-" + UUID.randomUUID();

        mockMvc.perform(get("/api/credits/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(user.userId()))
                .andExpect(jsonPath("$.data.balance").value(0));

        mockMvc.perform(post("/api/admin/credits/grants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "amount": 25,
                                  "reason": "beta grant"
                                }
                                """.formatted(user.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(user.userId()))
                .andExpect(jsonPath("$.data.balance").value(25))
                .andExpect(jsonPath("$.data.duplicate").value(false))
                .andExpect(jsonPath("$.data.entry.id", notNullValue()))
                .andExpect(jsonPath("$.data.entry.type").value("ADMIN_ADJUSTMENT"))
                .andExpect(jsonPath("$.data.entry.amount").value(25))
                .andExpect(jsonPath("$.data.entry.balanceAfter").value(25));

        mockMvc.perform(post("/api/admin/credits/grants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "amount": 25,
                                  "reason": "beta grant"
                                }
                                """.formatted(user.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.balance").value(25))
                .andExpect(jsonPath("$.data.duplicate").value(true));

        mockMvc.perform(get("/api/credits/ledger")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.entries[0].type").value("ADMIN_ADJUSTMENT"))
                .andExpect(jsonPath("$.data.entries[0].amount").value(25))
                .andExpect(jsonPath("$.data.entries[0].balanceAfter").value(25))
                .andExpect(jsonPath("$.data.entries[0].reason").value("beta grant"));
    }

    @Test
    void adminGrantRequiresAdminBearerToken() throws Exception {
        AuthPayload user = registerUser();
        AuthPayload target = registerUser();

        mockMvc.perform(post("/api/admin/credits/grants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header(IDEMPOTENCY_HEADER, "grant-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "amount": 10,
                                  "reason": "not admin"
                                }
                                """.formatted(target.userId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ADMIN_REQUIRED"));
    }

    @Test
    void balanceRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/credits/balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));
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
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.get("accessToken").asText()).isNotBlank();
        return new AuthPayload(
                data.get("user").get("id").asLong(),
                data.get("accessToken").asText()
        );
    }

    private static String bearer(AuthPayload payload) {
        return "Bearer " + payload.accessToken();
    }

    private record AuthPayload(long userId, String accessToken) {
    }
}
