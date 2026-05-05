package io.rankpeek.server.esports;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LplEsportsServiceTest {

    @Autowired
    private LplEsportsService lplEsportsService;

    @Test
    void savesAndQueriesMockLplUsageWithoutNetworkAccess() {
        lplEsportsService.saveMockUsage("26.09", 81, "ADC");

        assertThat(lplEsportsService.isExternalNetworkDisabled()).isTrue();
        assertThat(lplEsportsService.findChampionUsage("26.09", 81, "ADC"))
                .singleElement()
                .satisfies(usage -> {
                    assertThat(usage.championId()).isEqualTo(81);
                    assertThat(usage.role()).isEqualTo("ADC");
                    assertThat(usage.source()).isEqualTo("mock-lpl");
                });
    }
}
