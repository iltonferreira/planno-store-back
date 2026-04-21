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
import java.util.UUID;

@Entity
@Table(
        name = "payment_provider_oauth_states",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_provider_oauth_states_state", columnNames = "state")
        }
)
public class PaymentProviderOAuthState extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProvider provider;

    @Column(nullable = false, length = 200)
    private String state;

    @Column(nullable = false)
    private UUID createdByUserId;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Column
    private OffsetDateTime consumedAt;

    protected PaymentProviderOAuthState() {
    }

    private PaymentProviderOAuthState(Store store, PaymentProvider provider, String state, UUID createdByUserId, OffsetDateTime expiresAt) {
        this.store = store;
        this.provider = provider;
        this.state = state;
        this.createdByUserId = createdByUserId;
        this.expiresAt = expiresAt;
    }

    public static PaymentProviderOAuthState create(
            Store store,
            PaymentProvider provider,
            String state,
            UUID createdByUserId,
            OffsetDateTime expiresAt
    ) {
        return new PaymentProviderOAuthState(store, provider, state, createdByUserId, expiresAt);
    }

    public void consume() {
        this.consumedAt = OffsetDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt.isBefore(OffsetDateTime.now());
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public Store getStore() {
        return store;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getState() {
        return state;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }
}
