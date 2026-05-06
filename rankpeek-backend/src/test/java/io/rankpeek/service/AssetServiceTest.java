package io.rankpeek.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Test
    void loadItemsCachesLcuMetadataForFrontendTooltips() {
        AssetService.Item item = new AssetService.Item();
        item.setId(6610);
        item.setName("焚天");
        item.setDescription("<mainText><stats>40攻击力<br>400生命值</stats></mainText>");
        item.setPlaintext("光盾打击");
        item.setIconPath("/lol-game-data/assets/v1/items/6610.png");
        item.setGold(new AssetService.ItemGold(3100L, 900L, 2170L));

        when(lcuHttpClient.get(eq("lol-game-data/assets/v1/items.json"), eq(AssetService.Item[].class)))
                .thenReturn(new AssetService.Item[]{item});

        ReflectionTestUtils.invokeMethod(service, "loadItems");

        AssetService.GameAssetMetadata metadata = service.getGameAssetMetadata();
        AssetService.ItemMetadata entry = metadata.items().get("6610");

        assertThat(entry).isNotNull();
        assertThat(entry.id()).isEqualTo(6610);
        assertThat(entry.name()).isEqualTo("焚天");
        assertThat(entry.description()).contains("40攻击力");
        assertThat(entry.plaintext()).isEqualTo("光盾打击");
        assertThat(entry.icon()).isEqualTo("items/6610.png");
        assertThat(entry.gold().total()).isEqualTo(3100);
        assertThat(service.getItemIconPath(6610)).isEqualTo("/lol-game-data/assets/v1/items/6610.png");
    }

    @Test
    void loadAugmentsCachesLcuMetadataForFrontendTooltips() {
        AssetService.CherryAugment augment = new AssetService.CherryAugment();
        augment.setId(2005);
        augment.setNameTra("扳机炼狱");
        augment.setDescriptionTra("每回合，你要么变大。");
        augment.setRarity("gold");
        augment.setAugmentSmallIconPath("/lol-game-data/assets/v1/augments/2005.png");

        when(lcuHttpClient.get(eq("lol-game-data/assets/v1/cherry-augments.json"), eq(AssetService.CherryAugment[].class)))
                .thenReturn(new AssetService.CherryAugment[]{augment});

        ReflectionTestUtils.invokeMethod(service, "loadAugments");

        AssetService.GameAssetMetadata metadata = service.getGameAssetMetadata();
        AssetService.AugmentMetadata entry = metadata.augments().get("2005");

        assertThat(entry).isNotNull();
        assertThat(entry.id()).isEqualTo(2005);
        assertThat(entry.name()).isEqualTo("扳机炼狱");
        assertThat(entry.description()).isEqualTo("每回合，你要么变大。");
        assertThat(entry.rarity()).isEqualTo("gold");
        assertThat(entry.icon()).isEqualTo("augments/2005.png");
        assertThat(service.getAugmentIconPath(2005)).isEqualTo("/lol-game-data/assets/v1/augments/2005.png");
    }

    @Test
    void lcuMetadataLoadingFailuresDoNotEscape() {
        when(lcuHttpClient.get(eq("lol-game-data/assets/v1/items.json"), eq(AssetService.Item[].class)))
                .thenThrow(new RuntimeException("LCU unavailable"));
        when(lcuHttpClient.get(eq("lol-game-data/assets/v1/cherry-augments.json"), eq(AssetService.CherryAugment[].class)))
                .thenThrow(new RuntimeException("LCU unavailable"));

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(service, "loadItems")).doesNotThrowAnyException();
        assertThatCode(() -> ReflectionTestUtils.invokeMethod(service, "loadAugments")).doesNotThrowAnyException();

        assertThat(service.getGameAssetMetadata().items()).isEmpty();
        assertThat(service.getGameAssetMetadata().augments()).isEmpty();
    }
}
