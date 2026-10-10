CREATE TABLE store_provisioning_operations (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    store_id UUID NOT NULL REFERENCES stores(id),
    status VARCHAR(30) NOT NULL,
    store_admin_email VARCHAR(255) NOT NULL,
    store_admin_name VARCHAR(150) NOT NULL,
    plan_code VARCHAR(32) NOT NULL
);

CREATE INDEX store_provisioning_store_id_idx ON store_provisioning_operations(store_id);
