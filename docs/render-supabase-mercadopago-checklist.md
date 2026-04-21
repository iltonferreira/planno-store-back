# Render + Supabase + Mercado Pago Checklist

## Banco
- Criar projeto Supabase com Postgres.
- Configurar `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` no Render.
- Garantir SSL no `DB_URL` compativel com Supabase.
- Usar [render.yaml](/home/iltonferreira/dev/projetos/planno-store/planno-store-back/render.yaml:1) para subir somente o backend.
- O blueprint usa `runtime: docker` com [Dockerfile](/home/iltonferreira/dev/projetos/planno-store/planno-store-back/Dockerfile:1), porque `runtime: java` nao e aceito pelo schema atual do Render.
- Configurar `SPRING_PROFILES_ACTIVE=prod`.
- Configurar `APP_CORS_ALLOWED_ORIGINS` com os domínios reais do frontend.
- Configurar `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `APP_MEDIA_SUPABASE_PUBLIC_BASE_URL`, `APP_MEDIA_SUPABASE_BUCKET` e `APP_MEDIA_SUPABASE_STORE_IMAGE_FOLDER`.
- Configurar `JWT_SECRET_BASE64` e `APP_PAYMENT_CREDENTIALS_ENCRYPTION_KEY_BASE64` com chaves fortes em base64.
- Configurar `APP_PAYMENT_MERCADO_PAGO_CLIENT_ID`, `APP_PAYMENT_MERCADO_PAGO_CLIENT_SECRET`, `APP_PAYMENT_MERCADO_PAGO_ACCESS_TOKEN`, `APP_PAYMENT_MERCADO_PAGO_WEBHOOK_SECRET`, `APP_PAYMENT_MERCADO_PAGO_WEBHOOK_URL`, `APP_PAYMENT_MERCADO_PAGO_SUCCESS_URL`, `APP_PAYMENT_MERCADO_PAGO_PENDING_URL` e `APP_PAYMENT_MERCADO_PAGO_FAILURE_URL`.
- Configurar bootstrap inicial com `APP_BOOTSTRAP_STORE_*` e `APP_BOOTSTRAP_ADMIN_*` para criar a primeira loja e o primeiro admin.
- Rodar a app com Flyway habilitado.

## Storage
- Criar bucket `store-assets` no Supabase Storage.
- Definir bucket como publico para branding simples ou manter privado e adicionar etapa de signed URLs depois.
- Configurar:
  - `SUPABASE_URL`
  - `SUPABASE_SERVICE_ROLE_KEY`
  - `APP_MEDIA_SUPABASE_BUCKET`
  - `APP_MEDIA_SUPABASE_PUBLIC_BASE_URL`
- Nunca expor `SUPABASE_SERVICE_ROLE_KEY` no frontend.

## CORS
- Configurar `APP_CORS_ALLOWED_ORIGINS` com os domínios reais do storefront e do admin.

## JWT
- Configurar `JWT_SECRET_BASE64` forte e exclusivo por ambiente.
- Definir `JWT_EXPIRATION_MINUTES` conforme a politica operacional.

## Mercado Pago
- Configurar:
  - `APP_PAYMENT_MERCADO_PAGO_CLIENT_ID`
  - `APP_PAYMENT_MERCADO_PAGO_CLIENT_SECRET`
  - `APP_PAYMENT_MERCADO_PAGO_WEBHOOK_SECRET`
  - `APP_PAYMENT_MERCADO_PAGO_WEBHOOK_URL`
  - `APP_PAYMENT_MERCADO_PAGO_SUCCESS_URL`
  - `APP_PAYMENT_MERCADO_PAGO_PENDING_URL`
  - `APP_PAYMENT_MERCADO_PAGO_FAILURE_URL`
- Validar fluxo OAuth por loja.
- Validar `GET /api/v1/public/payments/transparent/capabilities`.
- Validar webhook com evento real de pagamento e refund.

## Bootstrap opcional
- `APP_BOOTSTRAP_STORE_*` apenas para ambiente inicial.
- `APP_BOOTSTRAP_ADMIN_*` apenas para setup inicial.
- Desabilitar bootstrap automatico depois do primeiro provisionamento se desejar.

## Smoke test de deploy
- `GET /api/v1/public/store/current`
- `POST /api/v1/public/cart`
- `POST /api/v1/public/orders`
- `GET /api/v1/admin/payments/mercado-pago/account`
- `GET /api/v1/platform/stores`
- upload de logo/banner
- webhook de pagamento
- refund administrativo
