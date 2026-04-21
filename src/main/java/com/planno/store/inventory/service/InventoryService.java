package com.planno.store.inventory.service;

import com.planno.store.catalog.service.ProductService;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.inventory.domain.InventoryItem;
import com.planno.store.inventory.dto.InventoryResponse;
import com.planno.store.inventory.dto.InventoryUpdateRequest;
import com.planno.store.inventory.repository.InventoryItemRepository;
import com.planno.store.store.service.CurrentStoreProvider;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final ProductService productService;
    private final CurrentStoreProvider currentStoreProvider;

    public InventoryService(
            InventoryItemRepository inventoryItemRepository,
            ProductService productService,
            CurrentStoreProvider currentStoreProvider
    ) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.productService = productService;
        this.currentStoreProvider = currentStoreProvider;
    }

    @Transactional(readOnly = true)
    public InventoryResponse getByProductId(UUID productId) {
        return InventoryResponse.from(getOrCreateInventory(productId));
    }

    @Transactional
    public InventoryResponse update(UUID productId, InventoryUpdateRequest request) {
        if (request.quantityReserved() > request.quantityAvailable()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Reserved quantity cannot exceed available quantity");
        }

        InventoryItem inventoryItem = getOrCreateInventory(productId);
        inventoryItem.setQuantities(request.quantityAvailable(), request.quantityReserved());
        return InventoryResponse.from(inventoryItem);
    }

    @Transactional(readOnly = true)
    public void assertAvailableQuantity(UUID productId, int requestedQuantity) {
        if (requestedQuantity <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Quantity must be greater than zero");
        }

        InventoryItem inventoryItem = getOrCreateInventory(productId);
        long availableToSell = inventoryItem.getQuantityAvailable() - inventoryItem.getQuantityReserved();
        if (requestedQuantity > availableToSell) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Insufficient inventory for the requested quantity");
        }
    }

    @Transactional
    public void reserveQuantity(UUID productId, int quantity) {
        try {
            getOrCreateInventory(productId).reserve(quantity);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @Transactional
    public void releaseReservedQuantity(UUID productId, int quantity) {
        try {
            getOrCreateInventory(productId).releaseReserved(quantity);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @Transactional
    public void commitReservedQuantity(UUID storeId, UUID productId, int quantity) {
        try {
            getOrCreateInventory(storeId, productId).commitReserved(quantity);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @Transactional
    public void restoreCommittedQuantity(UUID storeId, UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Quantity must be greater than zero");
        }

        InventoryItem inventoryItem = getOrCreateInventory(storeId, productId);
        inventoryItem.setQuantities(inventoryItem.getQuantityAvailable() + quantity, inventoryItem.getQuantityReserved());
    }

    @Transactional
    public void releaseReservedQuantity(UUID storeId, UUID productId, int quantity) {
        try {
            getOrCreateInventory(storeId, productId).releaseReserved(quantity);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private InventoryItem getOrCreateInventory(UUID productId) {
        return getOrCreateInventory(currentStoreProvider.getCurrentStoreId(), productId);
    }

    private InventoryItem getOrCreateInventory(UUID storeId, UUID productId) {
        return inventoryItemRepository.findByProductIdAndProductStoreId(productId, storeId)
                .orElseGet(() -> inventoryItemRepository.save(InventoryItem.create(
                        productService.getProductEntity(storeId, productId),
                        0,
                        0
                )));
    }
}
