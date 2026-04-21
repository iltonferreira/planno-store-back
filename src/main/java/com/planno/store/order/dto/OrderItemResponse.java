package com.planno.store.order.dto;

import com.planno.store.order.domain.OrderItem;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID productId,
        String productName,
        String productSlug,
        String sku,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProductNameSnapshot(),
                item.getProductSlugSnapshot(),
                item.getSkuSnapshot(),
                item.getQuantity(),
                item.getUnitPriceSnapshot(),
                item.getTotalPriceSnapshot()
        );
    }
}

