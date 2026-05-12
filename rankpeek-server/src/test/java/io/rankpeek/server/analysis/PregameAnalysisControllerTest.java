package io.rankpeek.server.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void pregameMockAcceptsTemporaryGamingSnapshotPayload() throws Exception {
        String request = """
                {
                  "patchKey": "26.09",
                  "queueId": 420,
                  "championId": 141,
                  "role": "JUNGLE",
                  "allyTeamTags": ["ally | W#1234 | champion=141 | status=NORMAL | tags=high win rate"],
                  "enemyTeamTags": ["enemy | Hidden#CN1 | champion=64 | status=PRIVATE"],
                  "snapshotSchemaVersion": "gaming_ai_input_snapshot.v1",
                  "snapshot": {
                    "schemaVersion": "gaming_ai_input_snapshot.v1",
                    "mode": "teammate",
                    "allyTeam": [
                      {
                        "displayName": "W#1234",
                        "championId": 141,
                        "recordStatus": "NORMAL",
                        "tags": [{"name": "high win rate"}]
                      }
                    ],
                    "enemyTeam": [
                      {
                        "displayName": "Hidden#CN1",
                        "championId": 64,
                        "recordStatus": "PRIVATE",
                        "tags": []
                      }
                    ]
                  }
                }
                """;

        mockMvc.perform(post("/api/analysis/pregame/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.analysisId").value("mock-pregame-26.09-141-JUNGLE-420"))
                .andExpect(jsonPath("$.data.type").value("PREGAME"))
                .andExpect(jsonPath("$.data.cost.mock").value(true))
                .andExpect(jsonPath("$.data.cost.chargedCredits").value(0));
    }

    @Test
    void pregameRequestDeserializesTemporaryGamingSnapshotFields() throws Exception {
        String request = """
                {
                  "queueId": 420,
                  "allyTeamTags": [],
                  "enemyTeamTags": [],
                  "snapshotSchemaVersion": "gaming_ai_input_snapshot.v1",
                  "snapshot": {
                    "schemaVersion": "gaming_ai_input_snapshot.v1",
                    "allyTeam": [{"displayName": "W#1234"}],
                    "enemyTeam": []
                  }
                }
                """;

        PregameAnalysisRequest parsed = objectMapper.readValue(request, PregameAnalysisRequest.class);

        org.assertj.core.api.Assertions.assertThat(parsed.snapshotSchemaVersion()).isEqualTo("gaming_ai_input_snapshot.v1");
        org.assertj.core.api.Assertions.assertThat(parsed.snapshot()).containsKey("allyTeam");
    }
}
