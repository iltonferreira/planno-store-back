package com.planno.store.payment.dto;

import com.planno.store.payment.domain.Payment;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StorePaymentResponse(
        UUID id,
        UUID storeId,
        UUID orderId,
        String provider,
        String checkoutType,
        String providerPaymentId,
        String providerCheckoutId,
        String status,
        String statusDetail,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        Integer installments,
        String payerEmail,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static StorePaymentResponse from(Payment payment) {
        return new StorePaymentResponse(
                payment.getId(),
                payment.getStore().getId(),
                payment.getOrder().getId(),
                payment.getProvider().name(),
                payment.getCheckoutType().name(),
                payment.getProviderPaymentId(),
                payment.getProviderCheckoutId(),
                payment.getStatus().name(),
                payment.getStatusDetail(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getInstallments(),
                payment.getPayerEmail(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
