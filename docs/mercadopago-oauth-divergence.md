# Investigação da divergência OAuth — 12/10/2026

**Resultado: causa do bloqueio atual identificada; solicitação alinhada ao contrato publicado; OAuth permanece bloqueado por falta de confirmação do ambiente.**

## Causa e evidência externa

Uma nova sessão autorizada realizou uma única chamada real `POST https://api.mercadopago.com/oauth/token`, com `grant_type=authorization_code` e `test_token="true"` como string JSON. A resposta foi HTTP 200; os prefixos TEST de access token e public key passaram. **O campo de primeiro nível `live_mode` estava ausente**, e não null, string, número ou booleano true. A guarda exige o booleano false e rejeitou a resposta antes da persistência.

O diagnóstico registra apenas classificações fixas, não tokens nem valores arbitrários. [Evidência externa sanitizada](validation/mercadopago-20261012/oauth-divergence-external.json): `live_mode_shape=absent`, `rejection_reason=live_mode_not_false`, `token_saved=false`, sessão consumida e arquivo de token inexistente. As verificações posteriores de vendedor, expiração, refresh, scopes e Bearer não foram concluídas; não se certifica a segurança da resposta inteira.

As duas tentativas anteriores não retiveram o corpo ou a classificação desse campo. Sua causa específica não pode ser reconstruída nem deduzida da tentativa atual.

## Documentação oficial e hipóteses

- [Criação OAuth](https://www.mercadopago.com.br/developers/pt/docs/security/oauth/creation): authorization_code depende de consentimento do vendedor, state identifica a tentativa e redirect deve corresponder ao cadastrado. A página instrui test_token true para testes e exemplifica o parâmetro como string. PKCE é recomendado.
- [Referência em Markdown](https://www.mercadopago.com.br/developers/pt/reference/authentication/oauth/_oauth_token/post.md), campos de request/response: `test_token` é string opcional; `live_mode` é booleano opcional e indica o modo da aplicação. O exemplo produtivo usa booleano true; não há nessa referência garantia de retorno obrigatório de false no teste.
- [Renovação](https://www.mercadopago.com.br/developers/pt/docs/security/oauth/renewal): refresh exige autorização prévia e offline_access; os novos access/refresh tokens precisam substituir os anteriores. Não é um fluxo para classificar ou liberar um token incerto. Nenhuma renovação foi executada.
- [Contas de teste](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-preferences/test-accounts): distingue vendedor, comprador e integrador; no Pro, documenta inclusive credenciais APP_USR do vendedor de teste. Prefixo isolado não substitui validação de ambiente; APP_USR continua proibido neste auxílio conforme o escopo autorizado.
- [Split Payments 1:1](https://www.mercadopago.com.br/developers/pt/docs/split-payments/split-1-1/integration-configuration/integrate-marketplace): Pro e Transparente usam OAuth do vendedor. Client credentials não substitui consentimento para operar em nome dele. Nenhum split foi executado ou validado externamente.

| Hipótese solicitada | Conclusão baseada em evidência |
|---|---|
| A. Solicitação incorreta | O booleano JSON anterior divergia do tipo string publicado. Foi alinhado para string "true"; a omissão persistiu. Não se comprova que o booleano causou os retornos anteriores ou que a API necessariamente o rejeita. |
| B. Resposta dentro do contrato | A omissão atual é compatível com o campo opcional publicado. Isso avalia a presença de live_mode, não certifica todos os campos da resposta. |
| C. Comportamento externo não documentado | Não confirmado para essa omissão, já compatível com o schema opcional. Não inventar causa interna no Mercado Pago. |
| D. Validação excessivamente restritiva | A guarda é mais restrita que o schema, mas cumpre a exigência explícita de não aceitar ausência, true ou valor inesperado. Não é uma validação a remover para desbloquear o fluxo. |

## Auxílio isolado e backend Java são fluxos distintos

O bloqueio observado ocorreu em [scripts/mercadopago-oauth-test.py](../scripts/mercadopago-oauth-test.py), e não no callback/persistência Java da Store. Essa distinção corrige a interpretação do contexto anterior.

No auxílio, a API é fixa, redirects HTTP são bloqueados, o request é JSON e authorization_code inclui verifier PKCE. `live_mode_shape` (:250) classifica presença/tipo; `validate_token_response` (:268) continua exigindo TEST nas duas chaves, false literal, vendedor esperado, expiração válida, refresh, scopes e Bearer. `exchange_code` (:294) envia string "true". Callback verifica state, host e caminho; sessão é consumida antes da chamada; retry de sessão consumida é bloqueado. Somente resposta totalmente validada pode criar credenciais privadas 600, com limpeza em falhas.

No backend:

- [MercadoPagoOAuthClient.java:55](../src/main/java/com/planno/store/payment/account/service/MercadoPagoOAuthClient.java) fixa `test_token="false"` em form-urlencoded no fluxo real. O parser lê tokens/identidade/expiração, mas não lê live_mode. Não foi usado na tentativa externa.
- [MercadoPagoOAuthTokenResponse.java:5](../src/main/java/com/planno/store/payment/account/service/MercadoPagoOAuthTokenResponse.java) não contém ambiente/live_mode.
- [MercadoPagoOAuthService.java:75](../src/main/java/com/planno/store/payment/account/service/MercadoPagoOAuthService.java) valida state existente, não consumido e prazo; obtém token e conecta a conta da loja associada ao state. Não implementa refresh. Seu comportamento de transação não foi alterado nesta missão.
- [PaymentProviderAccountService.java:122](../src/main/java/com/planno/store/payment/account/service/PaymentProviderAccountService.java) cifra e associa os tokens à loja sem guarda explícita de ambiente; [CredentialCipherService.java:37](../src/main/java/com/planno/store/payment/account/service/CredentialCipherService.java) usa AES/GCM. Criptografia não comprova ambiente.
- `application-test.yml` habilita mocks. `ProductionSafetyConfiguration` impede mocks sem perfil test/dev/local, mas não constitui uma proteção inversa contra credenciais produtivas em teste.

**Lacuna existente:** não atribuir ao backend Java as garantias TEST/false do auxílio. A validação real da Store em sandbox exige um modo explícito e controles antes da cifra e uso das credenciais; não usar seu caminho real atual para esse teste. Introduzir esse modo e seus contratos de persistência/consumo é uma mudança distinta, não um ajuste para aceitar a omissão. Java/configurações não foram modificados aqui.

## Correção implementada e arquivos

- `scripts/mercadopago-oauth-test.py`: string "true" no request; enum seguro de presença/tipo de live_mode. Sem conversão de string "false", zero, null ou ausência para false; sem alteração de guards, state, redirect, PKCE, prazo, retries ou contratos de armazenamento.
- `scripts/test_mercadopago_oauth_test.py`: 16 métodos novos e duas asserções antigas atualizadas para conferir o tipo/string exigido. Nenhum teste foi removido ou enfraquecido.
- Este relatório, resumo anterior, registro SECURITY_AUDIT.md e resumos sanitizados em docs/validation/mercadopago-20261012.

O teste de contrato do request falhou antes da correção (`"true" != True`) e passou depois. Isso reproduz a divergência local de tipo, não uma falha financeira no provedor.

## Testes, revisão e limitações

`python3 -B -m unittest discover -s scripts -p 'test_mercadopago_*.py' -v`: **72 aprovados = 54 OAuth + 18 preflight; 0 falhas, 0 erros, 0 ignorados**. [Sumário desta missão](validation/mercadopago-20261012/oauth-divergence-tests.json).

Cobertura: false booleano aceito com fixture TEST completa; true, ausência, null, string "false" e outros valores, números inclusive zero, arrays/objetos rejeitados; APP_USR em ambas as chaves rejeitado mesmo com false; JSON/UTF-8 inválidos, HTTP 400/401/403/429/500, HTTPError, falhas de rede/timeouts e nenhuma repetição automática. O ciclo completo do handler foi exercitado com HTTP/receptor simulados: respostas incertas consomem sessão e não deixam arquivo de token; fixture totalmente válida é salva em modo 600. Fixtures fictícias somente; isso não é sandbox nem E2E.

Revisor executou independentemente os mesmos 72 testes e git diff --check; nenhum novo achado de segurança introduzido. Confirmou a leitura A/B/C/D e o limite da evidência externa. Arquiteto identificou o contrato string/opcional e recomendou separar a mudança do auxílio do modo Java; sua investigação de referências adicionais foi interrompida pelo limite de uso da ferramenta. O principal concluiu a leitura dessas fontes oficiais. Não houve revisão arquitetural geral.

Somente **uma nova troca OAuth HTTP real** nesta missão. Ela falhou na guarda por ausência de live_mode. Nenhum pagamento/estorno, nenhuma utilização ou persistência de credenciais incertas. Os resultados Java H2/PostgreSQL do relatório anterior são históricos; não foram repetidos porque nenhum código Java foi alterado. Backend, Split, refresh e E2E continuam sem nova validação externa.

O túnel foi encerrado após 213,84 segundos, exit 0, abaixo do limite autorizado de 15 minutos. O usuário confirmou a remoção da URI temporária preservando a original. A restauração não foi verificada independentemente por leitura do painel.

## Pendência externa e próximo passo

Manter o bloqueio. Solicitar esclarecimento oficial ao Mercado Pago: em authorization_code de vendedor de teste com test_token string "true", por que live_mode é omitido e qual evidência programática documentada permite comprovar o ambiente da credencial nesse caso, inclusive no Split Payments 1:1? A resposta precisa esclarecer semântica/presença de live_mode e um contrato verificável de ambiente; HTTP 200, prefixo TEST ou o parâmetro enviado não substituem a política atual.

Nenhuma mensagem foi enviada ao provedor. Não anexar tokens, Client Secret, código OAuth, verifier, senhas ou resposta bruta a uma consulta. Se a confirmação oficial não fornecer um meio compatível com a restrição atual, o fluxo permanece bloqueado. Qualquer alteração dessa política exige decisão explícita do usuário e regressões; não foi aplicada automaticamente.

Sem commit, push, deploy, alteração de produção ou regras financeiras/comerciais.

## Atualização de implementação — 20/10/2026

A referência oficial consultada em 20/10 descreve os tipos de access token como `APP_USR` (em nome do usuário) e `TEST` (somente sandbox), e a documentação de criação OAuth instrui enviar `test_token=true` para credenciais de sandbox. A referência marca `live_mode` como opcional. Com base nesse contrato, o cliente Java passou a ter modo sandbox explícito no servidor: `APP_PAYMENT_MERCADO_PAGO_TEST_TOKEN=true` envia a string `true` e aceita somente prefixo `TEST-`; `live_mode`, se presente, deve ser booleano `false`. O default continua `false`, que exige `APP_USR-` e `live_mode=true`. Não há inferência de ambiente por public key, input do navegador ou valor ausente em modo de produção.

Testes locais sem conexão externa: adapter OAuth **89/89**; suíte H2 completa **288/288**, sem falhas, erros ou skips. A divergência local de política foi resolvida sem enfraquecer o modo de produção; a troca OAuth real, o token/seller conectado, refresh, split e ciclo de pagamento continuam sem validação externa nesta execução. PostgreSQL ainda não foi repetido; Colima está parado e não há `postgres`, `initdb` ou `pg_ctl` nativos disponíveis. Nenhuma credencial foi usada ou persistida e nenhuma operação financeira foi realizada.
