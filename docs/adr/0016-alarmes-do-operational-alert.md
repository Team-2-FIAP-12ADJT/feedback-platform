# ADR 0016 — Alarmes do Operational Alert: métricas e limites

- **Status:** aceita
- **Data:** 2026-10-17

## Contexto

A [ADR 0007](0007-operational-alert-separado-do-urgency-notice.md) definiu cinco gatilhos do Operational Alert e deixou
os limites para a rodada de deploy. O caminho do alerta já está decidido: alarme do CloudWatch → EventBridge, que
reescreve o texto em português → SNS por e-mail ([ADR 0014](0014-ses-para-funcoes-sns-para-alerta.md)).

## Decisão

| # | Gatilho | Métrica | Dispara quando | Frase ao Administrator |
|---|---|---|---|---|
| 1 | Critical Evaluation sem Urgency Notice | Mensagens visíveis na DLQ da `send-urgency-notice` | ≥ 1 | "1 avaliação crítica não pôde ser avisada" |
| 2a | Falha ao receber Evaluation | `5XXError` do API Gateway | ≥ 5 em 5 min | "Estudantes não estão conseguindo enviar avaliações" |
| 2b | Falha no envio do Urgency Notice | `Errors` da `send-urgency-notice` | ≥ 3 em 5 min | "O envio de avisos está falhando; a plataforma está tentando de novo" |
| 3 | Weekly Report falhou ou não rodou | `Errors` da `send-weekly-report`, ou mensagem na DLQ do agendador | ≥ 1 | "O relatório semanal não foi enviado" |
| 4a | Volume anormal de requisições | `Count` do API Gateway | > 150 em 5 min | "Volume de avaliações muito acima do normal" |
| 4b | Envio automatizado barrado | Requisições bloqueadas pelo WAF | ≥ 50 em 5 min | "Envio automatizado de avaliações foi bloqueado" |
| 4c | Enxurrada de Critical Evaluations | Invocações da `send-urgency-notice` | > 30 em 15 min | "Número anormal de avaliações críticas" |
| 5 | Custo | Orçamento da conta (80% e 100%) | — | Mensagem padrão do AWS Budgets |

- **2a usa o gateway, não a função:** a `receive-evaluation` devolve `500` como resposta HTTP, o que para a Lambda não é
  erro. Quem vê o `500` é o gateway.
- **"Não rodou" (3)**: se o agendador não consegue invocar a `send-weekly-report`, o evento vai para a DLQ dele, e é ela
  que alarma. Não há métrica de batimento: o relatório que sempre chega já é o sinal de vida
  ([ADR 0008](0008-regras-do-weekly-report.md)).
- **4a com 150 em 5 minutos:** uma turma de 50 Students avaliando ao mesmo tempo não dispara; um script dispara antes de
  o WAF bloqueá-lo, que só corta acima de 300 em 5 minutos por IP ([ADR 0015](0015-rest-api-com-waf.md)).
- **4c conta invocações da `send-urgency-notice`** porque o filtro do Streams só a acorda para Critical Evaluations
  ([ADR 0010](0010-dynamodb-particionado-por-dia.md)).
- **5 reaproveita o orçamento que já existe**, com o tópico do Operational Alert acrescentado aos destinatários, para chegar a
  todos os Administrators. É a única exceção à frase própria: o texto é o do AWS Budgets.
- **Falta de dado conta como "tudo bem"** (`notBreaching`): uma semana sem Evaluations não dispara alarme.
- **Os limites de 4a, 4b e 4c são parâmetros do template** ([ADR 0012](0012-iac-com-aws-sam.md)): pontos de partida, a
  ajustar depois do primeiro uso real.
- **Um dashboard do CloudWatch** reúne requisições, Critical Evaluations, erros, DLQs e bloqueios do WAF — é a
  configuração do monitoramento que o vídeo mostra.

## Alternativas consideradas

- **Volume anormal em 600 requisições em 5 minutos**: irreal para o volume de um trabalho acadêmico — o alarme só
  dispararia muito depois de a enxurrada estar acontecendo.
- **Métrica de batimento do Weekly Report** (alarme se nenhum relatório sair em 7 dias): cobre o caso de o agendamento
  ser apagado, ao custo de uma métrica própria e de um alarme de janela longa. A DLQ do agendador e o erro da função
  cobrem as falhas de execução.
- **`Errors` da `receive-evaluation` no lugar de `5XXError`**: não enxerga o `500` devolvido como resposta.

## Consequências

- Cerca de 10 alarmes e um dashboard: menos de US$ 2 por mês.
- Um limite mal calibrado gera alarme falso ou atrasado; como é parâmetro, ajustá-lo é um deploy, não uma mudança de
  código.
