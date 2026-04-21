package com.planno.store.catalog.dto;

import com.planno.store.catalog.domain.Category;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID storeId,
        String name,
        String slug,
        String description,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getStore().getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getStatus().name(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}

