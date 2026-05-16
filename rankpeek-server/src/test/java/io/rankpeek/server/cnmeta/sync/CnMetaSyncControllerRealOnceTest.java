package io.rankpeek.server.cnmeta.sync;

import io.rankpeek.server.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
    void configuredMatrixRejectsRealSourceEvenWhenManualIsAllowed() throws Exception {
        MockMvc mockMvc = mockMvc(properties(true, "real"), mock(CnMetaSyncService.class));

        mockMvc.perform(post("/api/cn-meta/sync/configured-matrix")
                        .param("patchKey", "26.09"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CN_META_REAL_SOURCE_DISABLED"));
    }

    private static MockMvc mockMvc(CnMetaSyncProperties properties, CnMetaSyncService service) {
        return MockMvcBuilders
                .standaloneSetup(new CnMetaSyncController(properties, service))
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
