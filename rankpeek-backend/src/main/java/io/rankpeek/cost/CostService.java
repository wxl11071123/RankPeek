package io.rankpeek.cost;

import io.rankpeek.ai.AiTokenUsage;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        LocalDate rangeFrom = normalizedFrom;
        LocalDate rangeTo = normalizedTo;
        BigDecimal manualCost = repository.listManualCosts().stream()
                .filter(ManualCostItem::active)
                .map(item -> manualCostInRange(item, rangeFrom, rangeTo))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CostSummaryResponse(
                normalizedFrom,
                normalizedTo,
                aiCost,
                manualCost,
                aiCost.add(manualCost)
        );
    }

    public CostEventListResponse listEvents(String type, int limit, int offset) {
        return new CostEventListResponse(repository.listEvents(type, limit, offset));
    }

    public ManualCostItem createManualCost(ManualCostRequest request) {
        return repository.createManualCost(request);
    }

    public ManualCostItem updateManualCost(long id, ManualCostRequest request) {
        return repository.updateManualCost(id, request);
    }

    public void deleteManualCost(long id) {
        repository.deleteManualCost(id);
    }

    public ManualCostListResponse listManualCosts() {
        return new ManualCostListResponse(repository.listManualCosts());
    }

    private BigDecimal manualCostInRange(ManualCostItem item, LocalDate from, LocalDate to) {
        if (item.effectiveDate().isAfter(to)) {
            return BigDecimal.ZERO;
        }
        return switch (item.cadence()) {
            case "monthly" -> item.amountCny().multiply(BigDecimal.valueOf(monthsInRange(item.effectiveDate(), from, to)));
            case "yearly" -> item.amountCny()
                    .divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(monthsInRange(item.effectiveDate(), from, to)));
            default -> !item.effectiveDate().isBefore(from) && !item.effectiveDate().isAfter(to)
                    ? item.amountCny()
                    : BigDecimal.ZERO;
        };
    }

    private long monthsInRange(LocalDate effectiveDate, LocalDate from, LocalDate to) {
        LocalDate start = effectiveDate.isAfter(from) ? effectiveDate : from;
        LocalDate cursor = start.withDayOfMonth(1);
        LocalDate end = to.withDayOfMonth(1);
        long months = 0;
        while (!cursor.isAfter(end)) {
            months++;
            cursor = cursor.plusMonths(1);
        }
        return months;
    }
}
