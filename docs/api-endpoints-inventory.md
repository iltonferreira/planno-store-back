# Planno Store Backend API Inventory

## Headers base
- `Authorization: Bearer <jwt>` para rotas autenticadas.
- `X-Store-Slug: <slug>` para rotas por loja.
- `X-Cart-Token: <guestToken>` para carrinho, checkout e lookup público de pedido.

## Auth
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`

## Publico
- `GET /api/v1/public/store/current`
- `GET /api/v1/public/categories`
- `GET /api/v1/public/products`
- `GET /api/v1/public/products/{slug}`
- `POST /api/v1/public/cart`
- `GET /api/v1/public/cart`
- `POST /api/v1/public/cart/items`
- `PUT /api/v1/public/cart/items/{itemId}`
- `DELETE /api/v1/public/cart/items/{itemId}`
- `DELETE /api/v1/public/cart`
- `POST /api/v1/public/cart/coupon`
- `DELETE /api/v1/public/cart/coupon`
- `POST /api/v1/public/coupons/validate`
- `POST /api/v1/public/shipping/quote`
- `POST /api/v1/public/orders`
- `GET /api/v1/public/orders/{orderId}`
- `POST /api/v1/public/payments`
- `POST /api/v1/public/payments/transparent`
- `GET /api/v1/public/payments/transparent/capabilities`
- `GET /api/v1/public/payments/mercado-pago/oauth/callback`
- `POST /api/v1/public/webhooks/mercado-pago`

## Admin da loja
- `GET /api/v1/admin/store/current`
- `PUT /api/v1/admin/store/current`
- `POST /api/v1/admin/store/current/assets/logo`
- `POST /api/v1/admin/store/current/assets/banner`
- `GET /api/v1/admin/dashboard/summary`
- `GET /api/v1/admin/dashboard/sales-by-day`
- `GET /api/v1/admin/dashboard/top-products`
- `GET /api/v1/admin/dashboard/recent-orders`
- `GET /api/v1/admin/categories`
- `GET /api/v1/admin/categories/{categoryId}`
- `POST /api/v1/admin/categories`
- `PUT /api/v1/admin/categories/{categoryId}`
- `DELETE /api/v1/admin/categories/{categoryId}`
- `GET /api/v1/admin/products`
- `GET /api/v1/admin/products/{productId}`
- `POST /api/v1/admin/products`
- `PUT /api/v1/admin/products/{productId}`
- `DELETE /api/v1/admin/products/{productId}`
- `GET /api/v1/admin/inventory/{productId}`
- `PUT /api/v1/admin/inventory/{productId}`
- `GET /api/v1/admin/shipping/methods`
- `POST /api/v1/admin/shipping/methods`
- `PUT /api/v1/admin/shipping/methods/{id}`
- `DELETE /api/v1/admin/shipping/methods/{id}`
- `GET /api/v1/admin/orders`
- `GET /api/v1/admin/orders/{id}`
- `PATCH /api/v1/admin/orders/{id}/status`
- `GET /api/v1/admin/promotions`
- `GET /api/v1/admin/promotions/{id}`
- `POST /api/v1/admin/promotions`
- `PUT /api/v1/admin/promotions/{id}`
- `DELETE /api/v1/admin/promotions/{id}`
- `GET /api/v1/admin/coupons`
- `GET /api/v1/admin/coupons/{id}`
- `POST /api/v1/admin/coupons`
- `PUT /api/v1/admin/coupons/{id}`
- `DELETE /api/v1/admin/coupons/{id}`
- `GET /api/v1/admin/payments/mercado-pago/connect`
- `GET /api/v1/admin/payments/mercado-pago/account`
- `GET /api/v1/admin/payments/mercado-pago/capabilities`
- `POST /api/v1/admin/payments/mercado-pago/disconnect`
- `GET /api/v1/admin/payments/orders/{orderId}`
- `GET /api/v1/admin/payments/{paymentId}`
- `POST /api/v1/admin/payments/{paymentId}/refunds`
- `GET /api/v1/admin/payments/{paymentId}/refunds`
- `GET /api/v1/admin/orders/{orderId}/refunds`
- `GET /api/v1/admin/refunds/{refundId}`
- `POST /api/v1/admin/payments/{paymentId}/reconcile`
- `POST /api/v1/admin/refunds/{refundId}/reconcile`
- `GET /api/v1/admin/webhooks/events`
- `GET /api/v1/admin/webhooks/events/{id}`
- `POST /api/v1/admin/webhooks/events/{id}/reprocess`

## Plataforma
- `GET /api/v1/platform/dashboard/summary`
- `GET /api/v1/platform/dashboard/commission-by-store`
- `GET /api/v1/platform/dashboard/sales-by-day`
- `GET /api/v1/platform/dashboard/top-stores`
- `GET /api/v1/platform/stores`
- `GET /api/v1/platform/stores/{id}`
- `POST /api/v1/platform/stores`
- `PUT /api/v1/platform/stores/{id}`
- `DELETE /api/v1/platform/stores/{id}`
- `POST /api/v1/platform/stores/{storeId}/assets/logo`
- `POST /api/v1/platform/stores/{storeId}/assets/banner`
- `GET /api/v1/platform/payments`
- `GET /api/v1/platform/payments/{paymentId}`
- `POST /api/v1/platform/payments/{paymentId}/reconcile`
- `GET /api/v1/platform/refunds`
- `GET /api/v1/platform/refunds/{refundId}`
- `POST /api/v1/platform/refunds/{refundId}/reconcile`
- `GET /api/v1/platform/webhooks/events`
- `GET /api/v1/platform/webhooks/events/{id}`
- `POST /api/v1/platform/webhooks/events/{id}/reprocess`

## Validacao operacional recomendada
- Auth: login, `me`, negacao de rota protegida sem token.
- Storefront: loja atual, carrinho, cupom, frete, pedido, capabilities e lookup do pedido.
- Admin store: CRUD de catalogo, estoque, frete, status Mercado Pago, refunds e webhooks.
- Plataforma: CRUD de lojas, listagens globais, reconciliacao e filtros por loja.
