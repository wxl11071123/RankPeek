package io.rankpeek.server.cnmeta.sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rankpeek.cn-meta.sync.request-delay-ms=0",
        "rankpeek.cn-meta.sync.allow-manual=true",
        "rankpeek.cn-meta.sync.tiers[0]=GOLD",
        "rankpeek.cn-meta.sync.roles[0]=MID"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CnMetaSyncControllerManualMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void configuredMatrixRunsWhenManualIsAllowed() throws Exception {
        mockMvc.perform(post("/api/cn-meta/sync/configured-matrix")
                        .param("patchKey", "26.37"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"));
    }
}
