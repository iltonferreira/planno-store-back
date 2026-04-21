package com.planno.store.dashboard.store.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StoreRecentOrderResponse(
        UUID orderId,
        String code,
        String status,
        BigDecimal subtotalAmount,
        BigDecimal shippingAmount,
        BigDecimal totalAmount,
        String customerName,
        String customerEmail,
        String latestPaymentStatus,
        OffsetDateTime createdAt
) {
}
