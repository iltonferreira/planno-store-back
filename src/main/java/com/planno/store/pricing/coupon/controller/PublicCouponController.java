package com.planno.store.pricing.coupon.controller;

import com.planno.store.cart.service.CartService;
import com.planno.store.pricing.coupon.dto.CouponValidationRequest;
import com.planno.store.pricing.coupon.dto.CouponValidationResponse;
import com.planno.store.pricing.coupon.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/coupons")
public class PublicCouponController {

    private final CouponService couponService;
    private final CartService cartService;

    public PublicCouponController(CouponService couponService, CartService cartService) {
        this.couponService = couponService;
        this.cartService = cartService;
    }

    @PostMapping("/validate")
    public ResponseEntity<CouponValidationResponse> validate(@Valid @RequestBody CouponValidationRequest request) {
        return ResponseEntity.ok(couponService.validateForCurrentStore(request.code(), cartService.currentSubtotalAfterAutomaticPromotions()));
    }
}
