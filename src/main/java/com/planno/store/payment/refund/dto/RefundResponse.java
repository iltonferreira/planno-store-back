package com.planno.store.payment.refund.dto;

import com.planno.store.payment.refund.domain.Refund;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID storeId,
        UUID paymentId,
        UUID orderId,
        String provider,
        String providerRefundId,
        String type,
        BigDecimal amount,
        String currency,
        String status,
        String reason,
        OffsetDateTime requestedAt,
        OffsetDateTime processedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getStore().getId(),
                refund.getPayment().getId(),
                refund.getOrder().getId(),
                refund.getProvider().name(),
                refund.getProviderRefundId(),
                refund.getType().name(),
                refund.getAmount(),
                refund.getCurrency(),
                refund.getStatus().name(),
                refund.getReason(),
                refund.getRequestedAt(),
                refund.getProcessedAt(),
                refund.getCreatedAt(),
                refund.getUpdatedAt()
        );
    }
}
