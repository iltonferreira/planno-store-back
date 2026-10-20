package com.planno.store;

import com.planno.store.support.AbstractIntegrationTest;
import com.planno.store.catalog.domain.Product;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.inventory.domain.InventoryItem;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class InventoryConcurrencyTests extends AbstractIntegrationTest {
    @Autowired EntityManagerFactory entities;
    @Test void concurrentReservationsCannotOverwriteEachOther() {
        var store = createStore("stock-concurrency");
        var product = productRepository.saveAndFlush(Product.create(store, null, "Product", "product", "SKU-LOCK", "", ProductStatus.ACTIVE, BigDecimal.TEN, null));
        var inventory = InventoryItem.create(product, 10, 0);
        inventory = inventoryItemRepository.saveAndFlush(inventory);
        try (var first = entities.createEntityManager(); var second = entities.createEntityManager()) {
            first.getTransaction().begin(); second.getTransaction().begin();
            var firstCopy = first.find(InventoryItem.class, inventory.getId());
            var secondCopy = second.find(InventoryItem.class, inventory.getId());
            firstCopy.reserve(8); secondCopy.reserve(8);
            first.getTransaction().commit();
            assertThrows(jakarta.persistence.RollbackException.class, () -> second.getTransaction().commit());
            if (second.getTransaction().isActive()) second.getTransaction().rollback();
        }
        assertEquals(8, inventoryItemRepository.findById(inventory.getId()).orElseThrow().getQuantityReserved());
    }
}
