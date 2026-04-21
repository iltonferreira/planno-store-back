package com.planno.store.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 160) String recipientName,
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 50) String number,
        @Size(max = 255) String complement,
        @NotBlank @Size(max = 120) String neighborhood,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Size(max = 80) String state,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 80) String country
) {
}

