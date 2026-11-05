# 15 — Portabilidade do build (clone limpo + autocrlf)

Timestamp: **03/11/2026 23:05** (BRT)

## Ambiente
- Clone limpo em `C:\Users\Gusta\tools\t10-portabilidade-clone\repo`
- Branch: `feat/t-10-validacao-manual`
- `git config core.autocrlf`: **true**
- OS: Windows 11; Maven 3.9.9; Java 25 (Temurin); SAM CLI 1.171.0; Docker Desktop 29.7.2

## Comandos
```text
git -c core.autocrlf=true clone --branch feat/t-10-validacao-manual ...
git config core.autocrlf true
mvn -B clean verify
sam validate --lint --template template.yaml
```

## Resultado
- `mvn -B clean verify`: **BUILD SUCCESS**, exit **0**, ~51 s (log: `15-portabilidade-mvn-verify.log`)
  - domain, receive-evaluation, email-support, send-weekly-report, send-urgency-notice: SUCCESS
- `sam validate --lint`: exit **0** (log: `15-portabilidade-sam-validate.log`) — `template.yaml is a valid SAM Template`
- Sem falha de fim de linha CRLF/LF no build

## Docker / Testcontainers
- Imagem `amazon/dynamodb-local:3.3.2` carregada localmente (`docker load`) após download via `curl -4` do registry (workaround de IPv6 no Docker Desktop)
- Testcontainers usou a imagem local; verify verde

## Critério do card T-10
Evidência na PR de clone limpo com `core.autocrlf=true` executando `mvn clean verify` e `sam validate --lint` com sucesso.
