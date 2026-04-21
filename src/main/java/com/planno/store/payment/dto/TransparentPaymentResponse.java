package com.planno.store.payment.dto;

import com.planno.store.payment.domain.Payment;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransparentPaymentResponse(
        UUID paymentId,
        UUID orderId,
        String provider,
        String providerPaymentId,
        String status,
        String statusDetail,
        boolean requiresAction,
        String checkoutType,
        String message,
        String fallbackCheckoutUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static TransparentPaymentResponse from(Payment payment, String message, String fallbackCheckoutUrl) {
        return new TransparentPaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getProvider().name(),
                payment.getProviderPaymentId(),
                payment.getStatus().name(),
                payment.getStatusDetail(),
                payment.getStatus().requiresCustomerAction(),
                payment.getCheckoutType().name(),
                message,
                fallbackCheckoutUrl,
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
