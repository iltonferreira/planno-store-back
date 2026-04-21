CREATE TABLE payments (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    order_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_payment_id VARCHAR(120),
    provider_checkout_id VARCHAR(120),
    status VARCHAR(40) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    payment_method VARCHAR(80),
    idempotency_key VARCHAR(120) NOT NULL,
    raw_init_response VARCHAR(20000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_payments_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_payments_provider_payment_id UNIQUE (provider, provider_payment_id),
    CONSTRAINT uk_payments_provider_checkout_id UNIQUE (provider, provider_checkout_id)
);

CREATE INDEX idx_payments_store_order_created_at ON payments (store_id, order_id, created_at);
CREATE INDEX idx_payments_store_status ON payments (store_id, status);
