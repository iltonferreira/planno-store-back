package com.planno.store.pricing.coupon.dto;

import com.planno.store.pricing.domain.DiscountType;
import java.math.BigDecimal;
import java.util.UUID;

public record CouponValidationResponse(
        UUID couponId,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        boolean freeShipping
) {
}
