package com.planno.store.pricing.coupon.domain;

import com.planno.store.core.auditing.AuditableEntity;
import com.planno.store.pricing.domain.DiscountType;
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
import java.time.OffsetDateTime;

@Entity
@Table(name = "coupons")
public class Coupon extends AuditableEntity {

    @jakarta.persistence.Version
    @jakarta.persistence.Column(nullable = false)
    private long version;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private DiscountType discountType;

    @Column(precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Column(precision = 19, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(nullable = false)
    private OffsetDateTime startsAt;

    @Column(nullable = false)
    private OffsetDateTime endsAt;

    @Column
    private Integer maxUses;

    @Column(nullable = false)
    private int currentUses;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean freeShipping;

    protected Coupon() {
    }

    private Coupon(
            Store store,
            String code,
            String description,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Integer maxUses,
            int currentUses,
            boolean active,
            boolean freeShipping
    ) {
        this.store = store;
        this.code = code.trim().toUpperCase();
        this.description = blankToNull(description);
        this.discountType = discountType;
        this.discountValue = scaleNullable(discountValue);
        this.minOrderAmount = scaleNullable(minOrderAmount);
        this.maxDiscountAmount = scaleNullable(maxDiscountAmount);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.maxUses = maxUses;
        this.currentUses = currentUses;
        this.active = active;
        this.freeShipping = freeShipping;
    }

    public static Coupon create(
            Store store,
            String code,
            String description,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Integer maxUses,
            boolean active,
            boolean freeShipping
    ) {
        return new Coupon(
                store,
                code,
                description,
                discountType,
                discountValue,
                minOrderAmount,
                maxDiscountAmount,
                startsAt,
                endsAt,
                maxUses,
                0,
                active,
                freeShipping
        );
    }

    public void update(
            String code,
            String description,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Integer maxUses,
            boolean active,
            boolean freeShipping
    ) {
        this.code = code.trim().toUpperCase();
        this.description = blankToNull(description);
        this.discountType = discountType;
        this.discountValue = scaleNullable(discountValue);
        this.minOrderAmount = scaleNullable(minOrderAmount);
        this.maxDiscountAmount = scaleNullable(maxDiscountAmount);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.maxUses = maxUses;
        this.active = active;
        this.freeShipping = freeShipping;
    }

    public void incrementUsage() {
        this.currentUses++;
    }

    public void decrementUsage() {
        if (this.currentUses > 0) {
            this.currentUses--;
        }
    }

    public boolean isActiveAt(OffsetDateTime now) {
        return active && !now.isBefore(startsAt) && !now.isAfter(endsAt);
    }

    public boolean hasAvailableUses() {
        return maxUses == null || currentUses < maxUses;
    }

    public Store getStore() {
        return store;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public BigDecimal getMinOrderAmount() {
        return minOrderAmount;
    }

    public BigDecimal getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public int getCurrentUses() {
        return currentUses;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isFreeShipping() {
        return freeShipping;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal scaleNullable(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
