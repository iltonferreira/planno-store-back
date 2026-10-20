# Validação Mercado Pago — 2026-10-12

**Resultado: CORRIGIDO, MAS NÃO TOTALMENTE VALIDADO.** Os defeitos de contrato abaixo foram corrigidos e as suítes locais passaram. A validação externa cobriu consultas e tokenização de cartão fictício. Três trocas OAuth reais responderam HTTP 200 e foram rejeitadas: a segunda identificou `live_mode_not_false`; a terceira comprovou **ausência de live_mode**, mesmo com test_token como string "true". Nenhum token resultante foi salvo ou usado. Nenhum pagamento, preferência ou estorno foi criado. Não há evidência suficiente para liberar clientes reais.

## Escopo e ambiente

Checkout Pro/Preferences, Checkout Transparente/Payments, OAuth, assinatura de webhook, refunds, reconciliação, idempotência e falhas externas. AGENTS.md e skills production-readiness, bug-investigation e security-audit aplicados; revisão e QA especializados. Branch homolog, mudanças anteriores preservadas. Sem commit, push, deploy, banco de produção, pagamento real ou alteração de preços/comissões.

Java 21, Spring Boot 3.5.16, Maven do projeto; H2 em memória e PostgreSQL 17.8 descartável no perfil existente. As integrações Java com Mercado Pago/Storage continuam simuladas. O cluster PostgreSQL foi encerrado e excluído após a execução.

Credenciais Access Token TEST e Public Key TEST estão em arquivo privado fora do Git. Seus valores não constam neste relatório. A conta Comprador existente “Tester Ilton” foi autorizada pelo usuário; não foi criada outra conta nem renovada a senha. O usuário concluiu o login na janela privada do Brave e o e-mail de teste foi identificado. Posteriormente autorizou usar o Client ID/Client Secret já existentes exclusivamente para obter OAuth sandbox, e preencheu o arquivo privado separado. Esse arquivo foi validado sem exibir valores e o Client ID corresponde à aplicação. Ainda não foram obtidos token OAuth de vendedor de teste, refresh token ou segredo de assinatura de webhook.

Configuração OAuth existente tem read/write/offline_access, PKCE desabilitado e retorno cadastrado fora do loopback. O formulário rejeitou HTTP (exige HTTPS) e depois HTTPS em 127.0.0.1 e localhost ("Revise a URL inserida"). O endereço anterior foi restaurado no rascunho naquele diagnóstico. [Evidência inicial de validação do formulário](validation/mercadopago-20261012/oauth-callback-form-validation.json).

O auxílio scripts/mercadopago-oauth-test.py usa test_token=true fixo, PKCE S256/state, listener HTTPS somente em 127.0.0.1, TLS mínimo 1.2 e certificados/chaves privados 600; valida TEST/live_mode=false/vendedor esperado antes de persistir. Aceita redirect de um único subdomínio trycloudflare.com com caminho exato, sem portas, query, fragmentos ou credenciais na URI. Host/API/escopo do listener permanecem restritos; nenhuma truststore foi alterada. Não usar o cliente atual da Store, que fixa test_token=false, para esse teste.

Após autorização específica do usuário, foi criado um túnel Cloudflare temporário, limitado ao callback HTTPS OAuth e encerrado após **350,16 segundos** (limite autorizado: 15 minutos). Binário oficial cloudflared 2026.10.0 com digest verificado, instalado apenas em diretório temporário; certificado local validado pelo processo do túnel. O usuário autorizou cadastrar/remover a URI temporária preservando a original e confirmou o cadastro. O usuário também confirmou o login como vendedor de teste e autorizou, na tela final, as permissões e termos apresentados. A troca autenticada foi realizada diretamente com o Mercado Pago, sem Client Secret ou tokens atravessando o túnel.

O probe público sem code/state retornou 400. O callback válido consumiu a sessão antes de uma única troca POST, que respondeu 200, mas a resposta falhou no contrato local. A evidência daquele código registrava somente rejeição genérica, portanto **não permite determinar qual campo falhou nem afirmar que o provedor devolveu credencial de produção**. Nenhuma resposta bruta foi guardada, nenhum token foi persistido e o código não foi reutilizado. O callback exibiu 502 por essa rejeição. O usuário confirmou a remoção da URI temporária após o encerramento; a restauração não foi conferida independentemente porque o controle do Brave passou a expor apenas a moldura. [Evidência da tentativa](validation/mercadopago-20261012/oauth-test-attempt-1.json). A concessão OAuth da conta de teste foi autorizada; removê-la do painel de permissões é uma operação distinta de remover a URI.

Depois de corrigir, testar e revisar o diagnóstico, o usuário autorizou **uma segunda tentativa** com nova sessão e outro túnel. O provedor reutilizou o consentimento existente e retornou antes de o receptor iniciar. A sessão ainda estava não consumida; recarregar esse callback entregou o código ao receptor e produziu exatamente **uma troca OAuth nessa sessão**, sem retry de código consumido. A API respondeu 200, e o diagnóstico indicou `live_mode_not_false`. As verificações de prefixo TEST de access_token e public_key, anteriores a esse predicado, passaram; as verificações posteriores de vendedor/expiração/refresh/scopes/Bearer não foram concluídas. Não foi retido o valor exato de live_mode: a evidência distingue somente que não era o booleano false, sem determinar ausência, true ou tipo inesperado. Isso é um bloqueio de validação frente à restrição autorizada, não prova de cobrança em produção ou vulnerabilidade do Mercado Pago.

O segundo túnel foi encerrado após **249,58 segundos**, com exit 0, e o usuário confirmou remover a segunda URI temporária preservando a original. Conferido independentemente: sessão consumida e arquivo de credenciais inexistente. Nenhum dos túneis permaneceu ativo; nenhum endpoint de webhook ou backend foi publicado. [Evidência da segunda tentativa](validation/mercadopago-20261012/oauth-test-attempt-2.json). Para continuar com comissão/OAuth é necessário esclarecer essa divergência e comprovar o contrato de teste exigido, preservando a guarda atual; HTTP 200 e prefixo TEST isoladamente não autorizam seu uso.

Na missão focada de divergência, a referência oficial revelou test_token como string e live_mode como booleano opcional. O auxílio foi alinhado de booleano true para string "true" e ganhou classificação segura de presença/tipo. Uma terceira tentativa explicitamente autorizada respondeu 200 e comprovou `live_mode_shape=absent`. A omissão é compatível com o campo opcional publicado, mas não satisfaz a exigência explícita de false; bloqueio mantido, nenhum token salvo/usado. Não inferir a causa das primeiras respostas a partir desta última. Túnel encerrado após 213,84s, URI removida conforme confirmação do usuário. [Investigação completa, hipóteses A–D e distinção do cliente Java](mercadopago-oauth-divergence.md); [evidência externa](validation/mercadopago-20261012/oauth-divergence-external.json).

## HTTP real executado

Host fixo `https://api.mercadopago.com`, HTTPS validado, redirects bloqueados. Foram realizadas **oito requisições programáticas à API**, quatro GET, uma tokenização POST e três trocas OAuth POST. As cinco primeiras usam credenciais TEST previamente verificadas; as trocas OAuth usam exclusivamente o par Client ID/Secret autorizado: true booleano nas primeiras duas e string "true" na terceira:

| Operação | Resposta observada | Limite da evidência |
|---|---|---|
| GET /v1/payment_methods, duas execuções | 200, JSON com 20 meios, Visa presente | Consulta de meios aceita; não comprova cobrança |
| GET /v1/payments/search com referência fictícia nova, duas execuções | 200, results vazio | Contrato de busca/autenticação; não comprova recuperação de pagamento existente |
| POST /v1/card_tokens, uma execução | 201, token presente, status active, Luhn verdadeiro, live_mode=false | Tokenização real de cartão oficial fictício; token não salvo nem usado |
| POST /oauth/token, três execuções, sessões/códigos distintos | 200 nas três; resposta rejeitada pelo contrato local; token_saved=false | Primeira rejeição genérica; segunda live_mode_not_false; terceira live_mode ausente comprovado. Transporte/trocas externos ocorreram; credencial utilizável de teste não foi validada |

Resumos e hashes das respostas estão em [evidência inicial](validation/mercadopago-20261012/mp-preflight-20261012.json) e [evidência da tokenização](validation/mercadopago-20261012/mp-tokenization-20261012.json). Corpos brutos, tokens, senhas e e-mail não foram gravados nessas evidências. A tokenização utilizou autenticação Bearer TEST; não comprova o SDK frontend/Public Key nem um E2E.

O script opt-in [mercadopago-test-preflight.py](../scripts/mercadopago-test-preflight.py) executa somente consultas por padrão. `--tokenize-test-card` permite exclusivamente a fixture fictícia fixa dos cartões oficiais. Nunca cria pagamento nem aceita cartão arbitrário, token APP_USR ou URL externa. Qualquer modo inesperado na resposta de tokenização deve impedir avanço para etapas posteriores.

## Defeitos reproduzidos e correções

1. **Identidade do recebedor:** o adapter ignorava `collector_id`, usado na resposta oficial de Payments. Passou a priorizá-lo sobre os fallbacks anteriores. A regressão falhou antes da correção por identidade nula. Evidência: MercadoPagoPaymentProviderClient.java:194; MercadoPagoHttpAdapterContractTests.java:140 e :166.
2. **Valor monetário:** strings monetárias eram convertidas silenciosamente em zero; valores ausentes/negativos/com precisão inválida não falhavam de maneira segura. Agora números e strings válidas são convertidos para BigDecimal exato e dois decimais, sem arredondar frações de centavo. Ausência/valor inválido retorna erro controlado. Evidência: MercadoPagoPaymentProviderClient.java:307; testes :154, :214 e :242.
3. **Precisão antes da validação:** o reader padrão Jackson podia transformar o literal JSON em double, apagando frações ou centavos antes de validar. Reader local com USE_BIG_DECIMAL_FOR_FLOATS nas cinco leituras preserva o valor original, sem mudar o ObjectMapper global. **Nove casos falharam na reprodução e passaram na correção**. Evidência: MercadoPagoPaymentProviderClient.java:275.
4. **Consulta de refund:** a rota anterior não incluía o pagamento pai. Adapter e consumidores agora utilizam `/v1/payments/{paymentId}/refunds/{refundId}`. Para refunds locais conhecidos, loja/conta são verificadas antes do HTTP e novamente antes dos efeitos. A overload anterior permanece para compatibilidade do stub; no adapter real, falta de IDs resulta em 409 antes de rede. Evidências: adapter :243/:248; PaymentProviderClient.java:19; PaymentReconciliationService.java:75; WebhookService.java:150; RefundService.java:156.
5. **Instrumentação de teste:** QA reproduziu vazamento de token fictício refletido em campos arbitrários de erro do preflight. O script passou a registrar apenas presença de erro e quantidade de causas, nunca strings recebidas. Não houve exposição de token real nesse teste. Regressão Python passou após a correção.
6. **Gravação local do OAuth:** o QA reproduziu erro de fsync após flush que deixava credenciais fictícias em arquivo privado enquanto o auxílio informava token_saved=false. O sucesso passou a ser confirmado somente após gravação/fechamento de token e evidência; falhas limpam exclusivamente os arquivos criados por esse handler, com verificação de dispositivo/inode. Se a limpeza falhar, informa estado desconhecido e inspeção necessária. As regressões de fsync de token/evidência e limpeza passaram. Nenhum token real foi gerado nesse ensaio.
7. **Diagnóstico de OAuth rejeitado:** a tentativa externa registrou apenas rejeição genérica. Agora o auxílio registra um código fixo para o primeiro campo inválido: JSON, prefixos TEST, live_mode, vendedor, expiração, refresh, scopes ou Bearer. Não registra valores externos nem relaxa validações. Doze novas regressões passaram, incluindo valores hostis/reflexão de segredos fictícios e compatibilidade SafeError. O diagnóstico novo não esclarece retroativamente a resposta anterior.

As duas primeiras reproduções produziram 48 casos/2 falhas no conjunto intermediário. A reprodução de precisão produziu 9 casos/9 falhas. Esses são resultados RED deliberados anteriores à correção, não falhas ocultadas nem a situação final.

## Arquivos desta missão

Modificados: MercadoPagoPaymentProviderClient.java; PaymentProviderClient.java; PaymentReconciliationService.java; RefundService.java; WebhookService.java; MercadoPagoPaymentStatusContractTests.java. Neste último, o fixture passou a incluir transaction_amount válido; as verificações de status permaneceram.

Adicionados: MercadoPagoHttpAdapterContractTests.java; MercadoPagoWebhookSignatureContractTests.java; scripts/mercadopago-test-preflight.py; scripts/test_mercadopago_test_preflight.py; scripts/mercadopago-oauth-test.py; scripts/test_mercadopago_oauth_test.py; SECURITY_AUDIT.md (registro restrito desta revisão); este relatório e os resumos em docs/validation/mercadopago-20261012. Mudanças anteriores de checkout, reservas, PostgreSQL e segurança permanecem no checkout e não são atribuídas a esta missão.

## Testes e resultados finais

| Suíte | Aprovados | Falhas | Erros | Ignorados |
|---|---:|---:|---:|---:|
| Java H2 clean verify | 280 | 0 | 0 | 0 |
| Java PostgreSQL 17.8 clean verify | 288 | 0 | 0 | 0 |
| Python, guardas do preflight, rede simulada | 18 | 0 | 0 | 0 |
| Python, guardas/ciclo/TLS/redirect/diagnóstico do auxílio OAuth, provider simulado, principal e revisor | 54 | 0 | 0 | 0 |

Os perfis Java repetem grande parte dos mesmos casos; **não são 568 testes distintos**. Foram acrescentados **106 casos Java**, sendo 82 no adapter e 24 no verificador HMAC. Não houve remoção de casos existentes.

Classificação H2: 146 casos unitários locais e 134 de integração Spring/H2. Classificação PostgreSQL: os mesmos 146 locais e 142 integrações reais de persistência. Nos dois perfis, Mercado Pago e Storage permanecem mocks. MockRestServiceServer valida HTTP interceptado/payloads e erros; não é HTTP real no sandbox.

Cobertura nova: total financeiro/frete/descontos nos snapshots, metadados/idempotência, collector_id, valor numérico/string/precisão, refunds parciais/totais e rota GET, erros 401/403/429/500, JSON inválido, conexão/timeout simulados, expiração/desconexão de conta e formulário OAuth. HMAC real local com segredo fictício: assinaturas válidas/alteradas, headers/data/ts inválidos, lowercase e modo mock explícito. Nenhuma nova janela de validade de timestamp foi inventada.

As regressões anteriores de estorno acumulado, concorrência de estoque/refunds, aprovação após cancelamento sem movimentar estoque, checkout autorizado, reservas e iniciação incerta continuam incluídas nas suítes verdes. Isso comprova efeitos locais com persistência, não comportamento financeiro remoto.

Comandos executados pelo QA:

```sh
./mvnw -o -B clean verify
./mvnw -o -B -Ppostgres-integration -Dplanno.test.postgres.bin-dir=/private/tmp/planno-postgres-runtime-jytspz18/runtime/bin clean verify
python3 -B -m unittest discover -s scripts -p test_mercadopago_test_preflight.py -v
git diff --check
```

BUILD SUCCESS em ambos os perfis: H2 27.430s; PostgreSQL 21.612s. Sumários conferidos independentemente pelos XMLs Surefire: [H2](validation/mercadopago-20261012/h2-summary.json), [PostgreSQL](validation/mercadopago-20261012/postgres-summary.json). [Limpeza do cluster](validation/mercadopago-20261012/postgres-cleanup.txt). Logs/XMLs completos e reproduções RED/GREEN preservados privadamente em `/private/tmp/planno-mp-evidence/`.

## Revisão e QA

Revisor: sem novos achados nas correções finais de identidade, dinheiro e refund; reader decimal local e validação de loja/conta preservam os contratos e o isolamento. Revisou também guardas de TEST, host/redirects e tokenização opt-in. A avaliação não certifica fluxo externo completo.

QA da missão anterior: suítes H2/PostgreSQL e 18 guardas Python aprovadas; confirmou as regressões relevantes e ausência de testes ignorados. QA executou somente rede simulada; as oito requisições reais descritas acima foram executadas pelo agente principal. A regressão Python de campos refletidos foi encontrada e resolvida. Os três contratos OAuth externos foram rejeitados; isso não altera a contagem de falhas das suítes locais, mas constitui falha explícita da validação externa.

Dos 38 casos OAuth aprovados pelo QA anterior, 26 cobrem a preparação e o ciclo original: oito guards, seis regressões do ciclo de gravação, seis verificações de TLS e seis de redirect restrito. Confirmam consumo antes da chamada, bloqueio de retry incerto, arquivos 600, limpeza em falhas de fsync, proteção de inode e estado desconhecido se limpeza falhar; TLS obrigatório e carregamento por descritores; URI local ou formato temporário permitido, rejeição de URI manipulada e igualdade entre sessão/autorização/exchange. A falha inicial de gravação foi reproduzida e corrigida sem credenciais reais. O revisor aprovou a correção focal sem novos achados confirmados.

Também passou um smoke ad hoc com HTTPS/socket real em 127.0.0.1:8765, certificado fictício com SAN IP, cliente verificando certificado/hostname e sem modificar trust global. Callback inválido retornou 400 sem consumir sessão; válido retornou 200, consumiu sessão e salvou fixtures 600 após uma troca **mockada**. Servidor/socket/thread encerrados e fixtures removidas. Zero chamadas ao Mercado Pago nesse smoke; não valida transporte de túnel, OAuth externo, concorrência do lock ou callback/persistência da Store. Suíte Python combinada anterior à missão focada: **56 aprovados = 38 OAuth + 18 preflight**, 0 falhas/erros. [Sumário](validation/mercadopago-20261012/python-summary.json). O QA executou mais 12 regressões de diagnóstico com provider simulado. Logs em `/private/tmp/planno-mp-evidence/planno-mp-python-final.log`; comando final: `python3 -B -m unittest discover -s scripts -p 'test_mercadopago_*.py' -v`. Revisor confirmou motivos constantes sem reflexão de dados, mesmas validações e ausência de retry.

Atualização da missão focada OAuth: **72 aprovados = 54 OAuth + 18 preflight**, 0 falhas/erros/ignorados, executados pelo principal e independentemente pelo revisor; 16 métodos novos. O wire type falhou antes da correção e passou depois. Cobrem live_mode false/true/ausente/null/tipos inesperados, APP_USR, erros HTTP/rede e o handler completo com provider simulado. [Sumário desta missão](validation/mercadopago-20261012/oauth-divergence-tests.json). Os resultados Java acima são históricos e não foram repetidos nesta mudança exclusivamente Python/documental. O bloqueio observado pertence ao auxílio; o Java fixa test_token false e não valida live_mode antes da cifra/persistência, lacuna existente registrada na investigação.

## Pendências e plano priorizado

| Prioridade | Achado / evidência | Natureza e próxima validação |
|---|---|---|
| P1 | `charged_back` mapeado para REFUNDED (adapter :289); WebhookService.java:263 assume total; RefundService.java:243/:259 restaura estoque quando integral | Caminho confirmado por código. Contestação não equivale automaticamente a estorno comercial. Definir política e acrescentar regressão antes de liberar esse evento; nenhuma contestação real disparada |
| P1 | RefundService.java:80/:103/:111 faz persistência e HTTP na mesma transação; chave remota deriva de Refund UUID (adapter :223) | Risco de resultado incerto: efeito remoto seguido de timeout/rollback pode perder a intenção local e gerar nova chave na repetição. Não reproduzido externamente. Persistir intenção/chave durável e testar recuperação antes de retry |
| P1 | Comissão é enviada como application_fee; OAuth atual fixa test_token=false (:55), sem fluxo refresh_token nem guarda explícita de ambiente | Bloqueio para teste completo com comissão e renovação. Consentimento de teste autorizado; três trocas pelo auxílio isolado rejeitadas, terceira live_mode ausente comprovado. Obter do Mercado Pago contrato verificável para ambiente nessa omissão; não executar o exchange atual da Store como sandbox |
| P2 | PaymentReconciliationService.java:47 usa providerCheckoutId se payment ID está ausente e chama GET payment | Mismatch confirmado por código: preference ID não é payment ID. Implementar descoberta documentada via referência/merchant_order e comprovar recuperação |
| P2 | Refund externo parcial cumulativo não é lido no adapter de payment (transaction_amount_refunded/status_detail) | Lacuna de contrato/cobertura; testar evento real suportado e reconciliar saldo sem duplicar ledger |
| P2 | Refund desconhecido sem payment pai termina em 409 antes do HTTP | Falha segura, mas recuperação incompleta. Confirmar payloads/eventos reais e descoberta do pai; não inventar endpoint |
| P2 | PaymentInitiationJournalService preserva intenção; após rollback, não há reconstrução automática de Payment pela busca do provedor | Proteção local contra duplicação comprovada; recuperação operacional ainda incompleta. Testar timeout após efeito remoto e associação segura à loja |

Ainda não executados externamente: criação de preferência Pro e total no provedor; criação/estados de pagamento Transparente; cobrança de comissão/split; assinatura entregue pelo Mercado Pago; duplicados/fora de ordem; aprovação após cancelamento; refunds parciais/totais; falha real de conexão/timeout após efeito; recuperação de resultado incerto; expiração/refresh OAuth; reconciliação de pagamento existente; E2E comprador→Store→provedor→webhook→estoque. Sem endpoint publicado ou segredo de assinatura, o webhook externo não pode ser demonstrado.

A documentação atual de Pro/Preferences usa credenciais APP_USR da conta vendedor de teste. Isso conflita com a restrição explícita de usar somente TEST; tais credenciais não foram usadas. Para Transparente com comissão, a documentação da criação de payment informa que application_fee requer token obtido por OAuth. A comissão não foi retirada para produzir uma aprovação artificial.

Sequência recomendada: resolver os P1 locais e o modo seguro OAuth de teste; obter credenciais/consentimento da conta vendedor de teste; criar fixtures exclusivamente sandbox com comissão; comprovar estados, idempotência e refunds suportados; autorizar um endpoint de testes isolado e validar notificações; executar E2E e recuperar resultado incerto. Sem isso, **produção permanece não comprovada para os fluxos financeiros externos**. Não houve release ou promessa de monitoramento em segundo plano.

## Fontes oficiais consultadas

- [Teste Payments](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-payments/integration-test/make-test-purchase) e [cartões fictícios](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-payments/additional-content/your-integrations/test/cards).
- [Criar payment: application_fee e erro 2059](https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-api-payments/create-payment/post).
- [Consultar refund pelo pagamento pai](https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-api-payments/get-refund/get).
- [Contas de teste Pro/Preferences](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-preferences/test-accounts).
- [OAuth: criar/renovar token](https://www.mercadopago.com.br/developers/pt/reference/authentication/oauth/_oauth_token/post).
- [Estados de payment](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-payments/response-handling/query-results?scope=prod).
- [Tokenização via API](https://www.mercadopago.com.br/developers/pt/docs/resources-pci-compliant-merchants/receiving-payment-by-card) e [SDK Java oficial CardTokenClient](https://github.com/mercadopago/sdk-java/blob/master/src/main/java/com/mercadopago/client/cardtoken/CardTokenClient.java).
