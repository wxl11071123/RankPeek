package io.rankpeek.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private LcuHttpClient lcuHttpClient;

    private AssetService service;

    @BeforeEach
    void setUp() {
        service = new AssetService(lcuHttpClient);
    }

    @Test
    void loadBuiltInChampionsUsesCurrentChampionIdsWhenLcuSummaryFails() {
        when(lcuHttpClient.get(eq("lol-game-data/assets/v1/champion-summary"), eq(AssetService.Champion[].class)))
                .thenThrow(new RuntimeException("LCU unavailable"));

        ReflectionTestUtils.invokeMethod(service, "loadChampions");

        assertThat(service.getChampionName(233)).isEqualTo("贝蕾亚");
        assertThat(service.getChampionName(200)).isEqualTo("卑尔维斯");
    }
}
