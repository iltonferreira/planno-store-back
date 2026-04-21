package com.planno.store.pricing.promotion.dto;

import com.planno.store.pricing.domain.DiscountType;
import com.planno.store.pricing.promotion.domain.AppliesToType;
import com.planno.store.pricing.promotion.domain.Promotion;
import com.planno.store.pricing.promotion.domain.PromotionType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PromotionResponse(
        UUID id,
        UUID storeId,
        String name,
        String description,
        PromotionType type,
        DiscountType discountType,
        BigDecimal discountValue,
        AppliesToType appliesToType,
        UUID appliesToId,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PromotionResponse from(Promotion promotion) {
        return new PromotionResponse(
                promotion.getId(),
                promotion.getStore().getId(),
                promotion.getName(),
                promotion.getDescription(),
                promotion.getType(),
                promotion.getDiscountType(),
                promotion.getDiscountValue(),
                promotion.getAppliesToType(),
                promotion.getAppliesToId(),
                promotion.getMinOrderAmount(),
                promotion.getMaxDiscountAmount(),
                promotion.getStartsAt(),
                promotion.getEndsAt(),
                promotion.isActive(),
                promotion.getCreatedAt(),
                promotion.getUpdatedAt()
        );
    }
}
