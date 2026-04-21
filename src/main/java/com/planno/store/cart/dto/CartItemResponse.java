package com.planno.store.cart.dto;

import com.planno.store.cart.domain.CartItem;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CartItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String productSlug,
        String productSku,
        int quantity,
        BigDecimal unitBasePrice,
        BigDecimal unitFinalPrice,
        BigDecimal unitDiscountAmount,
        BigDecimal totalBasePrice,
        BigDecimal totalFinalPrice,
        BigDecimal totalDiscountAmount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getProduct().getSlug(),
                item.getProduct().getSku(),
                item.getQuantity(),
                item.getUnitBasePriceSnapshot(),
                item.getUnitFinalPriceSnapshot(),
                item.getUnitDiscountAmountSnapshot(),
                item.getTotalBasePriceSnapshot(),
                item.getTotalFinalPriceSnapshot(),
                item.getTotalDiscountAmountSnapshot(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}

