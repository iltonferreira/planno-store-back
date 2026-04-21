# Documento Mestre de Implementacao Frontend

## 1. Fundamentos do app
- Criar dois apps Angular 21:
  - `storefront`
  - `admin`
- Compartilhar convenções:
  - DTOs espelhando o backend
  - interceptors de `Authorization`, `X-Store-Slug` e `X-Cart-Token`
  - tratamento centralizado de `ApiErrorResponse`
- Fonte de verdade:
  - backend para preços, desconto, frete, totais, pagamentos, refunds e permissões
  - frontend apenas para estado de tela e cache transitório

## 2. Configuração de ambiente e runtime
- Runtime config mínimo do storefront:
  - `apiBaseUrl`
  - `storeSlug`
- Runtime config mínimo do admin:
  - `apiBaseUrl`
- Nunca expor credenciais do Supabase nem do Mercado Pago secret.
- O frontend só consome URLs de imagem prontas retornadas pelo backend.

## 3. Modelos TypeScript compartilhados
- Criar modelos para:
  - auth
  - store
  - category
  - product
  - inventory
  - cart
  - shipping
  - order
  - payment
  - refund
  - webhook event
  - dashboard
- Critério de pronto:
  - todos os campos do backend mapeados
  - enums do backend refletidos em unions TypeScript

## 4. Auth, sessão e interceptors
- Implementar primeiro no admin:
  - `POST /api/v1/auth/login`
  - `GET /api/v1/auth/me`
- Interceptors:
  - `Authorization`
  - `X-Store-Slug`
  - `X-Cart-Token` no storefront
- Guards:
  - guest
  - store admin
  - platform admin
- Critério de pronto:
  - login, logout e restauração de sessão funcionando
  - redirecionamento por papel funcionando

## 5. Contexto de loja e de carrinho
- Storefront:
  - carregar `GET /api/v1/public/store/current` na inicialização
  - persistir `X-Cart-Token` após `POST /api/v1/public/cart`
- Admin store:
  - carregar `GET /api/v1/admin/store/current`
  - persistir `slug` da loja ativa
- Super admin:
  - não depende de store fixa, mas precisa selecionar loja em telas de filtro

## 6. Shells e rotas base
- Storefront:
  - `/`
  - `/categoria/:slug`
  - `/produto/:slug`
  - `/carrinho`
  - `/checkout`
  - `/pedido/:id/confirmacao`
  - `/pedido/:id/falha`
- Admin:
  - `/login`
  - `/admin/...`
  - `/platform/...`
- Criar layouts separados para admin da loja e plataforma.

## 7. Storefront publico
- Ordem ideal:
  1. home carregando loja, categorias e produtos
  2. listagem por categoria com filtro cliente
  3. detalhe de produto
  4. carrinho
- Fluxos:
  - `GET /api/v1/public/categories`
  - `GET /api/v1/public/products`
  - `GET /api/v1/public/products/{slug}`
  - `POST /api/v1/public/cart`
  - `GET /api/v1/public/cart`
  - `POST/PUT/DELETE` de itens
  - `POST /api/v1/public/coupons/validate`
  - `POST /api/v1/public/cart/coupon`
  - `DELETE /api/v1/public/cart/coupon`
- Critério de pronto:
  - todos os totais e descontos vindos apenas do backend

## 8. Checkout e pagamento
- Ordem ideal:
  1. endereço
  2. cotação de frete
  3. escolha do método
  4. criação do pedido
  5. consulta de capabilities do checkout transparente
  6. tokenização Mercado Pago
  7. criação do pagamento
  8. tela de confirmação/falha
- Fluxos:
  - `POST /api/v1/public/shipping/quote`
  - `POST /api/v1/public/orders`
  - `GET /api/v1/public/orders/{orderId}`
  - `GET /api/v1/public/payments/transparent/capabilities`
  - `POST /api/v1/public/payments/transparent`
  - `POST /api/v1/public/payments`
- Estado de tela:
  - `checkoutSession` em `sessionStorage`
  - nunca persistir PAN/CVV
- Critério de pronto:
  - frontend só envia token do cartão e metadados mínimos

## 9. Admin da loja
- Ordem ideal:
  1. dashboard
  2. settings da loja
  3. branding
  4. Mercado Pago
  5. categorias
  6. produtos
  7. estoque
  8. frete
  9. promoções
  10. cupons
  11. pedidos
  12. pagamentos
  13. refunds
  14. webhooks
- Endpoints principais:
  - `/api/v1/admin/store/current`
  - `/api/v1/admin/store/current/assets/logo`
  - `/api/v1/admin/store/current/assets/banner`
  - `/api/v1/admin/payments/mercado-pago/*`
  - CRUDs administrativos já inventariados
- Critério de pronto:
  - o operador da loja consegue gerir a operação inteira sem acessar o banco

## 10. Super admin
- Ordem ideal:
  1. dashboard global
  2. listagem de lojas
  3. criar/editar/desativar loja
  4. uploads por loja
  5. pagamentos globais
  6. refunds globais
  7. webhooks globais
  8. reconciliação global
- Endpoints principais:
  - `/api/v1/platform/dashboard/*`
  - `/api/v1/platform/stores*`
  - `/api/v1/platform/payments*`
  - `/api/v1/platform/refunds*`
  - `/api/v1/platform/webhooks/events*`
- Critério de pronto:
  - você consegue operar comissão, lojas e financeiro por uma única UI

## 11. Uploads e mídia
- Fluxo recomendado:
  - selecionar arquivo
  - enviar para endpoint do backend via multipart
  - atualizar preview com `publicUrl` retornada
- Endpoints:
  - admin store logo/banner
  - platform store logo/banner
- Regras:
  - PNG/JPG/WEBP/SVG
  - limite de 5MB
  - backend publica URL final

## 12. Tratamento de erros, loading e estados vazios
- Criar componente padrão para:
  - `loading`
  - `empty`
  - `error`
  - `retry`
- Mapear `ApiErrorResponse.message` para toast/banner.
- Casos críticos:
  - carrinho sem token
  - store slug inválido
  - payment capability indisponível
  - webhook ainda não consolidou pedido pago

## 13. Estratégia de testes frontend
- Unit:
  - services HTTP
  - guards
  - interceptors
- Component:
  - carrinho
  - checkout
  - settings da loja
  - grid/listas de plataforma
- E2E prioritário:
  - login admin
  - CRUD de produto
  - checkout transparente
  - CRUD de loja na plataforma

## 14. Checklist final de integração
- Storefront:
  - loja atual
  - vitrine
  - carrinho
  - cupom
  - frete
  - pedido
  - pagamento
- Admin store:
  - settings
  - branding
  - OAuth Mercado Pago
  - catálogo
  - estoque
  - frete
  - promoções
  - cupons
  - pedidos
  - pagamentos
  - refunds
  - webhooks
- Super admin:
  - dashboard
  - lojas
  - comissão
  - pagamentos
  - refunds
  - webhooks
  - reconciliação

## Dependências entre fases
- Não começar checkout antes de fechar carrinho e shipping.
- Não começar pagamentos transparentes antes de fechar `capabilities`.
- Não começar super admin de lojas antes de fechar auth e guard de plataforma.
- Não acoplar frontend diretamente ao Supabase.

## Critério global de pronto
- O sistema completo deve ser implementável só com este documento e com o inventário de endpoints.
- Nenhuma tela depende de endpoint “a definir”.
