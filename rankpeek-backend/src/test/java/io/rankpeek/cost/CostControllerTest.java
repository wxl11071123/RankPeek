package io.rankpeek.cost;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.ai.AiTokenUsage;
import io.rankpeek.cache.LocalCacheSchemaInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CostControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CostService service;
    private CostRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:rankpeek-cost-" + System.nanoTime()
                        + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        new LocalCacheSchemaInitializer(jdbcTemplate).initializeSchema();
        repository = new CostRepository(jdbcTemplate, objectMapper);
        service = new CostService(repository, new AiCostCalculator());
        mockMvc = MockMvcBuilders.standaloneSetup(new CostController(service)).build();
    }

    @Test
    void recordsAiAnalysisCostEventWithUsageMetadata() {
        AiCostBreakdown cost = service.recordAiAnalysis(
                10L,
                "pregame",
                new AiTokenUsage("deepseek", "deepseek-v4-flash", 300, 300, 600, 100, 200),
                AiPricingCatalog.forModel("deepseek", "deepseek-v4-flash").orElseThrow()
        );

        assertThat(cost.totalCny()).isEqualByComparingTo("0.000802");
        assertThat(repository.listEvents("ai_analysis", 20, 0)).hasSize(1);
        assertThat(repository.listEvents("ai_analysis", 20, 0).getFirst().metadataRawJson())
                .contains("\"runId\":10")
                .contains("\"promptCacheHitTokens\":100");
    }

    @Test
    void summaryOnlyReturnsAiTotalCostAndManualEndpointsAreGone() throws Exception {
        service.recordAiAnalysis(
                12L,
                "postgame",
                new AiTokenUsage("deepseek", "deepseek-v4-flash", 300, 300, 600, 100, 200),
                AiPricingCatalog.forModel("deepseek", "deepseek-v4-flash").orElseThrow()
        );

        mockMvc.perform(get("/api/v1/costs/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCostCny").value(0.000802))
                .andExpect(jsonPath("$.data.aiCostCny").doesNotExist())
                .andExpect(jsonPath("$.data.manualCostCny").doesNotExist());

        mockMvc.perform(get("/api/v1/costs/manual"))
                .andExpect(status().isNotFound());
    }

    @Test
    void eventsEndpointReturnsFilteredEvents() throws Exception {
        service.recordAiAnalysis(
                11L,
                "postgame",
                new AiTokenUsage("deepseek", "deepseek-v4-pro", 300, 300, 600, 100, 200),
                AiPricingCatalog.forModel("deepseek", "deepseek-v4-pro").orElseThrow()
        );

        mockMvc.perform(get("/api/v1/costs/events?type=ai_analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].eventType").value("ai_analysis"))
                .andExpect(jsonPath("$.data.items[0].source").value("postgame"));
    }
}
