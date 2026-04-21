package com.planno.store.pricing.coupon.dto;

import com.planno.store.pricing.domain.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CouponRequest(
        @NotBlank String code,
        String description,
        DiscountType discountType,
        @DecimalMin("0.00") BigDecimal discountValue,
        @DecimalMin("0.00") BigDecimal minOrderAmount,
        @DecimalMin("0.00") BigDecimal maxDiscountAmount,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        Integer maxUses,
        boolean active,
        boolean freeShipping
) {
}
