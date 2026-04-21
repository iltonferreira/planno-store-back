package com.planno.store.inventory.dto;

import com.planno.store.inventory.domain.InventoryItem;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryResponse(
        UUID id,
        UUID productId,
        String productName,
        String productSku,
        long quantityAvailable,
        long quantityReserved,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static InventoryResponse from(InventoryItem inventoryItem) {
        return new InventoryResponse(
                inventoryItem.getId(),
                inventoryItem.getProduct().getId(),
                inventoryItem.getProduct().getName(),
                inventoryItem.getProduct().getSku(),
                inventoryItem.getQuantityAvailable(),
                inventoryItem.getQuantityReserved(),
                inventoryItem.getCreatedAt(),
                inventoryItem.getUpdatedAt()
        );
    }
}

