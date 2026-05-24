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

    public Optional<OpggChampionList> findToday(OpggChampionListQuery query, LocalDate cacheDate) {
        return jdbcTemplate.query(
                """
                        select list_json
                        from opgg_champion_list_cache
                        where mode = ? and region = ? and tier = ? and cache_date = ?
                        """,
                (rs, rowNum) -> readList(rs.getString("list_json")),
                query.mode(),
                query.region(),
                query.tier(),
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
        int deletedDetails = jdbcTemplate.update(
                "delete from opgg_champion_detail_cache where cache_date < ?",
                Date.valueOf(cutoffDate)
        );
        int deletedLists = jdbcTemplate.update(
                "delete from opgg_champion_list_cache where cache_date < ?",
                Date.valueOf(cutoffDate)
        );
        return deletedDetails + deletedLists;
    }

    @Transactional
    public void upsertToday(
            OpggChampionListQuery query,
            LocalDate cacheDate,
            OpggChampionList list,
            Instant fetchedAt
    ) {
        String listJson = writeList(list);
        int updated = updateToday(query, cacheDate, list, fetchedAt, listJson);
        if (updated > 0) {
            return;
        }

        try {
            insertToday(query, cacheDate, list, fetchedAt, listJson);
        } catch (DuplicateKeyException exception) {
            updateToday(query, cacheDate, list, fetchedAt, listJson);
        }
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

    private OpggChampionList readList(String listJson) {
        try {
            return objectMapper.readValue(listJson, OpggChampionList.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read cached OP.GG champion list", exception);
        }
    }

    private String writeList(OpggChampionList list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to write cached OP.GG champion list", exception);
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

    private int updateToday(
            OpggChampionListQuery query,
            LocalDate cacheDate,
            OpggChampionList list,
            Instant fetchedAt,
            String listJson
    ) {
        return jdbcTemplate.update(
                """
                        update opgg_champion_list_cache
                        set source_version = ?, list_json = ?, fetched_at = ?, updated_at = ?
                        where mode = ? and region = ? and tier = ? and cache_date = ?
                        """,
                list.version(),
                listJson,
                Timestamp.from(fetchedAt),
                Timestamp.from(fetchedAt),
                query.mode(),
                query.region(),
                query.tier(),
                Date.valueOf(cacheDate)
        );
    }

    private void insertToday(
            OpggChampionListQuery query,
            LocalDate cacheDate,
            OpggChampionList list,
            Instant fetchedAt,
            String listJson
    ) {
        jdbcTemplate.update(
                """
                        insert into opgg_champion_list_cache (
                            mode, region, tier, cache_date,
                            source_version, list_json, fetched_at, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                query.mode(),
                query.region(),
                query.tier(),
                Date.valueOf(cacheDate),
                list.version(),
                listJson,
                Timestamp.from(fetchedAt),
                Timestamp.from(fetchedAt),
                Timestamp.from(fetchedAt)
        );
    }
}
