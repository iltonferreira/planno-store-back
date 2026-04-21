package com.planno.store.payment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull UUID orderId
) {
}
