package io.rankpeek.server.analysis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PregameAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pregameMockReturnsZeroCostMockAnalysis() throws Exception {
        String request = """
                {
                  "patchKey": "26.09",
                  "queueId": 420,
                  "championId": 81,
                  "role": "ADC",
                  "allyTeamTags": ["has_frontline", "has_engage"],
                  "enemyTeamTags": ["many_divers"]
                }
                """;

        mockMvc.perform(post("/api/analysis/pregame/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.analysisId").value("mock-pregame-26.09-81-ADC-420"))
                .andExpect(jsonPath("$.data.type").value("PREGAME"))
                .andExpect(jsonPath("$.data.cost.mock").value(true))
                .andExpect(jsonPath("$.data.cost.chargedCredits").value(0));
    }
}
