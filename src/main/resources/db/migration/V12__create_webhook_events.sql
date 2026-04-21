CREATE TABLE webhook_events (
    id UUID PRIMARY KEY,
    store_id UUID,
    provider VARCHAR(40) NOT NULL,
    external_event_id VARCHAR(160) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload VARCHAR(20000) NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMP WITH TIME ZONE,
    processing_result VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_webhook_events_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT uk_webhook_events_provider_external UNIQUE (provider, external_event_id)
);

CREATE INDEX idx_webhook_events_store_created_at ON webhook_events (store_id, created_at);
CREATE INDEX idx_webhook_events_provider_processed ON webhook_events (provider, processed);
