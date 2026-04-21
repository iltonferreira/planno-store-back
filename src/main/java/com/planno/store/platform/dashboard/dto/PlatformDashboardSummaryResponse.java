package com.planno.store.platform.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PlatformDashboardSummaryResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal grossRevenue,
        BigDecimal platformCommissionTotal,
        long storesCount,
        long paidOrdersCount,
        long waitingPaymentOrdersCount,
        long cancelledOrdersCount,
        BigDecimal averageTicket,
        List<PlatformTopStoreResponse> topStores,
        List<PlatformCommissionByStoreResponse> commissionByStore,
        List<PlatformSalesByDayResponse> salesByDay
) {
}
