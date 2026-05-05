package io.rankpeek.server.playstyle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PlaystyleCardServiceTest {

    @Autowired
    private PlaystyleCardService playstyleCardService;

    @Test
    void createsQueriesAndExpiresMockCard() {
        PlaystyleCard created = playstyleCardService.createMockCard("26.10", 82, "MID");

        assertThat(playstyleCardService.findCards("26.10", 82, "MID"))
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.id()).isEqualTo(created.id());
                    assertThat(card.freshnessStatus()).isEqualTo("FRESH");
                });

        playstyleCardService.addInvalidatingRule("26.10", 82, "Mock champion change invalidates the card");

        assertThat(playstyleCardService.findCards("26.10", 82, "MID"))
                .singleElement()
                .extracting(PlaystyleCard::freshnessStatus)
                .isEqualTo("EXPIRED");
    }
}
