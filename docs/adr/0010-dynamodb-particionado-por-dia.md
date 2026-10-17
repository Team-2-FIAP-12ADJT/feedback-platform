# ADR 0010 — DynamoDB particionado pelo dia da submissão; o aviso sai do Streams

- **Status:** aceita
- **Data:** 2026-10-17

## Contexto

O banco das Evaluations precisa atender cinco coisas já decididas:

| Necessidade | Origem |
|---|---|
| Gravar cada Evaluation recebida | [ADR 0003](0003-tres-funcoes-aviso-nasce-da-gravacao.md) |
| Disparar a `send-urgency-notice` **a partir da gravação** de uma Critical Evaluation, sem a `receive-evaluation` publicar um evento depois de gravar | [ADR 0003](0003-tres-funcoes-aviso-nasce-da-gravacao.md) |
| Marcar `urgencyNoticeSentAt` na própria Evaluation | [ADR 0005](0005-entrega-pelo-menos-uma-vez.md) |
| Ler todas as Evaluations de uma Report Week sem varrer a tabela | [ADR 0008](0008-regras-do-weekly-report.md) |
| Expirar sozinho o que passar de 12 semanas | [ADR 0009](0009-dados-gravados-retencao-e-logs.md) |

## Decisão

**DynamoDB, cobrança por requisição (on-demand).** Serverless, sem VPC para as funções e sem custo parado. Duas das
cinco necessidades são recurso nativo: **Streams** (a gravação vira evento) e **TTL** (o item expira sozinho).

### Chaves

| Atributo | Papel | Valor |
|---|---|---|
| `submissionDay` | Chave de partição | Data do Submission Time no fuso `America/Sao_Paulo`, `yyyy-MM-dd` |
| `submissionKey` | Chave de ordenação | `submissionTime#id` — o Submission Time em UTC (ISO-8601) e o `id`, que desempata |
| `expiresAt` | Atributo de TTL | Submission Time + 12 semanas, em segundos Unix |

Os três são derivados de campos que a [ADR 0009](0009-dados-gravados-retencao-e-logs.md) já grava: não acrescentam dado
do Student.

- **O Weekly Report faz 7 consultas**, uma por dia da Report Week, cada uma paginada até o fim. A contagem por dia sai
  da própria consulta.
- **A partição é o dia, não a semana:** a regra de Report Week ([ADR 0008](0008-regras-do-weekly-report.md)) fica no
  código, não gravada nos dados. Mudar a regra não exige regravar nada.
- **O dia é calculado em Brasília**, o mesmo fuso da Report Week: uma Evaluation de domingo 23:30 em Brasília cai no
  domingo, não na segunda em UTC.

### Gravação → Urgency Notice

- A tabela publica no **DynamoDB Streams**, que dispara a `send-urgency-notice`.
- **O filtro fica na assinatura do evento**, não na função: só item novo (`INSERT`) com `urgency = CRITICAL`. Assim, uma
  Evaluation `MEDIUM` ou `LOW` não acorda a função, e dois eventos que a tabela gera sozinha são descartados: a marcação de
  `urgencyNoticeSentAt` (`MODIFY`) — que reenviaria o e-mail — e a expiração pelo TTL (`REMOVE`).
- **Falha:** a assinatura reporta só os itens que falharam (`ReportBatchItemFailures`, [ADR 0005](0005-entrega-pelo-menos-uma-vez.md))
  e, esgotadas as tentativas, manda o evento para uma **fila SQS de falhas (DLQ)** — o gatilho principal do Operational
  Alert ([ADR 0007](0007-operational-alert-separado-do-urgency-notice.md)). Tentativas e retenção na
  [ADR 0017](0017-tentativas-e-filas-de-falha.md).

## Alternativas consideradas

- **Partição pela Report Week**: uma consulta por relatório em vez de sete, mas a regra da semana passaria a morar nos
  dados — mudar o dia de início ou o fuso exigiria regravar a tabela.
- **Partição pelo `id`**: cada Evaluation sozinha na sua partição; ler uma semana exigiria varrer a tabela ou um índice
  secundário só para isso.
- **RDS ou Aurora (PostgreSQL)**: consulta livre em SQL, mas exige VPC para as funções, cobra com o banco parado, e o
  banco não dispara função ao gravar — a `receive-evaluation` teria de publicar o evento depois de gravar, a janela de
  falha que a [ADR 0003](0003-tres-funcoes-aviso-nasce-da-gravacao.md) evita.
- **S3, um objeto por Evaluation**: dispara evento ao gravar, mas ler uma semana vira listar milhares de objetos, e marcar
  `urgencyNoticeSentAt` vira regravar o objeto.

## Consequências

- **Sem consulta livre.** A tabela é modelada para dois acessos — gravar e ler um dia. Um terceiro acesso exige índice
  novo.
- **A semana inteira é lida para a memória**: o Average Score e as contagens consideram todas as Evaluations, e o teto de
  500 da [ADR 0008](0008-regras-do-weekly-report.md) vale só para a lista. Milhares de Evaluations são poucos MB.
- **O TTL não apaga na hora exata** — pode levar até uns dois dias depois de `expiresAt`. Nada depende disso: o relatório
  lê a semana pelas chaves, não pela ausência dos itens antigos.
- **Todas as Evaluations de um dia ficam na mesma partição.** Uma partição aguenta 1.000 gravações por segundo, muito
  acima do volume esperado; o limite de taxa na borda ([ADR 0006](0006-endpoint-anonimo.md)) fica bem abaixo disso.
- Teste de integração contra DynamoDB emulado em container (DynamoDB Local ou LocalStack).
