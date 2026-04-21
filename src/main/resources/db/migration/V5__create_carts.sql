CREATE TABLE carts (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    user_id UUID,
    guest_token VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    subtotal NUMERIC(19, 2) NOT NULL DEFAULT 0,
    total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_carts_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_carts_store_status ON carts (store_id, status);
CREATE INDEX idx_carts_store_guest_token_status ON carts (store_id, guest_token, status);

