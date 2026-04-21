package com.planno.store.payment.domain;

import com.planno.store.core.auditing.AuditableEntity;
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
        name = "webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_webhook_events_provider_external", columnNames = {"provider", "external_event_id"})
        }
)
public class WebhookEvent extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProvider provider;

    @Column(name = "external_event_id", nullable = false, length = 160)
    private String externalEventId;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, length = 20000)
    private String payload;

    @Column(nullable = false)
    private boolean processed;

    @Column
    private OffsetDateTime processedAt;

    @Column(length = 1000)
    private String processingResult;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "refund_id")
    private UUID refundId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "reprocess_count", nullable = false)
    private int reprocessCount;

    @Column(name = "last_reprocessed_at")
    private OffsetDateTime lastReprocessedAt;

    @Column(name = "last_reprocessed_by", length = 255)
    private String lastReprocessedBy;

    @Column(name = "last_reprocess_result", length = 1000)
    private String lastReprocessResult;

    protected WebhookEvent() {
    }

    private WebhookEvent(Store store, PaymentProvider provider, String externalEventId, String eventType, String payload) {
        this.store = store;
        this.provider = provider;
        this.externalEventId = externalEventId;
        this.eventType = eventType;
        this.payload = payload;
        this.processed = false;
    }

    public static WebhookEvent create(
            Store store,
            PaymentProvider provider,
            String externalEventId,
            String eventType,
            String payload
    ) {
        return new WebhookEvent(store, provider, externalEventId, eventType, payload);
    }

    public void attachStore(Store store) {
        if (this.store == null) {
            this.store = store;
        }
    }

    public void attachReferences(UUID paymentId, UUID refundId, UUID orderId) {
        if (this.paymentId == null) {
            this.paymentId = paymentId;
        }
        if (this.refundId == null) {
            this.refundId = refundId;
        }
        if (this.orderId == null) {
            this.orderId = orderId;
        }
    }

    public void markProcessed(String processingResult) {
        this.processed = true;
        this.processedAt = OffsetDateTime.now();
        this.processingResult = processingResult == null || processingResult.isBlank() ? null : processingResult.trim();
    }

    public void markFailed(String processingResult) {
        this.processed = false;
        this.processedAt = null;
        this.processingResult = processingResult == null || processingResult.isBlank() ? null : processingResult.trim();
    }

    public void markReprocessed(String actor, String result) {
        this.reprocessCount += 1;
        this.lastReprocessedAt = OffsetDateTime.now();
        this.lastReprocessedBy = actor == null || actor.isBlank() ? null : actor.trim();
        this.lastReprocessResult = result == null || result.isBlank() ? null : result.trim();
    }

    public UUID getId() {
        return super.getId();
    }

    public Store getStore() {
        return store;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public boolean isProcessed() {
        return processed;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public String getProcessingResult() {
        return processingResult;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getRefundId() {
        return refundId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public int getReprocessCount() {
        return reprocessCount;
    }

    public OffsetDateTime getLastReprocessedAt() {
        return lastReprocessedAt;
    }

    public String getLastReprocessedBy() {
        return lastReprocessedBy;
    }

    public String getLastReprocessResult() {
        return lastReprocessResult;
    }
}
