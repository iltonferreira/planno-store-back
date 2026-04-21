package com.planno.store.cart.dto;

public record CreateCartResponse(
        String cartToken,
        CartResponse cart
) {
}

