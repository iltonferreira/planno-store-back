ALTER TABLE payments
    ADD COLUMN provider_merchant_id VARCHAR(80);

ALTER TABLE payments
    ADD COLUMN commission_rate NUMERIC(5, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE payments
    ADD COLUMN commission_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE payments
    ADD COLUMN seller_net_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00;

CREATE INDEX idx_payments_store_provider_status ON payments (store_id, provider, status);

CREATE TABLE payment_provider_accounts (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_user_id VARCHAR(80),
    connection_status VARCHAR(40) NOT NULL,
    access_token_encrypted VARCHAR(4096),
    refresh_token_encrypted VARCHAR(4096),
    token_expires_at TIMESTAMP WITH TIME ZONE,
    public_key VARCHAR(255),
    scope_granted VARCHAR(255),
    connected_at TIMESTAMP WITH TIME ZONE,
    disconnected_at TIMESTAMP WITH TIME ZONE,
    last_sync_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_payment_provider_accounts_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT uk_payment_provider_accounts_store_provider UNIQUE (store_id, provider)
);

CREATE INDEX idx_payment_provider_accounts_provider_user_id ON payment_provider_accounts (provider, provider_user_id);

CREATE TABLE payment_provider_oauth_states (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    state VARCHAR(200) NOT NULL,
    created_by_user_id UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_payment_provider_oauth_states_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT uk_payment_provider_oauth_states_state UNIQUE (state)
);

CREATE INDEX idx_payment_provider_oauth_states_expires_at ON payment_provider_oauth_states (expires_at);
