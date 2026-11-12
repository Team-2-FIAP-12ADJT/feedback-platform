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

### Captura de prints

Em **cada** print de evidência, incluir o timestamp no **canto superior direito**, no formato **dia/mês/ano hora:minuto** (exemplo: `03/11/2026 21:10`), exatamente como especificado para o T-10. Não omitir o relógio no canto; não usar outro formato de data.

---

## 8. Apêndice operacional — reexecução do E2E

Referência rápida para o time repetir a validação na demo (`sa-east-1`, stack `feedback-platform`, estágio `demo`) e comparar com o esperado. Não substitui as seções 0–7; completa com nomes de recurso, CLI e contornos locais.

### 8.1 Destinatários e SES (sandbox)

- Lista canônica: variable do GitHub **`ADMINISTRATORS`** (CSV). O **primeiro** endereço é o remetente SES ([ADR 0023](adr/0023-remetente-e-o-primeiro-administrator.md)).
- Remetente exibido: **`Feedback Platform <primeiro@…>`** (display name fixo; mailbox = 1º de `ADMINISTRATORS`).
- Em **sandbox**, o SES só entrega a identidades **verificadas**. Quem não está na lista / não verificou **não** recebe Urgency Notice nem Weekly Report.
- Cada Administrator confirma: (1) identidade SES, (2) assinatura do SNS Operational Alert, (3) regra “nunca enviar para o spam” para o remetente.
- **Não** versionar e-mails reais no repositório; use só a variable e placeholders nos docs.

### 8.2 Nomes dos recursos AWS (demo)

| Recurso | Nome / identificador |
|---|---|
| Região / ambiente | `sa-east-1` / `demo` |
| Stack | `feedback-platform` |
| Lambda HTTP | `feedback-platform-receive-evaluation` |
| Lambda Urgency Notice | `feedback-platform-send-urgency-notice` |
| Lambda Weekly Report | `feedback-platform-send-weekly-report` |
| Tabela DynamoDB | output `EvaluationsTableName` (tipicamente `evaluations`) |
| API | output `ApiEndpoint` (termina em `/avaliacao`) → `baseUrl` Postman |
| Dashboard CloudWatch | `feedback-platform-demo` |
| Tópico SNS (Operational Alert) | `feedback-platform-operational-alert` (ARN no output `OperationalAlertTopicArn`) |
| DLQ Urgency (Streams) | `feedback-platform-urgency-notice-dlq` |
| DLQ Weekly (Scheduler) | `feedback-platform-weekly-report-dlq` |
| Schedule Weekly | EventBridge Scheduler **ScheduleV2**, `cron(0 8 ? * MON *)`, fuso `America/Sao_Paulo` |
| Orçamento (gatilho 5) | Orçamento da **conta** (80% e 100%) com notificação para o ARN do tópico SNS acima (configuração manual pós-deploy; Budgets no console tipicamente em `us-east-1`) |
| Remetente SES | Display **Feedback Platform**; From = 1º de `ADMINISTRATORS` |

### 8.3 Invoke manual do Weekly Report (AWS CLI)

Formato da semana: **`yyyy-Www`** (ISO), Report Week **segunda–domingo** em `America/Sao_Paulo` ([ADR 0008](adr/0008-regras-do-weekly-report.md)). Exemplo usado no T-10: **`2026-W44`** (26/10–01/11/2026).

O handler aceita a semana como **string JSON** pura (não o template “Hello World” do console):

```bash
aws lambda invoke \
  --region sa-east-1 \
  --function-name feedback-platform-send-weekly-report \
  --cli-binary-format raw-in-base64-out \
  --payload '"2026-W44"' \
  weekly-invoke-out.json

type weekly-invoke-out.json
```

PowerShell (aspas do payload):

```powershell
Set-Content -Path payload-week.json -Value '"2026-W44"' -NoNewline
aws lambda invoke `
  --region sa-east-1 `
  --function-name feedback-platform-send-weekly-report `
  --cli-binary-format raw-in-base64-out `
  --payload fileb://payload-week.json `
  weekly-invoke-out.json
Get-Content weekly-invoke-out.json
```

**Esperado:** status HTTP da invoke `200`; corpo com resumo do tipo `ReportWeek 2026-W44: N avaliações, enviado para M destinatários.`; e-mail **Relatório semanal …** nas caixas dos Administrators verificados (HTML + texto; média e contagens por dia/urgência).

Alternativa equivalente: payload mapa `{"reportWeek":"2026-W44"}` (uma única chave).

### 8.4 Resultados esperados por etapa (comparar na reexecução)

| Etapa | Esperado |
|---|---|
| Collection **Felizes** (8 / 5 / 2) | `201`; body com `id` + `dataEnvio`; sem `Location`; sem `urgency` |
| Collection **Rejeições 400** | `400` + `application/problem+json`; `detail` sem ecoar valor recusado |
| Collection **Borda** | `415`; `413` (> 8 KB); `403` ou `404` |
| DynamoDB após felizes | Itens `LOW` / `MEDIUM` / `CRITICAL` no `submissionDay` de hoje (Brasília); `expiresAt` presente |
| Após CRITICAL | Item com **`urgencyNoticeSentAt`**; e-mail assunto **Aviso de urgência: avaliação crítica**; LOW/MEDIUM **sem** esse e-mail |
| ScheduleV2 | Enabled; segunda 08:00 `America/Sao_Paulo` |
| Invoke Weekly (`2026-W44` ou semana alvo) | Resumo no `weekly-invoke-out.json` + e-mail semanal nas caixas verificadas |
| Dashboard `feedback-platform-demo` | Widgets de requisições, Critical Evaluations, erros, DLQs, WAF |
| Alarmes 1…5 | Presentes; frases PT via EventBridge → SNS (sem `AlarmActions` diretas nos alarmes) |
| Budgets | Notificações 80%/100% apontando ao tópico `feedback-platform-operational-alert` |
| Logs das 3 Lambdas | Sem Description, sem valor recusado, sem e-mail de Administrator |
| Portabilidade (§6) | `mvn clean verify` exit 0; `sam validate --lint` exit 0 |

Referência de uma execução já capturada: [`evidencias-t10/`](evidencias-t10/).

### 8.5 Docker — `amazon/dynamodb-local:3.3.2` (portabilidade / Testcontainers)

No Windows, o `docker pull` via Docker Desktop pode falhar ao resolver o Hub em **IPv6**. Contorno usado no T-10: baixar o manifesto/blobs do registry com **`curl -4`** (IPv4 forçado), montar um arquivo tar no formato docker-archive e carregar com **`docker load`**.

Esboço (PowerShell; pasta de trabalho à escolha):

```powershell
# 1) Token e manifesto (IPv4)
$tokenJson = curl.exe -4 -sS "https://auth.docker.io/token?service=registry.docker.io&scope=repository:amazon/dynamodb-local:pull"
$token = ($tokenJson | ConvertFrom-Json).token
# 2) Baixar manifesto da tag 3.3.2 e blobs com:
#    curl.exe -4 -sS -L -H "Authorization: Bearer $token" ...
# 3) Montar docker-archive (manifest.json em array + layers) e:
docker load -i dynamodb-local-3.3.2.tar
docker images amazon/dynamodb-local:3.3.2
```

**Esperado:** imagem `amazon/dynamodb-local:3.3.2` listada localmente; em seguida `mvn clean verify` no clone limpo (`core.autocrlf=true`) conclui **BUILD SUCCESS** sem o Testcontainers tentar pull IPv6. Detalhe de uma prova: [`evidencias-t10/15-portabilidade.md`](evidencias-t10/15-portabilidade.md).
