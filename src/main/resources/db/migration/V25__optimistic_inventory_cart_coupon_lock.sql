-- Additive migration; existing rows start at version zero.
ALTER TABLE inventory_items ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE carts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE coupons ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
