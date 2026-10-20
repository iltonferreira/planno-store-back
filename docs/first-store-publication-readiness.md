# Primeira loja — prontidão para publicação

**Atualizado:** 20/10/2026
**Estado:** código local em homologação; ainda não liberar acesso público nem iniciar uma venda real.

## O que está implementado neste conjunto de mudanças

- Provisionamento idempotente Hub → Store, conta administrativa inicial e troca obrigatória da senha temporária.
- Login administrativo exige senha e código de seis dígitos enviado por e-mail em toda autenticação. O código tem validade de 10 minutos, uso único, limite de tentativas e intervalo de reenvio. A API falha fechada se o envio estiver indisponível; tokens emitidos após o segundo fator carregam a prova exigida pelo validador.
- Hub mantém fluxo explícito de prévia, revisão do cliente, aprovação da revisão exata, promoção por SHA e histórico auditável. Rollback local pode selecionar release previamente aprovada e verificada, inclusive após falha da publicação; conflito com configuração de domínio é bloqueado.
- Loja exemplo atende vitrine, carrinho, cotação de entrega, checkout e painel do lojista nos contratos locais simulados. Guardas de pagamentos e chamadas externas permanecem fechadas por padrão.

## Validações locais registradas

- Hub: suíte Playwright completa — 110 aprovados, 2 pulados (gates dependentes de ambiente). Testes Java focais do fluxo J5 — 20 aprovados; integrações PostgreSQL descartáveis V55–V58 — 5 aprovadas. Build do painel e template aprovados.
- Store: migrations V1–V29 aplicadas em H2 descartável; suíte Maven completa — **291 aprovados, 0 falhas/erros/skips**, incluindo OTP e contratos financeiros simulados.
- Site: build e lint aprovados; E2E do lojista — 11 aprovados, incluindo rejeição de OTP inválido e ausência de sessão até o código correto.
- O ensaio process-level Hub↔Store previamente registrado cobriu reinício e retry de provisionamento em PostgreSQL descartável, mas antecede esta migration V29 e deve ser repetido antes do merge.
- A última tentativa PostgreSQL completa da Store usou o snapshot anterior (V1–V28); a execução atual não foi repetida pois os binários PostgreSQL temporários não estão disponíveis. O teste H2 confirma a migration V29, mas não substitui essa validação.

## Bloqueios antes de liberar a primeira loja

1. Configurar e testar SMTP de homologação com `APP_EMAIL_2FA_SMTP_HOST`, `APP_EMAIL_2FA_SMTP_PORT`, `APP_EMAIL_2FA_SMTP_USERNAME`, `APP_EMAIL_2FA_SMTP_PASSWORD` e `APP_EMAIL_2FA_FROM`. Usar remetente aprovado e confirmar entrega, reenvio, expiração e limites sem registrar o código nos logs.
2. Reexecutar Store em PostgreSQL 17 descartável até V29 e repetir o ensaio process-level Hub↔Store com a migration atual.
3. Usar contas de homologação para verificar GitHub/Cloudflare: permissão de criar ref protegida, desligar deploy automático, publicar preview e promoção explícita pelo SHA/revisão aprovada. Não usar tokens reais de produção.
4. Confirmar DNS, certificado TLS e disponibilidade do domínio da loja após publicação de homologação.
5. Completar as decisões comerciais documentadas em `docs/financial-blockers-fixes.md`, `docs/mercadopago-oauth-divergence.md` e `docs/mercadopago-test-validation.md`; depois verificar checkout, confirmação, cancelamento, estorno e reconciliação somente com sandbox e valores de teste.
6. Confirmar dados do lojista, catálogo, estoque, preços, política de frete/devolução, contrato e termos de privacidade antes de cadastrar conteúdo de produção.
7. Commits e pushes em `homolog` foram concluídos nos três repositórios. O PR do site [#12](https://github.com/plannocorp/planno-site/pull/12) foi atualizado. A criação dos PRs da Store e do Hub foi bloqueada pela integração GitHub disponível: `403 Resource not accessible by integration` na Store e `404 Not Found` no Hub; o GitHub CLI não está instalado. Assim que o conector com escrita nesses repositórios estiver habilitado, abrir [Store homolog → deploy](https://github.com/iltonferreira/planno-store-back/compare/deploy...homolog?expand=1) e [Hub homolog → deploy](https://github.com/iltonferreira/planno-hub/compare/deploy...homolog?expand=1). A publicação de produção fica para depois do merge feito pelo responsável.

## Limites desta entrega

Nenhuma chamada financeira, credencial real, alteração de produção, publicação externa ou operação de DNS/TLS foi executada. Prévia local e testes simulados não comprovam comportamento real dos provedores. Não considerar a loja pronta para venda pública enquanto os bloqueios acima não forem validados ou formalmente resolvidos.
