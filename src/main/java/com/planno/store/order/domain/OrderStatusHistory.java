package com.planno.store.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private OrderStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private OrderStatus newStatus;

    @Column(nullable = false)
    private OffsetDateTime changedAt;

    @Column(nullable = false, length = 255)
    private String changedBy;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatusChangeSource source;

    protected OrderStatusHistory() {
    }

    private OrderStatusHistory(
            Order order,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            OffsetDateTime changedAt,
            String changedBy,
            String reason,
            OrderStatusChangeSource source
    ) {
        this.order = order;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
        this.reason = reason;
        this.source = source;
    }

    public static OrderStatusHistory create(
            Order order,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            String changedBy,
            String reason,
            OrderStatusChangeSource source
    ) {
        return new OrderStatusHistory(order, previousStatus, newStatus, OffsetDateTime.now(), changedBy, reason, source);
    }

    public UUID getId() {
        return id;
    }

    public OrderStatus getPreviousStatus() {
        return previousStatus;
    }

    public OrderStatus getNewStatus() {
        return newStatus;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getReason() {
        return reason;
    }

    public OrderStatusChangeSource getSource() {
        return source;
    }
}

