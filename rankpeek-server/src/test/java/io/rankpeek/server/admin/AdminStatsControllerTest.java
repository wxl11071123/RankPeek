package io.rankpeek.server.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.server.auth.AuthRepository;
import io.rankpeek.server.auth.AuthUser;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminStatsControllerTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
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
    void adminCanReadOperationalStatsOverview() throws Exception {
        LocalDate date = LocalDate.now(SHANGHAI);
        Instant dayStart = date.atStartOfDay(SHANGHAI).toInstant();
        Instant now = Instant.now();
        AuthPayload admin = createAdmin(now);
        AuthUser recentUser = createUser("recent", dayStart.plusSeconds(3600), now.minusSeconds(30 * 60));
        AuthUser threeHourUser = createUser("three-hour", dayStart.plusSeconds(4200), now.minusSeconds(2 * 60 * 60));
        AuthUser dayUser = createUser("day", dayStart.plusSeconds(4800), now.minusSeconds(20 * 60 * 60));
        AuthUser yesterdayUser = createUser("yesterday", dayStart.minusSeconds(90_000), dayStart.minusSeconds(90_000));
        AuthUser disabledUser = createUser("disabled", dayStart.plusSeconds(5400), now.minusSeconds(20 * 60));
        jdbcTemplate.update("update users set status = ? where id = ?", "DISABLED", disabledUser.id());

        seedRefreshToken(threeHourUser.id(), now.minusSeconds(90 * 60), now.plusSeconds(86400), null);
        seedCreditBalance(recentUser.id(), 33, dayStart.plusSeconds(7200));
        seedCreditBalance(threeHourUser.id(), 12, dayStart.plusSeconds(7300));
        seedLedger(recentUser.id(), admin.userId(), "ADMIN_ADJUSTMENT", 30, 30, dayStart.plusSeconds(7600));
        seedLedger(recentUser.id(), null, "AI_CHARGE", -1, 29, dayStart.plusSeconds(7800));
        seedLedger(recentUser.id(), null, "AI_REFUND", 1, 30, dayStart.plusSeconds(7900));
        seedLedger(threeHourUser.id(), admin.userId(), "ADMIN_ADJUSTMENT", 15, 15, dayStart.minusSeconds(1000));
        seedRun(recentUser.id(), "coach-summary", "SUCCEEDED", 1, 0, 80, 37, 117, dayStart.plusSeconds(8000));
        seedRun(recentUser.id(), "postgame-stream", "REFUNDED", 1, 1, 25, 10, 35, dayStart.plusSeconds(8100));
        seedRun(threeHourUser.id(), "pregame-stream", "RESERVED", 1, 0, 0, 0, 0, dayStart.plusSeconds(8200));
        seedRun(yesterdayUser.id(), "coach-summary", "SUCCEEDED", 1, 0, 11, 22, 33, dayStart.minusSeconds(1200));

        mockMvc.perform(get("/api/admin/stats/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("date", date.toString())
                        .param("zone", SHANGHAI.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.date").value(date.toString()))
                .andExpect(jsonPath("$.data.timeZone").value(SHANGHAI.getId()))
                .andExpect(jsonPath("$.data.activeUsers.last1Hour").value(1))
                .andExpect(jsonPath("$.data.activeUsers.last3Hours").value(2))
                .andExpect(jsonPath("$.data.activeUsers.last24Hours").value(3))
                .andExpect(jsonPath("$.data.users.total").value(6))
                .andExpect(jsonPath("$.data.users.active").value(5))
                .andExpect(jsonPath("$.data.users.disabled").value(1))
                .andExpect(jsonPath("$.data.users.admins").value(1))
                .andExpect(jsonPath("$.data.users.registeredToday").value(5))
                .andExpect(jsonPath("$.data.ai.requestsToday").value(3))
                .andExpect(jsonPath("$.data.ai.succeededToday").value(1))
                .andExpect(jsonPath("$.data.ai.failedToday").value(1))
                .andExpect(jsonPath("$.data.ai.reservedToday").value(1))
                .andExpect(jsonPath("$.data.ai.promptTokensToday").value(105))
                .andExpect(jsonPath("$.data.ai.completionTokensToday").value(47))
                .andExpect(jsonPath("$.data.ai.totalTokensToday").value(152))
                .andExpect(jsonPath("$.data.ai.successRate").value(0.3333))
                .andExpect(jsonPath("$.data.credits.adminGrantedToday").value(30))
                .andExpect(jsonPath("$.data.credits.aiChargedToday").value(1))
                .andExpect(jsonPath("$.data.credits.aiRefundedToday").value(1))
                .andExpect(jsonPath("$.data.credits.outstandingBalance").value(45))
                .andExpect(jsonPath("$.data.daily", hasSize(7)))
                .andExpect(jsonPath("$.data.daily[6].date").value(date.toString()))
                .andExpect(jsonPath("$.data.daily[6].registeredUsers").value(5))
                .andExpect(jsonPath("$.data.daily[6].aiRequests").value(3));
    }

    @Test
    void statsOverviewRequiresAdminRole() throws Exception {
        AuthPayload user = loginAs(createUser("plain", Instant.now(), Instant.now()), "Secret123!");

        mockMvc.perform(get("/api/admin/stats/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ADMIN_REQUIRED"));
    }

    @Test
    void adminPageIsServedAsStaticHtml() throws Exception {
        mockMvc.perform(get("/admin/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RankPeek Admin")));
    }

    private AuthUser createUser(String label, Instant createdAt, Instant lastLoginAt) {
        String email = label + "-" + UUID.randomUUID() + "@example.com";
        return authRepository.insertUser(
                email,
                "RankPeek " + label,
                passwordService.hash("Secret123!"),
                createdAt,
                lastLoginAt
        );
    }

    private AuthPayload createAdmin(Instant createdAt) throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        String password = "Admin123!";
        AuthUser admin = authRepository.upsertInitialAdmin(
                email,
                "RankPeek Admin",
                passwordService.hash(password),
                createdAt
        );
        return loginAs(admin, password);
    }

    private AuthPayload loginAs(AuthUser user, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(user.email(), password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn();
        JsonNode data = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).get("data");
        return new AuthPayload(data.get("user").get("id").asLong(), data.get("accessToken").asText());
    }

    private void seedRefreshToken(long userId, Instant createdAt, Instant expiresAt, Instant lastUsedAt) {
        jdbcTemplate.update(
                """
                        insert into auth_refresh_tokens (
                            user_id, token_hash, expires_at, created_at, last_used_at
                        ) values (?, ?, ?, ?, ?)
                        """,
                userId,
                "seed-token-" + UUID.randomUUID(),
                Timestamp.from(expiresAt),
                Timestamp.from(createdAt),
                lastUsedAt == null ? null : Timestamp.from(lastUsedAt)
        );
    }

    private void seedCreditBalance(long userId, int balance, Instant updatedAt) {
        jdbcTemplate.update(
                "insert into user_credit_balances (user_id, balance, updated_at) values (?, ?, ?)",
                userId,
                balance,
                Timestamp.from(updatedAt)
        );
    }

    private void seedLedger(long userId, Long actorUserId, String type, int amount, int balanceAfter, Instant createdAt) {
        jdbcTemplate.update(
                """
                        insert into credit_ledger_entries (
                            user_id, actor_user_id, entry_type, amount, balance_after,
                            idempotency_key, reference_type, reference_id, reason, created_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                actorUserId,
                type,
                amount,
                balanceAfter,
                "stats-" + UUID.randomUUID(),
                "stats-test",
                String.valueOf(UUID.randomUUID()),
                "stats seed",
                Timestamp.from(createdAt)
        );
    }

    private void seedRun(
            long userId,
            String endpoint,
            String status,
            int chargedCredits,
            int refundedCredits,
            long promptTokens,
            long completionTokens,
            long totalTokens,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                """
                        insert into ai_analysis_runs (
                            user_id, endpoint, provider, model, status, idempotency_key,
                            request_hash, charged_credits, refunded_credits, prompt_tokens,
                            completion_tokens, total_tokens, created_at, updated_at, completed_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                endpoint,
                "deepseek",
                "deepseek-v4-flash",
                status,
                "stats-run-" + UUID.randomUUID(),
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                chargedCredits,
                refundedCredits,
                promptTokens,
                completionTokens,
                totalTokens,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private static String bearer(AuthPayload user) {
        return "Bearer " + user.accessToken();
    }

    private record AuthPayload(long userId, String accessToken) {
    }
}
