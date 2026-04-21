package com.planno.store.cart.dto;

import com.planno.store.cart.domain.Cart;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID id,
        UUID storeId,
        String guestToken,
        String status,
        String couponCode,
        BigDecimal subtotalBeforeDiscount,
        BigDecimal discountAmount,
        BigDecimal itemDiscountAmount,
        BigDecimal couponDiscountAmount,
        boolean freeShippingApplied,
        BigDecimal subtotal,
        BigDecimal total,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<CartItemResponse> items
) {
    public static CartResponse from(Cart cart) {
        return new CartResponse(
                cart.getId(),
                cart.getStore().getId(),
                cart.getGuestToken(),
                cart.getStatus().name(),
                cart.getCouponCodeSnapshot(),
                cart.getSubtotalBeforeDiscount(),
                cart.getDiscountAmount(),
                cart.getItemDiscountAmount(),
                cart.getCouponDiscountAmount(),
                cart.isFreeShippingApplied(),
                cart.getSubtotal(),
                cart.getTotal(),
                cart.getCreatedAt(),
                cart.getUpdatedAt(),
                cart.getItems().stream().map(CartItemResponse::from).toList()
        );
    }
}
