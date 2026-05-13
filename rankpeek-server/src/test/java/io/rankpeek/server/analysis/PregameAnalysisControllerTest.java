package io.rankpeek.server.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
                  "mode": "teammate",
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
                  "mode": "opponent",
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
        org.assertj.core.api.Assertions.assertThat(parsed.mode()).isEqualTo("opponent");
        org.assertj.core.api.Assertions.assertThat(parsed.snapshot()).containsKey("allyTeam");
    }

    @Test
    void pregameStreamReturnsMockSseAnalysisForTemporaryGamingSnapshot() throws Exception {
        String request = """
                {
                  "mode": "teammate",
                  "queueId": 420,
                  "allyTeamTags": ["ally | W#1234 | champion=141 | status=NORMAL | sample=20"],
                  "enemyTeamTags": ["enemy | Hidden#CN1 | champion=64 | status=PRIVATE"],
                  "snapshotSchemaVersion": "gaming_ai_input_snapshot.v1",
                  "snapshot": {
                    "schemaVersion": "gaming_ai_input_snapshot.v1",
                    "mode": "teammate",
                    "allyTeam": [{"displayName": "W#1234"}],
                    "enemyTeam": [{"displayName": "Hidden#CN1"}]
                  }
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/analysis/pregame/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(request))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:start")))
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("event:done")));
    }
}
