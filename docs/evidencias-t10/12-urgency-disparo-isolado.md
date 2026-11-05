# T-10 — disparo isolado Urgency Notice (sem E2E)

Timestamp do print/nota (§7): **03/11/2026 21:34** (canto superior direito)

## Disparo
- POST CRITICAL isolado (não collection completa)
- HTTP: **201**
- id: `c18fa67b-cff4-45cb-b9ab-c213ac5b0b12`
- dataEnvio (API): `2026-11-04T00:31:20Z` → local BRT **03/11/2026 21:31:20**

## Marca SES / Urgency Notice
- `urgencyNoticeSentAt`: `2026-11-04T00:31:28.348371323Z` → local BRT **03/11/2026 21:31:28**
- Interpretação: Lambda `send-urgency-notice` **marcou envio** (~8s após dataEnvio).

## Destinatários (sandbox)
- Identidades SES verificadas: ricardo.engp@gmail.com, leomiroliveira5@gmail.com, leonardoalves.souza10@gmail.com
- Conta SES em **sandbox** — Gustavo **não** está nas identidades; e-mail **não** deve chegar na caixa dele.
