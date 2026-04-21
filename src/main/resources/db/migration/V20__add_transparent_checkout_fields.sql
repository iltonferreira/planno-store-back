ALTER TABLE payments
    ADD COLUMN checkout_type VARCHAR(40) NOT NULL DEFAULT 'PRO';

ALTER TABLE payments
    ADD COLUMN status_detail VARCHAR(255);

ALTER TABLE payments
    ADD COLUMN installments INTEGER;

ALTER TABLE payments
    ADD COLUMN payer_email VARCHAR(255);

CREATE INDEX idx_payments_store_checkout_type_status ON payments (store_id, checkout_type, status);
