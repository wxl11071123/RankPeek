package io.rankpeek.server.auth;

import io.rankpeek.server.common.JdbcSupport;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class AuthRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AuthUser> userMapper = (rs, rowNum) -> new AuthUser(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("display_name"),
            rs.getString("password_hash"),
            rs.getString("status"),
            rs.getString("role"),
            instantOrNull(rs.getTimestamp("created_at")),
            instantOrNull(rs.getTimestamp("updated_at")),
            instantOrNull(rs.getTimestamp("last_login_at"))
    );

    private final RowMapper<StoredRefreshToken> refreshTokenMapper = (rs, rowNum) -> new StoredRefreshToken(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("token_hash"),
            instantOrNull(rs.getTimestamp("expires_at")),
            instantOrNull(rs.getTimestamp("revoked_at")),
            instantOrNull(rs.getTimestamp("created_at")),
            instantOrNull(rs.getTimestamp("last_used_at")),
            rs.getString("user_agent")
    );

    private final RowMapper<StoredPasswordResetToken> passwordResetTokenMapper = (rs, rowNum) -> new StoredPasswordResetToken(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("token_hash"),
            instantOrNull(rs.getTimestamp("expires_at")),
            instantOrNull(rs.getTimestamp("used_at")),
            instantOrNull(rs.getTimestamp("created_at"))
    );

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AuthUser> findUserByEmail(String email) {
        List<AuthUser> rows = jdbcTemplate.query(
                "select * from users where email = ?",
                userMapper,
                email
        );
        return rows.stream().findFirst();
    }

    public Optional<AuthUser> findUserById(Long id) {
        List<AuthUser> rows = jdbcTemplate.query(
                "select * from users where id = ?",
                userMapper,
                id
        );
        return rows.stream().findFirst();
    }

    public List<AuthUser> findUsers(String query, String status, String role, int limit, int offset) {
        QueryParts queryParts = usersFilter(query, status, role);
        List<Object> args = new ArrayList<>(queryParts.args());
        args.add(limit);
        args.add(offset);

        return jdbcTemplate.query(
                """
                        select *
                        from users
                        """ + queryParts.whereClause() + """

                        order by created_at desc, id desc
                        limit ? offset ?
                        """,
                userMapper,
                args.toArray()
        );
    }

    public long countUsers(String query, String status, String role) {
        QueryParts queryParts = usersFilter(query, status, role);
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from users " + queryParts.whereClause(),
                Long.class,
                queryParts.args().toArray()
        );
        return count == null ? 0 : count;
    }

    public AuthUser insertUser(String email, String displayName, String passwordHash, Instant now) {
        KeyHolder keyHolder = JdbcSupport.newKeyHolder();
        jdbcTemplate.update(connection -> JdbcSupport.prepareInsert(
                connection,
                """
                        insert into users (
                            email, display_name, password_hash, status, role,
                            created_at, updated_at, last_login_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                email,
                displayName,
                passwordHash,
                "ACTIVE",
                "USER",
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now)
        ), keyHolder);
        return findUserById(JdbcSupport.requireGeneratedId(keyHolder)).orElseThrow();
    }

    public AuthUser upsertInitialAdmin(String email, String displayName, String passwordHash, Instant now) {
        Optional<AuthUser> existing = findUserByEmail(email);
        if (existing.isPresent()) {
            updateInitialAdmin(existing.get().id(), displayName, passwordHash, now);
            return findUserById(existing.get().id()).orElseThrow();
        }

        try {
            KeyHolder keyHolder = JdbcSupport.newKeyHolder();
            jdbcTemplate.update(connection -> JdbcSupport.prepareInsert(
                    connection,
                    """
                            insert into users (
                                email, display_name, password_hash, status, role,
                                created_at, updated_at, last_login_at
                            ) values (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    email,
                    displayName,
                    passwordHash,
                    "ACTIVE",
                    "ADMIN",
                    Timestamp.from(now),
                    Timestamp.from(now),
                    null
            ), keyHolder);
            return findUserById(JdbcSupport.requireGeneratedId(keyHolder)).orElseThrow();
        } catch (DuplicateKeyException exception) {
            AuthUser duplicate = findUserByEmail(email).orElseThrow();
            updateInitialAdmin(duplicate.id(), displayName, passwordHash, now);
            return findUserById(duplicate.id()).orElseThrow();
        }
    }

    public void updateLastLoginAt(Long userId, Instant now) {
        jdbcTemplate.update(
                "update users set last_login_at = ?, updated_at = ? where id = ?",
                Timestamp.from(now),
                Timestamp.from(now),
                userId
        );
    }

    public void updatePasswordHash(Long userId, String passwordHash, Instant now) {
        jdbcTemplate.update(
                "update users set password_hash = ?, updated_at = ? where id = ?",
                passwordHash,
                Timestamp.from(now),
                userId
        );
    }

    public AuthUser updateUserStatusAndRole(Long userId, String status, String role, Instant now) {
        jdbcTemplate.update(
                """
                        update users
                        set status = ?, role = ?, updated_at = ?
                        where id = ?
                        """,
                status,
                role,
                Timestamp.from(now),
                userId
        );
        return findUserById(userId).orElseThrow();
    }

    private void updateInitialAdmin(Long userId, String displayName, String passwordHash, Instant now) {
        jdbcTemplate.update(
                """
                        update users
                        set display_name = ?, password_hash = ?, status = ?, role = ?, updated_at = ?
                        where id = ?
                        """,
                displayName,
                passwordHash,
                "ACTIVE",
                "ADMIN",
                Timestamp.from(now),
                userId
        );
    }

    public void insertRefreshToken(
            Long userId,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt,
            String userAgent
    ) {
        jdbcTemplate.update(
                """
                        insert into auth_refresh_tokens (
                            user_id, token_hash, expires_at, created_at, user_agent
                        ) values (?, ?, ?, ?, ?)
                        """,
                userId,
                tokenHash,
                Timestamp.from(expiresAt),
                Timestamp.from(createdAt),
                userAgent
        );
    }

    public Optional<StoredRefreshToken> findRefreshTokenByHash(String tokenHash) {
        List<StoredRefreshToken> rows = jdbcTemplate.query(
                "select * from auth_refresh_tokens where token_hash = ?",
                refreshTokenMapper,
                tokenHash
        );
        return rows.stream().findFirst();
    }

    public void markRefreshTokenUsed(Long tokenId, Instant now) {
        jdbcTemplate.update(
                "update auth_refresh_tokens set last_used_at = ? where id = ?",
                Timestamp.from(now),
                tokenId
        );
    }

    public boolean revokeRefreshToken(Long tokenId, Instant now) {
        int updated = jdbcTemplate.update(
                "update auth_refresh_tokens set revoked_at = ? where id = ? and revoked_at is null",
                Timestamp.from(now),
                tokenId
        );
        return updated > 0;
    }

    public int revokeRefreshTokensForUser(Long userId, Instant now) {
        return jdbcTemplate.update(
                """
                        update auth_refresh_tokens
                        set revoked_at = ?
                        where user_id = ?
                          and revoked_at is null
                        """,
                Timestamp.from(now),
                userId
        );
    }

    public void insertPasswordResetToken(Long userId, String tokenHash, Instant expiresAt, Instant createdAt) {
        jdbcTemplate.update(
                """
                        insert into auth_password_reset_tokens (
                            user_id, token_hash, expires_at, created_at
                        ) values (?, ?, ?, ?)
                        """,
                userId,
                tokenHash,
                Timestamp.from(expiresAt),
                Timestamp.from(createdAt)
        );
    }

    public Optional<StoredPasswordResetToken> findPasswordResetTokenByHash(String tokenHash) {
        List<StoredPasswordResetToken> rows = jdbcTemplate.query(
                "select * from auth_password_reset_tokens where token_hash = ?",
                passwordResetTokenMapper,
                tokenHash
        );
        return rows.stream().findFirst();
    }

    public int markPasswordResetTokenUsed(Long tokenId, Instant now) {
        return jdbcTemplate.update(
                """
                        update auth_password_reset_tokens
                        set used_at = ?
                        where id = ?
                          and used_at is null
                        """,
                Timestamp.from(now),
                tokenId
        );
    }

    public int revokeUnusedPasswordResetTokensForUser(Long userId, Instant now) {
        return jdbcTemplate.update(
                """
                        update auth_password_reset_tokens
                        set used_at = ?
                        where user_id = ?
                          and used_at is null
                        """,
                Timestamp.from(now),
                userId
        );
    }

    private static QueryParts usersFilter(String query, String status, String role) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            clauses.add("(lower(email) like ? or lower(coalesce(display_name, '')) like ?)");
            String pattern = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            args.add(pattern);
            args.add(pattern);
        }
        if (status != null) {
            clauses.add("status = ?");
            args.add(status);
        }
        if (role != null) {
            clauses.add("role = ?");
            args.add(role);
        }

        if (clauses.isEmpty()) {
            return new QueryParts("", args);
        }
        return new QueryParts(" where " + String.join(" and ", clauses), args);
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record QueryParts(String whereClause, List<Object> args) {
    }
}
