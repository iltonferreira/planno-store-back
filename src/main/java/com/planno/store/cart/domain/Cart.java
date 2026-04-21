package com.planno.store.cart.domain;

import com.planno.store.core.auditing.AuditableEntity;
import com.planno.store.store.domain.Store;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class Cart extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String guestToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CartStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 19, scale = 2)
    private BigDecimal subtotalBeforeDiscount;

    @Column(precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal itemDiscountAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal couponDiscountAmount;

    @Column
    private UUID couponId;

    @Column(length = 80)
    private String couponCodeSnapshot;

    @Column(nullable = false)
    private boolean freeShippingApplied;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    private Cart(Store store, UUID userId, String guestToken) {
        this.store = store;
        this.userId = userId;
        this.guestToken = guestToken;
        this.status = CartStatus.ACTIVE;
        this.subtotalBeforeDiscount = zero();
        this.discountAmount = zero();
        this.itemDiscountAmount = zero();
        this.couponDiscountAmount = zero();
        this.subtotal = zero();
        this.total = zero();
        this.freeShippingApplied = false;
    }

    public static Cart create(Store store, UUID userId, String guestToken) {
        return new Cart(store, userId, guestToken);
    }

    public void addItem(CartItem item) {
        items.add(item);
    }

    public void removeItem(CartItem item) {
        items.remove(item);
    }

    public void clearItems() {
        items.clear();
        resetTotals();
    }

    public void markCheckedOut() {
        this.status = CartStatus.CHECKED_OUT;
    }

    public void applyPricing(
            BigDecimal subtotalBeforeDiscount,
            BigDecimal itemDiscountAmount,
            BigDecimal couponDiscountAmount,
            BigDecimal discountAmount,
            BigDecimal subtotal,
            BigDecimal total,
            boolean freeShippingApplied
    ) {
        this.subtotalBeforeDiscount = scale(subtotalBeforeDiscount);
        this.itemDiscountAmount = scale(itemDiscountAmount);
        this.couponDiscountAmount = scale(couponDiscountAmount);
        this.discountAmount = scale(discountAmount);
        this.subtotal = scale(subtotal);
        this.total = scale(total);
        this.freeShippingApplied = freeShippingApplied;
    }

    public void applyCoupon(UUID couponId, String couponCodeSnapshot) {
        this.couponId = couponId;
        this.couponCodeSnapshot = couponCodeSnapshot;
    }

    public void removeCoupon() {
        this.couponId = null;
        this.couponCodeSnapshot = null;
        this.freeShippingApplied = false;
    }

    public void resetTotals() {
        applyPricing(zero(), zero(), zero(), zero(), zero(), zero(), false);
    }

    public Store getStore() {
        return store;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getGuestToken() {
        return guestToken;
    }

    public CartStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getSubtotalBeforeDiscount() {
        return subtotalBeforeDiscount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getItemDiscountAmount() {
        return itemDiscountAmount;
    }

    public BigDecimal getCouponDiscountAmount() {
        return couponDiscountAmount;
    }

    public UUID getCouponId() {
        return couponId;
    }

    public String getCouponCodeSnapshot() {
        return couponCodeSnapshot;
    }

    public boolean isFreeShippingApplied() {
        return freeShippingApplied;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public List<CartItem> getItems() {
        return items;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
