package io.rankpeek.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class LocalCacheRecoveryCoordinator {

    private static final long RECOVERY_THROTTLE_MILLIS = 10_000L;

    private final LocalCacheRecoveryService recoveryService;
    private final Clock clock;
    private final ObjectProvider<LocalCacheSchemaInitializer> schemaInitializerProvider;
    private final AtomicBoolean recoveryInProgress = new AtomicBoolean(false);
    private final AtomicLong lastRecoveryAttemptMillis = new AtomicLong(0);

    @Autowired
    public LocalCacheRecoveryCoordinator(
            LocalCacheRecoveryService recoveryService,
            ObjectProvider<LocalCacheSchemaInitializer> schemaInitializerProvider) {
        this(recoveryService, Clock.systemDefaultZone(), schemaInitializerProvider);
    }

    public LocalCacheRecoveryCoordinator(LocalCacheRecoveryService recoveryService, Clock clock) {
        this(recoveryService, clock, null);
    }

    public LocalCacheRecoveryCoordinator(
            LocalCacheRecoveryService recoveryService,
            Clock clock,
            ObjectProvider<LocalCacheSchemaInitializer> schemaInitializerProvider) {
        this.recoveryService = recoveryService;
        this.clock = clock;
        this.schemaInitializerProvider = schemaInitializerProvider;
    }

    public CoordinatedRecoveryResult recoverIfCorrupt(Throwable error, String trigger) {
        if (recoveryService == null || !recoveryService.isRecoverableCorruption(error)) {
            return CoordinatedRecoveryResult.notAttempted("error is not recognized as local H2 corruption");
        }

        long now = clock.millis();
        long previousAttempt = lastRecoveryAttemptMillis.get();
        if (previousAttempt > 0 && now - previousAttempt < RECOVERY_THROTTLE_MILLIS) {
            log.warn("Skipping local H2 recovery because a recent attempt is still within throttle: trigger={}, rootCause={}",
                    trigger,
                    recoveryService.rootCauseSummary(error));
            return CoordinatedRecoveryResult.throttled("local H2 recovery throttled");
        }

        if (!recoveryInProgress.compareAndSet(false, true)) {
            log.warn("Skipping local H2 recovery because another recovery is already running: trigger={}, rootCause={}",
                    trigger,
                    recoveryService.rootCauseSummary(error));
            return CoordinatedRecoveryResult.throttled("local H2 recovery already in progress");
        }

        lastRecoveryAttemptMillis.set(now);
        try {
            log.warn("Starting local H2 recovery: trigger={}, rootCause={}",
                    trigger,
                    recoveryService.rootCauseSummary(error));
            LocalCacheRecoveryService.RecoveryResult recoveryResult =
                    recoveryService.quarantineIfRecoverable(error);
            boolean schemaInitialized = false;
            if (recoveryResult.recovered()) {
                LocalCacheSchemaInitializer schemaInitializer = schemaInitializerProvider == null
                        ? null
                        : schemaInitializerProvider.getIfAvailable();
                if (schemaInitializer != null) {
                    schemaInitialized = schemaInitializer.initializeSchemaIfPossible();
                    if (schemaInitialized) {
                        log.info("Local cache schema initialized after coordinated H2 recovery: trigger={}", trigger);
                    } else {
                        log.warn("Local cache schema initialization failed after coordinated H2 recovery: trigger={}",
                                trigger);
                    }
                }
            }
            return new CoordinatedRecoveryResult(
                    recoveryResult.attempted(),
                    recoveryResult.recovered(),
                    schemaInitialized,
                    false,
                    recoveryResult.message(),
                    recoveryResult
            );
        } finally {
            recoveryInProgress.set(false);
        }
    }

    public boolean isRecoverableCorruption(Throwable error) {
        return recoveryService != null && recoveryService.isRecoverableCorruption(error);
    }

    public boolean isLockedOrUnavailable(Throwable error) {
        return recoveryService != null && recoveryService.isLockedOrUnavailable(error);
    }

    public String rootCauseSummary(Throwable error) {
        return recoveryService == null ? "" : recoveryService.rootCauseSummary(error);
    }

    public record CoordinatedRecoveryResult(
            boolean attempted,
            boolean recovered,
            boolean schemaInitialized,
            boolean throttled,
            String message,
            LocalCacheRecoveryService.RecoveryResult recoveryResult
    ) {
        private static CoordinatedRecoveryResult notAttempted(String message) {
            return new CoordinatedRecoveryResult(false, false, false, false, message, null);
        }

        private static CoordinatedRecoveryResult throttled(String message) {
            return new CoordinatedRecoveryResult(false, false, false, true, message, null);
        }
    }
}
