# ADR 0017 — Tentativas e filas de falha (DLQ) do Urgency Notice e do Weekly Report

- **Status:** aceita
- **Substituída em parte por:** [ADR 0022](0022-send-urgency-notice-le-a-marca.md) — a justificativa do tamanho do lote
- **Data:** 2026-10-17

## Contexto

As duas funções acionadas por evento têm fila de falhas (DLQ), e as duas filas disparam o Operational Alert
([ADR 0016](0016-alarmes-do-operational-alert.md)). Falta decidir quanto insistir antes de desistir.

- A `send-urgency-notice` lê o DynamoDB Streams ([ADR 0010](0010-dynamodb-particionado-por-dia.md)), que entrega **em
  ordem**: um item que falha segura os que vêm atrás. O padrão da AWS é tentar para sempre — travar para sempre.
- A `send-weekly-report` é invocada pelo EventBridge Scheduler toda segunda às 08:00 de Brasília
  ([ADR 0008](0008-regras-do-weekly-report.md)).

## Decisão

### Urgency Notice

| Parâmetro | Valor | Por quê |
|---|---|---|
| Tentativas | **3** | Cobre uma falha momentânea do SES ou da rede sem segurar a fila por muito tempo |
| Idade máxima do evento | **1 hora** | Um aviso com mais de uma hora já não é imediato: alertar a pessoa vale mais que insistir |
| Dividir o lote quando falha | Sim | Isola o item problemático e libera os bons |
| Tamanho do lote | 10, com `ReportBatchItemFailures` | Só o item que falhou volta ([ADR 0005](0005-entrega-pelo-menos-uma-vez.md)) |
| Retenção na DLQ | 14 dias, o máximo do SQS | Dá tempo de alguém agir |

**A recuperação não depende da DLQ.** Para o Streams, o que vai à fila de falhas é um ponteiro para o trecho do stream,
que só guarda os dados por 24 horas — não a Evaluation. Quem ficou sem aviso é identificado pela própria tabela: toda
Critical Evaluation **sem** `urgencyNoticeSentAt` ([ADR 0005](0005-entrega-pelo-menos-uma-vez.md)). O procedimento de
reenvio — consultar o dia, achar essas Evaluations, reenviar — fica documentado no README, sem função nova.

### Weekly Report

| Parâmetro | Valor | Por quê |
|---|---|---|
| Tentativas do agendador | Até **4 horas** depois do horário (segunda 08:00 → 12:00) | O relatório ainda chega no mesmo dia útil |
| Tentativas da função | **2**, o padrão da invocação assíncrona | Cobre uma falha momentânea do SES |
| Depois disso | DLQ do agendador → "O relatório semanal não foi enviado" | Alguém roda manualmente, passando a semana ([ADR 0008](0008-regras-do-weekly-report.md)) |

## Alternativas consideradas

- **5 tentativas no Urgency Notice**: insiste mais numa falha momentânea, mas segura por mais tempo os avisos que vêm atrás.
- **Tentar para sempre** (padrão do Streams): uma Evaluation problemática travaria todos os avisos seguintes, sem alerta.
- **Reprocessar a partir da DLQ**: o ponteiro expira em 24 horas; a marca na tabela não expira enquanto a Evaluation
  existir (12 semanas, [ADR 0009](0009-dados-gravados-retencao-e-logs.md)).

## Consequências

- Se o Weekly Report chegou a sair e a função falhou depois, a nova tentativa manda o relatório de novo. É a mesma
  escolha da [ADR 0005](0005-entrega-pelo-menos-uma-vez.md): duplicar é melhor que perder.
- Uma indisponibilidade do SES maior que as 3 tentativas leva os avisos do período à DLQ — cada um vira Operational Alert,
  e o reenvio sai pela marca na tabela.
