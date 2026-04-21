package com.planno.store.pricing.promotion.domain;

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
import java.util.UUID;

@Entity
@Table(name = "promotions")
public class Promotion extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PromotionType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private DiscountType discountType;

    @Column(precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AppliesToType appliesToType;

    @Column
    private UUID appliesToId;

    @Column(precision = 19, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(nullable = false)
    private OffsetDateTime startsAt;

    @Column(nullable = false)
    private OffsetDateTime endsAt;

    @Column(nullable = false)
    private boolean active;

    protected Promotion() {
    }

    private Promotion(
            Store store,
            String name,
            String description,
            PromotionType type,
            DiscountType discountType,
            BigDecimal discountValue,
            AppliesToType appliesToType,
            UUID appliesToId,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            boolean active
    ) {
        this.store = store;
        this.name = name.trim();
        this.description = blankToNull(description);
        this.type = type;
        this.discountType = discountType;
        this.discountValue = scaleNullable(discountValue);
        this.appliesToType = appliesToType;
        this.appliesToId = appliesToId;
        this.minOrderAmount = scaleNullable(minOrderAmount);
        this.maxDiscountAmount = scaleNullable(maxDiscountAmount);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.active = active;
    }

    public static Promotion create(
            Store store,
            String name,
            String description,
            PromotionType type,
            DiscountType discountType,
            BigDecimal discountValue,
            AppliesToType appliesToType,
            UUID appliesToId,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            boolean active
    ) {
        return new Promotion(
                store,
                name,
                description,
                type,
                discountType,
                discountValue,
                appliesToType,
                appliesToId,
                minOrderAmount,
                maxDiscountAmount,
                startsAt,
                endsAt,
                active
        );
    }

    public void update(
            String name,
            String description,
            PromotionType type,
            DiscountType discountType,
            BigDecimal discountValue,
            AppliesToType appliesToType,
            UUID appliesToId,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            boolean active
    ) {
        this.name = name.trim();
        this.description = blankToNull(description);
        this.type = type;
        this.discountType = discountType;
        this.discountValue = scaleNullable(discountValue);
        this.appliesToType = appliesToType;
        this.appliesToId = appliesToId;
        this.minOrderAmount = scaleNullable(minOrderAmount);
        this.maxDiscountAmount = scaleNullable(maxDiscountAmount);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.active = active;
    }

    public Store getStore() {
        return store;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public PromotionType getType() {
        return type;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public AppliesToType getAppliesToType() {
        return appliesToType;
    }

    public UUID getAppliesToId() {
        return appliesToId;
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

    public boolean isActive() {
        return active;
    }

    public boolean isActiveAt(OffsetDateTime now) {
        return active && !now.isBefore(startsAt) && !now.isAfter(endsAt);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal scaleNullable(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
