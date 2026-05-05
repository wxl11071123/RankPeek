package io.rankpeek.server.cnmeta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CnMetaServiceTest {

    @Autowired
    private CnMetaService cnMetaService;

    @Test
    void savesAndQueriesMockSnapshotWithoutNetworkAccess() {
        cnMetaService.saveMockSnapshot("26.09", 81, "ADC");

        assertThat(cnMetaService.isExternalNetworkDisabled()).isTrue();
        assertThat(cnMetaService.findChampionMeta("26.09", 81, "ADC", "PLATINUM_PLUS"))
                .singleElement()
                .satisfies(meta -> {
                    assertThat(meta.source()).isEqualTo("mock-101");
                    assertThat(meta.championId()).isEqualTo(81);
                    assertThat(meta.role()).isEqualTo("ADC");
                });
    }
}
