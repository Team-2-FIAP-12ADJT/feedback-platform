# Validação manual — ambiente demo (`sa-east-1`)

Roteiro executável do **T-10**. Serve também de roteiro do vídeo de demonstração ([README](../README.md) §Testes item 5).

Fontes: [contrato HTTP](contracts/receive-evaluation.md), ADRs 0005, 0008, 0009, 0014–0017, 0023, collection em [`postman/`](postman/).

---

## 0. Pré-requisitos (Administrators)

1. Confirmar identidades **SES** (sandbox) e assinaturas do tópico **SNS** Operational Alert (e-mails de verificação).
2. Cada Administrator cria na própria caixa a regra **“nunca enviar para o spam”** para o remetente = **primeiro** endereço de `ADMINISTRATORS` ([ADR 0023](adr/0023-remetente-e-o-primeiro-administrator.md)).
3. Obter (somente leitura) os outputs da stack `feedback-platform`:
   - `ApiEndpoint` → variável `baseUrl` da collection Postman
   - `EvaluationsTableName` → inspeção DynamoDB
4. Importar [`postman/feedback-platform.postman_collection.json`](postman/feedback-platform.postman_collection.json) e preencher `baseUrl` / `stageBase` — ver [`postman/README.md`](postman/README.md).

---

## 1. Borda HTTP, recepção e gravação

**Funções / serviços:** `receive-evaluation`, API Gateway REST, AWS WAF, DynamoDB.

1. Rodar a pasta **Felizes** da collection (Scores 8, 5 e 2).
   - Esperado: `201`, body com `id` e `dataEnvio`, **sem** header `Location`, **sem** campo `urgency`.
2. Rodar a pasta **Rejeições 400**.
   - Esperado: `400` + `application/problem+json`; `detail` **não** ecoa o valor recusado ([ADR 0009](adr/0009-dados-gravados-retencao-e-logs.md)).
3. Rodar a pasta **Borda**: `415`, `413` (> 8 KB), `403`/`404`.
4. No DynamoDB (`sa-east-1`), partição `submissionDay` = data de hoje em `America/Sao_Paulo` (`yyyy-MM-dd`):
   - Itens das três faixas com `urgency` `LOW` / `MEDIUM` / `CRITICAL` conforme Score ([ADR 0001](adr/0001-urgencia-derivada-da-nota.md)).
   - `expiresAt` presente (TTL 12 semanas).

**429:** não forçar flood. Conferir só que a regra existe no WAF/API (documentação / console).

**Checklist (preencher na execução):**

| Caso | OK? | Evidência |
|---|---|---|
| 201 LOW / MEDIUM / CRITICAL | ☐ | |
| 400 (amostra do contrato) | ☐ | |
| 413 / 415 / 403\|404 | ☐ | |
| Itens no DynamoDB | ☐ | |

---

## 2. Urgency Notice (Streams + SES)

**Função:** `send-urgency-notice`.

1. Após o `201` com Score 2 (CRITICAL), aguardar o processamento do Streams.
2. DynamoDB: o item CRITICAL deve ter **`urgencyNoticeSentAt`** preenchido ([ADR 0005](adr/0005-entrega-pelo-menos-uma-vez.md)).
3. Caixa dos Administrators: e-mail de Urgency Notice (remetente = 1º de `ADMINISTRATORS`).
4. Conferir que Scores 8 e 5 **não** geraram Urgency Notice (filtro do Streams / marca ausente).

| Caso | OK? | Evidência |
|---|---|---|
| CRITICAL com `urgencyNoticeSentAt` | ☐ | |
| E-mail Urgency Notice | ☐ | |
| LOW/MEDIUM sem aviso | ☐ | |

---

## 3. Weekly Report (Scheduler + SES)

**Função:** `send-weekly-report`.

1. Console EventBridge Scheduler: schedule **ScheduleV2**, expressão segunda **08:00**, fuso `America/Sao_Paulo` ([ADR 0008](adr/0008-regras-do-weekly-report.md)).
2. Invocação **manual** da função com `ReportWeek` da semana desejada (ex.: `2026-W44`), para não esperar a segunda.
3. E-mail do Weekly Report nos Administrators (HTML + texto; média e contagens conforme ADR 0008).

| Caso | OK? | Evidência |
|---|---|---|
| ScheduleV2 correto | ☐ | |
| Invoke manual + e-mail | ☐ | |

---

## 4. Monitoramento e Operational Alert

Pré-requisito: alarmes CloudWatch, regra EventBridge (texto em português), dashboard e Budgets → SNS implantados na stack ([ADR 0016](adr/0016-alarmes-do-operational-alert.md)).

1. Dashboard CloudWatch: requisições, Critical Evaluations, erros, DLQs, bloqueios WAF.
2. Alarmes dos gatilhos 1, 2a, 2b, 3, 4a, 4b, 4c e 5 presentes; frases PT via EventBridge.
3. Orçamento da conta (80% e 100%) notifica o tópico SNS do Operational Alert.

| Caso | OK? | Evidência |
|---|---|---|
| Dashboard | ☐ | |
| Alarmes 1…5 | ☐ | |
| Budgets → SNS | ☐ | |

---

## 5. Segurança e privacidade nos logs

1. CloudWatch Logs das três funções: **sem** Description, **sem** valor recusado, **sem** e-mail de Administrator ([ADR 0009](adr/0009-dados-gravados-retencao-e-logs.md)).
2. Console Lambda: três funções ativas (`feedback-platform-receive-evaluation`, `…-send-urgency-notice`, `…-send-weekly-report`), SnapStart em versões publicadas (já coberto pelo smoke do deploy).

| Caso | OK? | Evidência |
|---|---|---|
| Logs limpos | ☐ | |
| Funções ativas + SnapStart | ☐ | |

---

## 6. Portabilidade do build (sem AWS)

Prova de fim de linha / SO (clone limpo, sem chamar a demo):

```bash
git config core.autocrlf=true
# clone limpo do repositório (ou worktree) e:
mvn clean verify
sam validate --lint --template template.yaml
```

No Windows, com `SAM_CLI_ENABLE_LANGUAGE_EXTENSIONS=1` se o CLI exigir (o template usa `AWS::LanguageExtensions`).

| Caso | OK? | Evidência |
|---|---|---|
| `mvn clean verify` | ☐ | |
| `sam validate --lint` | ☐ | |

---

## 7. Evidências para a PR

Anexar ou linkar na PR do T-10:

1. Item DynamoDB CRITICAL com `urgencyNoticeSentAt`.
2. E-mails de Urgency Notice e Weekly Report (prints ou headers, **sem** expor lista completa de Administrators no repo público se for sensível).
3. Resultado da collection (Runner export / prints de status).
4. Dashboard e alarmes do Operational Alert.
5. Log do clone limpo com `mvn clean verify` + `sam validate --lint`.

---

## Fora de escopo deste documento

- Seções finais de arquitetura, deploy e governança no `README.md` → **T-11**.
- Gravação do vídeo → **T-13**.
