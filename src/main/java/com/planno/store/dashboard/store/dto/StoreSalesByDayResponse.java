package com.planno.store.dashboard.store.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StoreSalesByDayResponse(
        LocalDate day,
        BigDecimal totalRevenue,
        long paidOrdersCount
) {
}
