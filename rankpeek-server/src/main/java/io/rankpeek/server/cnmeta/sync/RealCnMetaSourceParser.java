package io.rankpeek.server.cnmeta.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.server.cnmeta.CnMetaRoles;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class RealCnMetaSourceParser {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int MAX_SAMPLE_NOTE_LENGTH = 255;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CnMetaSourcePayload parse(
            String rawContent,
            String sourceUrl,
            String requestKey,
            String tierScope,
            String role,
            Integer httpStatus
    ) {
        JsonNode root = readJson(rawContent);
        DateResolution dateResolution = resolveDataDate(root);
        List<CnMetaChampionStatRow> rows = new ArrayList<>();

        String championDetails = findChampionDetails(root);
        if (championDetails != null) {
            rows.addAll(parseChampionDetailsRows(championDetails, tierScope));
        } else {
            JsonNode rowsNode = findRowsNode(root);
            if (rowsNode == null || !rowsNode.isArray()) {
                throw new CnMetaSourceException("Unable to parse real 101 response: champion stats rows not found");
            }

            String updateTime = firstText(root, "updateTime", "updatedAt", "update_time", "statTime", "dataTime");
            int index = 0;
            for (JsonNode rowNode : rowsNode) {
                index++;
                rows.add(parseRow(rowNode, role, tierScope, dateResolution.sourceDateUnavailable(), updateTime, index));
            }
        }
        if (rows.isEmpty()) {
            throw new CnMetaSourceException("Unable to parse real 101 response: champion stats rows are empty");
        }

        return new CnMetaSourcePayload(
                "real-101",
                sourceUrl,
                requestKey,
                httpStatus,
                rawContent,
                dateResolution.dataDate(),
                rows
        );
    }

    private List<CnMetaChampionStatRow> parseChampionDetailsRows(String championDetails, String tierScope) {
        if (championDetails.isBlank()) {
            throw new CnMetaSourceException("Unable to parse real 101 response: championdetails is empty");
        }
        String[] parts = championDetails.split("#");
        List<CnMetaChampionStatRow> rows = new ArrayList<>();
        int rowIndex = 0;
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            rowIndex++;
            rows.add(parseChampionDetailsRow(part, tierScope, rowIndex));
        }
        return rows;
    }

    private CnMetaChampionStatRow parseChampionDetailsRow(String rawRow, String tierScope, int rowIndex) {
        String[] fields = rawRow.split("_", -1);
        if (fields.length < 12) {
            throw new CnMetaSourceException(
                    "Unable to parse real 101 response: championdetails row " + rowIndex + " has fewer than 12 fields"
            );
        }
        return new CnMetaChampionStatRow(
                parseIntegerToken(fields[1], "championId", rowIndex),
                CnMetaRoles.ALL,
                tierScope,
                null,
                parseDecimalToken(fields[10], "pickRate", rowIndex),
                parseDecimalToken(fields[11], "banRate", rowIndex),
                parseDecimalToken(fields[2], "avgKda", rowIndex),
                parseDecimalToken(fields[7], "avgGold", rowIndex),
                null,
                null,
                parseIntegerToken(fields[0], "rankIndex", rowIndex),
                "101 getRankFieldAverage championdetails",
                parseDecimalToken(fields[3], "avgDamage", rowIndex),
                parseDecimalToken(fields[4], "avgDamageTaken", rowIndex),
                parseDecimalToken(fields[5], "avgHeal", rowIndex),
                parseIntegerToken(fields[6], "avgDurationSeconds", rowIndex),
                parseDecimalToken(fields[8], "avgKills", rowIndex),
                parseDecimalToken(fields[9], "avgAssists", rowIndex),
                CnMetaRoles.REAL_101_AGGREGATE_NOTE
        );
    }

    private CnMetaChampionStatRow parseRow(
            JsonNode rowNode,
            String role,
            String tierScope,
            boolean sourceDateUnavailable,
            String rootUpdateTime,
            int rowIndex
    ) {
        Integer championId = requiredInteger(rowNode, "championId", rowIndex,
                "championId", "champion_id", "heroId", "hero_id", "champId", "champ_id");
        BigDecimal winRate = requiredPercent(rowNode, "winRate", rowIndex, "winRate", "win_rate", "winrate");
        BigDecimal pickRate = requiredPercent(rowNode, "pickRate", rowIndex,
                "pickRate", "pick_rate", "pickrate", "selectRate", "select_rate");
        BigDecimal banRate = requiredPercent(rowNode, "banRate", rowIndex, "banRate", "ban_rate", "banrate");
        BigDecimal avgKda = optionalDecimal(rowNode, "avgKda", "avg_kda", "kda");
        BigDecimal avgGold = optionalDecimal(rowNode, "avgGold", "avg_gold", "gold", "goldEarned");
        BigDecimal avgDamageShare = optionalPercent(rowNode, "avgDamageShare", "avg_damage_share", "damageShare", "damage_share");
        BigDecimal avgDamageTakenShare = optionalPercent(rowNode,
                "avgDamageTakenShare", "avg_damage_taken_share", "damageTakenShare", "damage_taken_share");
        Integer rankIndex = optionalInteger(rowNode, "rankIndex", "rank_index", "rank");
        String sampleNote = sampleNote(rowNode, rootUpdateTime, sourceDateUnavailable);

        return new CnMetaChampionStatRow(
                championId,
                role,
                tierScope,
                winRate,
                pickRate,
                banRate,
                avgKda,
                avgGold,
                avgDamageShare,
                avgDamageTakenShare,
                rankIndex,
                sampleNote
        );
    }

    private JsonNode readJson(String rawContent) {
        try {
            return objectMapper.readTree(rawContent);
        } catch (Exception exception) {
            throw new CnMetaSourceException("Unable to parse real 101 response JSON: " + exception.getMessage());
        }
    }

    private String findChampionDetails(JsonNode root) {
        JsonNode result = null;
        JsonNode data = root.get("data");
        if (data != null) {
            result = data.get("result");
        }
        if (result == null) {
            result = root.get("result");
        }
        if (result == null || result.isNull()) {
            return null;
        }

        JsonNode resultObject = parseResultObject(result);
        JsonNode championDetailsNode = resultObject.get("championdetails");
        if (championDetailsNode == null || championDetailsNode.isNull()) {
            throw new CnMetaSourceException("Unable to parse real 101 response: championdetails not found in data.result");
        }
        String championDetails = championDetailsNode.asText(null);
        if (championDetails == null || championDetails.isBlank()) {
            throw new CnMetaSourceException("Unable to parse real 101 response: championdetails is empty");
        }
        return championDetails;
    }

    private JsonNode parseResultObject(JsonNode result) {
        if (result.isObject()) {
            return result;
        }
        String resultText = result.asText(null);
        if (resultText == null || resultText.isBlank()) {
            throw new CnMetaSourceException("Unable to parse real 101 response: data.result is empty");
        }
        try {
            return objectMapper.readTree(resultText);
        } catch (Exception exception) {
            throw new CnMetaSourceException("Unable to parse real 101 response data.result JSON: " + exception.getMessage());
        }
    }

    private static JsonNode findRowsNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        for (String field : List.of("rows", "list", "records", "items", "championStats", "heroStats")) {
            JsonNode child = node.get(field);
            if (child != null && child.isArray()) {
                return child;
            }
        }
        JsonNode data = node.get("data");
        if (data != null) {
            JsonNode rows = findRowsNode(data);
            if (rows != null) {
                return rows;
            }
        }
        JsonNode result = node.get("result");
        if (result != null) {
            return findRowsNode(result);
        }
        return null;
    }

    private static DateResolution resolveDataDate(JsonNode root) {
        String rawDate = firstText(root, "dataDate", "data_date", "statDate", "date", "updateDate");
        LocalDate parsed = parseDate(rawDate);
        if (parsed != null) {
            return new DateResolution(parsed, false);
        }
        return new DateResolution(LocalDate.now(SHANGHAI), true);
    }

    private static LocalDate parseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        String trimmed = rawDate.trim();
        try {
            if (trimmed.length() >= 10 && trimmed.charAt(4) == '-' && trimmed.charAt(7) == '-') {
                return LocalDate.parse(trimmed.substring(0, 10));
            }
            if (trimmed.matches("\\d{8}")) {
                return LocalDate.parse(trimmed, DateTimeFormatter.BASIC_ISO_DATE);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static Integer requiredInteger(JsonNode rowNode, String label, int rowIndex, String... aliases) {
        Integer value = optionalInteger(rowNode, aliases);
        if (value == null) {
            throw new CnMetaSourceException("Unable to parse real 101 response: missing numeric " + label + " at row " + rowIndex);
        }
        return value;
    }

    private static BigDecimal requiredPercent(JsonNode rowNode, String label, int rowIndex, String... aliases) {
        BigDecimal value = optionalPercent(rowNode, aliases);
        if (value == null) {
            throw new CnMetaSourceException("Unable to parse real 101 response: missing numeric " + label + " at row " + rowIndex);
        }
        return value;
    }

    private static Integer optionalInteger(JsonNode rowNode, String... aliases) {
        JsonNode value = first(rowNode, aliases);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isInt() || value.isLong()) {
            return value.asInt();
        }
        String text = value.asText(null);
        if (text == null || !text.trim().matches("\\d+")) {
            return null;
        }
        return Integer.parseInt(text.trim());
    }

    private static BigDecimal optionalPercent(JsonNode rowNode, String... aliases) {
        JsonNode valueNode = first(rowNode, aliases);
        ParsedDecimal parsed = parseDecimal(valueNode);
        if (parsed == null) {
            return null;
        }
        BigDecimal value = parsed.value();
        if (parsed.hadPercentSign() || value.compareTo(BigDecimal.ONE) > 0) {
            value = value.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        }
        return normalizeDecimal(value);
    }

    private static BigDecimal optionalDecimal(JsonNode rowNode, String... aliases) {
        ParsedDecimal parsed = parseDecimal(first(rowNode, aliases));
        return parsed == null ? null : normalizeDecimal(parsed.value());
    }

    private static Integer parseIntegerToken(String token, String label, int rowIndex) {
        try {
            return new BigDecimal(token.trim()).intValueExact();
        } catch (Exception exception) {
            throw new CnMetaSourceException(
                    "Unable to parse real 101 response: invalid numeric " + label + " in championdetails row " + rowIndex
            );
        }
    }

    private static BigDecimal parseDecimalToken(String token, String label, int rowIndex) {
        try {
            return normalizeDecimal(new BigDecimal(token.trim()));
        } catch (Exception exception) {
            throw new CnMetaSourceException(
                    "Unable to parse real 101 response: invalid numeric " + label + " in championdetails row " + rowIndex
            );
        }
    }

    private static ParsedDecimal parseDecimal(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isNumber()) {
            return new ParsedDecimal(valueNode.decimalValue(), false);
        }
        String text = valueNode.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        boolean percent = text.contains("%");
        String normalized = text.trim().replace("%", "").replace(",", "");
        try {
            return new ParsedDecimal(new BigDecimal(normalized), percent);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static BigDecimal normalizeDecimal(BigDecimal value) {
        return value.stripTrailingZeros();
    }

    private static String sampleNote(JsonNode rowNode, String rootUpdateTime, boolean sourceDateUnavailable) {
        List<String> parts = new ArrayList<>();
        String note = firstText(rowNode, "sampleNote", "sample_note", "note");
        if (note != null && !note.isBlank()) {
            parts.add(note.trim());
        }
        String sampleCount = firstText(rowNode, "sampleCount", "sample_count", "games", "matches");
        if (sampleCount != null && !sampleCount.isBlank()) {
            parts.add("sampleCount=" + sampleCount.trim());
        }
        String rowUpdateTime = firstText(rowNode, "updateTime", "updatedAt", "update_time");
        String updateTime = rowUpdateTime == null ? rootUpdateTime : rowUpdateTime;
        if (updateTime != null && !updateTime.isBlank()) {
            parts.add("updateTime=" + updateTime.trim());
        }
        if (sourceDateUnavailable) {
            parts.add("source date unavailable");
        }
        if (parts.isEmpty()) {
            parts.add("public 101 aggregate sample");
        }
        String joined = String.join("; ", parts);
        if (joined.length() <= MAX_SAMPLE_NOTE_LENGTH) {
            return joined;
        }
        return joined.substring(0, MAX_SAMPLE_NOTE_LENGTH);
    }

    private static JsonNode first(JsonNode node, String... aliases) {
        for (String alias : aliases) {
            JsonNode child = node.get(alias);
            if (child != null && !child.isNull()) {
                return child;
            }
        }
        return null;
    }

    private static String firstText(JsonNode node, String... aliases) {
        JsonNode child = first(node, aliases);
        if (child == null) {
            return null;
        }
        String text = child.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private record DateResolution(LocalDate dataDate, boolean sourceDateUnavailable) {
    }

    private record ParsedDecimal(BigDecimal value, boolean hadPercentSign) {
    }
}
