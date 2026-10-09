# Planno Store Back

Backend modular de e-commerce construído com Java 21, Spring Boot, PostgreSQL, Flyway e JWT.

## Visão do projeto

O projeto nasce como um backend para uma única loja, mas já estruturado para evoluir para múltiplas lojas em um backend único. A base foi corrigida para centralizar o contexto da loja atual, apertar a segurança e tornar o bootstrap idempotente. As fases atuais adicionam os módulos:

- `core`
- `store`
- `identity`
- `catalog`
- `inventory`
- `pricing`
- `cart`
- `shipping`
- `order`
- `payment`
- `payment.account`
- `payment.refund`
- `payment.reconciliation`
- `dashboard.store`
- `dashboard.platform`
- `pricing.promotion`
- `pricing.coupon`

Ainda não fazem parte desta fase:

- camada completa de plataforma multi-tenant

## Stack

- Java 21
- Spring Boot
- Spring Security
- JWT
- Spring Data JPA
- Flyway
- PostgreSQL
- Maven
- H2 apenas para testes automatizados

## Arquitetura inicial

O backend segue o padrão de monólito modular:

- separação por módulos de negócio
- controllers finos
- regras de negócio em services
- repositories isolando persistência
- DTOs para entrada e saída
- tratamento global de exceções
- segurança centralizada
- `Store` modelada desde o início como conceito real do domínio

Pacotes principais:

- `com.planno.store.core`
- `com.planno.store.store`
- `com.planno.store.identity`
- `com.planno.store.catalog`
- `com.planno.store.inventory`
- `com.planno.store.pricing`
- `com.planno.store.cart`
- `com.planno.store.shipping`
- `com.planno.store.order`
- `com.planno.store.payment`

## Escopo atual

Entregas implementadas até aqui:

- estrutura inicial do projeto Spring Boot
- configuração Maven
- profiles base
- configuração PostgreSQL
- configuração Flyway
- auditoria base
- tratamento global de exceções
- segurança com Spring Security + JWT
- módulo `store` com bootstrap da loja padrão
- módulo `identity` com usuários, papéis, vínculos por loja e login
- `StoreContext` com resolução centralizada da loja atual
- módulo `catalog` com categorias e produtos
- módulo `inventory` com estoque separado do produto
- módulo `pricing` com cálculo centralizado de preço
- módulo `cart` com snapshots de preço e validação de estoque
- módulo `shipping` com cálculo simples de frete por método da store
- módulo `order` com fechamento de pedido, snapshots completos e histórico de status
- módulo `payment` com iniciação de pagamento, integração inicial com Mercado Pago e webhook idempotente
- OAuth Mercado Pago por loja com credencial segura e adaptação do pagamento para usar a conta conectada da store
- checkout transparente por loja com tokenização no frontend e criação segura do pagamento no backend
- dashboard da loja com métricas operacionais e financeiras sem exposição da comissão
- dashboard da plataforma com métricas globais e comissão consolidada por loja
- promoções automáticas por produto, categoria, pedido e frete grátis
- cupons por loja com desconto percentual, fixo e frete grátis
- integração de promoções e cupons com pricing, carrinho e snapshot do pedido
- refunds administrativos com integração ao Mercado Pago, histórico local e reversão operacional segura
- reprocessamento administrativo de webhooks e reconciliação segura de payments/refunds

## Módulos existentes

### `core`

- auditoria
- segurança base
- JWT
- tratamento global de exceções
- utilitários e convenções compartilhadas

### `store`

- entidade `Store`
- persistência
- service
- endpoints público e administrativo
- bootstrap da loja padrão por variáveis de ambiente

### `identity`

- entidades `User`, `Role` e `UserRoleAssignment`
- autenticação com email e senha
- hash seguro de senha com BCrypt
- login com JWT
- endpoint de usuário autenticado
- bootstrap seguro do admin inicial

### `catalog`

- entidade `Category`
- entidade `Product`
- CRUD administrativo de categorias e produtos
- endpoints públicos com filtro apenas para itens ativos
- unicidade de `slug` e `sku` por loja

### `inventory`

- entidade `InventoryItem`
- leitura e atualização de estoque por produto
- separação clara entre catálogo e estoque
- base preparada para futura reserva e baixa

### `pricing`

- `PricingService` como fonte única de cálculo
- retorno padronizado com breakdown de preço
- promoções automáticas por item, pedido e frete
- cupons validados e aplicados de forma centralizada

### `pricing.promotion`

- entidade `Promotion`
- CRUD administrativo por store
- promoções por produto, categoria, pedido e frete grátis
- vigência, ativação, valor mínimo e limite de desconto

### `pricing.coupon`

- entidade `Coupon`
- CRUD administrativo por store
- validação pública de cupom
- cupom percentual, fixo ou frete grátis
- limite de uso, vigência e valor mínimo de compra

### `cart`

- entidades `Cart` e `CartItem`
- carrinho público isolado por store e token
- snapshots de preço unitário e total por item
- subtotal e total persistidos no carrinho
- validação de estoque antes de adicionar ou alterar item

### `shipping`

- entidade `ShippingMethod` por store
- entidade `Address` como snapshot do checkout/pedido
- cotação pública de frete por carrinho e endereço
- CRUD administrativo de métodos de frete
- cálculo isolado em `ShippingService`

### `order`

- entidades `Order`, `OrderItem` e `OrderStatusHistory`
- criação de pedido a partir do carrinho ativo
- snapshot de itens, preços, frete e endereço
- histórico de status desde a criação
- cancelamento administrativo com transição controlada

### `payment`

- entidades `Payment` e `WebhookEvent`
- iniciação pública de pagamento desacoplada do pedido
- integração inicial com Mercado Pago via Checkout Pro
- integração adicional com Checkout Transparente por tokenização
- webhook público com verificação de assinatura e idempotência
- consolidação assíncrona do pedido e do estoque quando o pagamento é aprovado

### `payment.account`

- entidade `PaymentProviderAccount` por store e provider
- entidade `PaymentProviderOAuthState` para `state` temporário e anti-replay
- OAuth Mercado Pago iniciado no admin da loja e concluído em callback público validado
- credenciais criptografadas em repouso
- status seguro da conexão sem exposição de tokens
- base pronta para credencial por loja, split automático e checkout transparente futuro

### `payment.refund`

- entidade `Refund` como conceito explícito do domínio
- solicitação administrativa de refund por payment
- suporte a refund total e parcial
- histórico persistido com status, valor, motivo e vínculo com pedido/pagamento
- integração encapsulada com Mercado Pago para criar e reconciliar refunds

### `payment.reconciliation`

- listagem administrativa de `WebhookEvent` filtrada por `store`
- reprocessamento administrativo de eventos persistidos
- reconciliação manual de `Payment` consultando o provider
- reconciliação manual de `Refund` consultando o provider
- auditoria operacional mínima de tentativas de reprocessamento

### `dashboard.store`

- dashboard administrativo da loja filtrado pela `store` atual
- métricas por período com `today`, `last7days`, `last30days`, `month` e range customizado
- receita baseada em pedidos `PAID`
- pedidos recentes, top produtos e vendas por dia
- sem exposição de `commissionRate`, `commissionAmount` ou `sellerNetAmount`

### `dashboard.platform`

- dashboard global acessível apenas por `PLATFORM_ADMIN`
- receita bruta, comissão total da plataforma e ticket médio
- comissão por loja, top lojas e vendas por dia
- mesma regra de período do dashboard da loja

## Como rodar

1. Configure as variáveis de ambiente.
2. Suba um PostgreSQL acessível.
3. Execute a aplicação com o profile desejado.

Exemplo com profile `dev`:

```bash
SPRING_PROFILES_ACTIVE=dev \
DB_URL=jdbc:postgresql://localhost:5432/planno_store \
DB_USERNAME=postgres \
DB_PASSWORD=postgres \
JWT_SECRET_BASE64=<base64-secret> \
./mvnw spring-boot:run
```

## Variáveis de ambiente

Obrigatórias para rodar a aplicação fora de `test`:

- `SPRING_PROFILES_ACTIVE`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET_BASE64`
- `APP_PAYMENT_MERCADO_PAGO_ACCESS_TOKEN`
- `APP_PAYMENT_MERCADO_PAGO_WEBHOOK_SECRET`
- `APP_PAYMENT_CREDENTIALS_ENCRYPTION_KEY_BASE64`
- `APP_PAYMENT_MERCADO_PAGO_CLIENT_ID`
- `APP_PAYMENT_MERCADO_PAGO_CLIENT_SECRET`

Configuração do contexto de loja:

- `APP_STORE_CONTEXT_HEADER_NAME` default `X-Store-Slug`

Configuração do carrinho:

- `APP_CART_TOKEN_HEADER_NAME` default `X-Cart-Token`

Configuração de pagamento:

- `APP_PAYMENT_MERCADO_PAGO_MOCK_ENABLED` default `false`
- `APP_PAYMENT_CREDENTIALS_ENCRYPTION_KEY_BASE64`
- `APP_PAYMENT_OAUTH_STATE_TTL_MINUTES` default `10`
- `APP_PAYMENT_MERCADO_PAGO_CLIENT_ID`
- `APP_PAYMENT_MERCADO_PAGO_CLIENT_SECRET`
- `APP_PAYMENT_MERCADO_PAGO_ACCESS_TOKEN`
- `APP_PAYMENT_MERCADO_PAGO_WEBHOOK_SECRET`
- `APP_PAYMENT_MERCADO_PAGO_WEBHOOK_URL`
- `APP_PAYMENT_MERCADO_PAGO_SUCCESS_URL`
- `APP_PAYMENT_MERCADO_PAGO_PENDING_URL`
- `APP_PAYMENT_MERCADO_PAGO_FAILURE_URL`
- `APP_PAYMENT_MERCADO_PAGO_API_BASE_URL` default `https://api.mercadopago.com`
- `APP_PAYMENT_MERCADO_PAGO_AUTH_BASE_URL` default `https://auth.mercadopago.com`

Bootstrap opcional da loja inicial:

- `APP_BOOTSTRAP_STORE_ENABLED` default `true`
- `APP_BOOTSTRAP_STORE_NAME`
- `APP_BOOTSTRAP_STORE_SLUG`
- `APP_BOOTSTRAP_STORE_CONTACT_EMAIL`
- `APP_BOOTSTRAP_STORE_CURRENCY` default `BRL`
- `APP_BOOTSTRAP_STORE_TIMEZONE` default `America/Sao_Paulo`
- `APP_BOOTSTRAP_STORE_DESCRIPTION`
- `APP_BOOTSTRAP_STORE_LOGO_URL`
- `APP_BOOTSTRAP_STORE_BANNER_URL`
- `APP_BOOTSTRAP_STORE_PRIMARY_COLOR`
- `APP_BOOTSTRAP_STORE_SECONDARY_COLOR`
- `APP_BOOTSTRAP_STORE_COMMISSION_RATE` default `2.50`

Bootstrap opcional do admin inicial:

- `APP_BOOTSTRAP_ADMIN_ENABLED` default `true`
- `APP_BOOTSTRAP_ADMIN_EMAIL`
- `APP_BOOTSTRAP_ADMIN_PASSWORD`
- `APP_BOOTSTRAP_ADMIN_FIRST_NAME` default `Admin`
- `APP_BOOTSTRAP_ADMIN_LAST_NAME` default `Planno`

Configuração de media com Supabase Storage:

- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `APP_MEDIA_SUPABASE_PUBLIC_BASE_URL` default `SUPABASE_URL`
- `APP_MEDIA_SUPABASE_BUCKET` default `store-assets`
- `APP_MEDIA_SUPABASE_STORE_IMAGE_FOLDER` default `stores`
- `APP_MEDIA_SUPABASE_MOCK_ENABLED` default `false`

Deploy no Render:

- O arquivo [render.yaml](/home/iltonferreira/dev/projetos/planno-store/planno-store-back/render.yaml:1) sobe apenas o backend.
- O Blueprint usa `runtime: docker`, porque o schema atual do Render nao aceita `runtime: java`.
- O [Dockerfile](/home/iltonferreira/dev/projetos/planno-store/planno-store-back/Dockerfile:1) empacota a app em Java 21 e publica na porta do Render via `server.port=$PORT`.
- Para ambiente produtivo, use `SPRING_PROFILES_ACTIVE=prod`.

## Endpoints desta fase

Públicos:

- `POST /api/v1/auth/login`
- `GET /api/v1/public/categories`
- `GET /api/v1/public/products`
- `GET /api/v1/public/products/{slug}`
- `GET /api/v1/public/store/current`
- `POST /api/v1/public/cart`
- `GET /api/v1/public/cart`
- `POST /api/v1/public/cart/items`
- `PUT /api/v1/public/cart/items/{id}`
- `DELETE /api/v1/public/cart/items/{id}`
- `DELETE /api/v1/public/cart`
- `POST /api/v1/public/shipping/quote`
- `POST /api/v1/public/orders`
- `POST /api/v1/public/payments`
- `POST /api/v1/public/payments/transparent`
- `POST /api/v1/public/coupons/validate`
- `POST /api/v1/public/cart/coupon`
- `DELETE /api/v1/public/cart/coupon`
- `GET /api/v1/public/payments/mercado-pago/oauth/callback`
- `POST /api/v1/public/webhooks/mercado-pago`

Protegidos:

- `GET /api/v1/auth/me`
- `GET /api/v1/admin/store/current`
- `PUT /api/v1/admin/store/current`
- `GET /api/v1/admin/categories`
- `GET /api/v1/admin/categories/{id}`
- `POST /api/v1/admin/categories`
- `PUT /api/v1/admin/categories/{id}`
- `DELETE /api/v1/admin/categories/{id}`
- `GET /api/v1/admin/products`
- `GET /api/v1/admin/products/{id}`
- `POST /api/v1/admin/products`
- `PUT /api/v1/admin/products/{id}`
- `DELETE /api/v1/admin/products/{id}`
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
- `GET /api/v1/admin/inventory/{productId}`
- `PUT /api/v1/admin/inventory/{productId}`
- `GET /api/v1/admin/shipping/methods`
- `POST /api/v1/admin/shipping/methods`
- `PUT /api/v1/admin/shipping/methods/{id}`
- `DELETE /api/v1/admin/shipping/methods/{id}`
- `GET /api/v1/admin/orders`
- `GET /api/v1/admin/orders/{id}`
- `PATCH /api/v1/admin/orders/{id}/status`
- `GET /api/v1/admin/payments/orders/{orderId}`
- `GET /api/v1/admin/payments/{paymentId}`
- `POST /api/v1/admin/payments/{paymentId}/refunds`
- `GET /api/v1/admin/payments/{paymentId}/refunds`
- `GET /api/v1/admin/orders/{orderId}/refunds`
- `GET /api/v1/admin/refunds/{refundId}`
- `GET /api/v1/admin/webhooks/events`
- `GET /api/v1/admin/webhooks/events/{id}`
- `POST /api/v1/admin/webhooks/events/{id}/reprocess`
- `POST /api/v1/admin/payments/{paymentId}/reconcile`
- `POST /api/v1/admin/refunds/{refundId}/reconcile`
- `GET /api/v1/admin/payments/mercado-pago/connect`
- `GET /api/v1/admin/payments/mercado-pago/account`
- `GET /api/v1/admin/payments/mercado-pago/capabilities`
- `POST /api/v1/admin/payments/mercado-pago/disconnect`
- `POST /internal/stores/provision` (Hub, service token)
- `GET /api/v1/admin/dashboard/summary`
- `GET /api/v1/admin/dashboard/sales-by-day`
- `GET /api/v1/admin/dashboard/top-products`
- `GET /api/v1/admin/dashboard/recent-orders`
- `GET /api/v1/platform/dashboard/summary`
- `GET /api/v1/platform/dashboard/commission-by-store`
- `GET /api/v1/platform/dashboard/sales-by-day`
- `GET /api/v1/platform/dashboard/top-stores`

O endpoint interno exige `X-Planno-Service-Token`, configurado por `APP_STORE_PROVISIONING_SERVICE_TOKEN` (mínimo 32 caracteres). Configure o mesmo segredo em `STORE_PROVISIONING_SERVICE_TOKEN` no Hub. `APP_STORE_PROVISIONING_DOMAIN_SUFFIX` define o domínio provisório (padrão `planno.store`). A requisição cria um registro de loja e uma operação idempotente; repetir a mesma chave e payload retorna os mesmos IDs. Chave reutilizada com payload diferente ou slug existente retorna `409 Conflict`. Os dados do administrador e o código de plano são registrados na operação; criação/ativação de usuário e cobrança ainda não fazem parte deste endpoint.

## Correções da base

- `StoreContext` foi introduzido para centralizar a resolução da loja atual.
- A resolução atual aceita `X-Store-Slug` e fallback seguro para single-store, sem hardcode espalhado.
- O acesso admin passou a ficar explicitamente separado em `/api/v1/admin/**`.
- `/api/v1/public/**` é público.
- `/api/v1/auth/me` é autenticado, enquanto as demais rotas de autenticação ficam abertas quando apropriado.
- O bootstrap da store agora valida unicidade por `slug`.
- O bootstrap de roles continua idempotente.
- O bootstrap do admin evita recriação de usuário e vínculo duplicado com a loja.

## Como funciona a store atual

- Cada request pode informar a loja atual pelo header `X-Store-Slug`.
- Se o header não for enviado, a aplicação usa fallback centralizado compatível com a fase single-store.
- Usuários autenticados não conseguem operar uma loja fora do seu escopo quando o contexto aponta para outra store.
- Services de `store`, `catalog` e `inventory` usam `CurrentStoreProvider`, e não consultas improvisadas à primeira loja ativa.

## Regras de cálculo de preço

- O preço sempre vem de `PricingService`.
- `Product` continua guardando só o `basePrice`; a lógica de cálculo não vive na entidade.
- O breakdown atual retorna `basePrice`, `finalPrice`, `discountAmount`, `appliedPromotion`, `appliedCoupon`, `lineBasePrice` e `lineFinalPrice`.
- Todos os valores monetários são normalizados com `scale(2)` e `RoundingMode.HALF_UP`.
- Promoções e cupons entram no cálculo como colaboradores do pricing, não como lógica espalhada em `cart` ou `order`.

## Promoções e cupons

- Promoções e cupons pertencem sempre à `store` atual.
- Promoções automáticas suportadas nesta fase:
  `PRODUCT`
  `CATEGORY`
  `ORDER`
  `FREE_SHIPPING`
- Cupons suportados nesta fase:
  percentual
  valor fixo
  frete grátis
- Um cupom é validado por código, vigência, status, limite de uso e valor mínimo.
- O frontend nunca define desconto final; ele apenas solicita validação/aplicação.

## Política de empilhamento

- No máximo 1 promoção automática por item.
- No máximo 1 promoção automática de pedido por carrinho.
- No máximo 1 cupom por carrinho.
- Promoção automática e cupom podem coexistir nesta fase.
- A ordem aplicada é:
  1. melhor promoção automática por item
  2. promoção automática de pedido sobre o subtotal já reduzido pelos itens
  3. cupom sobre o subtotal após promoções automáticas
  4. frete grátis por promoção/cupom quando aplicável
- O objetivo é manter uma política simples, previsível e sem combinação descontrolada.

## Regras do carrinho

- O carrinho atual é público e usa o header `X-Cart-Token`.
- Cada carrinho pertence a uma única store.
- Cada item salva snapshots de preço para não depender de mudanças futuras no produto.
- `CartService` orquestra a operação, mas não calcula preço; ele delega para `PricingService`.
- O estoque é validado antes de adicionar ou atualizar quantidades.
- Produtos inativos não podem entrar no carrinho.
- O subtotal e o total são recalculados a cada alteração.
- O carrinho agora persiste `couponId`, `couponCodeSnapshot`, `subtotalBeforeDiscount`, `discountAmount`, `itemDiscountAmount`, `couponDiscountAmount` e `freeShippingApplied`.
- `POST /api/v1/public/cart/coupon` aplica um cupom validado à store e ao carrinho atual.
- `DELETE /api/v1/public/cart/coupon` remove o cupom e força novo recálculo.

## Consumo do cupom

- O cupom é apenas validado e persistido no carrinho durante a navegação.
- O consumo efetivo de `currentUses` acontece na criação do pedido.
- O pedido revalida o cupom antes de consumi-lo.
- Se o pedido for cancelado antes da consolidação financeira, o uso consumido é liberado.
- Essa estratégia evita consumir uso definitivo cedo demais e reduz inconsistência com abandono de carrinho.

## Fluxo atual de checkout

1. o cliente cria um carrinho público da store atual
2. adiciona produtos ativos com estoque disponível
3. consulta opções de frete informando carrinho e endereço
4. escolhe um método de frete ao criar o pedido
5. o sistema gera o pedido com status inicial `WAITING_PAYMENT`
6. o pedido salva snapshots de itens, preços, frete e endereço
7. o carrinho é marcado como `CHECKED_OUT`

## Regras de shipping

- O frete pertence sempre à store atual.
- O cálculo é feito em `ShippingService`, não no carrinho.
- Nesta fase o frete é manual por método ativo da store.
- Se não houver método ativo disponível, a API retorna erro claro.
- O frete selecionado vira snapshot no pedido.

## Regras de order

- O pedido sempre nasce a partir de um carrinho ativo e não vazio.
- O pedido é a fonte oficial da compra; o carrinho não substitui o pedido.
- O pedido persiste `subtotal`, `shipping`, `discount` e `total`.
- O pedido persiste também `couponId` e `couponCodeSnapshot` quando houver cupom aplicado.
- O pedido não depende do estado vivo de `Product` depois da criação.
- Toda mudança de status gera histórico.
- Nesta fase, a transição manual permitida continua sendo apenas para `CANCELLED`.
- O desconto final do pedido é sempre recalculado via `PricingService` no fechamento, nunca confiado ao carrinho ou ao frontend.

## Fluxo de pagamento adotado

1. o pedido nasce em `WAITING_PAYMENT`
2. o frontend chama `POST /api/v1/public/payments` com o `orderId`
3. `PaymentService` valida store, estado do pedido, valor derivado do pedido e se a store possui conta Mercado Pago conectada
4. o backend cria um `Payment` local com snapshot financeiro do pedido e inicia um Checkout Pro no Mercado Pago usando a credencial da loja
5. o retorno síncrono só confirma a criação da tentativa e devolve o `checkoutUrl`
6. a confirmação real do pagamento acontece via webhook do Mercado Pago
7. o webhook reconcilia o pagamento, atualiza o pedido e consolida o estoque

## Fluxo de checkout transparente

1. o pedido nasce em `WAITING_PAYMENT`
2. o frontend usa a SDK do Mercado Pago para tokenizar os dados do cartão no navegador/app
3. o frontend envia ao backend apenas o token e os dados mínimos do payer em `POST /api/v1/public/payments/transparent`
4. o backend valida store, pedido, conta conectada e readiness para checkout transparente
5. o backend cria o pagamento real com a credencial OAuth da loja e com valor derivado exclusivamente de `Order.totalAmount`
6. a comissão continua vindo do snapshot de `Order` e entra no provider como `application_fee`
7. a confirmação operacional final continua vindo do webhook assinado

## Papel do frontend e do backend

- o frontend coleta os dados do comprador e faz a tokenização segura via Mercado Pago
- o frontend não calcula valor, não decide comissão e não confirma pagamento definitivamente
- o backend cria a tentativa financeira real, decide o valor a cobrar, persiste `Payment` e integra com split
- o backend nunca persiste PAN, CVV ou outro dado sensível do cartão

## Fluxo OAuth por loja

1. um usuário `ADMIN` ou `STAFF` autenticado da loja chama `GET /api/v1/admin/payments/mercado-pago/connect`
2. o backend valida o escopo da store atual e cria um `state` temporário, seguro e de uso único
3. o frontend redireciona o lojista para a `authorizationUrl` do Mercado Pago
4. o callback público recebe `code` e `state`
5. o backend valida o `state`, consome o registro e troca o código por tokens
6. os tokens são criptografados e persistidos em `PaymentProviderAccount`
7. a loja passa a aparecer como `CONNECTED` no painel admin

## Status da conta conectada

- cada store tem no máximo uma conta ativa do Mercado Pago nesta fase
- o status seguro é consultado por `GET /api/v1/admin/payments/mercado-pago/account`
- a resposta inclui apenas metadados seguros como `connectionStatus`, `connectedAt`, `providerUserId` mascarado, validade do token e readiness
- `readyForTransparentCheckout` indica se a store tem token válido e `publicKey` disponível para o fluxo transparente
- `POST /api/v1/admin/payments/mercado-pago/disconnect` faz desconexão lógica e impede novos pagamentos até uma nova conexão

## Comissão e split

- a comissão da plataforma é `2,50%`
- a base de cálculo é `totalAmount` final do pedido já com descontos aplicados
- o snapshot financeiro oficial fica no `Order`:
  `commissionRate`
  `commissionAmount`
  `sellerNetAmount`
- pedidos antigos não são recalculados
- `Payment` copia esse snapshot para fins de rastreabilidade, mas não é dono da regra
- a camada de integração envia a comissão para o Mercado Pago como `marketplace_fee`, deixando a base pronta para split automático 1:1
- quando existe desconto por promoção ou cupom, a comissão incide sobre o valor efetivamente cobrado do cliente

## Dashboards e métricas

- o dashboard da loja usa sempre a `store` atual e nunca mistura dados de outras lojas
- o dashboard da plataforma é global e exige `ROLE_PLATFORM_ADMIN`
- métricas financeiras principais consideram apenas pedidos com status `PAID`
- contadores operacionais continuam exibindo `WAITING_PAYMENT`, `CANCELLED` e total de pedidos no período
- filtros aceitos:
  `today`
  `last7days`
  `last30days`
  `month`
  `from` e `to`

### O que a loja enxerga

- `totalRevenue`
- `paidOrdersCount`
- `waitingPaymentOrdersCount`
- `cancelledOrdersCount`
- `averageTicket`
- `ordersCount`
- `itemsSoldCount`
- `recentOrders`
- `topProducts`
- `salesByDay`

### O que só a plataforma enxerga

- `platformCommissionTotal`
- `commissionByStore`
- `sellerNetTotal`
- consolidação global de lojas e receita

### Regra explícita de comissão

- a comissão continua calculada e persistida em `Order` e `Payment`
- a comissão continua sendo usada internamente no fluxo financeiro e no split
- a comissão **não é exibida ao lojista**
- DTOs administrativos da loja e respostas públicas de pedido/pagamento não serializam:
  `commissionRate`
  `commissionAmount`
  `sellerNetAmount`

## Estratégia com Mercado Pago

- A integração escolhida nesta fase é `Checkout Pro`.
- O backend cria uma `preference` usando o access token da conta conectada da loja e devolve a URL de checkout para o frontend.
- O retorno síncrono da criação da preference não marca o pedido como pago.
- A consolidação final depende do webhook assinado e do reprocessamento idempotente.
- A arquitetura já isola a conta do provedor por store e prepara a migração para checkout transparente sem trocar o núcleo de `order`, `payment` e `webhook`.

## Compatibilidade entre Checkout Pro e Checkout Transparente

- `Checkout Pro` continua suportado e é o fluxo redirecionado já estável do projeto
- `Checkout Transparente` agora convive no mesmo módulo `payment` com `checkoutType=TRANSPARENT`
- ambos usam a mesma store conectada, o mesmo snapshot financeiro do `Order`, o mesmo split e o mesmo webhook
- ambos terminam no mesmo processo de consolidação de `Payment -> Order -> Inventory`
- se a store não estiver pronta para transparente, o backend falha com erro claro; não há fallback implícito nesta fase

## Funcionamento do webhook

- O endpoint público recebe eventos do Mercado Pago em `POST /api/v1/public/webhooks/mercado-pago`.
- A assinatura é validada com `x-signature` e `x-request-id`.
- O evento bruto é persistido em `webhook_events`.
- A idempotência usa `provider + external_event_id`.
- Reentregas do mesmo webhook não duplicam efeitos.
- O processamento resolve a conta conectada da store correta e consulta o estado do pagamento via camada de integração, em vez de confiar só no payload recebido.
- O webhook também processa eventos de `refund` quando o provedor os enviar.
- Reprocessamento do mesmo evento não duplica refund, histórico nem movimentação de estoque.

## Reprocessamento administrativo e reconciliação

- `GET /api/v1/admin/webhooks/events` e `GET /api/v1/admin/webhooks/events/{id}` permitem inspecionar eventos da store atual sem expor segredos do provider.
- `POST /api/v1/admin/webhooks/events/{id}/reprocess` reutiliza o núcleo do `WebhookService`; não existe caminho paralelo no controller.
- O resultado do reprocessamento fica auditado no próprio `WebhookEvent` com contador, data, ator e mensagem resumida.
- Use reprocessamento quando o evento já foi recebido pelo sistema, mas o processamento falhou ou precisa ser reexecutado com segurança.
- Use reconciliação quando o estado local divergiu do estado real do Mercado Pago e é necessário consultar o provider novamente.

## Reconciliação de payment

- `POST /api/v1/admin/payments/{paymentId}/reconcile` consulta o provider com a credencial da própria store e compara o estado externo com o `Payment` local.
- Se o provider estiver `APPROVED` e o sistema local ainda não consolidou, a reconciliação aprova o payment, marca o pedido como `PAID` e finaliza a baixa definitiva de estoque sem duplicidade.
- Se o estado local já estiver correto, a resposta informa reconciliação sem mudança.
- Admin da loja só consegue reconciliar payments da própria store.

## Reconciliação de refund

- `POST /api/v1/admin/refunds/{refundId}/reconcile` consulta o estado real do refund no provider e corrige a divergência local de forma idempotente.
- Refund total aprovado reconcilia `Refund`, `Payment` e `Order` sem devolver estoque duas vezes.
- Refund parcial aprovado mantém a política conservadora: ajusta o estado financeiro, mas não devolve estoque automaticamente.
- Admin da loja só consegue reconciliar refunds da própria store.

## O que nunca deve ser feito manualmente

- não editar status de `payment`, `refund`, `order` ou estoque direto no banco para corrigir integração
- não reenviar efeitos financeiros por fora do `WebhookService`, `PaymentReconciliationService` ou `RefundService`
- não usar reprocessamento para “forçar” pagamento/refund sem antes validar o estado real do provider
- não expor payload bruto completo de webhook, tokens ou segredos em respostas administrativas

## Regras de payment

- `Payment` representa a tentativa financeira do pedido.
- O valor do pagamento sempre vem de `Order.totalAmount`.
- Currency vem da store/pedido, nunca do frontend.
- O pedido continua sendo a fonte oficial da compra.
- O pagamento agora usa a credencial Mercado Pago da própria store.
- Se a store não tiver conta conectada e válida, a iniciação falha com erro claro.
- No checkout transparente, a store também precisa estar `readyForTransparentCheckout`.
- `Payment` agora persiste `checkoutType`, `statusDetail`, `installments` e `payerEmail` como snapshots operacionais seguros.
- Nesta fase, um pedido não aceita uma nova tentativa enquanto já tiver pagamento `CREATED`, `PENDING` ou `APPROVED`.

## Módulo refund

- Refund é uma entidade própria; não é um simples campo em `Payment`.
- Todo refund pertence à store atual e sempre referencia um `Payment` e um `Order`.
- O valor elegível é calculado no backend a partir do valor já pago e dos refunds acumulados.
- O frontend não informa saldo disponível de refund; ele apenas solicita o valor desejado.
- Refunds ficam auditáveis por `status`, `type`, `amount`, `reason`, `requestedByUserId`, `requestedAt` e `processedAt`.

## Política adotada de refund

- Refund total e refund parcial estão habilitados nesta fase.
- Refund total aprovado:
  `Payment -> REFUNDED`
  `Order -> REFUNDED`
  histórico do pedido é registrado
- Refund parcial aprovado:
  `Payment -> PARTIALLY_REFUNDED`
  `Order` permanece `PAID`
- Refunds acima do valor restante elegível são bloqueados.
- O cálculo considera refunds `REQUESTED`, `PENDING` e `APPROVED` para evitar oversubscription.

## Política de estoque no refund

- Refund total aprovado devolve estoque ao inventário.
- A reversão só acontece uma vez e é protegida por idempotência do fluxo.
- Refund parcial não devolve estoque automaticamente nesta fase.
- A razão é manter uma política conservadora enquanto ainda não existe fluxo logístico detalhado por item.
- Como a fase atual ainda não usa expedição/entrega operacional antes do refund, devolver estoque no refund total de um pedido `PAID` é uma regra segura neste estágio.

## Como refund afeta order e payment

- `Order` continua sendo a fonte oficial da compra e preserva seus snapshots históricos.
- Refund não apaga snapshots de comissão, preço, frete ou desconto.
- A comissão histórica permanece registrada no `Order` e no `Payment`.
- Em refund total, o pedido passa para `REFUNDED`.
- Em refund parcial, o pedido permanece `PAID` e o efeito financeiro fica refletido no `Payment` e na entidade `Refund`.

## Como refund afeta comissão e relatórios

- O snapshot histórico de `commissionRate`, `commissionAmount` e `sellerNetAmount` não é sobrescrito por refund.
- Refund passa a ser um evento financeiro adicional, preservando a trilha da venda original.
- Nesta fase, o README documenta essa preservação histórica; agregações analíticas mais ricas sobre venda refundada ficam para fases futuras.
- A comissão continua não sendo exibida ao lojista.

## Interação entre webhook e refund

- A criação administrativa do refund tenta refletir o resultado síncrono do provedor imediatamente.
- O webhook continua relevante para reconciliação e idempotência de eventos assíncronos.
- Se um refund for confirmado novamente pelo webhook, o sistema não duplica efeitos.
- Se o provedor enviar um refund externo já aprovado, o backend consegue reconciliar `Payment`, `Order` e `Refund`.

## Limitações atuais

- ainda não há motor promocional avançado com combinação arbitrária de regras
- ainda não há cashback, gift card ou campanhas complexas
- ainda não há consumo de cupom condicionado à confirmação do pagamento; o consumo ocorre no pedido e é revertido em cancelamento
- ainda não há snapshot detalhado de cada promoção aplicada no pedido além do valor final de desconto e do cupom
- frete grátis continua simples, baseado em promoção/cupom único por carrinho
- o impacto analítico de refunds em dashboards financeiros ainda será refinado em fase posterior
- ainda não há reprocessamento administrativo em massa ou fila de retries automáticos
- ainda não há reconciliação financeira analítica completa para relatórios
- ainda não há fluxo de área do cliente para reuso de cartões ou pagamentos salvos
- o fallback entre transparente e pro é decisão explícita da aplicação; nesta fase o backend não alterna automaticamente

## Próximos passos recomendados

- reprocessamento administrativo de eventos
- área do cliente
- relatórios comerciais e financeiros mais ricos
- multi-tenant real por domínio/JWT

## Regra de consolidação do pedido

- Pedido criado: `WAITING_PAYMENT`
- Webhook com pagamento aprovado: `WAITING_PAYMENT -> PAID`
- O histórico do pedido registra a transição com fonte `PAYMENT_WEBHOOK`
- Pagamento rejeitado/cancelado/falho não cancela automaticamente o pedido nesta fase
- Nesses casos o pedido permanece em `WAITING_PAYMENT`, permitindo estratégia futura de nova tentativa

## Política de estoque no pedido e no pagamento

- Na criação do pedido, o sistema valida estoque novamente.
- A política adotada nesta fase é reserva, não baixa definitiva.
- `quantityReserved` aumenta quando o pedido é criado.
- `quantityReserved` é liberado se o pedido for cancelado.
- Quando o webhook confirma `APPROVED`, a reserva é convertida em baixa definitiva.
- Nessa consolidação:
  `quantityReserved` diminui
  `quantityAvailable` diminui
- Reprocessamento idempotente impede dupla baixa.

## Decisões arquiteturais

- `Store` existe desde o dia zero e não é tratada como configuração solta.
- Papéis são persistidos em tabela própria.
- Vínculos entre usuário, papel e loja ficam em `user_role_assignments`, preparando a autorização futura por escopo de loja.
- O sistema ainda opera de forma simples para single-store, mas com contexto de loja centralizado e pronto para crescer.
- O bootstrap inicial é controlado por ambiente, evitando credenciais fixas em código.
- Produtos e estoque ficam separados em módulos distintos.
- Catálogo público só expõe categorias e produtos ativos.
- O módulo `pricing` concentra a evolução futura de desconto, promoção e cupom.
- O módulo `cart` já persiste snapshots de preço para preparar o pedido futuro.
- `ShippingService` calcula frete de forma isolada e store-scoped.
- `OrderService` orquestra o fechamento do pedido sem acoplar pagamento.
- `PaymentService` é o ponto único de iniciação de pagamentos.
- A integração com o provedor fica encapsulada em `PaymentProviderClient`.
- A conexão OAuth da conta do provedor fica encapsulada em `MercadoPagoOAuthService` e `PaymentProviderAccountService`.
- O webhook é a fonte oficial de confirmação final do pagamento.
- A política atual de estoque reserva no pedido e só faz a baixa definitiva na confirmação assíncrona.
- A comissão é calculada em `OrderService` e persiste como snapshot financeiro da compra.
- O módulo atual já prepara o caminho para split 1:1 e checkout transparente sem mover a regra de comissão para `Payment`.

## Próximos módulos recomendados

1. rastreamento e logística
2. checkout transparente com credencial por store
3. split da plataforma com capacidades mais ricas
4. reprocessamento administrativo em lote e fila operacional
5. carrinho autenticado por cliente
6. área do cliente
7. autorização por escopo mais rica
8. resolução dinâmica de store por domínio

## O que ainda não foi implementado

- multi-tenant completo com resolução dinâmica por domínio
- variações de produto
- regras avançadas de frete
- conciliação financeira completa
- reconciliação administrativa em massa
- credenciais adicionais por store
- split da plataforma com relatórios financeiros
- carrinho autenticado por cliente
- área do cliente
- autorização por escopo completo de loja
