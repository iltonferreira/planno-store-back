package com.planno.store.shipping.dto;

import com.planno.store.shipping.domain.ShippingMethodType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ShippingMethodRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull ShippingMethodType type,
        @NotNull @DecimalMin(value = "0.00") BigDecimal price,
        @Min(1) int estimatedDays,
        boolean active
) {
}

