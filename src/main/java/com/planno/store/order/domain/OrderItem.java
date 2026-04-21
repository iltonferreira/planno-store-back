package com.planno.store.order.domain;

import com.planno.store.catalog.domain.Product;
import com.planno.store.core.auditing.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 180)
    private String productNameSnapshot;

    @Column(nullable = false, length = 180)
    private String productSlugSnapshot;

    @Column(nullable = false, length = 80)
    private String skuSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPriceSnapshot;

    protected OrderItem() {
    }

    private OrderItem(
            Order order,
            Product product,
            String productNameSnapshot,
            String productSlugSnapshot,
            String skuSnapshot,
            int quantity,
            BigDecimal unitPriceSnapshot,
            BigDecimal totalPriceSnapshot
    ) {
        this.order = order;
        this.product = product;
        this.productNameSnapshot = productNameSnapshot;
        this.productSlugSnapshot = productSlugSnapshot;
        this.skuSnapshot = skuSnapshot;
        this.quantity = quantity;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.totalPriceSnapshot = totalPriceSnapshot;
    }

    public static OrderItem create(
            Order order,
            Product product,
            String productNameSnapshot,
            String productSlugSnapshot,
            String skuSnapshot,
            int quantity,
            BigDecimal unitPriceSnapshot,
            BigDecimal totalPriceSnapshot
    ) {
        return new OrderItem(order, product, productNameSnapshot, productSlugSnapshot, skuSnapshot, quantity, unitPriceSnapshot, totalPriceSnapshot);
    }

    public Product getProduct() {
        return product;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public String getProductSlugSnapshot() {
        return productSlugSnapshot;
    }

    public String getSkuSnapshot() {
        return skuSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public BigDecimal getTotalPriceSnapshot() {
        return totalPriceSnapshot;
    }
}

