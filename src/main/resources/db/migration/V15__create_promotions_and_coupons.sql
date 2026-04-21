CREATE TABLE promotions (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(2000),
    type VARCHAR(40) NOT NULL,
    discount_type VARCHAR(40),
    discount_value NUMERIC(19, 2),
    applies_to_type VARCHAR(40) NOT NULL,
    applies_to_id UUID,
    min_order_amount NUMERIC(19, 2),
    max_discount_amount NUMERIC(19, 2),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_promotions_store FOREIGN KEY (store_id) REFERENCES stores (id)
);

CREATE INDEX idx_promotions_store_active_period ON promotions (store_id, active, starts_at, ends_at);
CREATE INDEX idx_promotions_store_type ON promotions (store_id, type);
CREATE INDEX idx_promotions_store_applies_to ON promotions (store_id, applies_to_type, applies_to_id);

CREATE TABLE coupons (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    description VARCHAR(1000),
    discount_type VARCHAR(40),
    discount_value NUMERIC(19, 2),
    min_order_amount NUMERIC(19, 2),
    max_discount_amount NUMERIC(19, 2),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    max_uses INTEGER,
    current_uses INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    free_shipping BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_coupons_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT uq_coupons_store_code UNIQUE (store_id, code)
);

CREATE INDEX idx_coupons_store_active_period ON coupons (store_id, active, starts_at, ends_at);
