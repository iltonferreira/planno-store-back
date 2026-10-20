-- Legacy orders retain a null deadline and are intentionally excluded from automatic expiration.
ALTER TABLE orders ADD COLUMN reservation_expires_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_orders_reservation_expiration ON orders (status, reservation_expires_at, id);

-- No parent FKs: this evidence commits independently while order/payment are locked
-- or not yet committed. Application authorization binds these immutable identifiers.
CREATE TABLE payment_initiation_journal (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    order_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_payment_initiation_journal_order ON payment_initiation_journal(store_id, order_id, resolved);
