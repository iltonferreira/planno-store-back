package com.planno.store.shipping.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ShippingQuoteRequest(
        @NotNull UUID cartId,
        @NotNull @Valid AddressRequest address
) {
}

