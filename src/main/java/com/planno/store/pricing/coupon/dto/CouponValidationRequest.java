package com.planno.store.pricing.coupon.dto;

import jakarta.validation.constraints.NotBlank;

public record CouponValidationRequest(
        @NotBlank String code
) {
}
