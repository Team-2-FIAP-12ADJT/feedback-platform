# ADR 0014 — SES para os e-mails das funções; SNS para o Operational Alert

- **Status:** aceita
- **Complementada por:** [ADR 0023](0023-remetente-e-o-primeiro-administrator.md) — o remetente
- **Data:** 2026-10-17

## Contexto

A plataforma envia dois tipos de e-mail aos Administrators:

- o **Urgency Notice** e o **Weekly Report**, enviados pelas funções `send-urgency-notice` e `send-weekly-report`;
- o **Operational Alert**, disparado pelo monitoramento, sem função
  ([ADR 0003](0003-tres-funcoes-aviso-nasce-da-gravacao.md)).

A [ADR 0007](0007-operational-alert-separado-do-urgency-notice.md) exige que o Operational Alert saia por um canal
**independente** do usado pelas funções — a falha do e-mail não pode derrubar também o alerta que avisaria dela — e com
**texto não técnico**.

## Decisão

### Urgency Notice e Weekly Report: Amazon SES

- SES em `sa-east-1` ([ADR 0011](0011-regiao-sao-paulo.md)), com HTML e versão em texto puro
  ([ADR 0008](0008-regras-do-weekly-report.md)).
- **Fica em sandbox**: só envia para endereços verificados, até 200 por dia. Para a lista fixa de Administrators basta —
  cada um confirma o e-mail de verificação da AWS uma vez.
- Os destinatários vêm da lista de Administrators na configuração das funções.

### Operational Alert: CloudWatch Alarm → EventBridge → SNS

- Os alarmes do CloudWatch mudam de estado; uma **regra do EventBridge** captura a mudança e entrega a um **tópico SNS**
  com assinaturas de e-mail. Cada Administrator confirma a sua assinatura uma vez.
- **O texto é reescrito na regra** (*input transformer*), em português e sem jargão — "1 avaliação crítica não pôde ser
  avisada", não "DLQ depth > 0". O e-mail que o CloudWatch manda direto ao SNS é técnico; o transformador resolve isso por
  configuração, sem função.
- SNS e SES são serviços diferentes, com caminhos de entrega diferentes: a independência pedida pela ADR 0007.

## Alternativas consideradas

- **SNS também para o Urgency Notice e o Weekly Report**: o e-mail do SNS é só texto, sem HTML, e o Weekly Report tem
  tabela e lista de até 500 itens ([ADR 0008](0008-regras-do-weekly-report.md)).
- **SES também para o Operational Alert**: o alerta cairia junto com o canal cuja falha ele precisa avisar.
- **Alarme direto no SNS, sem EventBridge**: menos uma peça, mas o Administrator recebe nome de métrica e limite em vez
  de uma frase.
- **Função para formatar o alerta**: texto livre, mas cria uma quarta função só para isso, contra a
  [ADR 0003](0003-tres-funcoes-aviso-nasce-da-gravacao.md).

## Consequências

- **O remetente do SES ainda não está definido.** Sem domínio próprio, será um endereço verificado; provedores como Gmail
  e Outlook podem mandar a mensagem para o spam, porque a autenticação do envio não bate com o domínio do remetente. No
  vídeo, conferir a caixa de spam no primeiro envio.
- **Uma lista só alimenta os dois canais:** destinatários do SES e assinaturas do SNS saem do mesmo parâmetro do deploy
  ([ADR 0020](0020-lista-de-administrators-no-github.md)).
- Um Administrator que não confirma a verificação do SES ou a assinatura do SNS não recebe o e-mail correspondente — a
  confirmação entra no roteiro de deploy.
