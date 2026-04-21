package com.planno.store.shipping.dto;

import com.planno.store.shipping.domain.ShippingMethod;
import java.math.BigDecimal;
import java.util.UUID;

public record ShippingQuoteResponse(
        UUID shippingMethodId,
        String name,
        String type,
        BigDecimal price,
        int estimatedDays
) {
    public static ShippingQuoteResponse from(ShippingMethod method) {
        return new ShippingQuoteResponse(
                method.getId(),
                method.getName(),
                method.getType().name(),
                method.getPrice(),
                method.getEstimatedDays()
        );
    }
}

