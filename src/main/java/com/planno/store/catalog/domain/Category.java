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

@Entity
@Table(name = "categories")
public class Category extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 150)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CategoryStatus status;

    @Column(length = 1000)
    private String description;

    protected Category() {
    }

    private Category(Store store, String name, String slug, String description, CategoryStatus status) {
        this.store = store;
        this.name = name.trim();
        this.slug = StringNormalizer.slugify(slug);
        this.description = blankToNull(description);
        this.status = status;
    }

    public static Category create(Store store, String name, String slug, String description, CategoryStatus status) {
        return new Category(store, name, slug, description, status);
    }

    public void update(String name, String slug, String description, CategoryStatus status) {
        this.name = name.trim();
        this.slug = StringNormalizer.slugify(slug);
        this.description = blankToNull(description);
        this.status = status;
    }

    public Store getStore() {
        return store;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public CategoryStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

