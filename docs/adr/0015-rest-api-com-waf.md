# ADR 0015 — API Gateway REST com AWS WAF na frente do `POST /avaliacao`

- **Status:** aceita
- **Data:** 2026-10-17

## Contexto

A [ADR 0006](0006-endpoint-anonimo.md) decidiu que o `POST /avaliacao` é anônimo e que a borda limita a taxa de
requisições e o tamanho do corpo. O [contrato](../contracts/receive-evaluation.md) promete `413` e `429` respondidos pela
borda, sem chegar à `receive-evaluation`.

O risco que motiva os limites: um script envia milhares de Evaluations com Score 0, e cada uma vira um Urgency Notice.

Fatos que decidem a escolha:

- **Nenhum tipo de API Gateway configura tamanho máximo de corpo**; o limite fixo é 10 MB. Um limite próprio na borda
  exige o **AWS WAF**.
- **O WAF se acopla à REST API, não à HTTP API.**
- Sem limite por IP, uma enxurrada esgota a cota de todos: os Students legítimos também recebem `429`.

## Decisão

**API Gateway REST (regional), com uma web ACL do AWS WAF.**

| Camada | Regra | Resposta |
|---|---|---|
| WAF — tamanho | Corpo acima de **8 KB** é bloqueado; corpo além do que o WAF inspeciona conta como acima | `413` |
| WAF — taxa por IP | Mais de **300 requisições em 5 minutos** do mesmo IP | `429` |
| Estágio da API — teto global | **20 requisições por segundo**, pico (*burst*) de **50** | `429` |
| Recurso da API | Só `POST /avaliacao`; outras rotas e métodos não chegam à função | `403`/`404` do gateway |

- **8 KB:** a Description tem até 1.000 caracteres, e um caractere ocupa até 4 bytes em UTF-8 — o pior caso, com o JSON
  em volta, fica abaixo de 8 KB.
- **300 em 5 minutos por IP**, e não menos: uma turma inteira atrás da mesma rede da faculdade sai por um IP só.
- **O teto global** limita o custo de uma enxurrada distribuída por muitos IPs, que o limite por IP não pega.
- **Logs de acesso do gateway com retenção de 14 dias.** Eles guardam o IP, que a Evaluation não guarda
  ([ADR 0009](0009-dados-gravados-retencao-e-logs.md)); servem para investigar uma enxurrada, e por isso a retenção é
  curta.
- As métricas do gateway (requisições, 4xx, 5xx, latência) alimentam o gatilho de volume anormal do Operational Alert
  ([ADR 0007](0007-operational-alert-separado-do-urgency-notice.md)).

## Alternativas consideradas

- **Lambda Function URL, sem gateway**: sem custo fixo, mas toda requisição invoca a função — inclusive as de um ataque —,
  o único freio é o teto de concorrência, que barra também os Students, não há log de acesso, e o `413` do contrato só
  valeria acima de 6 MB.
- **HTTP API**: mais barata por requisição e mais simples, com limite global, logs e métricas, mas sem WAF: perde o limite
  por IP, e o `413` só valeria acima de 10 MB.

## Consequências

- **Custo fixo do WAF**, por volta de US$ 7 por mês (web ACL mais uma cobrança por regra), e a REST API custa mais por
  requisição que a HTTP API. Cabe no orçamento de US$ 80 por mês; os valores são conferidos no primeiro mês.
- O limite por IP freia um script, mas não uma enxurrada vinda de muitos IPs: o teto global e o alarme de volume cobrem
  esse caso, e o risco segue declarado no README.
- O contrato da rota não muda: `413` e `429` continuam vindo da borda.
