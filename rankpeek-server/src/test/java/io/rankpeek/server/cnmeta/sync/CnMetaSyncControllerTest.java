package io.rankpeek.server.cnmeta.sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "rankpeek.cn-meta.sync.request-delay-ms=0")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CnMetaSyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mockOnceWritesStatsThatExistingChampionEndpointCanQuery() throws Exception {
        mockMvc.perform(post("/api/cn-meta/sync/mock-once")
                        .param("patchKey", "26.34")
                        .param("tierScope", "GOLD")
                        .param("role", "MID")
                        .param("queueId", "420"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.source").value("mock-101"))
                .andExpect(jsonPath("$.data.rowCount").value(3));

        mockMvc.perform(get("/api/cn-meta/sync/jobs").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].source").value("mock-101"));

        mockMvc.perform(get("/api/cn-meta/champions/103")
                        .param("patchKey", "26.34")
                        .param("role", "MID")
                        .param("tierScope", "GOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].championId").value(103))
                .andExpect(jsonPath("$.data[0].tierScope").value("GOLD"));
    }

    @Test
    void latestChampionEndpointUsesExactTierWithoutPatchKeyOrFallback() throws Exception {
        mockMvc.perform(post("/api/cn-meta/sync/mock-once")
                        .param("patchKey", "26.37")
                        .param("tierScope", "GOLD")
                        .param("role", "MID")
                        .param("queueId", "420"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cn-meta/champions/103/latest")
                        .param("tierScope", "GOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].patchKey").value("26.37"))
                .andExpect(jsonPath("$.data[0].championId").value(103))
                .andExpect(jsonPath("$.data[0].tierScope").value("GOLD"));

        mockMvc.perform(get("/api/cn-meta/champions/103/latest")
                        .param("tierScope", "EMERALD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void serverHealthRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/server/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ok"));
    }

    @Test
    void configuredMatrixIsRejectedWhenDisabledAndManualNotAllowed() throws Exception {
        mockMvc.perform(post("/api/cn-meta/sync/configured-matrix")
                        .param("patchKey", "26.35"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CN_META_SYNC_DISABLED"));
    }

    @Test
    void realOnceIsRejectedWhenRealSourceIsDisabled() throws Exception {
        mockMvc.perform(post("/api/cn-meta/sync/real-once")
                        .param("patchKey", "26.35")
                        .param("tierScope", "GOLD")
                        .param("role", "MID")
                        .param("queueId", "420"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CN_META_REAL_SOURCE_DISABLED"));
    }

    @Test
    void mockOnceRejectsInvalidRoleTierAndQueue() throws Exception {
        mockMvc.perform(post("/api/cn-meta/sync/mock-once")
                        .param("patchKey", "26.36")
                        .param("tierScope", "GOLD")
                        .param("role", "BOT")
                        .param("queueId", "420"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        mockMvc.perform(post("/api/cn-meta/sync/mock-once")
                        .param("patchKey", "26.36")
                        .param("tierScope", "WOOD")
                        .param("role", "MID")
                        .param("queueId", "420"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        mockMvc.perform(post("/api/cn-meta/sync/mock-once")
                        .param("patchKey", "26.36")
                        .param("tierScope", "GOLD")
                        .param("role", "MID")
                        .param("queueId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }
}
