package io.rankpeek.server.analysis.coachsummary;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public record CoachSummaryReport(
        String schemaVersion,
        String analysisType,
        String inputHash,
        String title,
        String summary,
        Verdict verdict,
        List<KeyFinding> keyFindings,
        List<TrainingPlanItem> trainingPlan,
        List<ChampionAdvice> championAdvice,
        List<ChartBlock> chartBlocks,
        List<ReportWarning> warnings,
        Metadata metadata
) {
    public static final String SCHEMA_VERSION = "coach_summary_report.v1";
    public static final String ANALYSIS_TYPE = "coach_summary";

    public CoachSummaryReport {
        schemaVersion = requireExact(schemaVersion, SCHEMA_VERSION, "schemaVersion");
        analysisType = requireExact(analysisType, ANALYSIS_TYPE, "analysisType");
        inputHash = requireNonBlank(inputHash, "inputHash");
        title = requireNonBlank(title, "title");
        summary = requireNonBlank(summary, "summary");
        verdict = requireNonNull(verdict, "verdict");
        keyFindings = requireNonEmptyList(keyFindings, "keyFindings");
        trainingPlan = requireNonEmptyList(trainingPlan, "trainingPlan");
        championAdvice = requireList(championAdvice, "championAdvice");
        chartBlocks = requireList(chartBlocks, "chartBlocks");
        warnings = requireList(warnings, "warnings");
        metadata = requireNonNull(metadata, "metadata");
    }

    public record Verdict(
            String label,
            Double score,
            Confidence confidence,
            String summary
    ) {
        public Verdict {
            label = requireNonBlank(label, "verdict.label");
            score = requireScore(score, "verdict.score");
            confidence = requireNonNull(confidence, "verdict.confidence");
            summary = requireNonBlank(summary, "verdict.summary");
        }
    }

    public record KeyFinding(
            String id,
            Priority priority,
            FindingCategory category,
            String claim,
            String evidence,
            String reasoning,
            String advice,
            Confidence confidence,
            List<String> evidenceRefs
    ) {
        public KeyFinding {
            id = requireNonBlank(id, "keyFindings.id");
            priority = requireNonNull(priority, "keyFindings.priority");
            category = requireNonNull(category, "keyFindings.category");
            claim = requireNonBlank(claim, "keyFindings.claim");
            evidence = requireNonBlank(evidence, "keyFindings.evidence");
            reasoning = requireNonBlank(reasoning, "keyFindings.reasoning");
            advice = requireNonBlank(advice, "keyFindings.advice");
            confidence = requireNonNull(confidence, "keyFindings.confidence");
            evidenceRefs = requireNonEmptyStringList(evidenceRefs, "keyFindings.evidenceRefs");
        }
    }

    public record TrainingPlanItem(
            String focus,
            String why,
            Integer nextGames,
            String task,
            String metricToTrack,
            String target,
            Priority priority
    ) {
        public TrainingPlanItem {
            focus = requireNonBlank(focus, "trainingPlan.focus");
            why = requireNonBlank(why, "trainingPlan.why");
            nextGames = requirePositive(nextGames, "trainingPlan.nextGames");
            task = requireNonBlank(task, "trainingPlan.task");
            metricToTrack = requireNonBlank(metricToTrack, "trainingPlan.metricToTrack");
            target = requireNonBlank(target, "trainingPlan.target");
            priority = requireNonNull(priority, "trainingPlan.priority");
        }
    }

    public record ChampionAdvice(
            String championName,
            String role,
            ChampionRecommendation recommendation,
            String reason,
            Confidence confidence
    ) {
        public ChampionAdvice {
            championName = requireNonBlank(championName, "championAdvice.championName");
            role = requireNonBlank(role, "championAdvice.role");
            recommendation = requireNonNull(recommendation, "championAdvice.recommendation");
            reason = requireNonBlank(reason, "championAdvice.reason");
            confidence = requireNonNull(confidence, "championAdvice.confidence");
        }
    }

    public record ChartBlock(
            String id,
            ChartBlockType type,
            String title,
            String description,
            String dataRef,
            String highlight
    ) {
        public ChartBlock {
            id = requireNonBlank(id, "chartBlocks.id");
            type = requireNonNull(type, "chartBlocks.type");
            title = requireNonBlank(title, "chartBlocks.title");
            description = requireNonBlank(description, "chartBlocks.description");
            dataRef = requireNonBlank(dataRef, "chartBlocks.dataRef");
            highlight = requireNonBlank(highlight, "chartBlocks.highlight");
        }
    }

    public record ReportWarning(
            WarningType type,
            String message
    ) {
        public ReportWarning {
            type = requireNonNull(type, "warnings.type");
            message = requireNonBlank(message, "warnings.message");
        }
    }

    public record Metadata(
            String modelName,
            String promptVersion,
            String generatedAt,
            String snapshotSchemaVersion,
            Confidence dataQualityConfidence
    ) {
        public Metadata {
            modelName = requireNonBlank(modelName, "metadata.modelName");
            promptVersion = requireNonBlank(promptVersion, "metadata.promptVersion");
            generatedAt = requireIsoInstant(generatedAt, "metadata.generatedAt");
            snapshotSchemaVersion = requireNonBlank(snapshotSchemaVersion, "metadata.snapshotSchemaVersion");
            dataQualityConfidence = requireNonNull(dataQualityConfidence, "metadata.dataQualityConfidence");
        }
    }

    public enum Confidence implements WireValue {
        HIGH("high"),
        MEDIUM("medium"),
        LOW("low");

        private final String value;

        Confidence(String value) {
            this.value = value;
        }

        @JsonCreator
        public static Confidence fromJson(String value) {
            return enumFromValue(Confidence.class, value);
        }

        @Override
        @JsonValue
        public String value() {
            return value;
        }
    }

    public enum Priority implements WireValue {
        HIGH("high"),
        MEDIUM("medium"),
        LOW("low");

        private final String value;

        Priority(String value) {
            this.value = value;
        }

        @JsonCreator
        public static Priority fromJson(String value) {
            return enumFromValue(Priority.class, value);
        }

        @Override
        @JsonValue
        public String value() {
            return value;
        }
    }

    public enum FindingCategory implements WireValue {
        LANING("laning"),
        MID_GAME("mid_game"),
        OBJECTIVE("objective"),
        DEATH("death"),
        CHAMPION_POOL("champion_pool"),
        ROLE_POOL("role_pool"),
        ECONOMY("economy"),
        TEAMFIGHT("teamfight"),
        VISION("vision"),
        GENERAL("general");

        private final String value;

        FindingCategory(String value) {
            this.value = value;
        }

        @JsonCreator
        public static FindingCategory fromJson(String value) {
            return enumFromValue(FindingCategory.class, value);
        }

        @Override
        @JsonValue
        public String value() {
            return value;
        }
    }

    public enum ChampionRecommendation implements WireValue {
        KEEP("keep"),
        PRACTICE("practice"),
        AVOID_TEMPORARILY("avoid_temporarily"),
        OBSERVE_MORE("observe_more");

        private final String value;

        ChampionRecommendation(String value) {
            this.value = value;
        }

        @JsonCreator
        public static ChampionRecommendation fromJson(String value) {
            return enumFromValue(ChampionRecommendation.class, value);
        }

        @Override
        @JsonValue
        public String value() {
            return value;
        }
    }

    public enum ChartBlockType implements WireValue {
        GOLD_CURVE("gold_curve"),
        DEATH_TIMELINE("death_timeline"),
        CHAMPION_POOL("champion_pool"),
        ROLE_PROFILE("role_profile"),
        METRIC_COMPARISON("metric_comparison"),
        OBJECTIVE_DEATHS("objective_deaths");

        private final String value;

        ChartBlockType(String value) {
            this.value = value;
        }

        @JsonCreator
        public static ChartBlockType fromJson(String value) {
            return enumFromValue(ChartBlockType.class, value);
        }

        @Override
        @JsonValue
        public String value() {
            return value;
        }
    }

    public enum WarningType implements WireValue {
        DATA_QUALITY("data_quality"),
        INSUFFICIENT_SAMPLE("insufficient_sample"),
        LOW_CONFIDENCE("low_confidence"),
        UNSUPPORTED_CLAIM("unsupported_claim");

        private final String value;

        WarningType(String value) {
            this.value = value;
        }

        @JsonCreator
        public static WarningType fromJson(String value) {
            return enumFromValue(WarningType.class, value);
        }

        @Override
        @JsonValue
        public String value() {
            return value;
        }
    }

    private interface WireValue {
        String value();
    }

    private static String requireExact(String value, String expected, String field) {
        String nonBlank = requireNonBlank(value, field);
        if (!expected.equals(nonBlank)) {
            throw new IllegalArgumentException(field + " must be " + expected);
        }
        return nonBlank;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String requireIsoInstant(String value, String field) {
        String nonBlank = requireNonBlank(value, field);
        try {
            Instant.parse(nonBlank);
            return nonBlank;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " must be an ISO-8601 instant", exception);
        }
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static Double requireScore(Double value, String field) {
        if (value == null || value < 0 || value > 100) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
        return value;
    }

    private static Integer requirePositive(Integer value, String field) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(field + " must be greater than 0");
        }
        return value;
    }

    private static <T> List<T> requireList(List<T> value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return List.copyOf(value);
    }

    private static <T> List<T> requireNonEmptyList(List<T> value, String field) {
        List<T> list = requireList(value, field);
        if (list.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return list;
    }

    private static List<String> requireNonEmptyStringList(List<String> value, String field) {
        List<String> list = requireNonEmptyList(value, field);
        return list.stream()
                .map(item -> requireNonBlank(item, field + " item"))
                .toList();
    }

    private static <E extends Enum<E> & WireValue> E enumFromValue(Class<E> enumClass, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(enumClass.getSimpleName() + " is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(candidate -> candidate.value().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported " + enumClass.getSimpleName() + ": " + value));
    }
}
