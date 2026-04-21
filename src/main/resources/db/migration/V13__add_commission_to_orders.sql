ALTER TABLE orders
    ADD COLUMN commission_rate NUMERIC(5, 2) NOT NULL DEFAULT 2.50;

ALTER TABLE orders
    ADD COLUMN commission_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE orders
    ADD COLUMN seller_net_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00;

UPDATE orders
SET commission_amount = ROUND((subtotal_amount + shipping_amount) * 0.025, 2),
    seller_net_amount = total_amount - ROUND((subtotal_amount + shipping_amount) * 0.025, 2)
WHERE commission_amount = 0.00
  AND seller_net_amount = 0.00;

CREATE INDEX idx_orders_store_status_created_at ON orders (store_id, status, created_at);
