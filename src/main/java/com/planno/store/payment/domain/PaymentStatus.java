package com.planno.store.payment.domain;

public enum PaymentStatus {
    CREATED,
    PENDING,
    APPROVED,
    PARTIALLY_REFUNDED,
    REJECTED,
    CANCELLED,
    REFUNDED,
    FAILED

    ;

    public boolean requiresCustomerAction() {
        return this == CREATED || this == PENDING;
    }
}
