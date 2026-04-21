package com.planno.store.payment.account.domain;

public enum PaymentProviderConnectionStatus {
    PENDING,
    CONNECTED,
    EXPIRED,
    REVOKED,
    DISCONNECTED,
    ERROR
}
