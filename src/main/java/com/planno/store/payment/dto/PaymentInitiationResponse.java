package com.planno.store.payment.dto;

import com.planno.store.payment.domain.Payment;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentInitiationResponse(
        UUID paymentId,
        UUID orderId,
        String provider,
        String status,
        String checkoutType,
        BigDecimal amount,
        String currency,
        String checkoutUrl,
        String externalCheckoutId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PaymentInitiationResponse from(Payment payment, String checkoutUrl) {
        return new PaymentInitiationResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getProvider().name(),
                payment.getStatus().name(),
                payment.getCheckoutType().name(),
                payment.getAmount(),
                payment.getCurrency(),
                checkoutUrl,
                payment.getProviderCheckoutId(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
