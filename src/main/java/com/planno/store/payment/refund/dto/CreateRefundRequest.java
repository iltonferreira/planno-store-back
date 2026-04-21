package com.planno.store.payment.refund.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record CreateRefundRequest(
        @DecimalMin(value = "0.01", inclusive = true, message = "Refund amount must be greater than zero")
        BigDecimal amount,
        String reason
) {
}
