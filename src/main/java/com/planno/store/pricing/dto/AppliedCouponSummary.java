package com.planno.store.pricing.dto;

import java.util.UUID;

public record AppliedCouponSummary(
        UUID couponId,
        String couponCode,
        boolean freeShipping
) {
}
