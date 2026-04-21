package com.planno.store.pricing.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CartPricingSummary(
        BigDecimal subtotalBeforeDiscount,
        BigDecimal subtotalAfterItemPromotions,
        BigDecimal subtotalAfterDiscount,
        BigDecimal itemDiscountAmount,
        BigDecimal orderPromotionDiscountAmount,
        BigDecimal couponDiscountAmount,
        BigDecimal shippingDiscountAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        boolean freeShippingApplied,
        AppliedPromotionSummary appliedOrderPromotion,
        AppliedCouponSummary appliedCoupon
) {

    public CartPricingSummary {
        subtotalBeforeDiscount = scale(subtotalBeforeDiscount);
        subtotalAfterItemPromotions = scale(subtotalAfterItemPromotions);
        subtotalAfterDiscount = scale(subtotalAfterDiscount);
        itemDiscountAmount = scale(itemDiscountAmount);
        orderPromotionDiscountAmount = scale(orderPromotionDiscountAmount);
        couponDiscountAmount = scale(couponDiscountAmount);
        shippingDiscountAmount = scale(shippingDiscountAmount);
        discountAmount = scale(discountAmount);
        totalAmount = scale(totalAmount);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
