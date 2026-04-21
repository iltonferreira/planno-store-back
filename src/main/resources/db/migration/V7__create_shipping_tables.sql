CREATE TABLE shipping_methods (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(40) NOT NULL,
    price NUMERIC(19, 2) NOT NULL,
    estimated_days INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_shipping_methods_store FOREIGN KEY (store_id) REFERENCES stores (id)
);

CREATE INDEX idx_shipping_methods_store ON shipping_methods (store_id);
CREATE INDEX idx_shipping_methods_store_active ON shipping_methods (store_id, active);

