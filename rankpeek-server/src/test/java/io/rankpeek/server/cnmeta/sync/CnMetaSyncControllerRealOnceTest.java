package io.rankpeek.server.cnmeta.sync;

import io.rankpeek.server.auth.AuthService;
import io.rankpeek.server.auth.AuthUser;
import io.rankpeek.server.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CnMetaSyncControllerRealOnceTest {

    @Test
    void realOnceCallsRealSourceWithAggregateAllRoleWhenExplicitlyEnabled() throws Exception {
        CnMetaSyncService service = mock(CnMetaSyncService.class);
        CnMetaSyncResult result = new CnMetaSyncResult(
                7L,
                "real-101",
                "26.09",
                420,
                "PLATINUM",
                "ALL",
                "SUCCESS",
                1,
                1,
                "hash",
                null,
                Instant.parse("2026-05-15T00:00:00Z"),
                Instant.parse("2026-05-15T00:00:01Z")
        );
        when(service.syncOnceWithSource("real", "26.09", 420, "PLATINUM", "ALL")).thenReturn(result);
        MockMvc mockMvc = mockMvc(properties(true, "mock"), service);

        mockMvc.perform(post("/api/cn-meta/sync/real-once")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .param("patchKey", "26.09")
                        .param("tierScope", "PLATINUM")
                        .param("role", "TOP")
                        .param("queueId", "420"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.source").value("real-101"))
                .andExpect(jsonPath("$.data.role").value("ALL"));

        verify(service).syncOnceWithSource("real", "26.09", 420, "PLATINUM", "ALL");
    }

    @Test
    void configuredMatrixAllowsRealSourceWhenManualIsAllowed() throws Exception {
        CnMetaSyncService service = mock(CnMetaSyncService.class);
        CnMetaSyncResult result = new CnMetaSyncResult(
                8L,
                "real-101",
                "26.09",
                420,
                "PLATINUM",
                "ALL",
                "SUCCESS",
                1,
                1,
                "hash",
                null,
                Instant.parse("2026-05-15T00:00:00Z"),
                Instant.parse("2026-05-15T00:00:01Z")
        );
        when(service.syncConfiguredMatrix("26.09")).thenReturn(List.of(result));
        MockMvc mockMvc = mockMvc(properties(true, "real"), service);

        mockMvc.perform(post("/api/cn-meta/sync/configured-matrix")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .param("patchKey", "26.09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].source").value("real-101"))
                .andExpect(jsonPath("$.data[0].role").value("ALL"));

        verify(service).syncConfiguredMatrix("26.09");
    }

    private static MockMvc mockMvc(CnMetaSyncProperties properties, CnMetaSyncService service) {
        AuthService authService = mock(AuthService.class);
        when(authService.requireAdmin(any())).thenReturn(new AuthUser(
                1L,
                "admin@example.com",
                "Admin",
                "hash",
                "ACTIVE",
                "ADMIN",
                Instant.parse("2026-05-15T00:00:00Z"),
                Instant.parse("2026-05-15T00:00:00Z"),
                null
        ));
        return MockMvcBuilders
                .standaloneSetup(new CnMetaSyncController(properties, service, authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static CnMetaSyncProperties properties(boolean realSourceEnabled, String source) {
        return new CnMetaSyncProperties(
                false,
                true,
                source,
                "0 30 4 * * *",
                "Asia/Shanghai",
                0,
                0,
                List.of(401, 403, 429),
                420,
                List.of("GOLD", "PLATINUM"),
                List.of("MID", "TOP"),
                realSourceEnabled,
                "",
                1,
                666,
                1,
                Map.of("PLATINUM", "20"),
                "RankPeek/dev-public-aggregate-client",
                500,
                2000,
                20000
        );
    }
}
