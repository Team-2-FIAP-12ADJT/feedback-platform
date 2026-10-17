# ADR 0011 — Região `sa-east-1` (São Paulo)

- **Status:** aceita
- **Data:** 2026-10-17

## Contexto

O grupo usa uma conta AWS própria, então a região é escolha nossa. As candidatas eram `us-east-1` (Virgínia do Norte),
referência de preço e primeira a receber recurso novo, e `sa-east-1` (São Paulo), perto de quem usa a plataforma.

Conferido em 2026-10-17:

| Serviço do desenho | `us-east-1` | `sa-east-1` |
|---|---|---|
| Lambda com Java 25 ([ADR 0004](0004-framework-escolhido-pelo-gatilho.md)) | Sim | Sim — o runtime saiu em todas as regiões |
| SnapStart ([ADR 0004](0004-framework-escolhido-pelo-gatilho.md)) | Sim | Provável, **não confirmado** — a documentação o dá em todas as regiões comerciais exceto Nova Zelândia e Taipé, sem citar São Paulo |
| DynamoDB com Streams ([ADR 0010](0010-dynamodb-particionado-por-dia.md)), SQS, API Gateway, CloudWatch | Sim | Sim |
| Agendador com fuso ([ADR 0008](0008-regras-do-weekly-report.md)) — EventBridge Scheduler | Sim | Sim |
| Envio de e-mail (SES) | Sandbox, 200 por dia | Sandbox, 200 por dia |

## Decisão

**Tudo sobe em `sa-east-1`.**

- **Os dados ficam no Brasil.** A Description é texto livre do Student e pode conter dado pessoal
  ([ADR 0009](0009-dados-gravados-retencao-e-logs.md)). A LGPD não exige que o dado fique no país, mas mantê-lo aqui
  elimina a transferência internacional.
- **Todos os serviços já decididos existem na região.** O preço é maior que em `us-east-1`, mas no volume esperado a
  diferença é de centavos.
- **Condição: o SnapStart é provado no primeiro deploy**, publicando a função com ele ligado. Se a região não o oferecer,
  esta ADR é revista antes de existir dado gravado: ou a plataforma vai para `us-east-1`, ou a
  [ADR 0004](0004-framework-escolhido-pelo-gatilho.md) é revista sem SnapStart.

## Alternativas consideradas

- **`us-east-1`**: mais barata e primeira a receber recurso novo, mas leva para fora do país um texto livre que pode
  conter dado pessoal, sem ganho funcional para este desenho.

## Consequências

- O SES fica em sandbox: só envia para endereços verificados. Para a lista fixa de Administrators isso basta — cada um
  confirma o e-mail de verificação da AWS uma vez. Sair do sandbox não é necessário.
- O orçamento da conta não depende de região. Um alarme de custo pelo CloudWatch, se for usado no Operational Alert
  ([ADR 0007](0007-operational-alert-separado-do-urgency-notice.md)), fica em `us-east-1`, a única região com métricas de
  faturamento.
