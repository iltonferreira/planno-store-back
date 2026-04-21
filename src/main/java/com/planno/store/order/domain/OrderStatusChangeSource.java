package com.planno.store.order.domain;

public enum OrderStatusChangeSource {
    CHECKOUT,
    ADMIN,
    PAYMENT_WEBHOOK,
    REFUND,
    REFUND_WEBHOOK
}
