package io.rankpeek.server.analysis.coachsummary;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    }
}
