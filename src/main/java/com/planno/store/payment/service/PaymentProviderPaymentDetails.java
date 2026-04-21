package com.planno.store.payment.service;

import com.planno.store.payment.domain.PaymentStatus;
import java.math.BigDecimal;

public record PaymentProviderPaymentDetails(
        String providerPaymentId,
        String providerCheckoutId,
        String providerMerchantId,
        PaymentStatus status,
        String statusDetail,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String externalReference
) {
}
