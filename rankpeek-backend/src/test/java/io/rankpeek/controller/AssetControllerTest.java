package io.rankpeek.controller;

import io.rankpeek.service.AssetService;
import io.rankpeek.service.LcuHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssetControllerTest {

    @Mock
    private LcuHttpClient lcuHttpClient;

    @Mock
    private AssetService assetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AssetController(lcuHttpClient, assetService)).build();
    }

    @Test
    void metadataEndpointReturnsItemAndAugmentMetadata() throws Exception {
        AssetService.GameAssetMetadata metadata = new AssetService.GameAssetMetadata(
                "lcu",
                "zh_CN",
                Map.of("6610", new AssetService.ItemMetadata(
                        6610,
                        "焚天",
                        "<mainText>40攻击力</mainText>",
                        "光盾打击",
                        "items/6610.png",
                        new AssetService.ItemGold(3100L, 900L, 2170L),
                        null,
                        null
                )),
                Map.of(),
                Map.of("2005", new AssetService.AugmentMetadata(
                        2005,
                        "扳机炼狱",
                        "每回合，你要么变大。",
                        "",
                        "gold",
                        "augments/2005.png"
                ))
        );
        when(assetService.getGameAssetMetadata()).thenReturn(metadata);

        mockMvc.perform(get("/api/v1/asset/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("lcu"))
                .andExpect(jsonPath("$.locale").value("zh_CN"))
                .andExpect(jsonPath("$.items.6610.name").value("焚天"))
                .andExpect(jsonPath("$.items.6610.gold.total").value(3100))
                .andExpect(jsonPath("$.augments.2005.name").value("扳机炼狱"))
                .andExpect(jsonPath("$.augments.2005.rarity").value("gold"));
    }
}
