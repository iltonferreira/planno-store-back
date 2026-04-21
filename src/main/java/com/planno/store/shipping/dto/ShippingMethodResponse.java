package com.planno.store.shipping.dto;

import com.planno.store.shipping.domain.ShippingMethod;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ShippingMethodResponse(
        UUID id,
        UUID storeId,
        String name,
        String type,
        BigDecimal price,
        int estimatedDays,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ShippingMethodResponse from(ShippingMethod method) {
        return new ShippingMethodResponse(
                method.getId(),
                method.getStore().getId(),
                method.getName(),
                method.getType().name(),
                method.getPrice(),
                method.getEstimatedDays(),
                method.isActive(),
                method.getCreatedAt(),
                method.getUpdatedAt()
        );
    }
}

