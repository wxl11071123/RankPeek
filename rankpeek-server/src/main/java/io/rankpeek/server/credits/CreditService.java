package io.rankpeek.server.credits;

import io.rankpeek.server.auth.AuthRepository;
import io.rankpeek.server.auth.AuthUser;
import io.rankpeek.server.ai.DeepSeekTokenUsage;
import io.rankpeek.server.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class CreditService {

    private static final int DEFAULT_LEDGER_LIMIT = 50;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final int MAX_REASON_LENGTH = 512;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final CreditRepository creditRepository;
    private final AuthRepository authRepository;

    public CreditService(CreditRepository creditRepository, AuthRepository authRepository) {
        this.creditRepository = creditRepository;
        this.authRepository = authRepository;
    }

    @Transactional
    public CreditBalanceResponse balanceFor(AuthUser user) {
        return creditRepository.getOrCreateBalance(user.id(), Instant.now());
    }

    @Transactional
    public CreditLedgerResponse ledgerFor(AuthUser user) {
        balanceFor(user);
        return new CreditLedgerResponse(
                creditRepository.listEntries(user.id(), DEFAULT_LEDGER_LIMIT).stream()
                        .map(CreditLedgerEntryResponse::from)
                        .toList()
        );
    }

    @Transactional
    public AdminCreditGrantResponse adjustByAdmin(
            AuthUser admin,
            AdminCreditGrantRequest request,
            String idempotencyKey
    ) {
        if (request.userId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (request.amount() == 0) {
            throw new IllegalArgumentException("amount must not be zero");
        }
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        String reason = normalizeReason(request.reason());
        authRepository.findUserById(request.userId()).orElseThrow(CreditService::userNotFound);

        Instant now = Instant.now();
        CreditBalanceResponse current = creditRepository.getOrCreateBalance(request.userId(), now);
        var existing = creditRepository.findEntryByIdempotencyKey(request.userId(), normalizedKey);
        if (existing.isPresent()) {
            CreditBalanceResponse balance = creditRepository.getOrCreateBalance(request.userId(), now);
            return new AdminCreditGrantResponse(
                    request.userId(),
                    balance.balance(),
                    true,
                    CreditLedgerEntryResponse.from(existing.get())
            );
        }

        int nextBalance = current.balance() + request.amount();
        if (nextBalance < 0) {
            throw insufficientCredits();
        }

        creditRepository.updateBalance(request.userId(), nextBalance, now);
        CreditLedgerEntry entry = creditRepository.insertLedgerEntry(
                request.userId(),
                admin.id(),
                "ADMIN_ADJUSTMENT",
                request.amount(),
                nextBalance,
                normalizedKey,
                "admin",
                String.valueOf(admin.id()),
                reason,
                now
        );
        return new AdminCreditGrantResponse(
                request.userId(),
                nextBalance,
                false,
                CreditLedgerEntryResponse.from(entry)
        );
    }

    @Transactional
    public AiCreditReservation reserveAiRun(
            AuthUser user,
            String endpoint,
            String provider,
            String model,
            int chargeCredits,
            String requestHash,
            String idempotencyKey
    ) {
        if (chargeCredits <= 0) {
            throw new IllegalArgumentException("chargeCredits must be positive");
        }

        String normalizedKey = normalizeOptionalIdempotencyKey(idempotencyKey);
        Instant now = Instant.now();
        CreditBalanceResponse current = creditRepository.getOrCreateBalance(user.id(), now);
        int nextBalance = current.balance() - chargeCredits;
        if (nextBalance < 0) {
            throw insufficientCredits();
        }

        Long runId = creditRepository.insertAiRun(
                user.id(),
                normalizeRequired(endpoint, "endpoint"),
                normalizeRequired(provider, "provider"),
                normalizeRequired(model, "model"),
                "RESERVED",
                normalizedKey,
                normalizeOptional(requestHash),
                chargeCredits,
                now
        );
        creditRepository.updateBalance(user.id(), nextBalance, now);
        CreditLedgerEntry chargeEntry = creditRepository.insertLedgerEntry(
                user.id(),
                null,
                "AI_CHARGE",
                -chargeCredits,
                nextBalance,
                "ai-charge:" + runId,
                "ai_analysis_run",
                String.valueOf(runId),
                "AI analysis charge",
                now
        );
        creditRepository.attachAiRunChargeLedger(runId, chargeEntry.id(), now);
        return new AiCreditReservation(runId, user.id(), chargeCredits, true);
    }

    @Transactional
    public void completeAiRun(AiCreditReservation reservation, DeepSeekTokenUsage usage, String responseJson) {
        if (reservation == null || !reservation.chargeApplied()) {
            return;
        }

        DeepSeekTokenUsage normalizedUsage = usage == null
                ? new DeepSeekTokenUsage("deepseek", "unknown", 0, 0, 0, 0, 0)
                : usage;
        creditRepository.markAiRunSucceeded(
                reservation.runId(),
                normalizedUsage.promptTokens(),
                normalizedUsage.completionTokens(),
                normalizedUsage.totalTokens(),
                responseJson,
                Instant.now()
        );
    }

    @Transactional
    public void refundAiRun(AiCreditReservation reservation, String errorCode, String errorMessage) {
        if (reservation == null || !reservation.chargeApplied() || reservation.chargedCredits() <= 0) {
            return;
        }

        Instant now = Instant.now();
        CreditBalanceResponse current = creditRepository.getOrCreateBalance(reservation.userId(), now);
        int nextBalance = current.balance() + reservation.chargedCredits();
        creditRepository.updateBalance(reservation.userId(), nextBalance, now);
        CreditLedgerEntry refundEntry = creditRepository.insertLedgerEntry(
                reservation.userId(),
                null,
                "AI_REFUND",
                reservation.chargedCredits(),
                nextBalance,
                "ai-refund:" + reservation.runId(),
                "ai_analysis_run",
                String.valueOf(reservation.runId()),
                "AI analysis refund",
                now
        );
        creditRepository.markAiRunRefunded(
                reservation.runId(),
                reservation.chargedCredits(),
                normalizeOptional(errorCode),
                normalizeErrorMessage(errorMessage),
                refundEntry.id(),
                now
        );
    }

    public Optional<AiAnalysisRun> findAiRunByIdempotencyKey(Long userId, String idempotencyKey) {
        return creditRepository.findAiRunByIdempotencyKey(userId, normalizeOptionalIdempotencyKey(idempotencyKey));
    }

    public Optional<AiAnalysisRun> findAiRunById(Long runId) {
        if (runId == null) {
            return Optional.empty();
        }
        return creditRepository.findAiRunById(runId);
    }

    public List<AiAnalysisRun> listAiRuns(Long userId, String endpoint, String status, int limit, int offset) {
        return creditRepository.listAiRuns(
                userId,
                normalizeOptionalEndpoint(endpoint),
                normalizeOptionalAiRunStatus(status),
                normalizeLimit(limit),
                normalizeOffset(offset)
        );
    }

    public long countAiRuns(Long userId, String endpoint, String status) {
        return creditRepository.countAiRuns(
                userId,
                normalizeOptionalEndpoint(endpoint),
                normalizeOptionalAiRunStatus(status)
        );
    }

    public List<AiAnalysisRun> listAiRunsForAdmin(Long userId, String endpoint, String status, int limit, int offset) {
        return creditRepository.listAiRunsForAdmin(
                userId,
                normalizeOptionalEndpoint(endpoint),
                normalizeOptionalAiRunStatus(status),
                normalizeLimit(limit),
                normalizeOffset(offset)
        );
    }

    public long countAiRunsForAdmin(Long userId, String endpoint, String status) {
        return creditRepository.countAiRunsForAdmin(
                userId,
                normalizeOptionalEndpoint(endpoint),
                normalizeOptionalAiRunStatus(status)
        );
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("X-RankPeek-Idempotency-Key is required");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("X-RankPeek-Idempotency-Key must be 128 characters or fewer");
        }
        return normalized;
    }

    private static String normalizeOptionalIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("X-RankPeek-Idempotency-Key must be 128 characters or fewer");
        }
        return normalized;
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeOptionalEndpoint(String endpoint) {
        String normalized = normalizeOptional(endpoint);
        if (normalized != null && normalized.length() > 128) {
            throw new IllegalArgumentException("Endpoint must be 128 characters or fewer");
        }
        return normalized;
    }

    private static String normalizeOptionalAiRunStatus(String status) {
        String normalized = normalizeOptional(status);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "RESERVED", "SUCCEEDED", "FAILED", "REFUNDED" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported AI run status");
        };
    }

    private static int normalizeLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        return limit;
    }

    private static int normalizeOffset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be zero or greater");
        }
        return offset;
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String normalized = reason.trim();
        return normalized.length() <= MAX_REASON_LENGTH
                ? normalized
                : normalized.substring(0, MAX_REASON_LENGTH);
    }

    private static String normalizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        String normalized = errorMessage.trim();
        return normalized.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private static ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found");
    }

    private static ApiException insufficientCredits() {
        return new ApiException(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_CREDITS", "Credit balance is insufficient");
    }
}
