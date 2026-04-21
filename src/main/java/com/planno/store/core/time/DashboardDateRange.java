package com.planno.store.core.time;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DashboardDateRange(
        LocalDate fromDate,
        LocalDate toDate,
        OffsetDateTime fromDateTime,
        OffsetDateTime toExclusiveDateTime,
        String appliedPreset
) {
}
