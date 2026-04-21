package com.planno.store.payment.service;

public record PaymentCheckoutResult(
        String providerPaymentId,
        String providerCheckoutId,
        String checkoutUrl,
        com.planno.store.payment.domain.PaymentStatus status,
        String statusDetail,
        String paymentMethod,
        String rawResponse
) {
}
