package io.rankpeek.server.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class AdminStatsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminStatsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countActiveUsersSince(Instant threshold, Instant now) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(distinct user_id)
                        from (
                            select id as user_id
                            from users
                            where status = 'ACTIVE'
                              and role <> 'ADMIN'
                              and last_login_at >= ?
                            union
                            select t.user_id as user_id
                            from auth_refresh_tokens t
                            join users u on u.id = t.user_id
                            where u.status = 'ACTIVE'
                              and u.role <> 'ADMIN'
                              and t.revoked_at is null
                              and t.expires_at > ?
                              and coalesce(t.last_used_at, t.created_at) >= ?
                        ) active_users
                        """,
                Long.class,
                Timestamp.from(threshold),
                Timestamp.from(now),
                Timestamp.from(threshold)
        );
        return zeroIfNull(count);
    }

    public UserAggregate userAggregate(Instant dayStart, Instant dayEnd) {
        return jdbcTemplate.queryForObject(
                """
                        select
                            count(*) as total,
                            coalesce(sum(case when status = 'ACTIVE' then 1 else 0 end), 0) as active,
                            coalesce(sum(case when status = 'DISABLED' then 1 else 0 end), 0) as disabled,
                            coalesce(sum(case when role = 'ADMIN' then 1 else 0 end), 0) as admins,
                            coalesce(sum(case when created_at >= ? and created_at < ? then 1 else 0 end), 0) as registered_today
                        from users
                        """,
                (rs, rowNum) -> new UserAggregate(
                        rs.getLong("total"),
                        rs.getLong("active"),
                        rs.getLong("disabled"),
                        rs.getLong("admins"),
                        rs.getLong("registered_today")
                ),
                Timestamp.from(dayStart),
                Timestamp.from(dayEnd)
        );
    }

    public long countRegisteredUsers(Instant startInclusive, Instant endExclusive) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from users where created_at >= ? and created_at < ?",
                Long.class,
                Timestamp.from(startInclusive),
                Timestamp.from(endExclusive)
        );
        return zeroIfNull(count);
    }

    public AiAggregate aiAggregate(Instant startInclusive, Instant endExclusive) {
        return jdbcTemplate.queryForObject(
                """
                        select
                            count(*) as requests,
                            coalesce(sum(case when status = 'SUCCEEDED' then 1 else 0 end), 0) as succeeded,
                            coalesce(sum(case when status in ('FAILED', 'REFUNDED') then 1 else 0 end), 0) as failed,
                            coalesce(sum(case when status = 'RESERVED' then 1 else 0 end), 0) as reserved,
                            coalesce(sum(prompt_tokens), 0) as prompt_tokens,
                            coalesce(sum(completion_tokens), 0) as completion_tokens,
                            coalesce(sum(total_tokens), 0) as total_tokens,
                            coalesce(sum(charged_credits), 0) as charged_credits,
                            coalesce(sum(refunded_credits), 0) as refunded_credits
                        from ai_analysis_runs
                        where created_at >= ? and created_at < ?
                        """,
                (rs, rowNum) -> new AiAggregate(
                        rs.getLong("requests"),
                        rs.getLong("succeeded"),
                        rs.getLong("failed"),
                        rs.getLong("reserved"),
                        rs.getLong("prompt_tokens"),
                        rs.getLong("completion_tokens"),
                        rs.getLong("total_tokens"),
                        rs.getLong("charged_credits"),
                        rs.getLong("refunded_credits")
                ),
                Timestamp.from(startInclusive),
                Timestamp.from(endExclusive)
        );
    }

    public CreditAggregate creditAggregate(Instant startInclusive, Instant endExclusive) {
        return jdbcTemplate.queryForObject(
                """
                        select
                            coalesce(sum(case
                                when entry_type = 'ADMIN_ADJUSTMENT' and amount > 0 then amount
                                else 0
                            end), 0) as admin_granted,
                            coalesce(sum(case
                                when entry_type = 'AI_CHARGE' then -amount
                                else 0
                            end), 0) as ai_charged,
                            coalesce(sum(case
                                when entry_type = 'AI_REFUND' then amount
                                else 0
                            end), 0) as ai_refunded
                        from credit_ledger_entries
                        where created_at >= ? and created_at < ?
                        """,
                (rs, rowNum) -> new CreditAggregate(
                        rs.getLong("admin_granted"),
                        rs.getLong("ai_charged"),
                        rs.getLong("ai_refunded")
                ),
                Timestamp.from(startInclusive),
                Timestamp.from(endExclusive)
        );
    }

    public long outstandingBalance() {
        Long total = jdbcTemplate.queryForObject(
                "select coalesce(sum(balance), 0) from user_credit_balances",
                Long.class
        );
        return zeroIfNull(total);
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0 : value;
    }

    public record UserAggregate(
            long total,
            long active,
            long disabled,
            long admins,
            long registeredToday
    ) {
    }

    public record AiAggregate(
            long requests,
            long succeeded,
            long failed,
            long reserved,
            long promptTokens,
            long completionTokens,
            long totalTokens,
            long chargedCredits,
            long refundedCredits
    ) {
    }

    public record CreditAggregate(
            long adminGranted,
            long aiCharged,
            long aiRefunded
    ) {
    }
}
