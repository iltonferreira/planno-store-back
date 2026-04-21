package com.planno.store.cart.domain;

import com.planno.store.catalog.domain.Product;
import com.planno.store.core.auditing.AuditableEntity;
import com.planno.store.pricing.dto.PriceCalculation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "cart_items")
public class CartItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitBasePriceSnapshot;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitFinalPriceSnapshot;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitDiscountAmountSnapshot;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalBasePriceSnapshot;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalFinalPriceSnapshot;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDiscountAmountSnapshot;

    protected CartItem() {
    }

    private CartItem(Cart cart, Product product, int quantity, PriceCalculation priceCalculation) {
        this.cart = cart;
        this.product = product;
        applyPricing(quantity, priceCalculation);
    }

    public static CartItem create(Cart cart, Product product, int quantity, PriceCalculation priceCalculation) {
        return new CartItem(cart, product, quantity, priceCalculation);
    }

    public void applyPricing(int quantity, PriceCalculation priceCalculation) {
        this.quantity = quantity;
        this.unitBasePriceSnapshot = priceCalculation.basePrice();
        this.unitFinalPriceSnapshot = priceCalculation.finalPrice();
        this.unitDiscountAmountSnapshot = priceCalculation.discountAmount();
        this.totalBasePriceSnapshot = priceCalculation.lineBasePrice();
        this.totalFinalPriceSnapshot = priceCalculation.lineFinalPrice();
        this.totalDiscountAmountSnapshot = priceCalculation.lineDiscountAmount();
    }

    public Cart getCart() {
        return cart;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitBasePriceSnapshot() {
        return unitBasePriceSnapshot;
    }

    public BigDecimal getUnitFinalPriceSnapshot() {
        return unitFinalPriceSnapshot;
    }

    public BigDecimal getUnitDiscountAmountSnapshot() {
        return unitDiscountAmountSnapshot;
    }

    public BigDecimal getTotalBasePriceSnapshot() {
        return totalBasePriceSnapshot;
    }

    public BigDecimal getTotalFinalPriceSnapshot() {
        return totalFinalPriceSnapshot;
    }

    public BigDecimal getTotalDiscountAmountSnapshot() {
        return totalDiscountAmountSnapshot;
    }
}

