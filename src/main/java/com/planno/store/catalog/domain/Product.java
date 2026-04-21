package com.planno.store.catalog.domain;

import com.planno.store.core.auditing.AuditableEntity;
import com.planno.store.core.util.StringNormalizer;
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

@Entity
@Table(name = "products")
public class Product extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 180)
    private String slug;

    @Column(nullable = false, length = 80)
    private String sku;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal basePrice;

    @Column(length = 2048)
    private String mainImageUrl;

    protected Product() {
    }

    private Product(
            Store store,
            Category category,
            String name,
            String slug,
            String sku,
            String description,
            ProductStatus status,
            BigDecimal basePrice,
            String mainImageUrl
    ) {
        this.store = store;
        this.category = category;
        this.name = name.trim();
        this.slug = StringNormalizer.slugify(slug);
        this.sku = sku.trim().toUpperCase();
        this.description = blankToNull(description);
        this.status = status;
        this.basePrice = basePrice;
        this.mainImageUrl = blankToNull(mainImageUrl);
    }

    public static Product create(
            Store store,
            Category category,
            String name,
            String slug,
            String sku,
            String description,
            ProductStatus status,
            BigDecimal basePrice,
            String mainImageUrl
    ) {
        return new Product(store, category, name, slug, sku, description, status, basePrice, mainImageUrl);
    }

    public void update(
            Category category,
            String name,
            String slug,
            String sku,
            String description,
            ProductStatus status,
            BigDecimal basePrice,
            String mainImageUrl
    ) {
        this.category = category;
        this.name = name.trim();
        this.slug = StringNormalizer.slugify(slug);
        this.sku = sku.trim().toUpperCase();
        this.description = blankToNull(description);
        this.status = status;
        this.basePrice = basePrice;
        this.mainImageUrl = blankToNull(mainImageUrl);
    }

    public Store getStore() {
        return store;
    }

    public Category getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public String getMainImageUrl() {
        return mainImageUrl;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

