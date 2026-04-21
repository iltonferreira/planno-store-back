package com.planno.store.inventory.dto;

import jakarta.validation.constraints.Min;

public record InventoryUpdateRequest(
        @Min(0) long quantityAvailable,
        @Min(0) long quantityReserved
) {
}

