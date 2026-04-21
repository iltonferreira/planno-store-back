package com.planno.store.catalog.dto;

import com.planno.store.catalog.domain.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        UUID categoryId,
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 180) String slug,
        @NotBlank @Size(max = 80) String sku,
        @Size(max = 4000) String description,
        @NotNull ProductStatus status,
        @NotNull @DecimalMin(value = "0.00") BigDecimal basePrice,
        @Size(max = 2048) String mainImageUrl
) {
}

