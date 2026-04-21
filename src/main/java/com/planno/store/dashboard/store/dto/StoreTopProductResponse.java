package com.planno.store.dashboard.store.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StoreTopProductResponse(
        UUID productId,
        String productName,
        String productSlug,
        String sku,
        long quantitySold,
        BigDecimal grossRevenue
) {
}
