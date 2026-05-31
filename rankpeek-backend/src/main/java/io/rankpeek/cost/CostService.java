package io.rankpeek.cost;

import io.rankpeek.ai.AiTokenUsage;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CostService {

    private final CostRepository repository;
    private final AiCostCalculator calculator;

    public CostService(CostRepository repository, AiCostCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    public AiCostBreakdown recordAiAnalysis(
            long runId,
            String source,
            AiTokenUsage usage,
            AiPricing pricing
    ) {
        AiCostBreakdown cost = calculator.calculate(usage, pricing);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("runId", runId);
        metadata.put("usage", usage);
        metadata.put("cost", cost);
        repository.insertEvent(
                "ai_analysis",
                usage.provider(),
                usage.model(),
                source,
                cost.totalCny(),
                cost.currency(),
                usage.totalTokens(),
                metadata
        );
        return cost;
    }

    public CostSummaryResponse summary(LocalDate from, LocalDate to) {
        LocalDate normalizedFrom = from == null ? LocalDate.now() : from;
        LocalDate normalizedTo = to == null ? normalizedFrom : to;
        if (normalizedTo.isBefore(normalizedFrom)) {
            LocalDate swap = normalizedFrom;
            normalizedFrom = normalizedTo;
            normalizedTo = swap;
        }
        ZoneId zoneId = ZoneId.systemDefault();
        long fromMillis = normalizedFrom.atStartOfDay(zoneId).toInstant().toEpochMilli();
        long toMillis = normalizedTo.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1;
        BigDecimal aiCost = repository.sumEventsBetween(fromMillis, toMillis, "ai_analysis");
        return new CostSummaryResponse(
                normalizedFrom,
                normalizedTo,
                aiCost
        );
    }

    public CostEventListResponse listEvents(String type, int limit, int offset) {
        return new CostEventListResponse(repository.listEvents(type, limit, offset));
    }
}
