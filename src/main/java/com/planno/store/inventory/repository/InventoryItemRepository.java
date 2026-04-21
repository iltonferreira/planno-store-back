package com.planno.store.inventory.repository;

import com.planno.store.inventory.domain.InventoryItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    @EntityGraph(attributePaths = {"product", "product.category", "product.store"})
    Optional<InventoryItem> findByProductIdAndProductStoreId(UUID productId, UUID storeId);
}

