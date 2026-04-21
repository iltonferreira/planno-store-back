package com.planno.store.pricing.coupon.dto;

import com.planno.store.pricing.coupon.domain.Coupon;
import com.planno.store.pricing.domain.DiscountType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        UUID storeId,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Integer maxUses,
        int currentUses,
        boolean active,
        boolean freeShipping,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getStore().getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMinOrderAmount(),
                coupon.getMaxDiscountAmount(),
                coupon.getStartsAt(),
                coupon.getEndsAt(),
                coupon.getMaxUses(),
                coupon.getCurrentUses(),
                coupon.isActive(),
                coupon.isFreeShipping(),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt()
        );
    }
}
