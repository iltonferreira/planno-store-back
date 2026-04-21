package com.planno.store.payment.account.domain;

import com.planno.store.core.auditing.AuditableEntity;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.store.domain.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "payment_provider_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_provider_accounts_store_provider", columnNames = {"store_id", "provider"})
        }
)
public class PaymentProviderAccount extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProvider provider;

    @Column(nullable = false, length = 80)
    private String providerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProviderConnectionStatus connectionStatus;

    @Column(length = 4096)
    private String accessTokenEncrypted;

    @Column(length = 4096)
    private String refreshTokenEncrypted;

    @Column
    private OffsetDateTime tokenExpiresAt;

    @Column(length = 255)
    private String publicKey;

    @Column(length = 255)
    private String scopeGranted;

    @Column
    private OffsetDateTime connectedAt;

    @Column
    private OffsetDateTime disconnectedAt;

    @Column
    private OffsetDateTime lastSyncAt;

    protected PaymentProviderAccount() {
    }

    private PaymentProviderAccount(Store store, PaymentProvider provider) {
        this.store = store;
        this.provider = provider;
        this.connectionStatus = PaymentProviderConnectionStatus.PENDING;
    }

    public static PaymentProviderAccount create(Store store, PaymentProvider provider) {
        return new PaymentProviderAccount(store, provider);
    }

    public void connect(
            String providerUserId,
            String accessTokenEncrypted,
            String refreshTokenEncrypted,
            OffsetDateTime tokenExpiresAt,
            String publicKey,
            String scopeGranted
    ) {
        this.providerUserId = providerUserId;
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.refreshTokenEncrypted = refreshTokenEncrypted;
        this.tokenExpiresAt = tokenExpiresAt;
        this.publicKey = blankToNull(publicKey);
        this.scopeGranted = blankToNull(scopeGranted);
        this.connectionStatus = PaymentProviderConnectionStatus.CONNECTED;
        this.connectedAt = OffsetDateTime.now();
        this.disconnectedAt = null;
        this.lastSyncAt = OffsetDateTime.now();
    }

    public void markError() {
        this.connectionStatus = PaymentProviderConnectionStatus.ERROR;
        this.lastSyncAt = OffsetDateTime.now();
    }

    public void disconnect() {
        this.connectionStatus = PaymentProviderConnectionStatus.DISCONNECTED;
        this.accessTokenEncrypted = null;
        this.refreshTokenEncrypted = null;
        this.tokenExpiresAt = null;
        this.publicKey = null;
        this.scopeGranted = null;
        this.disconnectedAt = OffsetDateTime.now();
        this.lastSyncAt = OffsetDateTime.now();
    }

    public boolean hasValidAccessToken() {
        return connectionStatus == PaymentProviderConnectionStatus.CONNECTED
                && accessTokenEncrypted != null
                && tokenExpiresAt != null
                && tokenExpiresAt.isAfter(OffsetDateTime.now());
    }

    public Store getStore() {
        return store;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public PaymentProviderConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public String getAccessTokenEncrypted() {
        return accessTokenEncrypted;
    }

    public String getRefreshTokenEncrypted() {
        return refreshTokenEncrypted;
    }

    public OffsetDateTime getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getScopeGranted() {
        return scopeGranted;
    }

    public OffsetDateTime getConnectedAt() {
        return connectedAt;
    }

    public OffsetDateTime getDisconnectedAt() {
        return disconnectedAt;
    }

    public OffsetDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
