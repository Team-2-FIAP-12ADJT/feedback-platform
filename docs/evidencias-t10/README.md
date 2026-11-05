# Evidências T-10 (captura 03/11/2026)

Pasta: `docs/evidencias-t10/`

Regra de print (também em `docs/validacao-manual.md` §7): timestamp no canto superior direito, formato `DD/MM/YYYY HH:MM` (ex.: `03/11/2026 21:10`).

| Arquivo | Conteúdo |
|---|---|
| 00-collection-resultado.html | Resultado HTTP 19/19 vs ApiEndpoint demo |
| 01-collection-http.png | Collection Postman vs ApiEndpoint (19/19) |
| 02-dynamodb-evaluations.png | Scan tabela `evaluations` |
| 03-dynamodb-critical-urgency.png | CRITICAL + `urgencyNoticeSentAt` |
| 04-cloudwatch-dashboard.png | Dashboard `feedback-platform-demo` + alarmes |
| 05-alarms-search.png | Lista Alarms (filtro console; ver 04) |
| 06-logs-urgency-notice-group.png | Log group send-urgency-notice (sem corpo sensível) |
| 07-lambda-urgency-monitor.png | Lambda send-urgency-notice Monitor |
| 08-lambda-weekly-monitor.png | Lambda send-weekly-report Monitor |
| 09-weekly-schedule-enabled.png | Scheduler Weekly Enabled |
| 10-ses-account-dashboard.png | SES Account dashboard (sandbox) |
| 12-urgency-disparo-isolado.md | CRITICAL isolado 201 + `urgencyNoticeSentAt` |
| 13-urgency-email-print-ricardo.png | Urgency Notice (Ricardo confirmou) |
| 14-weekly-report-print.png | Weekly Report 26/10–01/11 (seg–dom; ADR 0008) |
| 15-portabilidade.md | Clone limpo + `autocrlf=true`; mvn/sam verdes |
| 15-portabilidade-mvn-verify.log | Log `mvn -B clean verify` (BUILD SUCCESS) |
| 15-portabilidade-sam-validate.log | Log `sam validate --lint` |

