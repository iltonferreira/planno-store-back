package com.planno.store.payment.service;

public record TransparentPaymentProviderRequest(
        String token,
        String paymentMethodId,
        String issuerId,
        int installments,
        String payerEmail,
        String payerFirstName,
        String payerLastName,
        String identificationType,
        String identificationNumber,
        String deviceId
) {
}
