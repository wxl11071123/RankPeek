package io.rankpeek.server.opgg;

import io.rankpeek.server.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpggChampionControllerTest {

    @Test
    void detailEndpointReturnsLightweightChampionData() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new OpggChampionController(
                        query -> detail(query.tier(), query.position()),
                        query -> championList(query.tier())
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/opgg/champions/103/detail")
                        .param("mode", "ranked")
                        .param("region", "kr")
                        .param("tier", "emerald_plus")
                        .param("position", "mid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.championId").value(103))
                .andExpect(jsonPath("$.data.mode").value("ranked"))
                .andExpect(jsonPath("$.data.region").value("kr"))
                .andExpect(jsonPath("$.data.tier").value("emerald_plus"))
                .andExpect(jsonPath("$.data.position").value("mid"))
                .andExpect(jsonPath("$.data.stats.winRate").value(0.51))
                .andExpect(jsonPath("$.data.summonerSpells[0].ids[0]").value(4))
                .andExpect(jsonPath("$.data.coreItems[0].ids[2]").value(4645));
    }

    @Test
    void rankedDetailRequiresSupportedKrTierAndPosition() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new OpggChampionController(
                        query -> detail(query.tier(), query.position()),
                        query -> championList(query.tier())
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/opgg/champions/103/detail")
                        .param("mode", "ranked")
                        .param("region", "na")
                        .param("tier", "emerald_plus")
                        .param("position", "mid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/api/opgg/champions/103/detail")
                        .param("mode", "ranked")
                        .param("region", "kr")
                        .param("tier", "emerald_plus")
                        .param("position", "none"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void nonRankedDetailNormalizesMissingFiltersToAllAndNone() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new OpggChampionController(
                        query -> detail(query.tier(), query.position()),
                        query -> championList(query.tier())
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/opgg/champions/103/detail")
                        .param("mode", "aram")
                        .param("region", "kr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("all"))
                .andExpect(jsonPath("$.data.position").value("none"));
    }

    @Test
    void listEndpointReturnsChampionTierRowsWithoutChampionSelection() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new OpggChampionController(
                        query -> detail(query.tier(), query.position()),
                        query -> championList(query.tier())
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/opgg/champions")
                        .param("mode", "ranked")
                        .param("region", "kr")
                        .param("tier", "emerald_plus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mode").value("ranked"))
                .andExpect(jsonPath("$.data.region").value("kr"))
                .andExpect(jsonPath("$.data.tier").value("emerald_plus"))
                .andExpect(jsonPath("$.data.items[0].championId").value(103))
                .andExpect(jsonPath("$.data.items[0].rank").value(7))
                .andExpect(jsonPath("$.data.items[0].positions[0].position").value("mid"))
                .andExpect(jsonPath("$.data.items[0].positions[0].counters[0].championId").value(238));
    }

    @Test
    void listEndpointAcceptsAllTierButStillRequiresKrRegion() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new OpggChampionController(
                        query -> detail(query.tier(), query.position()),
                        query -> championList(query.tier())
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/opgg/champions")
                        .param("mode", "ranked")
                        .param("region", "kr")
                        .param("tier", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("all"));

        mockMvc.perform(get("/api/opgg/champions")
                        .param("mode", "ranked")
                        .param("region", "na")
                        .param("tier", "all"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void opggEndpointsAllowRendererOrigin() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new OpggChampionController(
                        query -> detail(query.tier(), query.position()),
                        query -> championList(query.tier())
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/opgg/champions")
                        .param("mode", "ranked")
                        .param("region", "kr")
                        .param("tier", "all")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    private static OpggChampionDetail detail(String tier, String position) {
        return new OpggChampionDetail(
                103,
                "Ahri",
                "ranked",
                "kr",
                tier,
                position,
                "16.10",
                Instant.parse("2026-05-23T04:00:00Z"),
                new OpggChampionStats(1000, 0.51, 0.12, 0.03, 2.6),
                List.of(new OpggBuildOption("spells", List.of(4, 12), List.of(), 100L, 0.52, 0.6)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new OpggBuildOption("core", List.of(3118, 3152, 4645), List.of(), 70L, 0.54, 0.19))
        );
    }

    private static OpggChampionList championList(String tier) {
        return new OpggChampionList(
                "ranked",
                "kr",
                tier,
                "16.10",
                Instant.parse("2026-05-23T04:00:00Z"),
                List.of(new OpggChampionListItem(
                        103,
                        1,
                        7,
                        new OpggChampionStats(0, 0.51, 0.12, 0.03, 2.6),
                        List.of(new OpggChampionPositionStats(
                                "mid",
                                0,
                                2,
                                new OpggChampionStats(0, 0.50, 0.10, 0.03, 2.5),
                                List.of(new OpggChampionCounter(238, 1200, 590L))
                        ))
                ))
        );
    }
}
