package io.rankpeek.server.cnmeta.sync;

import io.rankpeek.server.patch.PatchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class CnMetaSyncScheduler {

    private final CnMetaSyncProperties properties;
    private final CnMetaSyncService syncService;
    private final PatchService patchService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CnMetaSyncScheduler(
            CnMetaSyncProperties properties,
            CnMetaSyncService syncService,
            PatchService patchService
    ) {
        this.properties = properties;
        this.syncService = syncService;
        this.patchService = patchService;
    }

    @Scheduled(cron = "${rankpeek.cn-meta.sync.cron}", zone = "${rankpeek.cn-meta.sync.zone}")
    public void runScheduledSync() {
        if (!properties.enabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            String patchKey = patchService.findCurrentPatch()
                    .map(patch -> patch.patchKey())
                    .orElse("mock-current");
            syncService.syncConfiguredMatrix(patchKey);
        } finally {
            running.set(false);
        }
    }
}
