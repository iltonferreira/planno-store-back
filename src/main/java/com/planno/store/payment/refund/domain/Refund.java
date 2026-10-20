package com.planno.store.payment.refund.domain;

import com.planno.store.core.auditing.AuditableEntity;
import com.planno.store.order.domain.Order;
import com.planno.store.payment.domain.Payment;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "refunds",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_refunds_provider_refund_id", columnNames = {"provider", "provider_refund_id"})
        }
)
public class Refund extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProvider provider;

    @Column(name = "provider_refund_id", length = 120)
    private String providerRefundId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(length = 500)
    private String reason;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(nullable = false)
    private OffsetDateTime requestedAt;

    @Column
    private OffsetDateTime processedAt;

    @Column(name = "raw_response", length = 20000)
    private String rawResponse;

    @Column(name = "inventory_restored", nullable = false)
    private boolean inventoryRestored;

    @Column(name = "provider_state_summary", nullable = false)
    private boolean providerStateSummary;

    protected Refund() {
    }

    private Refund(
            Store store,
            Payment payment,
            Order order,
            PaymentProvider provider,
            RefundType type,
            BigDecimal amount,
            String currency,
            String reason,
            UUID requestedByUserId,
            boolean providerStateSummary
    ) {
        if (amount == null || amount.signum() < 0 || (!providerStateSummary && amount.signum() == 0)
                || (providerStateSummary && amount.compareTo(payment.getAmount()) > 0)) {
            throw new IllegalArgumentException("Invalid refund amount");
        }
        this.store = store;
        this.payment = payment;
        this.order = order;
        this.provider = provider;
        this.type = type;
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency.trim().toUpperCase();
        this.status = RefundStatus.REQUESTED;
        this.reason = blankToNull(reason);
        this.requestedByUserId = requestedByUserId;
        this.requestedAt = OffsetDateTime.now();
        this.inventoryRestored = false;
        this.providerStateSummary = providerStateSummary;
    }

    public static Refund requested(
            Store store,
            Payment payment,
            Order order,
            PaymentProvider provider,
            RefundType type,
            BigDecimal amount,
            String currency,
            String reason,
            UUID requestedByUserId
    ) {
        return new Refund(store, payment, order, provider, type, amount, currency, reason, requestedByUserId, false);
    }

    public static Refund providerStateSummary(Payment payment, BigDecimal remainingAmount) {
        Refund summary = new Refund(payment.getStore(), payment, payment.getOrder(), payment.getProvider(),
                RefundType.FULL, remainingAmount, payment.getCurrency(),
                "Cumulative full refund confirmed by provider", null, true);
        summary.status = RefundStatus.APPROVED;
        summary.processedAt = OffsetDateTime.now();
        return summary;
    }

    public void rebalanceProviderStateSummary(BigDecimal remainingAmount) {
        if (!providerStateSummary || remainingAmount == null || remainingAmount.signum() < 0
                || remainingAmount.compareTo(payment.getAmount()) > 0) {
            throw new IllegalStateException("Invalid provider refund summary update");
        }
        this.amount = remainingAmount.setScale(2, RoundingMode.HALF_UP);
    }

    public void updateProviderState(String providerRefundId, RefundStatus status, String rawResponse) {
        if (providerStateSummary) {
            throw new IllegalStateException("Provider refund summaries cannot receive individual refund events");
        }
        if (this.status == RefundStatus.APPROVED && status != RefundStatus.APPROVED) {
            return;
        }
        if (this.providerRefundId != null && !this.providerRefundId.equals(blankToNull(providerRefundId))) {
            throw new IllegalArgumentException("Refund provider identity cannot change");
        }
        this.providerRefundId = blankToNull(providerRefundId);
        this.status = status;
        this.rawResponse = blankToNull(rawResponse);
        if (status != RefundStatus.REQUESTED) {
            this.processedAt = OffsetDateTime.now();
        }
    }

    public Store getStore() {
        return store;
    }

    public Payment getPayment() {
        return payment;
    }

    public Order getOrder() {
        return order;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getProviderRefundId() {
        return providerRefundId;
    }

    public RefundType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public UUID getRequestedByUserId() {
        return requestedByUserId;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public boolean isApproved() {
        return status == RefundStatus.APPROVED;
    }

    public boolean isInventoryRestored() {
        return inventoryRestored;
    }

    public boolean isProviderStateSummary() {
        return providerStateSummary;
    }

    public void markInventoryRestored() {
        this.inventoryRestored = true;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
