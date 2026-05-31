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
import java.time.LocalDate;
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

    public ManualCostItem createManualCost(ManualCostRequest request) {
        long now = System.currentTimeMillis();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO manual_cost_items (
                        label,
                        category,
                        amount_cny,
                        cadence,
                        effective_date,
                        note,
                        active,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            setManualCostParams(ps, request, now, now);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return findManualCost(key == null ? 0L : key.longValue());
    }

    public ManualCostItem updateManualCost(long id, ManualCostRequest request) {
        long now = System.currentTimeMillis();
        jdbcTemplate.update("""
                UPDATE manual_cost_items
                SET label = ?,
                    category = ?,
                    amount_cny = ?,
                    cadence = ?,
                    effective_date = ?,
                    note = ?,
                    active = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                normalizeLabel(request.label()),
                normalizeCategory(request.category()),
                nonNegativeAmount(request.amountCny()),
                normalizeCadence(request.cadence()),
                normalizeEffectiveDate(request.effectiveDate()).toString(),
                request.note(),
                request.active() == null || request.active(),
                now,
                id);
        return findManualCost(id);
    }

    public void deleteManualCost(long id) {
        jdbcTemplate.update("DELETE FROM manual_cost_items WHERE id = ?", id);
    }

    public ManualCostItem findManualCost(long id) {
        return jdbcTemplate.query("""
                SELECT *
                FROM manual_cost_items
                WHERE id = ?
                """, this::mapManualCost, id).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Manual cost item not found: " + id));
    }

    public List<ManualCostItem> listManualCosts() {
        return jdbcTemplate.query("""
                SELECT *
                FROM manual_cost_items
                ORDER BY created_at DESC
                """, this::mapManualCost);
    }

    private void setManualCostParams(PreparedStatement ps, ManualCostRequest request, long createdAt, long updatedAt)
            throws SQLException {
        ps.setString(1, normalizeLabel(request.label()));
        ps.setString(2, normalizeCategory(request.category()));
        ps.setBigDecimal(3, nonNegativeAmount(request.amountCny()));
        ps.setString(4, normalizeCadence(request.cadence()));
        ps.setString(5, normalizeEffectiveDate(request.effectiveDate()).toString());
        ps.setString(6, request.note());
        ps.setBoolean(7, request.active() == null || request.active());
        ps.setLong(8, createdAt);
        ps.setLong(9, updatedAt);
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

    private ManualCostItem mapManualCost(ResultSet rs, int rowNum) throws SQLException {
        return new ManualCostItem(
                rs.getLong("id"),
                rs.getString("label"),
                rs.getString("category"),
                rs.getBigDecimal("amount_cny"),
                rs.getString("cadence"),
                LocalDate.parse(rs.getString("effective_date")),
                rs.getString("note"),
                rs.getBoolean("active"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
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

    private String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label is required");
        }
        return label.trim();
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank() ? "manual" : category.trim();
    }

    private BigDecimal nonNegativeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amountCny must be non-negative");
        }
        return amount;
    }

    private String normalizeCadence(String cadence) {
        String normalized = cadence == null || cadence.isBlank() ? "one_time" : cadence.trim().toLowerCase();
        return switch (normalized) {
            case "one_time", "monthly", "yearly" -> normalized;
            default -> throw new IllegalArgumentException("cadence must be one_time, monthly, or yearly");
        };
    }

    private LocalDate normalizeEffectiveDate(LocalDate effectiveDate) {
        return effectiveDate == null ? LocalDate.now() : effectiveDate;
    }
}
