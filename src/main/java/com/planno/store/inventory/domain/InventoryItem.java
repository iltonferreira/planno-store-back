package com.planno.store.inventory.domain;

import com.planno.store.catalog.domain.Product;
import com.planno.store.core.auditing.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_items")
public class InventoryItem extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    private long quantityAvailable;

    @Column(nullable = false)
    private long quantityReserved;

    protected InventoryItem() {
    }

    private InventoryItem(Product product, long quantityAvailable, long quantityReserved) {
        this.product = product;
        setQuantities(quantityAvailable, quantityReserved);
    }

    public static InventoryItem create(Product product, long quantityAvailable, long quantityReserved) {
        return new InventoryItem(product, quantityAvailable, quantityReserved);
    }

    public void setQuantities(long quantityAvailable, long quantityReserved) {
        if (quantityAvailable < 0 || quantityReserved < 0) {
            throw new IllegalArgumentException("Inventory quantities cannot be negative");
        }
        if (quantityReserved > quantityAvailable) {
            throw new IllegalArgumentException("Reserved quantity cannot exceed available quantity");
        }
        this.quantityAvailable = quantityAvailable;
        this.quantityReserved = quantityReserved;
    }

    public void reserve(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reserved quantity must be greater than zero");
        }
        if (quantityReserved + quantity > quantityAvailable) {
            throw new IllegalArgumentException("Reserved quantity cannot exceed available quantity");
        }
        this.quantityReserved += quantity;
    }

    public void releaseReserved(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Released quantity must be greater than zero");
        }
        if (quantity > quantityReserved) {
            throw new IllegalArgumentException("Cannot release more reserved quantity than currently reserved");
        }
        this.quantityReserved -= quantity;
    }

    public void commitReserved(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Committed quantity must be greater than zero");
        }
        if (quantity > quantityReserved) {
            throw new IllegalArgumentException("Cannot commit more reserved quantity than currently reserved");
        }
        if (quantity > quantityAvailable) {
            throw new IllegalArgumentException("Committed quantity cannot exceed available quantity");
        }
        this.quantityReserved -= quantity;
        this.quantityAvailable -= quantity;
    }

    public Product getProduct() {
        return product;
    }

    public long getQuantityAvailable() {
        return quantityAvailable;
    }

    public long getQuantityReserved() {
        return quantityReserved;
    }
}
