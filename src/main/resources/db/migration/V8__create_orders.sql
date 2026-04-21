CREATE TABLE orders (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    cart_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    subtotal_amount NUMERIC(19, 2) NOT NULL,
    shipping_amount NUMERIC(19, 2) NOT NULL,
    discount_amount NUMERIC(19, 2) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    customer_document VARCHAR(40),
    shipping_method_id UUID NOT NULL,
    shipping_method_name VARCHAR(120) NOT NULL,
    shipping_method_type VARCHAR(40) NOT NULL,
    shipping_estimated_days INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_orders_store FOREIGN KEY (store_id) REFERENCES stores (id)
);

CREATE INDEX idx_orders_store_created_at ON orders (store_id, created_at);
CREATE INDEX idx_orders_store_status ON orders (store_id, status);

