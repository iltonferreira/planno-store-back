package com.planno.store.catalog.dto;

import com.planno.store.catalog.domain.Product;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID storeId,
        UUID categoryId,
        String categoryName,
        String name,
        String slug,
        String sku,
        String description,
        String status,
        BigDecimal basePrice,
        String mainImageUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getStore().getId(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getName(),
                product.getSlug(),
                product.getSku(),
                product.getDescription(),
                product.getStatus().name(),
                product.getBasePrice(),
                product.getMainImageUrl(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}

