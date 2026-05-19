package io.rankpeek.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServerHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    void versionReturnsConfiguredVersion() throws Exception {
        mockMvc.perform(get("/api/server/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value("0.1.0"))
                .andExpect(jsonPath("$.data.service").value("rankpeek-server"));
    }

    @Test
    void diagnosticsReturnsDatabaseAndFlywayStatus() throws Exception {
        mockMvc.perform(get("/api/server/diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.service").value("rankpeek-server"))
                .andExpect(jsonPath("$.data.mode").value("test"))
                .andExpect(jsonPath("$.data.database.status").value("ok"))
                .andExpect(jsonPath("$.data.flyway.status").value("ok"))
                .andExpect(jsonPath("$.data.flyway.currentVersion").value("4"))
                .andExpect(jsonPath("$.data.flyway.appliedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    @Test
    void diagnosticsAllowsRendererOrigin() throws Exception {
        mockMvc.perform(get("/api/server/diagnostics").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }
}
