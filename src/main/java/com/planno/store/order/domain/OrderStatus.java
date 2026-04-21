package com.planno.store.order.domain;

public enum OrderStatus {
    CREATED,
    WAITING_PAYMENT,
    PAID,
    REFUNDED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
