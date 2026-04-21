package com.planno.store.payment.service;

import com.planno.store.payment.refund.domain.RefundStatus;
import java.math.BigDecimal;

public record PaymentProviderRefundDetails(
        String providerRefundId,
        String providerPaymentId,
        RefundStatus status,
        BigDecimal amount,
        String currency,
        String rawResponse
) {
}
