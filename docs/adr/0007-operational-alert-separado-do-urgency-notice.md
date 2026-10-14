# ADR 0007 — Operational Alert separado do Urgency Notice

- **Status:** proposta
- **Data:** 2026-10-14

## Contexto

O enunciado fala de críticos duas vezes. Em **Problema**: "enviar notificações para **itens** críticos". Em
**Requisitos**, numa lista que só trata de ambiente e logo depois de "aplicação monitorada": "notificações automáticas
aos administradores para **problemas** críticos".

## Decisão

São coisas diferentes:

- **Itens críticos** são Critical Evaluations, e geram o **Urgency Notice** ([ADR 0001](0001-urgencia-derivada-da-nota.md)).
- **Problemas críticos** são falhas da plataforma, e geram o **Operational Alert**, disparado pelo monitoramento.

O Operational Alert:

- vai para a **mesma lista de Administrators**, como diz o enunciado, com **texto não técnico** ("1 avaliação crítica
  não pôde ser avisada", nunca "DLQ depth > 0");
- sai por um **canal independente** do usado pela `send-urgency-notice`, para que a falha do e-mail não derrube também
  o alerta que avisaria dela;
- tem a lista de destinatários configurada **no próprio monitoramento**, não lida por nenhuma função.

Gatilhos, com os limites exatos na rodada de deploy:

1. **Mensagem na fila de mensagens que falharam (DLQ) da `send-urgency-notice`**: uma Critical Evaluation ficou sem
   Urgency Notice. É o gatilho principal.
2. Erro em qualquer uma das três funções acima de um limite.
3. O Weekly Report falhou ou não rodou no horário.
4. Volume anormal de requisições ou de Critical Evaluations ([ADR 0006](0006-endpoint-anonimo.md)).
5. Alarme de custo.

## Por que esta leitura

Ela cobre as duas interpretações. Se o enunciado quis dizer a mesma coisa duas vezes, o Urgency Notice já atende; se
quis dizer duas coisas e entregássemos só o aviso, faltaria um requisito. O custo é configuração de monitoramento, sem
função nova.
