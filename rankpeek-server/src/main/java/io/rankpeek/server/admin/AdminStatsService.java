package io.rankpeek.server.admin;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminStatsService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DAILY_WINDOW_DAYS = 7;

    private final AdminStatsRepository repository;

    public AdminStatsService(AdminStatsRepository repository) {
        this.repository = repository;
    }

    public AdminStatsOverviewResponse overview(String rawDate, String rawZone) {
        ZoneId zone = parseZone(rawZone);
        LocalDate date = parseDate(rawDate, zone);
        Instant generatedAt = Instant.now();
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();

        AdminStatsRepository.UserAggregate users = repository.userAggregate(dayStart, dayEnd);
        AdminStatsRepository.AiAggregate ai = repository.aiAggregate(dayStart, dayEnd);
        AdminStatsRepository.CreditAggregate credits = repository.creditAggregate(dayStart, dayEnd);

        return new AdminStatsOverviewResponse(
                date.toString(),
                zone.getId(),
                generatedAt,
                new AdminStatsOverviewResponse.ActiveUsers(
                        repository.countActiveUsersSince(generatedAt.minusSeconds(60 * 60), generatedAt),
                        repository.countActiveUsersSince(generatedAt.minusSeconds(3 * 60 * 60), generatedAt),
                        repository.countActiveUsersSince(generatedAt.minusSeconds(24 * 60 * 60), generatedAt)
                ),
                new AdminStatsOverviewResponse.UserStats(
                        users.total(),
                        users.active(),
                        users.disabled(),
                        users.admins(),
                        users.registeredToday()
                ),
                new AdminStatsOverviewResponse.AiStats(
                        ai.requests(),
                        ai.succeeded(),
                        ai.failed(),
                        ai.reserved(),
                        ai.promptTokens(),
                        ai.completionTokens(),
                        ai.totalTokens(),
                        ai.chargedCredits(),
                        ai.refundedCredits(),
                        successRate(ai.succeeded(), ai.requests())
                ),
                new AdminStatsOverviewResponse.CreditStats(
                        credits.adminGranted(),
                        credits.aiCharged(),
                        credits.aiRefunded(),
                        credits.aiCharged() - credits.aiRefunded(),
                        repository.outstandingBalance()
                ),
                dailyStats(date, zone)
        );
    }

    private List<AdminStatsOverviewResponse.DailyStats> dailyStats(LocalDate targetDate, ZoneId zone) {
        LocalDate firstDate = targetDate.minusDays(DAILY_WINDOW_DAYS - 1L);
        List<AdminStatsOverviewResponse.DailyStats> points = new ArrayList<>();
        for (int index = 0; index < DAILY_WINDOW_DAYS; index++) {
            LocalDate day = firstDate.plusDays(index);
            Instant start = day.atStartOfDay(zone).toInstant();
            Instant end = day.plusDays(1).atStartOfDay(zone).toInstant();
            AdminStatsRepository.AiAggregate ai = repository.aiAggregate(start, end);
            points.add(new AdminStatsOverviewResponse.DailyStats(
                    day.toString(),
                    repository.countRegisteredUsers(start, end),
                    ai.requests(),
                    ai.succeeded(),
                    ai.failed(),
                    ai.chargedCredits(),
                    ai.refundedCredits(),
                    ai.totalTokens()
            ));
        }
        return points;
    }

    private static ZoneId parseZone(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(value.trim());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Unsupported time zone");
        }
    }

    private static LocalDate parseDate(String value, ZoneId zone) {
        if (value == null || value.isBlank()) {
            return LocalDate.now(zone);
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid date");
        }
    }

    private static double successRate(long succeeded, long requests) {
        if (requests <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(succeeded)
                .divide(BigDecimal.valueOf(requests), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
