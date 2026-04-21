ALTER TABLE carts ADD COLUMN coupon_id UUID;
ALTER TABLE carts ADD COLUMN coupon_code_snapshot VARCHAR(80);
ALTER TABLE carts ADD COLUMN subtotal_before_discount NUMERIC(19, 2) NOT NULL DEFAULT 0;
ALTER TABLE carts ADD COLUMN discount_amount NUMERIC(19, 2) NOT NULL DEFAULT 0;
ALTER TABLE carts ADD COLUMN item_discount_amount NUMERIC(19, 2) NOT NULL DEFAULT 0;
ALTER TABLE carts ADD COLUMN coupon_discount_amount NUMERIC(19, 2) NOT NULL DEFAULT 0;
ALTER TABLE carts ADD COLUMN free_shipping_applied BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_carts_store_coupon ON carts (store_id, coupon_id);

ALTER TABLE orders ADD COLUMN coupon_id UUID;
ALTER TABLE orders ADD COLUMN coupon_code_snapshot VARCHAR(80);
