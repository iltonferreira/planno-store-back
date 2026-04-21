package com.planno.store.order.dto;

import com.planno.store.shipping.dto.AddressRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID cartId,
        @NotNull UUID shippingMethodId,
        @NotBlank @Size(max = 160) String customerName,
        @NotBlank @Email @Size(max = 255) String customerEmail,
        @Size(max = 40) String customerDocument,
        @NotNull @Valid AddressRequest address
) {
}

