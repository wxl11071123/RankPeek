package io.rankpeek.server.cnmeta.sync;

import io.rankpeek.server.patch.PatchService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CnMetaSyncSchedulerTest {

    @Test
    void disabledSchedulerDoesNotRunService() {
        CnMetaSyncService service = mock(CnMetaSyncService.class);
        PatchService patchService = mock(PatchService.class);
        CnMetaSyncScheduler scheduler = new CnMetaSyncScheduler(disabledProperties(), service, patchService);

        scheduler.runScheduledSync();

        verifyNoInteractions(service);
        verifyNoInteractions(patchService);
    }

    @Test
    void enabledSchedulerRunsConfiguredMatrixWithFallbackPatch() {
        CnMetaSyncService service = mock(CnMetaSyncService.class);
        PatchService patchService = mock(PatchService.class);
        when(patchService.findCurrentPatch()).thenReturn(Optional.empty());
        CnMetaSyncScheduler scheduler = new CnMetaSyncScheduler(enabledProperties(), service, patchService);

        scheduler.runScheduledSync();

        verify(service).syncConfiguredMatrix("mock-current");
    }

    @Test
    void enabledSchedulerPreventsReentryInSameProcess() {
        CnMetaSyncService service = mock(CnMetaSyncService.class);
        PatchService patchService = mock(PatchService.class);
        when(patchService.findCurrentPatch()).thenReturn(Optional.empty());
        CnMetaSyncScheduler[] schedulerRef = new CnMetaSyncScheduler[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            schedulerRef[0].runScheduledSync();
            return List.of();
        }).when(service).syncConfiguredMatrix("mock-current");
        CnMetaSyncScheduler scheduler = new CnMetaSyncScheduler(enabledProperties(), service, patchService);
        schedulerRef[0] = scheduler;

        scheduler.runScheduledSync();

        verify(service, times(1)).syncConfiguredMatrix("mock-current");
    }

    @Test
    void enabledSchedulerRunsRealSourceMatrix() {
        CnMetaSyncService service = mock(CnMetaSyncService.class);
        PatchService patchService = mock(PatchService.class);
        when(patchService.findCurrentPatch()).thenReturn(Optional.empty());
        CnMetaSyncScheduler scheduler = new CnMetaSyncScheduler(realSourceProperties(), service, patchService);

        scheduler.runScheduledSync();

        verify(service).syncConfiguredMatrix("mock-current");
    }

    private static CnMetaSyncProperties disabledProperties() {
        return properties(false);
    }

    private static CnMetaSyncProperties enabledProperties() {
        return properties(true);
    }

    private static CnMetaSyncProperties realSourceProperties() {
        return new CnMetaSyncProperties(
                true,
                false,
                "real",
                "0 30 4 * * *",
                "Asia/Shanghai",
                0,
                2,
                List.of(401, 403, 429),
                420,
                List.of("GOLD"),
                List.of("MID"),
                true,
                "http://127.0.0.1/never-called",
                1,
                666,
                1,
                Map.of("PLATINUM", "20"),
                "RankPeek/dev-public-aggregate-client",
                500,
                2000,
                20000
        );
    }

    private static CnMetaSyncProperties properties(boolean enabled) {
        return new CnMetaSyncProperties(
                enabled,
                false,
                "mock",
                "0 30 4 * * *",
                "Asia/Shanghai",
                0,
                2,
                List.of(401, 403, 429),
                420,
                List.of("GOLD"),
                List.of("MID"),
                false,
                "",
                1,
                666,
                1,
                Map.of("PLATINUM", "20"),
                "RankPeek/dev-public-aggregate-client",
                500,
                2000,
                20000
        );
    }
}
