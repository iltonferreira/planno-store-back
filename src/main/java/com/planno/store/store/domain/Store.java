package com.planno.store.store.domain;

import com.planno.store.core.auditing.AuditableEntity;
import com.planno.store.core.util.StringNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "stores")
public class Store extends AuditableEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StoreStatus status;

    @Column(nullable = false, length = 255)
    private String contactEmail;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, length = 80)
    private String timezone;

    @Column(length = 2048)
    private String logoUrl;

    @Column(length = 2048)
    private String bannerUrl;

    @Column(length = 30)
    private String primaryColor;

    @Column(length = 30)
    private String secondaryColor;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    protected Store() {
    }

    private Store(
            String name,
            String slug,
            String contactEmail,
            String currency,
            String timezone,
            String description,
            String logoUrl,
            String bannerUrl,
            String primaryColor,
            String secondaryColor,
            BigDecimal commissionRate
    ) {
        this.name = name.trim();
        this.slug = StringNormalizer.slugify(slug);
        this.status = StoreStatus.ACTIVE;
        this.contactEmail = contactEmail.trim().toLowerCase();
        this.currency = currency == null || currency.isBlank() ? "BRL" : currency.trim().toUpperCase();
        this.timezone = timezone == null || timezone.isBlank() ? "America/Sao_Paulo" : timezone.trim();
        this.description = blankToNull(description);
        this.logoUrl = blankToNull(logoUrl);
        this.bannerUrl = blankToNull(bannerUrl);
        this.primaryColor = blankToNull(primaryColor);
        this.secondaryColor = blankToNull(secondaryColor);
        this.commissionRate = scaleCommissionRate(commissionRate);
    }

    public static Store create(
            String name,
            String slug,
            String contactEmail,
            String currency,
            String timezone,
            String description,
            String logoUrl,
            String bannerUrl,
            String primaryColor,
            String secondaryColor,
            BigDecimal commissionRate
    ) {
        return new Store(name, slug, contactEmail, currency, timezone, description, logoUrl, bannerUrl, primaryColor, secondaryColor,
                commissionRate);
    }

    public void update(
            String name,
            String contactEmail,
            String currency,
            String timezone,
            String description,
            String logoUrl,
            String bannerUrl,
            String primaryColor,
            String secondaryColor,
            BigDecimal commissionRate
    ) {
        this.name = name.trim();
        this.contactEmail = contactEmail.trim().toLowerCase();
        this.currency = currency.trim().toUpperCase();
        this.timezone = timezone.trim();
        this.description = blankToNull(description);
        this.logoUrl = blankToNull(logoUrl);
        this.bannerUrl = blankToNull(bannerUrl);
        this.primaryColor = blankToNull(primaryColor);
        this.secondaryColor = blankToNull(secondaryColor);
        this.commissionRate = scaleCommissionRate(commissionRate);
    }

    public void updateBranding(String logoUrl, String bannerUrl) {
        this.logoUrl = blankToNull(logoUrl);
        this.bannerUrl = blankToNull(bannerUrl);
    }

    public void renameSlug(String slug) {
        this.slug = StringNormalizer.slugify(slug);
    }

    public void deactivate() {
        this.status = StoreStatus.INACTIVE;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public StoreStatus getStatus() {
        return status;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getCurrency() {
        return currency;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public enum StoreStatus {
        ACTIVE,
        INACTIVE
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal scaleCommissionRate(BigDecimal value) {
        BigDecimal rate = value == null ? new BigDecimal("2.50") : value;
        return rate.setScale(2, RoundingMode.HALF_UP);
    }
}
