package com.planno.store.pricing.promotion.dto;

import com.planno.store.pricing.domain.DiscountType;
import com.planno.store.pricing.promotion.domain.AppliesToType;
import com.planno.store.pricing.promotion.domain.PromotionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PromotionRequest(
        @NotBlank String name,
        String description,
        @NotNull PromotionType type,
        DiscountType discountType,
        @DecimalMin("0.00") BigDecimal discountValue,
        @NotNull AppliesToType appliesToType,
        UUID appliesToId,
        @DecimalMin("0.00") BigDecimal minOrderAmount,
        @DecimalMin("0.00") BigDecimal maxDiscountAmount,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        boolean active
) {
}
