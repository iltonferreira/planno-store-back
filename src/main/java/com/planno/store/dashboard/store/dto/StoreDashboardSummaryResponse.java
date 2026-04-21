package com.planno.store.dashboard.store.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StoreDashboardSummaryResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal totalRevenue,
        long paidOrdersCount,
        long waitingPaymentOrdersCount,
        long cancelledOrdersCount,
        BigDecimal averageTicket,
        long ordersCount,
        long itemsSoldCount,
        List<StoreRecentOrderResponse> recentOrders,
        List<StoreTopProductResponse> topProducts,
        List<StoreSalesByDayResponse> salesByDay
) {
}
