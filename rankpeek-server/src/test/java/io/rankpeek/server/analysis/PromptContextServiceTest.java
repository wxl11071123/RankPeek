package io.rankpeek.server.analysis;

import io.rankpeek.server.cnmeta.CnMetaService;
import io.rankpeek.server.esports.LplEsportsService;
import io.rankpeek.server.patch.PatchService;
import io.rankpeek.server.playstyle.PlaystyleCardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PromptContextServiceTest {

    @Autowired
    private PatchService patchService;

    @Autowired
    private CnMetaService cnMetaService;

    @Autowired
    private LplEsportsService lplEsportsService;

    @Autowired
    private PlaystyleCardService playstyleCardService;

    @Autowired
    private PromptContextService promptContextService;

    @Test
    void aggregatesDeterministicPromptContext() {
        patchService.saveMockPatchVersion("26.09");
        cnMetaService.saveMockSnapshot("26.09", 81, "ADC");
        lplEsportsService.saveMockUsage("26.09", 81, "ADC");
        playstyleCardService.createMockCard("26.09", 81, "ADC");

        PromptContext first = promptContextService.buildContext(81, "ADC", "26.09", 420);
        PromptContext second = promptContextService.buildContext(81, "ADC", "26.09", 420);

        assertThat(second).isEqualTo(first);
        assertThat(first.patchNotes()).isNotEmpty();
        assertThat(first.cnMetaNotes()).isNotEmpty();
        assertThat(first.lplNotes()).isNotEmpty();
        assertThat(first.playstyleCards()).isNotEmpty();
    }
}
