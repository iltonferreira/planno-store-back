package com.planno.store.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Durable evidence of a possibly external effect, independent of the financial transaction. */
@Entity
@Table(name = "payment_initiation_journal")
public class PaymentInitiationJournal {
    @Id
    private UUID id;

    // Intentionally scalar: an independent insert must not lock an uncommitted parent row.
    @Column(name = "store_id", nullable = false, updatable = false)
    private UUID storeId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private boolean resolved;

    protected PaymentInitiationJournal() {}

    public static PaymentInitiationJournal started(UUID storeId, UUID orderId, UUID attemptId) {
        var entry = new PaymentInitiationJournal();
        entry.id = attemptId;
        entry.storeId = storeId;
        entry.orderId = orderId;
        entry.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return entry;
    }

    public void resolve() { resolved = true; }
    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public UUID getOrderId() { return orderId; }
    public boolean isResolved() { return resolved; }
}
