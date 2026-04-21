CREATE TABLE addresses (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    cart_id UUID,
    order_id UUID UNIQUE,
    recipient_name VARCHAR(160) NOT NULL,
    street VARCHAR(255) NOT NULL,
    number VARCHAR(50) NOT NULL,
    complement VARCHAR(255),
    neighborhood VARCHAR(120) NOT NULL,
    city VARCHAR(120) NOT NULL,
    state VARCHAR(80) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(80) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_addresses_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_addresses_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_addresses_store ON addresses (store_id);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    changed_by VARCHAR(255) NOT NULL,
    reason VARCHAR(500),
    source VARCHAR(30) NOT NULL,
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_order_status_history_order ON order_status_history (order_id);
