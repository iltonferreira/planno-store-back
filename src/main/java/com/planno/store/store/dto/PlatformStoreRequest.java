package com.planno.store.store.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PlatformStoreRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 150) String slug,
        @NotBlank @Email @Size(max = 255) String contactEmail,
        @NotBlank @Size(max = 10) String currency,
        @NotBlank @Size(max = 80) String timezone,
        @Size(max = 1000) String description,
        @Size(max = 2048) String logoUrl,
        @Size(max = 2048) String bannerUrl,
        @Size(max = 30) String primaryColor,
        @Size(max = 30) String secondaryColor,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionRate
) {
}
