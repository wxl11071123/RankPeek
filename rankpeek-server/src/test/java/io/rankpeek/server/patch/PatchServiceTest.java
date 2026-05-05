package io.rankpeek.server.patch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PatchServiceTest {

    @Autowired
    private PatchService patchService;

    @Test
    void savesMockPatchAndQueriesItBack() {
        PatchVersion saved = patchService.saveMockPatchVersion("26.09");

        assertThat(patchService.findPatchVersion("26.09")).contains(saved);
        assertThat(patchService.findCurrentPatch()).isPresent();
        assertThat(patchService.findPatchChanges("26.09"))
                .extracting(PatchChange::targetKey)
                .contains("81");
    }
}
