package io.rankpeek.server.auth;

import io.rankpeek.server.common.JdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
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

    public void updateLastLoginAt(Long userId, Instant now) {
        jdbcTemplate.update(
                "update users set last_login_at = ?, updated_at = ? where id = ?",
                Timestamp.from(now),
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

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
