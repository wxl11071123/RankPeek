package io.rankpeek.server.analysis.coachsummary;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoachSummaryReportTest {

    private static final Path EXAMPLE_PATH = Path.of(
            "src/test/resources/fixtures/coach-summary-report-v1.example.json"
    );
    private static final Path SCHEMA_PATH = Path.of(
            "src/main/resources/schemas/coach-summary-report-v1.schema.json"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesExampleReport() throws Exception {
        CoachSummaryReport report = objectMapper.readValue(EXAMPLE_PATH.toFile(), CoachSummaryReport.class);

        assertEquals(CoachSummaryReport.SCHEMA_VERSION, report.schemaVersion());
        assertEquals(CoachSummaryReport.ANALYSIS_TYPE, report.analysisType());
        assertEquals("9f4c2a10", report.inputHash());
        assertEquals(CoachSummaryReport.Confidence.MEDIUM, report.verdict().confidence());
        assertEquals(CoachSummaryReport.FindingCategory.LANING, report.keyFindings().getFirst().category());
        assertEquals(CoachSummaryReport.ChartBlockType.GOLD_CURVE, report.chartBlocks().getFirst().type());
    }

    @Test
    void serializesStableWireEnumValuesAndCoreFields() throws Exception {
        CoachSummaryReport report = objectMapper.readValue(EXAMPLE_PATH.toFile(), CoachSummaryReport.class);

        JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(report));

        assertEquals("coach_summary_report.v1", node.get("schemaVersion").asText());
        assertEquals("coach_summary", node.get("analysisType").asText());
        assertEquals("medium", node.get("verdict").get("confidence").asText());
        assertEquals("laning", node.get("keyFindings").get(0).get("category").asText());
        assertEquals("gold_curve", node.get("chartBlocks").get(0).get("type").asText());
    }

    @Test
    void deserializesOptionalUiReportFieldsAndChartData() throws Exception {
        ObjectNode node = (ObjectNode) objectMapper.readTree(EXAMPLE_PATH.toFile());
        node.put("headline", "贝蕾亚波动偏高");
        node.put("finalSummary", "接下来一周先压低资源前死亡。");

        ObjectNode overview = node.putObject("overview");
        overview.put("totalMatches", 20);
        overview.put("wins", 11);
        overview.put("losses", 9);
        overview.put("winRate", 55);
        overview.put("summary", "主玩打野，贝蕾亚和凯隐占比最高。");
        ArrayNode heroStats = overview.putArray("heroStats");
        ObjectNode hero = heroStats.addObject();
        hero.put("championId", 233);
        hero.put("championCanonicalName", "Briar");
        hero.put("championDisplayName", "贝蕾亚");
        hero.put("role", "JUNGLE");
        hero.put("games", 8);
        hero.put("wins", 4);
        hero.put("losses", 4);
        hero.put("winRate", 50);
        hero.put("kda", "7.1 / 6.0 / 8.4");
        hero.put("averageKda", 2.58);

        ObjectNode chart = (ObjectNode) node.get("chartBlocks").get(0);
        chart.put("kind", "bar");
        chart.put("placement", "overview");
        chart.put("intent", "对比主玩英雄胜率");
        chart.put("interpretation", "凯隐更稳定。");
        chart.putArray("evidenceRefs").add("overview.heroStats");
        chart.putArray("yKeys").add("winRate");
        chart.put("labelKey", "champion");
        chart.put("valueKey", "winRate");
        chart.putArray("data")
                .addObject()
                .put("champion", "贝蕾亚")
                .put("winRate", 50)
                .put("games", 8);

        CoachSummaryReport report = objectMapper.readValue(node.toString(), CoachSummaryReport.class);

        assertEquals("贝蕾亚波动偏高", report.headline());
        assertEquals(20, report.overview().totalMatches());
        assertEquals("贝蕾亚", report.overview().heroStats().getFirst().championDisplayName());
        assertEquals(CoachSummaryReport.ChartKind.BAR, report.chartBlocks().getFirst().kind());
        assertEquals(CoachSummaryReport.ChartPlacement.OVERVIEW, report.chartBlocks().getFirst().placement());
        assertEquals("overview.heroStats", report.chartBlocks().getFirst().evidenceRefs().getFirst());
        assertEquals("接下来一周先压低资源前死亡。", report.finalSummary());
    }

    @Test
    void rejectsInvalidEnumValues() throws Exception {
        ObjectNode node = (ObjectNode) objectMapper.readTree(EXAMPLE_PATH.toFile());
        ((ObjectNode) node.get("verdict")).put("confidence", "certain");

        assertThrows(JsonMappingException.class, () ->
                objectMapper.readValue(node.toString(), CoachSummaryReport.class)
        );
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        ObjectNode node = (ObjectNode) objectMapper.readTree(EXAMPLE_PATH.toFile());
        node.remove("title");

        assertThrows(JsonMappingException.class, () ->
                objectMapper.readValue(node.toString(), CoachSummaryReport.class)
        );
    }

    @Test
    void schemaResourceIsValidJson() throws Exception {
        JsonNode schema = objectMapper.readTree(SCHEMA_PATH.toFile());

        assertEquals("RankPeek coach_summary AI report v1", schema.get("title").asText());
        assertEquals("coach_summary_report.v1", schema.get("properties").get("schemaVersion").get("const").asText());
        assertEquals("string", schema.get("properties").get("headline").get("type").asText());
        assertEquals("object", schema.get("properties").get("overview").get("type").asText());
        assertEquals("bar", schema.get("$defs").get("chartKind").get("enum").get(0).asText());
    }
}
