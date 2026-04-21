package com.planno.store.core.time;

import com.planno.store.core.exception.BusinessException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DashboardDateRangeResolver {

    public DashboardDateRange resolve(LocalDate from, LocalDate to, DashboardPeriodPreset preset, ZoneId zoneId) {
        if ((from == null) != (to == null)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Both 'from' and 'to' must be informed together");
        }
        if (from != null && to != null) {
            if (to.isBefore(from)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "The 'to' date must be greater than or equal to 'from'");
            }
            return build(from, to, "CUSTOM", zoneId);
        }

        DashboardPeriodPreset appliedPreset = preset != null ? preset : DashboardPeriodPreset.LAST30DAYS;
        LocalDate today = LocalDate.now(zoneId);

        return switch (appliedPreset) {
            case TODAY -> build(today, today, appliedPreset.name(), zoneId);
            case LAST7DAYS -> build(today.minusDays(6), today, appliedPreset.name(), zoneId);
            case LAST30DAYS -> build(today.minusDays(29), today, appliedPreset.name(), zoneId);
            case MONTH -> build(today.withDayOfMonth(1), today, appliedPreset.name(), zoneId);
        };
    }

    private DashboardDateRange build(LocalDate from, LocalDate to, String appliedPreset, ZoneId zoneId) {
        ZonedDateTime fromDateTime = from.atStartOfDay(zoneId);
        ZonedDateTime toExclusiveDateTime = to.plusDays(1).atStartOfDay(zoneId);
        return new DashboardDateRange(
                from,
                to,
                fromDateTime.toOffsetDateTime(),
                toExclusiveDateTime.toOffsetDateTime(),
                appliedPreset
        );
    }
}
