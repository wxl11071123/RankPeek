package io.rankpeek.controller;

import io.rankpeek.model.CacheStatus;
import io.rankpeek.service.CacheStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CacheControllerTest {

    private CacheStatusService cacheStatusService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cacheStatusService = mock(CacheStatusService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CacheController(cacheStatusService)).build();
    }

    @Test
    void getCacheStatus_returnsSuccessfulApiResponse() throws Exception {
        CacheStatus status = CacheStatus.builder()
                .enabled(true)
                .databasePath("C:/RankPeek/cache/rankpeek-cache")
                .databaseSizeBytes(1234L)
                .summonerCount(2L)
                .rankCount(2L)
                .matchCount(10L)
                .gameDetailCount(3L)
                .participantCount(100L)
                .playerMatchIndexCount(80L)
                .trackedPlayerCount(8L)
                .latestMatchCreation(1710000000000L)
                .build();
        when(cacheStatusService.getStatus()).thenReturn(status);

        mockMvc.perform(get("/api/v1/cache/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.databasePath").value("C:/RankPeek/cache/rankpeek-cache"))
                .andExpect(jsonPath("$.data.databaseSizeBytes").value(1234))
                .andExpect(jsonPath("$.data.summonerCount").value(2))
                .andExpect(jsonPath("$.data.rankCount").value(2))
                .andExpect(jsonPath("$.data.matchCount").value(10))
                .andExpect(jsonPath("$.data.gameDetailCount").value(3))
                .andExpect(jsonPath("$.data.participantCount").value(100))
                .andExpect(jsonPath("$.data.playerMatchIndexCount").value(80))
                .andExpect(jsonPath("$.data.trackedPlayerCount").value(8))
                .andExpect(jsonPath("$.data.latestMatchCreation").value(1710000000000L));
    }
}
