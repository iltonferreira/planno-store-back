package com.planno.store.platform.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PlatformSalesByDayResponse(
        LocalDate day,
        BigDecimal grossRevenue,
        BigDecimal platformCommissionTotal,
        long paidOrdersCount
) {
}
