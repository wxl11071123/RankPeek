package io.rankpeek.server.credits;

import io.rankpeek.server.common.JdbcSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class CreditRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<CreditLedgerEntry> ledgerMapper = (rs, rowNum) -> new CreditLedgerEntry(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getObject("actor_user_id") == null ? null : rs.getLong("actor_user_id"),
            rs.getString("entry_type"),
            rs.getInt("amount"),
            rs.getInt("balance_after"),
            rs.getString("idempotency_key"),
            rs.getString("reference_type"),
            rs.getString("reference_id"),
            rs.getString("reason"),
            instantOrNull(rs.getTimestamp("created_at"))
    );

    private final RowMapper<AiAnalysisRun> aiRunMapper = (rs, rowNum) -> new AiAnalysisRun(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("endpoint"),
            rs.getString("provider"),
            rs.getString("model"),
            rs.getString("status"),
            rs.getString("idempotency_key"),
            rs.getString("request_hash"),
            rs.getString("response_json"),
            rs.getString("error_message"),
            rs.getInt("charged_credits"),
            rs.getInt("refunded_credits"),
            rs.getLong("prompt_tokens"),
            rs.getLong("completion_tokens"),
            rs.getLong("total_tokens"),
            rs.getString("error_code"),
            longOrNull(rs, "charge_ledger_entry_id"),
            longOrNull(rs, "refund_ledger_entry_id"),
            instantOrNull(rs.getTimestamp("created_at")),
            instantOrNull(rs.getTimestamp("updated_at")),
            instantOrNull(rs.getTimestamp("completed_at"))
    );

    public CreditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CreditBalanceResponse getOrCreateBalance(Long userId, Instant now) {
        return findBalance(userId).orElseGet(() -> {
            jdbcTemplate.update(
                    "insert into user_credit_balances (user_id, balance, updated_at) values (?, ?, ?)",
                    userId,
                    0,
                    Timestamp.from(now)
            );
            return new CreditBalanceResponse(userId, 0);
        });
    }

    public Optional<CreditBalanceResponse> findBalance(Long userId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select user_id, balance from user_credit_balances where user_id = ?",
                    (rs, rowNum) -> new CreditBalanceResponse(rs.getLong("user_id"), rs.getInt("balance")),
                    userId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void updateBalance(Long userId, int balance, Instant now) {
        jdbcTemplate.update(
                "update user_credit_balances set balance = ?, updated_at = ? where user_id = ?",
                balance,
                Timestamp.from(now),
                userId
        );
    }

    public Optional<CreditLedgerEntry> findEntryByIdempotencyKey(Long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        List<CreditLedgerEntry> rows = jdbcTemplate.query(
                "select * from credit_ledger_entries where user_id = ? and idempotency_key = ?",
                ledgerMapper,
                userId,
                idempotencyKey
        );
        return rows.stream().findFirst();
    }

    public CreditLedgerEntry insertLedgerEntry(
            Long userId,
            Long actorUserId,
            String type,
            int amount,
            int balanceAfter,
            String idempotencyKey,
            String referenceType,
            String referenceId,
            String reason,
            Instant createdAt
    ) {
        KeyHolder keyHolder = JdbcSupport.newKeyHolder();
        jdbcTemplate.update(connection -> JdbcSupport.prepareInsert(
                connection,
                """
                        insert into credit_ledger_entries (
                            user_id, actor_user_id, entry_type, amount, balance_after,
                            idempotency_key, reference_type, reference_id, reason, created_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                actorUserId,
                type,
                amount,
                balanceAfter,
                idempotencyKey,
                referenceType,
                referenceId,
                reason,
                Timestamp.from(createdAt)
        ), keyHolder);
        return findEntryById(JdbcSupport.requireGeneratedId(keyHolder)).orElseThrow();
    }

    public Optional<CreditLedgerEntry> findEntryById(Long id) {
        List<CreditLedgerEntry> rows = jdbcTemplate.query(
                "select * from credit_ledger_entries where id = ?",
                ledgerMapper,
                id
        );
        return rows.stream().findFirst();
    }

    public List<CreditLedgerEntry> listEntries(Long userId, int limit) {
        return jdbcTemplate.query(
                """
                        select * from credit_ledger_entries
                        where user_id = ?
                        order by id desc
                        limit ?
                        """,
                ledgerMapper,
                userId,
                limit
        );
    }

    public Optional<AiCreditReservation> findAiReservationByIdempotencyKey(Long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        List<AiCreditReservation> rows = jdbcTemplate.query(
                """
                        select id, user_id, charged_credits
                        from ai_analysis_runs
                        where user_id = ? and idempotency_key = ?
                        """,
                (rs, rowNum) -> new AiCreditReservation(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getInt("charged_credits"),
                        false
                ),
                userId,
                idempotencyKey
        );
        return rows.stream().findFirst();
    }

    public Optional<AiAnalysisRun> findAiRunByIdempotencyKey(Long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        List<AiAnalysisRun> rows = jdbcTemplate.query(
                "select * from ai_analysis_runs where user_id = ? and idempotency_key = ?",
                aiRunMapper,
                userId,
                idempotencyKey
        );
        return rows.stream().findFirst();
    }

    public Optional<AiAnalysisRun> findAiRunById(Long runId) {
        List<AiAnalysisRun> rows = jdbcTemplate.query(
                "select * from ai_analysis_runs where id = ?",
                aiRunMapper,
                runId
        );
        return rows.stream().findFirst();
    }

    public List<AiAnalysisRun> listAiRuns(Long userId, String endpoint, String status, int limit, int offset) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select *
                from ai_analysis_runs
                where user_id = ?
                """);
        params.add(userId);
        appendAiRunFilters(sql, params, endpoint, status);
        sql.append(" order by id desc limit ? offset ?");
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql.toString(), aiRunMapper, params.toArray());
    }

    public long countAiRuns(Long userId, String endpoint, String status) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select count(*)
                from ai_analysis_runs
                where user_id = ?
                """);
        params.add(userId);
        appendAiRunFilters(sql, params, endpoint, status);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0 : count;
    }

    public List<AiAnalysisRun> listAiRunsForAdmin(Long userId, String endpoint, String status, int limit, int offset) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select *
                from ai_analysis_runs
                where 1 = 1
                """);
        if (userId != null) {
            sql.append(" and user_id = ?");
            params.add(userId);
        }
        appendAiRunFilters(sql, params, endpoint, status);
        sql.append(" order by id desc limit ? offset ?");
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql.toString(), aiRunMapper, params.toArray());
    }

    public long countAiRunsForAdmin(Long userId, String endpoint, String status) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select count(*)
                from ai_analysis_runs
                where 1 = 1
                """);
        if (userId != null) {
            sql.append(" and user_id = ?");
            params.add(userId);
        }
        appendAiRunFilters(sql, params, endpoint, status);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0 : count;
    }

    public Long insertAiRun(
            Long userId,
            String endpoint,
            String provider,
            String model,
            String status,
            String idempotencyKey,
            String requestHash,
            int chargedCredits,
            Instant createdAt
    ) {
        KeyHolder keyHolder = JdbcSupport.newKeyHolder();
        jdbcTemplate.update(connection -> JdbcSupport.prepareInsert(
                connection,
                """
                        insert into ai_analysis_runs (
                            user_id, endpoint, provider, model, status,
                            idempotency_key, request_hash, charged_credits, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                endpoint,
                provider,
                model,
                status,
                idempotencyKey,
                requestHash,
                chargedCredits,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        ), keyHolder);
        return JdbcSupport.requireGeneratedId(keyHolder);
    }

    public void attachAiRunChargeLedger(Long runId, Long chargeLedgerEntryId, Instant updatedAt) {
        jdbcTemplate.update(
                """
                        update ai_analysis_runs
                        set charge_ledger_entry_id = ?, updated_at = ?
                        where id = ?
                        """,
                chargeLedgerEntryId,
                Timestamp.from(updatedAt),
                runId
        );
    }

    public void markAiRunSucceeded(
            Long runId,
            long promptTokens,
            long completionTokens,
            long totalTokens,
            String responseJson,
            Instant completedAt
    ) {
        jdbcTemplate.update(
                """
                        update ai_analysis_runs
                        set status = ?, prompt_tokens = ?, completion_tokens = ?, total_tokens = ?,
                            response_json = ?, error_code = null, error_message = null,
                            updated_at = ?, completed_at = ?
                        where id = ?
                        """,
                "SUCCEEDED",
                promptTokens,
                completionTokens,
                totalTokens,
                responseJson,
                Timestamp.from(completedAt),
                Timestamp.from(completedAt),
                runId
        );
    }

    public void markAiRunRefunded(
            Long runId,
            int refundedCredits,
            String errorCode,
            String errorMessage,
            Long refundLedgerEntryId,
            Instant completedAt
    ) {
        jdbcTemplate.update(
                """
                        update ai_analysis_runs
                        set status = ?, refunded_credits = ?, error_code = ?, error_message = ?,
                            refund_ledger_entry_id = ?, updated_at = ?, completed_at = ?
                        where id = ?
                        """,
                "REFUNDED",
                refundedCredits,
                errorCode,
                errorMessage,
                refundLedgerEntryId,
                Timestamp.from(completedAt),
                Timestamp.from(completedAt),
                runId
        );
    }

    private static void appendAiRunFilters(StringBuilder sql, List<Object> params, String endpoint, String status) {
        if (endpoint != null) {
            sql.append(" and endpoint = ?");
            params.add(endpoint);
        }
        if (status != null) {
            sql.append(" and status = ?");
            params.add(status);
        }
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Long longOrNull(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        return value == null ? null : ((Number) value).longValue();
    }
}
