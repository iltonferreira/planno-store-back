package com.planno.store.order.domain;

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
@Table(name = "orders")
public class Order extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "coupon_id")
    private UUID couponId;

    @Column(name = "coupon_code_snapshot", length = 80)
    private String couponCodeSnapshot;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal commissionAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal sellerNetAmount;

    @Column(nullable = false, length = 160)
    private String customerName;

    @Column(nullable = false, length = 255)
    private String customerEmail;

    @Column(length = 40)
    private String customerDocument;

    @Column(name = "shipping_method_id", nullable = false)
    private UUID shippingMethodId;

    @Column(nullable = false, length = 120)
    private String shippingMethodName;

    @Column(nullable = false, length = 40)
    private String shippingMethodType;

    @Column(nullable = false)
    private int shippingEstimatedDays;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    protected Order() {
    }

    private Order(
            Store store,
            UUID cartId,
            String code,
            OrderStatus status,
            BigDecimal subtotalAmount,
            BigDecimal shippingAmount,
            BigDecimal discountAmount,
            UUID couponId,
            String couponCodeSnapshot,
            BigDecimal totalAmount,
            BigDecimal commissionRate,
            BigDecimal commissionAmount,
            BigDecimal sellerNetAmount,
            String customerName,
            String customerEmail,
            String customerDocument,
            UUID shippingMethodId,
            String shippingMethodName,
            String shippingMethodType,
            int shippingEstimatedDays
    ) {
        this.store = store;
        this.cartId = cartId;
        this.code = code;
        this.status = status;
        this.subtotalAmount = scale(subtotalAmount);
        this.shippingAmount = scale(shippingAmount);
        this.discountAmount = scale(discountAmount);
        this.couponId = couponId;
        this.couponCodeSnapshot = blankToNull(couponCodeSnapshot);
        this.totalAmount = scale(totalAmount);
        this.commissionRate = scale(commissionRate);
        this.commissionAmount = scale(commissionAmount);
        this.sellerNetAmount = scale(sellerNetAmount);
        this.customerName = customerName.trim();
        this.customerEmail = customerEmail.trim().toLowerCase();
        this.customerDocument = blankToNull(customerDocument);
        this.shippingMethodId = shippingMethodId;
        this.shippingMethodName = shippingMethodName;
        this.shippingMethodType = shippingMethodType;
        this.shippingEstimatedDays = shippingEstimatedDays;
    }

    public static Order create(
            Store store,
            UUID cartId,
            String code,
            OrderStatus status,
            BigDecimal subtotalAmount,
            BigDecimal shippingAmount,
            BigDecimal discountAmount,
            UUID couponId,
            String couponCodeSnapshot,
            BigDecimal totalAmount,
            BigDecimal commissionRate,
            BigDecimal commissionAmount,
            BigDecimal sellerNetAmount,
            String customerName,
            String customerEmail,
            String customerDocument,
            UUID shippingMethodId,
            String shippingMethodName,
            String shippingMethodType,
            int shippingEstimatedDays
    ) {
        return new Order(
                store,
                cartId,
                code,
                status,
                subtotalAmount,
                shippingAmount,
                discountAmount,
                couponId,
                couponCodeSnapshot,
                totalAmount,
                commissionRate,
                commissionAmount,
                sellerNetAmount,
                customerName,
                customerEmail,
                customerDocument,
                shippingMethodId,
                shippingMethodName,
                shippingMethodType,
                shippingEstimatedDays
        );
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    public void addHistory(OrderStatusHistory history) {
        statusHistory.add(history);
    }

    public void updateStatus(OrderStatus status) {
        this.status = status;
    }

    public Store getStore() {
        return store;
    }

    public UUID getCartId() {
        return cartId;
    }

    public String getCode() {
        return code;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public BigDecimal getShippingAmount() {
        return shippingAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public UUID getCouponId() {
        return couponId;
    }

    public String getCouponCodeSnapshot() {
        return couponCodeSnapshot;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public BigDecimal getCommissionAmount() {
        return commissionAmount;
    }

    public BigDecimal getSellerNetAmount() {
        return sellerNetAmount;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getCustomerDocument() {
        return customerDocument;
    }

    public UUID getShippingMethodId() {
        return shippingMethodId;
    }

    public String getShippingMethodName() {
        return shippingMethodName;
    }

    public String getShippingMethodType() {
        return shippingMethodType;
    }

    public int getShippingEstimatedDays() {
        return shippingEstimatedDays;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public List<OrderStatusHistory> getStatusHistory() {
        return statusHistory;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
