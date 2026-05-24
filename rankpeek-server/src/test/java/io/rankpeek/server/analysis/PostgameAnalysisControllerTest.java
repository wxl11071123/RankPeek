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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostgameAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void postgameRequestDeserializesSnapshotFields() throws Exception {
        String request = """
                {
                  "mode": "review",
                  "snapshotSchemaVersion": "postgame_ai_input_snapshot.v1",
                  "snapshot": {
                    "schemaVersion": "postgame_ai_input_snapshot.v1",
                    "dataQuality": {
                      "hasGameDetail": true,
                      "hasTimeline": false,
                      "participantCount": 10
                    }
                  }
                }
                """;

        PostgameAnalysisRequest parsed = objectMapper.readValue(request, PostgameAnalysisRequest.class);

        org.assertj.core.api.Assertions.assertThat(parsed.mode()).isEqualTo("review");
        org.assertj.core.api.Assertions.assertThat(parsed.snapshotSchemaVersion()).isEqualTo("postgame_ai_input_snapshot.v1");
        org.assertj.core.api.Assertions.assertThat(parsed.snapshot()).containsKey("dataQuality");
    }

    @Test
    void postgameStreamReturnsMockSseForReviewSnapshot() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/analysis/postgame/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(createRequest("review")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:start")))
                .andExpect(content().string(containsString("RankPeek postgame mock stream started")))
                .andExpect(content().string(containsString("postgame_review_result.v1")))
                .andExpect(content().string(containsString("\\u592F")))
                .andExpect(content().string(containsString("\\u62C9\\u5B8C\\u4E86")))
                .andExpect(content().string(containsString("event:done")));
    }

    @Test
    void postgameStreamReturnsMockSseForPraiseSnapshot() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/analysis/postgame/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(createRequest("praise")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("postgame_praise_result.v1")))
                .andExpect(content().string(containsString("\\u8FD9\\u5C40\\u4F60\\u6709\\u4E1C\\u897F\\u7684")))
                .andExpect(content().string(containsString("event:done")));
    }

    @Test
    void postgameStreamAcceptsEmptySnapshotWithoutServerError() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/analysis/postgame/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "mode": "review",
                                  "snapshotSchemaVersion": "postgame_ai_input_snapshot.v1",
                                  "snapshot": {}
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RankPeek postgame mock stream started")))
                .andExpect(content().string(containsString("postgame_review_result.v1")))
                .andExpect(content().string(containsString("event:done")));
    }

    private static String createRequest(String mode) {
        return """
                {
                  "mode": "%s",
                  "snapshotSchemaVersion": "postgame_ai_input_snapshot.v1",
                  "snapshot": {
                    "schemaVersion": "postgame_ai_input_snapshot.v1",
                    "mode": "%s",
                    "dataQuality": {
                      "hasGameDetail": true,
                      "hasTimeline": false,
                      "participantCount": 10
                    }
                  }
                }
                """.formatted(mode, mode);
    }
}
