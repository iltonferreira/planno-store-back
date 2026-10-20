# Correções dos bloqueadores financeiros — 2026-10-11

Resultado: os quatro defeitos estão **CORRIGIDOS E VALIDADOS no ambiente local**. A validação usa dados fictícios, H2, Spring/MockMvc, provedor simulado e HTTP interceptado. Não comprova Mercado Pago ou PostgreSQL reais, nem prontidão da Store para produção.

Trabalho realizado na branch `homolog`, preservando alterações anteriores. Sem commit, push, deploy, banco real, pagamentos ou estornos reais. Skill aplicada: `bug-investigation`; arquitetura, implementação, revisão e QA delegados conforme o escopo. A auditoria existente foi utilizada; não foi iniciada outra auditoria geral.

## Causas e correções

| Defeito | Causa confirmada | Correção | Classificação |
| --- | --- | --- | --- |
| FIN01: R$ 92,90 enviados como R$ 80,00 | A preferência do Checkout Pro somava linhas de produtos, omitindo ajustes do total e podendo divergir por arredondamento/descontos. | Uma linha de pedido cobra o snapshot financeiro exato. Guarda exige pagamento igual ao total do pedido e valor positivo em centavos. Frete e descontos permanecem calculados pelas regras existentes; comissões, metadados e URLs foram preservados. | CORRIGIDO E VALIDADO localmente |
| FIN02: aprovação após cancelamento | O webhook tentava consumir uma reserva já liberada. | Registra pagamento `APPROVED`, mantém pedido `CANCELLED`, preserva estoque e sinaliza reconciliação manual no webhook e na reconciliação administrativa, inclusive em reentregas. Política explicitamente aprovada pelo usuário; sem estorno automático. | CORRIGIDO E VALIDADO localmente |
| FIN03: replay de parcial devolvia estoque novamente | O marcador de devolução era consultado apenas no estorno recebido, embora outro estorno tivesse completado a devolução. | Efeito exige pedido `PAID` e ausência de marcador de devolução em qualquer estorno do mesmo pagamento/loja, sob locks de pedido e pagamento. A reconciliação repara status divergente sem repetir estoque. Entidades carregadas antes dos locks são atualizadas para impedir sobrescrita concorrente. | CORRIGIDO E VALIDADO localmente |
| FIN04: parcial seguido de total mantinha pedido `PAID` | A confirmação cumulativa reutilizava um registro parcial em vez de representar corretamente o saldo total confirmado. | Saldo interno identificado por `providerStateSummary` representa somente o valor ainda não atribuído a IDs individuais. Eventos posteriores absorvem esse saldo, inclusive até zero, preservando valores e identidades. Soma acima do pagamento, associações inconsistentes e legado ambíguo falham com conflito; pagamento/pedido chegam a `REFUNDED`, com uma única devolução. | CORRIGIDO E VALIDADO localmente |

## Arquivos desta missão

Produção:

- [MercadoPagoPaymentProviderClient.java](../src/main/java/com/planno/store/payment/service/MercadoPagoPaymentProviderClient.java)
- [WebhookService.java](../src/main/java/com/planno/store/payment/service/WebhookService.java)
- [PaymentReconciliationService.java](../src/main/java/com/planno/store/payment/reconciliation/service/PaymentReconciliationService.java)
- [RefundService.java](../src/main/java/com/planno/store/payment/refund/service/RefundService.java)
- [Refund.java](../src/main/java/com/planno/store/payment/refund/domain/Refund.java)
- [RefundRepository.java](../src/main/java/com/planno/store/payment/refund/repository/RefundRepository.java)
- [V26__refund_provider_state_summary.sql](../src/main/resources/db/migration/V26__refund_provider_state_summary.sql): coluna aditiva, padrão `false`, sem backfill.

Testes adicionados: [MercadoPagoCheckoutContractTests.java](../src/test/java/com/planno/store/MercadoPagoCheckoutContractTests.java), [LatePaymentApprovalIntegrationTests.java](../src/test/java/com/planno/store/LatePaymentApprovalIntegrationTests.java), [FinancialRefundRegressionTests.java](../src/test/java/com/planno/store/FinancialRefundRegressionTests.java). Documentação: [README.md](../README.md) e este relatório. Nenhum teste existente foi removido ou enfraquecido.

## Execuções e evidências

| Execução | Resultado observado | Log |
| --- | --- | --- |
| Checkout antes da correção | 14 casos; 12 falhas | `/private/tmp/planno-financial-fixes-pro-red.log` |
| Aprovação tardia antes da correção | 2 casos; 1 falha e 1 erro | `/private/tmp/planno-financial-late-red.log` |
| Estornos antes da correção | 2 casos; 2 falhas: estoque 10 em vez de 8 e pedido `PAID` em vez de `REFUNDED` | `/private/tmp/planno-financial-refund-red.log` |
| Concorrência antes da correção adicional | 2 casos; 1 falha e 1 erro: aprovação sobrescrita e conflito por saldo desatualizado | `/private/tmp/planno-financial-refund-race-two-red.log` |
| Primeira suíte completa | 102 casos; 1 falha no teste existente de reconciliação; interrompeu antes do pacote | `/private/tmp/planno-financial-final-verify.log` |
| Regressão da divergência antes de corrigi-la | 2 casos; 2 falhas, ambos estoque 10 em vez de 8 | `/private/tmp/planno-financial-refund-divergence-red.log` |
| Estorno e reconciliação após a correção | 28 aprovados; zero falhas/erros | `/private/tmp/planno-financial-refund-divergence-green.log` |
| Suíte completa final | **103 aprovados, zero falhas, zero erros, zero ignorados; 17 suítes; BUILD SUCCESS** | `/private/tmp/planno-financial-final-verify-2.log` |

Comandos relevantes, executados na raiz do repositório:

```sh
./mvnw -B -o -Dspring.profiles.active=test -Dtest=MercadoPagoCheckoutContractTests test
./mvnw -o -B -Dspring.profiles.active=test -Dtest=LatePaymentApprovalIntegrationTests test
./mvnw -B -Dspring.profiles.active=test -Dtest=FinancialRefundRegressionTests,RefundIntegrationTests,ReconciliationIntegrationTests test
./mvnw -o -B -Dspring.profiles.active=test clean verify
git diff --check
```

A execução final recompilou 245 fontes de produção e 18 fontes de teste, aplicou as 26 migrations até V26 em H2 e gerou o JAR. Dos 103 casos, 39 são novos: 15 de checkout, 4 de aprovação tardia e 20 de estorno; 64 já existiam. Inclui testes financeiros, webhooks, estoque, autenticação e isolamento disponíveis na suíte. Os XMLs finais estão em `target/surefire-reports/`. `git diff --check` passou. Não há gates adicionais de lint, cobertura ou análise de dependências configurados no POM.

## Revisão e QA

O revisor terminou sem achados abertos no escopo. Durante a revisão identificou a corrida de entidades de estorno desatualizadas; duas regressões determinísticas reproduziram o problema e validaram a atualização sob locks.

O QA encontrou uma regressão de compatibilidade na primeira suíte completa: status de pedido divergente fazia repetir uma devolução já registrada. O teste existente foi preservado, a causa foi corrigida e uma nova regressão cobre replay de outro parcial. O QA repetiu `clean verify` e confirmou os 103 casos aprovados, compilação, migrations H2 e pacote.

## Pendências e limites

- Validar Mercado Pago em ambiente de testes autorizado. Os testes atuais usam Stub/MockRestServiceServer; nenhuma transação externa foi realizada.
- Validar migrations, locks e concorrência em PostgreSQL descartável. Docker e `psql` não foram encontrados no PATH; H2 não comprova equivalência operacional.
- Conferir registros legados aprovados sem ID externo e sem marcação de saldo antes da liberação. V26 não infere esses registros; reconciliação ambígua retorna conflito. Nenhum reparo de dados reais foi executado.
- Operacionalizar a reconciliação manual dos pagamentos tardios; não foi criado fluxo logístico ou estorno automático.
- Os demais achados da auditoria anterior permanecem fora desta missão. E2E em navegador, infraestrutura real e prontidão global para produção não foram validados.
