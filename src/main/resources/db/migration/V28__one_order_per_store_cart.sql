-- A checked-out cart is the recovery key for a checkout whose HTTP response was lost.
-- Existing duplicate rows cause migration failure and must be reviewed manually; this
-- migration never deletes or merges orders.
CREATE UNIQUE INDEX uq_orders_store_cart ON orders (store_id, cart_id);
