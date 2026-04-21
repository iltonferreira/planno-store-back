package com.planno.store.pricing.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record PriceCalculation(
        UUID productId,
        int quantity,
        BigDecimal basePrice,
        BigDecimal finalPrice,
        BigDecimal discountAmount,
        AppliedPromotionSummary appliedPromotion,
        AppliedCouponSummary appliedCoupon,
        BigDecimal lineBasePrice,
        BigDecimal lineFinalPrice,
        BigDecimal lineDiscountAmount
) {

    public PriceCalculation {
        basePrice = scale(basePrice);
        finalPrice = scale(finalPrice);
        discountAmount = scale(discountAmount);
        lineBasePrice = scale(lineBasePrice);
        lineFinalPrice = scale(lineFinalPrice);
        lineDiscountAmount = scale(lineDiscountAmount);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
