package com.planno.store.payment.dto;

import com.planno.store.payment.domain.Payment;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID storeId,
        UUID orderId,
        String provider,
        String providerPaymentId,
        String providerCheckoutId,
        String providerMerchantId,
        String status,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal sellerNetAmount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getStore().getId(),
                payment.getOrder().getId(),
                payment.getProvider().name(),
                payment.getProviderPaymentId(),
                payment.getProviderCheckoutId(),
                payment.getProviderMerchantId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getCommissionRate(),
                payment.getCommissionAmount(),
                payment.getSellerNetAmount(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
