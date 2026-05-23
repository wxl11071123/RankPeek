package io.rankpeek.server.cnmeta.sync;

import io.rankpeek.server.cnmeta.CnMetaRoles;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

@Service
public class CnMetaSyncService {

    private final CnMetaSyncProperties properties;
    private final CnMetaSyncRepository repository;
    private final Map<String, CnMetaSourceClient> clientsBySource;
    private final TransactionOperations transactionOperations;

    @Autowired
    public CnMetaSyncService(
            CnMetaSyncProperties properties,
            CnMetaSyncRepository repository,
            List<CnMetaSourceClient> sourceClients,
            PlatformTransactionManager transactionManager
    ) {
        this(properties, repository, sourceClients, new TransactionTemplate(transactionManager));
    }

    CnMetaSyncService(
            CnMetaSyncProperties properties,
            CnMetaSyncRepository repository,
            List<CnMetaSourceClient> sourceClients
    ) {
        this(properties, repository, sourceClients, new NoTransactionOperations());
    }

    private CnMetaSyncService(
            CnMetaSyncProperties properties,
            CnMetaSyncRepository repository,
            List<CnMetaSourceClient> sourceClients,
            TransactionOperations transactionOperations
    ) {
        this.properties = properties;
        this.repository = repository;
        this.clientsBySource = indexClients(sourceClients);
        this.transactionOperations = transactionOperations;
    }

    public CnMetaSyncResult syncOnce(String patchKey, Integer queueId, String tierScope, String role) {
        return syncOnceWithSource(properties.source(), patchKey, queueId, tierScope, role);
    }

    public CnMetaSyncResult syncOnceWithSource(String source, String patchKey, Integer queueId, String tierScope, String role) {
        String normalizedSource = normalizeSource(source);
        CnMetaSourceClient client = clientFor(normalizedSource);
        String requestRole = aggregateRoleForRealSource(client.source(), role);
        Instant startedAt = Instant.now();
        CnMetaSyncJob job = inTransaction(() -> repository.createJob(client.source(), patchKey, queueId, tierScope, requestRole, startedAt));

        int[] requestCount = {0};
        try {
            SourceFetch fetch = fetchWithRetries(client, patchKey, queueId, tierScope, requestRole, requestCount);
            CnMetaSourcePayload payload = fetch.payload();
            String contentHash = hash(payload.rawContent());
            int finalRequestCount = fetch.requestCount();
            return inTransaction(() -> finishSuccessfulJob(
                    job,
                    payload,
                    patchKey,
                    queueId,
                    tierScope,
                    requestRole,
                    contentHash,
                    finalRequestCount
            ));
        } catch (CnMetaSourceException exception) {
            String status = shouldStop(exception) ? "STOPPED" : "FAILED";
            int finalRequestCount = requestCount[0];
            return inTransaction(() -> finishFailedJob(job.id(), status, finalRequestCount, exception.getMessage()));
        } catch (RuntimeException exception) {
            int finalRequestCount = requestCount[0];
            return inTransaction(() -> finishFailedJob(job.id(), "FAILED", finalRequestCount, exception.getMessage()));
        }
    }

    public List<CnMetaSyncResult> syncConfiguredMatrix(String patchKey) {
        String configuredSource = normalizeSource(properties.source());
        List<CnMetaSyncResult> results = new ArrayList<>();
        boolean first = true;
        boolean realSource = isRealSourceAlias(configuredSource);
        for (String tier : properties.tiers()) {
            if (realSource) {
                if (!first) {
                    sleepBetweenRequests();
                }
                first = false;
                CnMetaSyncResult result = syncOnceWithSource(configuredSource, patchKey, properties.defaultQueueId(), tier, CnMetaRoles.ALL);
                results.add(result);
                if ("STOPPED".equals(result.status())) {
                    return results;
                }
                continue;
            }
            for (String role : properties.roles()) {
                if (!first) {
                    sleepBetweenRequests();
                }
                first = false;
                CnMetaSyncResult result = syncOnce(patchKey, properties.defaultQueueId(), tier, role);
                results.add(result);
                if ("STOPPED".equals(result.status())) {
                    return results;
                }
            }
        }
        return results;
    }

    public List<CnMetaSyncJob> findRecentJobs(int limit) {
        return repository.findRecentJobs(limit);
    }

    private CnMetaSourceClient clientFor(String source) {
        CnMetaSourceClient client = clientsBySource.get(source);
        if (client != null) {
            return client;
        }
        throw new CnMetaSourceException("Unsupported CN meta source: " + source);
    }

    private SourceFetch fetchWithRetries(
            CnMetaSourceClient client,
            String patchKey,
            Integer queueId,
            String tierScope,
            String role,
            int[] requestCount
    ) {
        CnMetaSourcePayload payload = null;
        int maxAttempts = Math.max(1, properties.maxRetries() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            requestCount[0] = attempt;
            try {
                payload = client.fetchChampionStats(patchKey, queueId, tierScope, role);
                break;
            } catch (CnMetaSourceException exception) {
                if (shouldStop(exception) || attempt == maxAttempts) {
                    throw exception;
                }
            }
        }
        if (payload == null) {
            throw new CnMetaSourceException("Source returned no payload");
        }
        return new SourceFetch(payload, requestCount[0]);
    }

    private CnMetaSyncResult finishSuccessfulJob(
            CnMetaSyncJob job,
            CnMetaSourcePayload payload,
            String patchKey,
            Integer queueId,
            String tierScope,
            String role,
            String contentHash,
            int requestCount
    ) {
        repository.insertSourceDocument(job.id(), payload, contentHash, Instant.now());
        String storageRole = storageRole(payload, role);
        List<CnMetaChampionStatRow> storageRows = storageRows(payload.rows(), tierScope, storageRole, payload.source());

        Long snapshotId = repository.insertSnapshot(payload, patchKey, queueId, tierScope, storageRole, contentHash, Instant.now());
        repository.insertChampionStats(snapshotId, storageRows);
        repository.deleteSupersededMeta(payload.source(), queueId, tierScope, storageRole, snapshotId, job.id());
        CnMetaSyncJob finished = repository.updateJobFinished(
                job.id(),
                "SUCCESS",
                requestCount,
                storageRows.size(),
                contentHash,
                null,
                Instant.now()
        );
        return toResult(finished);
    }

    private CnMetaSyncResult finishFailedJob(Long jobId, String status, int requestCount, String errorMessage) {
        CnMetaSyncJob finished = repository.updateJobFinished(
                jobId,
                status,
                requestCount,
                0,
                null,
                errorMessage,
                Instant.now()
        );
        return toResult(finished);
    }

    private <T> T inTransaction(java.util.function.Supplier<T> supplier) {
        return transactionOperations.execute(status -> supplier.get());
    }

    private boolean shouldStop(CnMetaSourceException exception) {
        Integer httpStatus = exception.httpStatus();
        return exception.stopSignal() || (httpStatus != null && properties.stopOnHttpStatus().contains(httpStatus));
    }

    private void sleepBetweenRequests() {
        long delay = properties.requestDelayMs();
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CnMetaSourceException("CN meta sync interrupted");
        }
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "mock";
        }
        return source.trim().toLowerCase(Locale.ROOT);
    }

    private static String aggregateRoleForRealSource(String source, String requestedRole) {
        return isRealSourceAlias(source) ? CnMetaRoles.ALL : requestedRole;
    }

    private static boolean isRealSourceAlias(String source) {
        return "real".equalsIgnoreCase(source) || "real-101".equalsIgnoreCase(source);
    }

    private static String storageRole(CnMetaSourcePayload payload, String requestedRole) {
        if ("real-101".equalsIgnoreCase(payload.source())) {
            return CnMetaRoles.ALL;
        }
        return requestedRole;
    }

    private static List<CnMetaChampionStatRow> storageRows(
            List<CnMetaChampionStatRow> rows,
            String tierScope,
            String storageRole,
            String source
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> storageRow(row, tierScope, storageRole, source))
                .toList();
    }

    private static CnMetaChampionStatRow storageRow(
            CnMetaChampionStatRow row,
            String tierScope,
            String storageRole,
            String source
    ) {
        String dataSourceNote = row.dataSourceNote();
        if ("real-101".equalsIgnoreCase(source) && (dataSourceNote == null || dataSourceNote.isBlank())) {
            dataSourceNote = CnMetaRoles.REAL_101_AGGREGATE_NOTE;
        }
        return new CnMetaChampionStatRow(
                row.championId(),
                storageRole,
                row.tierScope() == null ? tierScope : row.tierScope(),
                row.winRate(),
                row.pickRate(),
                row.banRate(),
                row.avgKda(),
                row.avgGold(),
                row.avgDamageShare(),
                row.avgDamageTakenShare(),
                row.rankIndex(),
                row.sampleNote(),
                row.avgDamage(),
                row.avgDamageTaken(),
                row.avgHeal(),
                row.avgDurationSeconds(),
                row.avgKills(),
                row.avgAssists(),
                dataSourceNote
        );
    }

    private static Map<String, CnMetaSourceClient> indexClients(List<CnMetaSourceClient> sourceClients) {
        Map<String, CnMetaSourceClient> clients = new HashMap<>();
        for (CnMetaSourceClient client : sourceClients) {
            String source = normalizeSource(client.source());
            clients.put(source, client);
            if ("mock-101".equals(source)) {
                clients.put("mock", client);
            }
            if ("real-101".equals(source)) {
                clients.put("real", client);
            }
        }
        return clients;
    }

    private static String hash(String rawContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawContent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash CN meta payload", exception);
        }
    }

    private static CnMetaSyncResult toResult(CnMetaSyncJob job) {
        return new CnMetaSyncResult(
                job.id(),
                job.source(),
                job.patchKey(),
                job.queueId(),
                job.tierScope(),
                job.role(),
                job.status(),
                job.requestCount(),
                job.rowCount(),
                job.contentHash(),
                job.errorMessage(),
                job.startedAt(),
                job.finishedAt()
        );
    }

    private record SourceFetch(CnMetaSourcePayload payload, int requestCount) {
    }

    private static class NoTransactionOperations implements TransactionOperations {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }
}
