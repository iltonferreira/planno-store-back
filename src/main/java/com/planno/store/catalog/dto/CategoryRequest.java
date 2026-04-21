package com.planno.store.catalog.dto;

import com.planno.store.catalog.domain.CategoryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 150) String slug,
        @Size(max = 1000) String description,
        @NotNull CategoryStatus status
) {
}

