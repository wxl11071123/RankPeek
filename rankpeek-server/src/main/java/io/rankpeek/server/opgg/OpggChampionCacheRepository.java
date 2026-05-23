package io.rankpeek.server.opgg;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public class OpggChampionCacheRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OpggChampionCacheRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<OpggChampionDetail> findToday(OpggChampionDetailQuery query, LocalDate cacheDate) {
        return jdbcTemplate.query(
                """
                        select detail_json
                        from opgg_champion_detail_cache
                        where mode = ? and region = ? and tier = ? and position = ?
                          and champion_id = ? and cache_date = ?
                        """,
                (rs, rowNum) -> readDetail(rs.getString("detail_json")),
                query.mode(),
                query.region(),
                query.tier(),
                query.position(),
                query.championId(),
                Date.valueOf(cacheDate)
        ).stream().findFirst();
    }

    @Transactional
    public void upsertToday(
            OpggChampionDetailQuery query,
            LocalDate cacheDate,
            OpggChampionDetail detail,
            Instant fetchedAt
    ) {
        String detailJson = writeDetail(detail);
        int updated = updateToday(query, cacheDate, detail, fetchedAt, detailJson);
        if (updated > 0) {
            return;
        }

        try {
            insertToday(query, cacheDate, detail, fetchedAt, detailJson);
        } catch (DuplicateKeyException exception) {
            updateToday(query, cacheDate, detail, fetchedAt, detailJson);
        }
    }

    public int deleteBefore(LocalDate cutoffDate) {
        return jdbcTemplate.update(
                "delete from opgg_champion_detail_cache where cache_date < ?",
                Date.valueOf(cutoffDate)
        );
    }

    private OpggChampionDetail readDetail(String detailJson) {
        try {
            return objectMapper.readValue(detailJson, OpggChampionDetail.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read cached OP.GG champion detail", exception);
        }
    }

    private String writeDetail(OpggChampionDetail detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to write cached OP.GG champion detail", exception);
        }
    }

    private int updateToday(
            OpggChampionDetailQuery query,
            LocalDate cacheDate,
            OpggChampionDetail detail,
            Instant fetchedAt,
            String detailJson
    ) {
        return jdbcTemplate.update(
                """
                        update opgg_champion_detail_cache
                        set source_version = ?, detail_json = ?, fetched_at = ?, updated_at = ?
                        where mode = ? and region = ? and tier = ? and position = ?
                          and champion_id = ? and cache_date = ?
                        """,
                detail.version(),
                detailJson,
                Timestamp.from(fetchedAt),
                Timestamp.from(fetchedAt),
                query.mode(),
                query.region(),
                query.tier(),
                query.position(),
                query.championId(),
                Date.valueOf(cacheDate)
        );
    }

    private void insertToday(
            OpggChampionDetailQuery query,
            LocalDate cacheDate,
            OpggChampionDetail detail,
            Instant fetchedAt,
            String detailJson
    ) {
        jdbcTemplate.update(
                """
                        insert into opgg_champion_detail_cache (
                            mode, region, tier, position, champion_id, cache_date,
                            source_version, detail_json, fetched_at, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                query.mode(),
                query.region(),
                query.tier(),
                query.position(),
                query.championId(),
                Date.valueOf(cacheDate),
                detail.version(),
                detailJson,
                Timestamp.from(fetchedAt),
                Timestamp.from(fetchedAt),
                Timestamp.from(fetchedAt)
        );
    }
}
