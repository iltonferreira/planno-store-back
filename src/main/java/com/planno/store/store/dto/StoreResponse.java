package com.planno.store.store.dto;

import com.planno.store.store.domain.Store;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StoreResponse(
        UUID id,
        String name,
        String slug,
        String status,
        String contactEmail,
        String currency,
        String timezone,
        String description,
        String logoUrl,
        String bannerUrl,
        String primaryColor,
        String secondaryColor,
        BigDecimal commissionRate,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getName(),
                store.getSlug(),
                store.getStatus().name(),
                store.getContactEmail(),
                store.getCurrency(),
                store.getTimezone(),
                store.getDescription(),
                store.getLogoUrl(),
                store.getBannerUrl(),
                store.getPrimaryColor(),
                store.getSecondaryColor(),
                store.getCommissionRate(),
                store.getCreatedAt(),
                store.getUpdatedAt()
        );
    }
}
