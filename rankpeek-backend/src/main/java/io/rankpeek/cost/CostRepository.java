package io.rankpeek.cost;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Repository
public class CostRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CostRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long insertEvent(
            String eventType,
            String provider,
            String model,
            String source,
            BigDecimal amountCny,
            String currency,
            long quantity,
            Object metadata
    ) {
        long now = System.currentTimeMillis();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO cost_events (
                        event_type,
                        provider,
                        model,
                        source,
                        amount_cny,
                        currency,
                        quantity,
                        metadata_raw_json,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, eventType);
            ps.setString(2, provider);
            ps.setString(3, model);
            ps.setString(4, source);
            ps.setBigDecimal(5, amountCny);
            ps.setString(6, currency);
            ps.setLong(7, quantity);
            ps.setString(8, writeJson(metadata));
            ps.setLong(9, now);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    public List<CostEvent> listEvents(String eventType, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM cost_events WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND event_type = ?");
            args.add(eventType.trim());
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        args.add(Math.max(1, Math.min(limit, 100)));
        args.add(Math.max(0, offset));
        return jdbcTemplate.query(sql.toString(), this::mapEvent, args.toArray());
    }

    public BigDecimal sumEventsBetween(long fromInclusive, long toInclusive, String eventType) {
        BigDecimal result = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount_cny), 0)
                FROM cost_events
                WHERE created_at >= ?
                  AND created_at <= ?
                  AND (? IS NULL OR event_type = ?)
                """, BigDecimal.class, fromInclusive, toInclusive, eventType, eventType);
        return result == null ? BigDecimal.ZERO : result;
    }

    private CostEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new CostEvent(
                rs.getLong("id"),
                rs.getString("event_type"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("source"),
                rs.getBigDecimal("amount_cny"),
                rs.getString("currency"),
                rs.getLong("quantity"),
                rs.getString("metadata_raw_json"),
                rs.getLong("created_at")
        );
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cost metadata is not serializable", exception);
        }
    }
}
