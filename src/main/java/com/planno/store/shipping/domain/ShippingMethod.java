package com.planno.store.shipping.domain;

import com.planno.store.core.auditing.AuditableEntity;
import com.planno.store.store.domain.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "shipping_methods")
public class ShippingMethod extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ShippingMethodType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int estimatedDays;

    @Column(nullable = false)
    private boolean active;

    protected ShippingMethod() {
    }

    private ShippingMethod(Store store, String name, ShippingMethodType type, BigDecimal price, int estimatedDays, boolean active) {
        this.store = store;
        this.name = name.trim();
        this.type = type;
        this.price = scale(price);
        this.estimatedDays = estimatedDays;
        this.active = active;
    }

    public static ShippingMethod create(Store store, String name, ShippingMethodType type, BigDecimal price, int estimatedDays, boolean active) {
        return new ShippingMethod(store, name, type, price, estimatedDays, active);
    }

    public void update(String name, ShippingMethodType type, BigDecimal price, int estimatedDays, boolean active) {
        this.name = name.trim();
        this.type = type;
        this.price = scale(price);
        this.estimatedDays = estimatedDays;
        this.active = active;
    }

    public Store getStore() {
        return store;
    }

    public String getName() {
        return name;
    }

    public ShippingMethodType getType() {
        return type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getEstimatedDays() {
        return estimatedDays;
    }

    public boolean isActive() {
        return active;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

