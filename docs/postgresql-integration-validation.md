# Validação PostgreSQL da Planno Store

Data: 11/10/2026. Branch: homolog. Escopo: PostgreSQL local descartável, migrações e persistência dos fluxos críticos.

**Resultado: CORRIGIDO E VALIDADO EM POSTGRESQL LOCAL.** As falhas de persistência encontradas foram reproduzidas e corrigidas. Não há declaração de prontidão para produção ou de funcionamento de integrações externas.

## Ambiente e diagnóstico

AGENTS.md do diretório e repositório e as skills production-readiness, bug-investigation e infrastructure-operations foram lidos. Trabalho anterior preservado; nenhum commit, push, deploy, acesso a homologação/produção, alteração de dados reais ou pagamento real.

A configuração padrão usa PostgreSQL/JPA e Flyway com validação de schema. O perfil Spring test usa H2 em memória e mocks; não existia perfil PostgreSQL nem infraestrutura Testcontainers no projeto. Driver PostgreSQL e módulo Flyway PostgreSQL já estavam disponíveis, portanto nenhuma dependência nova foi adicionada.

Docker, Podman, Colima, PostgreSQL, psql e Homebrew não estavam disponíveis; não havia daemon/socket Docker local identificado. Alguns artefatos Testcontainers estavam no cache Maven, mas não existia runtime compatível configurado. Sem runtime, adicionar a biblioteca não permitiria executar containers. Foi adotado PostgreSQL nativo portátil, sem instalação global.

Distribuição: io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8:17.8.0, do Maven Central. SHA-256 publicado e verificado: 19022eadefcbb09274519e88daa3cf3f5888d5fdd96beaf63922285970e6adc3. O executável macOS é universal; a versão informada pelo servidor foi **PostgreSQL 17.8**. Plataforma utilizada: macOS/Darwin arm64, Java 21, Spring Boot 3.5.16. A distribuição fornece um servidor PostgreSQL real; não é uma emulação H2. [Fonte do distribuidor](https://github.com/zonkyio/embedded-postgres-binaries).

O suporte de teste cria cluster e banco novos em diretório privado, com SCRAM, credenciais temporárias e porta aleatória somente em 127.0.0.1. Não reutiliza volumes nem aceita URL de banco existente. O papel da aplicação é NOSUPERUSER/NOCREATEDB/NOCREATEROLE e proprietário apenas do banco descartável para executar migrations. Isso não comprova a política de privilégios de produção.

Datasource e Flyway são fixados explicitamente para o banco próprio antes do boot; JNDI externo é rejeitado. Init SQL alternativo e bootstraps são desativados. O cleanup verifica a URL antes de excluir fixtures. O processo é encerrado e somente seu cluster é removido no shutdown. **Encerramento e ausência do diretório final foram confirmados.** O bundle portátil permanece temporário, sem servidor ativo.

A sandbox inicialmente bloqueou a abertura de porta local; a execução autorizada com permissão de processo/socket local resolveu esse impedimento. A consulta às branches remotas também foi limitada por DNS; permaneceu homolog, sem criação ou publicação de branch.

## Migrações, constraints e legados fictícios

**V1–V27 executadas e validadas pelo Flyway em banco vazio.** Hibernate validou o schema. Nenhuma migration existente foi modificada.

O teste de upgrade criou outro banco descartável, aplicou V1–V25, inseriu dados fictícios e aplicou V26–V27:

- V26: provider_state_summary é NOT NULL, DEFAULT FALSE. O refund antigo permaneceu false, com seu valor e marcador de estoque preservados. A migration não infere que um estorno sem identidade era um resumo cumulativo.
- V27: reservation_expires_at é nullable, TIMESTAMP WITH TIME ZONE. Pedidos PAID, CANCELLED e WAITING_PAYMENT antigos mantiveram status e prazo nulo; reservas e pagamentos permaneceram intactos.
- Índices idx_orders_reservation_expiration e idx_payment_initiation_journal_order existem. PK do journal e constraints de refund/payment e evento único foram verificadas no catálogo.
- Journal sem FKs é deliberado: sua transação independente não pode aguardar o pai bloqueado/não confirmado. O teste confirma essa estrutura e o default resolved=false; a persistência independente é exercitada pela suíte de iniciação incerta.
- Inserção de evento com store inexistente foi rejeitada por FK com SQLSTATE 23503. Duplicidade de provider/evento foi rejeitada com 23505. A transação falhada rejeitou consulta com 25P02; após rollback, uma nova inserção foi confirmada. A linha legítima foi preservada.

Evidências: [src/test/java/com/planno/store/PostgresPersistenceIT.java](/Users/m4/Documents/PlannoCorp/plannostore/src/test/java/com/planno/store/PostgresPersistenceIT.java:38), [src/test/java/com/planno/store/PostgresPersistenceIT.java](/Users/m4/Documents/PlannoCorp/plannostore/src/test/java/com/planno/store/PostgresPersistenceIT.java:55) e [src/test/java/com/planno/store/PostgresPersistenceIT.java](/Users/m4/Documents/PlannoCorp/plannostore/src/test/java/com/planno/store/PostgresPersistenceIT.java:99).

FKs individuais existentes não constituem uma constraint tenant composta. Não foi acrescentado hardening de schema nem presumida existência de RLS. Os testes comprovam o escopo das consultas e APIs exercitadas; não representam prova de todos os acessos possíveis por SQL direto.

## Problemas confirmados e correções

### P1 — leitura financeira obsoleta após espera por bloqueio

O refresh com PESSIMISTIC_WRITE permitia que Hibernate carregasse estado antes do bloqueio efetivo, por follow-on locking. No PostgreSQL/MVCC, a transação perdedora podia esperar o escritor e continuar com o estado anterior.

Reprodução determinística: escritor bloqueia e altera pedido para CANCELLED, ainda sem commit; leitor vê WAITING_PAYMENT e solicita lock; observador confirma a espera real em pg_stat_activity; escritor confirma; o serviço antigo ainda retorna WAITING_PAYMENT. A reprodução falhou com esperado CANCELLED / obtido WAITING_PAYMENT.

Isso também provocou falhas reais nos cenários de aprovação concorrente, duas expirações e aprovação disputando expiração: tentativa de movimentar reserva já consumida/liberada. Não se afirma que houve cobrança indevida ou corrupção de dados reais.

Correção: **adquirir o lock primeiro e atualizar o estado depois**, tanto no pedido quanto pagamento, preservando ordem pedido→pagamento e transação MANDATORY. Quantidades, cálculos e regras financeiras não foram alterados.

Código: [src/main/java/com/planno/store/core/auditing/FinancialLock.java](/Users/m4/Documents/PlannoCorp/plannostore/src/main/java/com/planno/store/core/auditing/FinancialLock.java:16). Regressão determinística: [src/test/java/com/planno/store/PostgresPersistenceIT.java](/Users/m4/Documents/PlannoCorp/plannostore/src/test/java/com/planno/store/PostgresPersistenceIT.java:170).

### P2 — webhook duplicado recuperado dentro de transação abortada

Duas chamadas com o mesmo provider/eventId disputavam a unique constraint. Após saveAndFlush falhar com 23505, o catch consultava na mesma transação PostgreSQL abortada e recebia 25P02. O teste anterior de eventos distintos não reproduzia essa colisão.

Correção: consulta/inserção em TransactionTemplate REQUIRES_NEW; captura somente após o rollback; recuperação em outra transação REQUIRES_NEW, readOnly. Retorna a identidade existente ou relança a exceção original se não existir. O registro durável independente e a atomicidade do processamento financeiro foram preservados.

Código: [src/main/java/com/planno/store/payment/webhook/service/WebhookEventLifecycleService.java](/Users/m4/Documents/PlannoCorp/plannostore/src/main/java/com/planno/store/payment/webhook/service/WebhookEventLifecycleService.java:33). Regressão: [src/test/java/com/planno/store/PostgresPersistenceIT.java](/Users/m4/Documents/PlannoCorp/plannostore/src/test/java/com/planno/store/PostgresPersistenceIT.java:140). O spy sincroniza apenas duas leituras; consultas, inserts, disputa de constraint e transações são reais.

### P2 — filtros opcionais incompatíveis com a tipagem PostgreSQL

GETs administrativos de lojas e eventos retornavam 500: filtro textual nulo era passado para upper como bytea, gerando 42883. Após corrigir texto, limites temporais ausentes em IS NULL revelaram 42P18, parâmetro sem tipo determinado.

Correção somente nas queries HQL: casts textuais e OffsetDateTime explícitos nas guardas nulas. Foram preservados ausência de filtro, comparação sem distinção de caixa, comportamento de branco existente, tenant, ordenação e comparações inclusivas de datas. A regressão inclui limites presentes/isolados, negativos e offset -03:00.

Código: [src/main/java/com/planno/store/store/repository/StoreRepository.java](/Users/m4/Documents/PlannoCorp/plannostore/src/main/java/com/planno/store/store/repository/StoreRepository.java:30) e [src/main/java/com/planno/store/payment/repository/WebhookEventRepository.java](/Users/m4/Documents/PlannoCorp/plannostore/src/main/java/com/planno/store/payment/repository/WebhookEventRepository.java:29). Testes: [src/test/java/com/planno/store/PostgresPersistenceIT.java](/Users/m4/Documents/PlannoCorp/plannostore/src/test/java/com/planno/store/PostgresPersistenceIT.java:202) e [src/test/java/com/planno/store/PostgresPersistenceIT.java](/Users/m4/Documents/PlannoCorp/plannostore/src/test/java/com/planno/store/PostgresPersistenceIT.java:213). [Referência HQL sobre casts](https://docs.hibernate.org/orm/6.6/querylanguage/html_single/).

A revisão também identificou necessidade de fixar o datasource próprio do Flyway, que pode ser separado do datasource da aplicação. Essa proteção foi adicionada antes da conclusão; não havia override externo observado nem conexão a banco existente.

## Comandos e resultados

Execução PostgreSQL final do QA:

```sh
./mvnw -o -B -Ppostgres-integration \
  -Dplanno.test.postgres.bin-dir=/private/tmp/planno-postgres-runtime-jytspz18/runtime/bin \
  clean verify
```

**BUILD SUCCESS, exit 0, 18,760 s. 182 testes, 22 suítes, 0 falhas, 0 erros, 0 ignorados.**

Regressão H2 posterior:

```sh
./mvnw -o -B -Dspring.profiles.active=test clean verify
```

**BUILD SUCCESS, exit 0, 27,151 s. 174 testes, 21 suítes, 0 falhas, 0 erros, 0 ignorados.**

Os 182 são o total da suíte no perfil PostgreSQL, incluindo testes unitários; não são 182 testes de integração. Os testes de persistência usaram PostgreSQL real. Os oito testes IT novos não são executados pelo perfil padrão H2; foram executados integralmente no perfil PostgreSQL. Os mesmos 174 casos anteriores passaram nas duas execuções; não se soma execuções repetidas como casos únicos.

Build, JAR e git diff --check passaram. Não foram removidos testes nem enfraquecidas verificações.

Histórico de reprodução preservado:

- Corrida do mesmo webhook: 4 aprovados / 1 falha nos cinco IT iniciais, 23505→25P02.
- Primeira suíte completa PostgreSQL: 179 casos, 173 aprovados, 3 falhas e 3 erros.
- Lock determinístico: 1 caso / 1 falha por estado obsoleto.
- Após lock e texto: 182 casos, 179 aprovados, 2 falhas e 1 erro temporal.
- Final: 182 aprovados. Não se confundem exceções SQL esperadas pelos testes positivos de constraints com testes falhados.
- Tentativas iniciais com porta bloqueada e barreira de Mockito incorreta ocorreram antes da reprodução correta; foram corrigidas como infraestrutura/teste, não classificadas como defeitos de produção.

Evidências permanentes: [docs/validation/postgresql-2026-10-11/results.json](/Users/m4/Documents/PlannoCorp/plannostore/docs/validation/postgresql-2026-10-11/results.json), [docs/validation/postgresql-2026-10-11/postgres-verify.log](/Users/m4/Documents/PlannoCorp/plannostore/docs/validation/postgresql-2026-10-11/postgres-verify.log) e [docs/validation/postgresql-2026-10-11/h2-regression.log](/Users/m4/Documents/PlannoCorp/plannostore/docs/validation/postgresql-2026-10-11/h2-regression.log). O JSON contém totais por suíte, ambiente/cleanup e recortes RED com caminhos e hashes dos logs privados originais.

## Cobertura dos nove fluxos

| Área | Evidência executada no PostgreSQL |
|---|---|
| Multi-tenant | CheckoutAuthorizationIntegrationTests (18), cenários webhook de múltiplas lojas, IT tenant e busca filtrada |
| Idempotência de webhooks | PaymentWebhookIntegrationTests (10), colisão determinística do mesmo evento no IT |
| Concorrência em estornos | FinancialRefundRegressionTests (20), replay concorrente, resumo cumulativo e refresh de ledger |
| Estoque | InventoryConcurrencyTests, regressões de estorno, aprovação e expiração |
| Expiração | OrderReservationExpirationIntegrationTests (29), duplicidade, cancelamento e aprovação concorrentes |
| Aprovação tardia | LatePaymentApprovalIntegrationTests (4) e aprovação após expiração |
| Iniciação incerta | PaymentInitiationSafetyIntegrationTests (10), REQUIRES_NEW e rollback reais; efeito externo simulado |
| Transações/rollback | Webhook com rollback financeiro, estoque/cupom rollback e IT SQLSTATE/recuperação |
| Estados | Casos parametrizados de pedidos/pagamentos, estado sob lock e upgrade com legados |

Os números na tabela indicam suítes que se sobrepõem; não devem ser somados para inventar uma contagem por fluxo.

## Legados: política proposta, não aplicada

| Categoria | Tratamento seguro proposto |
|---|---|
| CANCELLED | Preservar estado; não liberar novamente. Conciliar reserva residual e eventual aprovação tardia separadamente. |
| PAID ou REFUNDED | Excluir da expiração; conferir ledger e estoque sem inferir movimentos pela idade. |
| WAITING_PAYMENT com CREATED/PENDING/APPROVED | Preservar reserva até confirmação/reconciliação no provedor e decisão comercial. |
| Resultado incerto ou journal pendente | Preservar reserva e bloquear nova tentativa; resolver o efeito externo antes de alterar prazo/estado. |
| Sem pagamento ou somente REJECTED/CANCELLED/FAILED | Candidato a backfill apenas após provar ausência de efeito externo, validar estoque e receber autorização para prazo e execução. |
| Reserva antiga sem associação confiável | Inventariar inconsistências por tenant; não liberar por idade nem atribuir saldo agregado a pedidos arbitrariamente. |

Antes da V27, uma chamada externa pode não ter deixado journal. Ausência de Payment/journal antigo **não prova ausência de cobrança**. Um futuro backfill deve ser incremental, idempotente, auditável e interromper-se diante de inconsistências. A autorização de 30 minutos para pedidos novos não autoriza cancelamento retroativo em massa.

Nenhuma política comercial foi aplicada, nem migration de backfill criada. V26 também não resolve automaticamente a classificação semântica de estornos históricos sem identidade; o tratamento existente falha fechado.

## Revisão e QA

O revisor avaliou infraestrutura, migrações, locks, queries e correções: **sem novos achados confirmados no patch final revisado**. Classificou leitura financeira obsoleta como P1; recuperação de webhook e filtros como P2. Não executou Maven.

O QA executou o PostgreSQL, comprovou os REDs e o resultado final, comparou os nomes dos casos com a regressão H2 e confirmou que nenhum caso anterior desapareceu. Confirmou role, loopback, ausência de H2 na execução PostgreSQL, parada e remoção do cluster. H2 foi executado pelo principal; o QA conferiu independentemente seus XMLs e log.

## Arquivos da missão

O repositório já estava modificado. Esta missão alterou oito arquivos existentes, adicionou quatro arquivos de suporte/teste, este relatório e três artefatos de evidência (**16 arquivos**). A lista não atribui alterações anteriores a esta missão:

- [AGENTS.md](/Users/m4/Documents/PlannoCorp/plannostore/AGENTS.md)
- [README.md](/Users/m4/Documents/PlannoCorp/plannostore/README.md)
- [pom.xml](/Users/m4/Documents/PlannoCorp/plannostore/pom.xml)
- [src/test/java/com/planno/store/support/AbstractIntegrationTest.java](/Users/m4/Documents/PlannoCorp/plannostore/src/test/java/com/planno/store/support/AbstractIntegrationTest.java)
- [src/main/java/com/planno/store/core/auditing/FinancialLock.java](/Users/m4/Documents/PlannoCorp/plannostore/src/main/java/com/planno/store/core/auditing/FinancialLock.java)
- [src/main/java/com/planno/store/payment/webhook/service/WebhookEventLifecycleService.java](/Users/m4/Documents/PlannoCorp/plannostore/src/main/java/com/planno/store/payment/webhook/service/WebhookEventLifecycleService.java)
- [src/main/java/com/planno/store/payment/repository/WebhookEventRepository.java](/Users/m4/Documents/PlannoCorp/plannostore/src/main/java/com/planno/store/payment/repository/WebhookEventRepository.java)
- [src/main/java/com/planno/store/store/repository/StoreRepository.java](/Users/m4/Documents/PlannoCorp/plannostore/src/main/java/com/planno/store/store/repository/StoreRepository.java)
- [src/test/java/com/planno/store/support/PostgresTestDatabase.java](/Users/m4/Documents/PlannoCorp/plannostore/src/test/java/com/planno/store/support/PostgresTestDatabase.java)
- [src/test/java/com/planno/store/support/PostgresTestContextCustomizerFactory.java](/Users/m4/Documents/PlannoCorp/plannostore/src/test/java/com/planno/store/support/PostgresTestContextCustomizerFactory.java)
- [src/test/resources/META-INF/spring.factories](/Users/m4/Documents/PlannoCorp/plannostore/src/test/resources/META-INF/spring.factories)
- [src/test/java/com/planno/store/PostgresPersistenceIT.java](/Users/m4/Documents/PlannoCorp/plannostore/src/test/java/com/planno/store/PostgresPersistenceIT.java)
- [docs/postgresql-integration-validation.md](/Users/m4/Documents/PlannoCorp/plannostore/docs/postgresql-integration-validation.md)
- [docs/validation/postgresql-2026-10-11/results.json](/Users/m4/Documents/PlannoCorp/plannostore/docs/validation/postgresql-2026-10-11/results.json)
- [docs/validation/postgresql-2026-10-11/postgres-verify.log](/Users/m4/Documents/PlannoCorp/plannostore/docs/validation/postgresql-2026-10-11/postgres-verify.log)
- [docs/validation/postgresql-2026-10-11/h2-regression.log](/Users/m4/Documents/PlannoCorp/plannostore/docs/validation/postgresql-2026-10-11/h2-regression.log)

## Riscos remanescentes e pendências para produção

- Validar a versão exata do PostgreSQL de destino e o runtime/CI de produção. A plataforma testada foi macOS com processo nativo; Testcontainers/Docker e CI Linux PostgreSQL não foram executados nem configurados automaticamente.
- Mercado Pago, Storage e efeitos externos continuam mocks/HTTP interceptado. Não foram comprovados pagamentos remotos, webhooks realmente recebidos, validade de preferências ou estornos externos.
- Validar pool e comportamento sob carga/múltiplas JVMs. O journal e registro de webhook usam conexão adicional com a transação financeira suspensa. Concorrência funcional local não é teste de capacidade.
- Definir e validar operação de reconciliação das iniciações incertas, com rastreabilidade e resolução segura de registros órfãos. Reservas de pagamentos ativos continuam protegidas conforme política autorizada.
- Aprovar política específica e plano de execução para legados; não há autorização para backfill/cancelamento em massa.
- Agendamento em tempo real, interrupção abrupta de processo e recuperação após crash não executados. Rotinas e concorrência foram chamadas diretamente.
- Backup/restauração, TLS, observabilidade e infraestrutura real não foram validados. Não há RPO/RTO inventados.
- FKs compostas de tenant e CHECKs adicionais permanecem propostas de defesa futura; não há achado de bypass de API confirmado por essa lacuna nesta missão.

Sequência de release apenas proposta: confirmar versão/runtime de destino; validar integrações e operação; preparar backup/restauração isolada e revisão do pacote; então solicitar autorização específica para migração/deploy. V26/V27 são aditivas, mas reversão de código não deve descartar journal nem ignorar iniciações incertas. Um plano de rollback precisa preservar evidência financeira e política de expiração; não foram executados rollback/destruição em banco real.

A persistência dos cenários cobertos foi validada em PostgreSQL local. A aprovação local não certifica a utilização por clientes reais sem as validações operacionais e externas acima.

## Reexecução — 20/10/2026 — alterações locais atuais

- Bundle PostgreSQL 17.8 `embedded-postgres-binaries-darwin-arm64v8:17.8.0` baixado do Maven Central e conferido com SHA-256 `19022eadefcbb09274519e88daa3cf3f5888d5fdd96beaf63922285970e6adc3`, conforme o registro de distribuição acima. Executáveis ficaram somente em diretório privado temporário e foram removidos após a execução; nenhuma instalação global ou mudança no harness foi feita.
- `./mvnw -o -B -Ppostgres-integration -Dplanno.test.postgres.bin-dir=/private/tmp/planno-pg-bin.E6PZC7/bin verify` — **296 testes aprovados, 0 falhas/erros/skips; BUILD SUCCESS** no snapshot local atual, inclusive contrato OAuth e regressões recentes de reserva, concorrência e segurança.
- O Flyway validou e aplicou V1–V27 em PostgreSQL 17.8. O cluster descartável ficou limitado a `127.0.0.1`, usou role de aplicação sem privilégios de superusuário e foi encerrado/removido; `target/postgres-test-artifacts/cleanup.txt` confirma o cleanup.
- O teste H2 completo do mesmo snapshot continua aprovado: **288 testes, 0 falhas/erros/skips**. Nenhuma chamada externa Mercado Pago/Storage foi feita; esta evidência não valida credenciais, pagamentos, OAuth externo, webhooks, estornos ou publicação reais.

## Validação posterior — 20/10/2026 — contrato Hub↔Store

- `./mvnw -o -B -Dspring.profiles.active=test test` — **289 testes H2 aprovados, 0 falhas/erros/skips**, no estado local após adicionar comparação exata entre o header `Idempotency-Key` e a chave JSON. O teste focal dessa rota executou **11/11** e rejeitou ausência, divergência e espaços externos sem criar loja.
- O ensaio integrado em `plannohub/docs/features/j3/PROGRESS.md` executou a rota real Hub→Store com dois PostgreSQL 17 descartáveis, incluindo primeira resposta perdida, reinício e retry; confirmou uma loja, operação e admin. Assim, a alteração da chave foi exercitada em PostgreSQL real, sem repetir toda a suíte de persistência nesta data.
- Este ensaio não cobre checkout/pagamento do cliente; Mercado Pago, DNS/TLS e publicação continuam gates externos.

## Reexecução — 20/10/2026 — V28 e recuperação concorrente de checkout

- O bundle PostgreSQL 17.8 foi obtido do Maven Central e conferido com SHA-256 `19022eadefcbb09274519e88daa3cf3f5888d5fdd96beaf63922285970e6adc3`. Os executáveis e arquivos foram extraídos somente em `/private/tmp` e removidos ao final.
- `./mvnw -o -B -Ppostgres-integration -Dplanno.test.postgres.bin-dir=/private/tmp/planno-pg-17.8/bin verify` — **299 testes aprovados, 0 falhas/erros/skips; BUILD SUCCESS** em PostgreSQL 17.8. Flyway aplicou V1–V28; o teste de upgrade V25→V28 preservou pedidos, pagamentos, reservas e legados, e verificou que a chave loja/carrinho é única.
- `PostgresPersistenceIT` passou **9/9**, inclusive duas requisições de checkout simultâneas: ambas receberam o mesmo pedido, ficou uma ordem e uma reserva de estoque. A rotina de cleanup confirmou a parada e remoção do cluster descartável em loopback.
- `./mvnw -o -B -Dspring.profiles.active=test test` passou **290 testes H2, 0 falhas/erros/skips**. Nenhum serviço externo, credencial, pagamento ou banco existente foi usado.
- Essa evidência valida a Store local. Não valida Mercado Pago externo, OAuth de produção, DNS/TLS, backup/restauração ou configuração operacional de uma nova loja.

## Validação posterior — 20/10/2026 — autenticação por e-mail e primeira loja

- `./mvnw -o test` passou **291 testes H2, 0 falhas/erros/skips**; Flyway aplicou V1–V29. A migration V29 cria o armazenamento de desafio OTP.
- O ensaio process-level Hub↔Store foi repetido depois das migrations Hub V55–V58 e Store V25–V29. Ambos os processos iniciaram contra PostgreSQLs 17 descartáveis separados e convergiram após resposta perdida, parada e retry; uma loja, operação e admin permaneceram persistidos.
- O pacote PostgreSQL nativo usado no perfil completo anterior não estava disponível para repetir agora o conjunto PostgreSQL de 299 testes. O ensaio integrado valida aplicação/migrations e retry em PostgreSQL, mas não substitui a suíte inteira neste snapshot.
- Login, OTP e entrega de email foram testados com `JavaMailSender` mockado; SMTP externo não foi chamado. Consulte [prontidão da primeira loja](first-store-publication-readiness.md) para os gates restantes.
