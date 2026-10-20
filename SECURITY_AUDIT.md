# Registro de segurança — Planno Store

## 2026-10-12 — Contratos Mercado Pago

Revisão restrita às correções de identificação do recebedor, parsing monetário e consulta de refunds, com testes de contrato, HMAC local e PostgreSQL descartável. Não representa auditoria geral nem certificação de produção.

Resultados, evidências por arquivo/linha, limites da validação externa e pendências financeiras estão em [docs/mercadopago-test-validation.md](docs/mercadopago-test-validation.md). A integração externa permanece parcialmente validada: consultas e tokenização TEST reais; pagamentos, comissão/OAuth, estornos e webhooks externos pendentes.

OAuth de teste foi tentado em duas sessões autorizadas, com túneis exclusivos de callback encerrados após 350,16s e 249,58s. As duas trocas responderam HTTP 200, mas nenhuma credencial foi salva/usada. A segunda registrou motivo constante `live_mode_not_false`: prefixos TEST passaram, mas o booleano false exigido não foi confirmado. O valor exato recebido não foi retido; isso não comprova credencial de produção. Diagnóstico seguro revisado e 56 testes Python aprovados, sem relaxar as guardas. Remoção das URIs confirmada pelo usuário; restauração não verificada independentemente no painel.

Bloqueadores prioritários: tratamento de chargeback como estorno integral, recuperação de resultado incerto de refund e fluxo seguro OAuth de teste/renovação. O relatório distingue o caminho confirmado por código dos riscos que ainda exigem reprodução.

### Atualização focada — divergência OAuth

Na terceira troca autorizada, com test_token como string "true" conforme referência, HTTP 200 e `live_mode_shape=absent`. A referência descreve esse campo como booleano opcional, mas sua ausência não satisfaz a política explícita; nenhuma credencial salva/usada. Túnel encerrado após 213,84s, remoção da URI confirmada pelo usuário. A tentativa não usou o cliente Java da Store. Este ignora live_mode e não tem a guarda TEST/false do auxílio, lacuna existente registrada sem alterar Java/configuração nesta missão.

72 testes Python aprovados (54 OAuth + 18 preflight), incluindo 16 novos; revisor executou independentemente e não identificou regressão de segurança. [Investigação e pendência a esclarecer com o Mercado Pago](docs/mercadopago-oauth-divergence.md).

### Atualização de validação — 20/10/2026

O perfil PostgreSQL da Store foi reexecutado sobre o snapshot local atual: **296 testes aprovados, sem falhas, erros ou skips**, PostgreSQL 17.8 descartável, migrations V1–V27 e cleanup confirmado em `docs/postgresql-integration-validation.md`. Mercado Pago e Storage permaneceram simulados. Isso fecha a pendência de persistência para este snapshot, sem validar OAuth, pagamentos, webhooks, estornos ou conciliação contra o provedor real.

### Atualização — 20/10/2026 — validação do modo OAuth no Java

A documentação oficial agora confirma que `test_token=true` solicita credenciais sandbox e define `TEST` como token válido somente em sandbox; por isso, a resposta pode ser classificada mesmo quando `live_mode` é omitido, desde que o prefixo seja `TEST` e o modo seja explicitamente habilitado no servidor. A Store implementa esse modo com `APP_PAYMENT_MERCADO_PAGO_TEST_TOKEN` (default `false`). Em produção, a guarda continua estrita: exige `APP_USR-` e `live_mode=true`; nenhum token fora do modo configurado é cifrado ou persistido. A evidência é o tipo oficial do token, não o prefixo de public key.

Validação local desta mudança: `./mvnw -o -B -Dspring.profiles.active=test -Dtest=MercadoPagoHttpAdapterContractTests test` — 89 aprovados; `./mvnw -o -B -Dspring.profiles.active=test test` — 288 aprovados, zero falhas/erros/skips. Os contratos HTTP usam respostas e credenciais fictícias. OAuth externo e pagamento continuam sem execução nesta etapa; PostgreSQL local não foi repetido porque Colima está parado e os binários nativos requeridos pelo perfil não estão disponíveis. Consulte [a investigação atualizada](docs/mercadopago-oauth-divergence.md).
