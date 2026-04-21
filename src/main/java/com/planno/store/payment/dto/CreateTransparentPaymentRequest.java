package com.planno.store.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateTransparentPaymentRequest(
        @NotNull UUID orderId,
        @NotBlank String token,
        @NotBlank String paymentMethodId,
        String issuerId,
        @NotNull @Min(1) @Max(24) Integer installments,
        @NotBlank @Email String payerEmail,
        String payerFirstName,
        String payerLastName,
        String identificationType,
        String identificationNumber,
        String deviceId
) {
}
