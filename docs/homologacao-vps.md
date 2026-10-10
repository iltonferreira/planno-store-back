# Planno Store em homologação na VPS

Esta configuração usa a mesma VPS Hostinger e o mesmo projeto PostgreSQL Supabase do Hub, mas cria um schema PostgreSQL separado chamado `planno_store`. As migrations da Store ficam nesse schema; não compartilhe tabelas com o Hub.

## Endereços

- Vitrine: `https://<slug-da-loja>.planno.online` (por exemplo, `https://demo.planno.online`)
- API: `https://api-store.planno.online`
- Prontidão da API e conexão com o banco: `GET https://api-store.planno.online/actuator/health/readiness` (retorna somente `UP` ou `DOWN`)
- Verificação da API: `GET /api/v1/public/store/current`

O frontend é estático e pode ser publicado no Cloudflare Pages. A API Java roda em Docker na VPS. O HTTPS termina no Caddy. Nenhuma porta de banco de dados ou de aplicação é aberta para a internet.

## Valores privados da API

Crie o arquivo `.env` no servidor ao lado do código da Store. Defina nele:

| Nome | Como preencher |
| --- | --- |
| `DB_URL` | URL JDBC do Session pooler Supabase com `sslmode=require`; mantenha o schema padrão da conexão e use `DB_SCHEMA=planno_store` |
| `DB_USERNAME` e `DB_PASSWORD` | Usuário e senha do projeto Supabase; não são o login da Store |
| `DB_SCHEMA` | `planno_store` |
| `JWT_SECRET_BASE64` | Segredo forte e exclusivo, gerado no servidor com `openssl rand -base64 48` |
| `APP_PAYMENT_CREDENTIALS_ENCRYPTION_KEY_BASE64` | Outra chave forte e exclusiva; gere separadamente com `openssl rand -base64 32` |
| `APP_CORS_ALLOWED_ORIGINS` | `https://hub.planno.online` para a administração; as rotas públicas de vitrine aceitam origens públicas sem cookies |
| `APP_PAYMENT_MERCADO_PAGO_MOCK_ENABLED` | `true` somente em homologação; não processa pagamento real |
| `APP_MEDIA_SUPABASE_MOCK_ENABLED` | `true` enquanto o Storage não estiver configurado |
| `APP_BOOTSTRAP_STORE_ENABLED` | `false`; as lojas são criadas pela tela **Lojas** do Planno Hub |
| `APP_BOOTSTRAP_ADMIN_ENABLED` | `false`; não criar uma loja ou conta de demonstração ao iniciar |
| `APP_STORE_PROVISIONING_SERVICE_TOKEN` | Segredo compartilhado com o Hub para autorizar a criação de lojas |
| `APP_STORE_PROVISIONING_DOMAIN_SUFFIX` | `planno.online`; a Hub solicita `slug.planno.online` ao criar a loja |

No primeiro início, a API aplica as migrations pelo Flyway. O Hub chama a API interna para criar cada loja quando você usa **Lojas → Nova loja**. Guarde o mesmo token de integração nos dois arquivos `.env`. Restrinja os arquivos `.env` ao administrador da VPS; nunca os envie ao GitHub ou ao frontend.

Não habilite pagamentos reais para testar a tela. Antes de ativar o Mercado Pago, use credenciais de teste, configure o webhook como `https://api-store.planno.online/api/v1/public/webhooks/mercado-pago` e teste o fluxo de ponta a ponta sem cobrar clientes.

## Frontend da loja

Ao criar uma loja em **Hub → Lojas**, a Hub prepara o repositório privado do frontend, associa o projeto Pages e configura `slug.planno.online`. O repositório fica disponível para edição visual e cada commit em `main` inicia uma nova publicação. As variáveis `VITE_*` ficam visíveis no navegador; use-as somente para endereços públicos e nunca inclua senhas ou tokens privados.

## Teste de fumaça

1. Abra o endereço provisório indicado nos detalhes da loja e confirme que a página carrega.
2. Consulte `GET https://api-store.planno.online/api/v1/public/store/current` e confirme que a loja responde.
3. Entre na administração, crie uma categoria e um produto fictício, e confira o produto na vitrine.
4. Adicione o produto ao carrinho, informe um endereço de teste e percorra o checkout.
5. Confira o pedido usando o token do carrinho enviado pelo próprio navegador. Não compartilhe esse token: ele permite consultar o pedido.
6. Confirme que o pagamento está em modo simulado. Não use cartão real.

## Limitações desta homologação

O Supabase Free pode pausar projetos sem atividade e não substitui uma política própria de backup. Exporte uma cópia periódica para fora do Supabase e faça um teste de restauração antes de usar dados de clientes. Uptime Kuma confirma disponibilidade HTTP; ele não confere a correção dos pedidos nem a liquidação de pagamentos.
