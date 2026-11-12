# Roteiro do vídeo — Tech Challenge Fase 4

Duração-alvo: **~10 minutos**. Cada bloco cobre um item da planilha de avaliação (coluna à direita). Mostrar, falar o essencial e seguir em frente — detalhes ficam no [README](../README.md) e nos ADRs.

Preparação (antes de gravar, fora do vídeo): stack `feedback-platform` implantada em `sa-east-1`, Postman com `baseUrl` e `apiKey` preenchidos ([`postman/README.md`](postman/README.md)), caixa de e-mail de um Administrator aberta, abas do console AWS já logadas.

---

| # | Bloco | Tempo | Critério da planilha |
|---|---|---|---|
| 1 | Abertura e arquitetura | 1 min | Desenho de Arquitetura |
| 2 | Ambiente de nuvem | 1 min | Configuração de ambiente |
| 3 | Deploy | 1 min 30 | Deploy – Documento / Script |
| 4 | Recebimento de feedback | 1 min 30 | Endpoints de entrada · Segurança – Autenticação |
| 5 | Notificação de urgência | 1 min | E-mail enviado · Dados do aviso de urgência |
| 6 | Relatório semanal | 1 min 30 | Relatório contempla requisitos · Trigger |
| 7 | Monitoramento | 1 min | Monitoramento |
| 8 | Segurança | 30 s | Segurança – Documentação |
| 9 | Código e encerramento | 30 s | README · Qualidade do código · Repositórios |

---

## 1. Abertura e arquitetura (~1 min)

**Mostrar:** [`arquitetura-feedback-platform.jpg`](../arquitetura-feedback-platform.jpg).

**Falar:**
- Apresentação do grupo e objetivo: plataforma serverless de feedback de aulas.
- Fluxo em uma frase: API Gateway + WAF → Lambda `receive-evaluation` → DynamoDB → Streams → `send-urgency-notice` (SES); EventBridge Scheduler → `send-weekly-report` (SES).
- Por que três funções: responsabilidade única, cada uma com seu gatilho.

## 2. Ambiente de nuvem (~1 min)

**Mostrar:** console Lambda com as três funções; [`template.yaml`](../template.yaml) rapidamente.

**Falar:**
- Modelo escolhido: **serverless na AWS** (Lambda, DynamoDB, SES, EventBridge), pago por uso, sem servidor para manter.
- Infra como código com **AWS SAM**; região `sa-east-1`.
- Segurança da configuração: roles IAM de privilégio mínimo por função, criptografia em repouso, deploy via OIDC (sem chaves de acesso no GitHub).

## 3. Deploy (~1 min 30)

**Mostrar:** README § *Build, testes e deploy*; depois [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) e uma execução verde na aba **Actions** do GitHub.

**Falar:**
- O documento explica bootstrap OIDC, variables do GitHub e pós-deploy.
- Script automatizado: push na `main` → CI (`mvn verify`) → `sam build` / `sam deploy` → smoke test.

## 4. Recebimento de feedback (~1 min 30)

**Mostrar:** Postman — pasta **Felizes** e uma requisição de **Rejeições 400**; em seguida o item no DynamoDB.

**Falar / mostrar resultado:**
- `POST /avaliacao` com Score 8, 5 e 2 → `201` com `id` e `dataEnvio`.
- Payload inválido → `400` (`application/problem+json`).
- DynamoDB: itens gravados com urgência `LOW` / `MEDIUM` / `CRITICAL` derivada da nota.
- **Autenticação:** rodar **403 sem API key** (pasta Borda) → o API Gateway recusa antes de chegar à Lambda. Mostrar no console API Gateway → *API keys* / *Usage plans* o plano com throttle e cota diária ([ADR 0028](adr/0028-api-key-com-usage-plan.md)). **Não** exibir o valor da chave na tela.
- **Proteção da borda:** WAF bloqueia corpo acima de 8 KB (`413`) e mais de 300 req/5 min por IP. As funções internas (Streams, Scheduler) não são públicas e só executam via IAM.

## 5. Notificação de urgência (~1 min)

**Mostrar:** caixa de e-mail do Administrator; item CRITICAL no DynamoDB.

**Falar / mostrar resultado:**
- A avaliação com Score 2 disparou o e-mail **“Aviso de urgência: avaliação crítica”**.
- Abrir o e-mail e apontar os dados: descrição, nota, urgência e data de envio.
- Item no DynamoDB marcado com `urgencyNoticeSentAt` (evita reenvio duplicado).
- Scores 8 e 5 **não** geraram aviso.

## 6. Relatório semanal (~1 min 30)

**Mostrar:** EventBridge Scheduler → schedule do Weekly Report; terminal com o invoke manual; e-mail recebido.

**Falar / mostrar resultado:**
- Trigger: toda **segunda às 08:00** (`America/Sao_Paulo`).
- Para não esperar segunda, invocar manualmente:

  ```powershell
  aws lambda invoke --region sa-east-1 --function-name feedback-platform-send-weekly-report `
    --cli-binary-format raw-in-base64-out --payload fileb://payload-week.json weekly-invoke-out.json
  ```

- Abrir o e-mail e apontar os requisitos do relatório: **média das notas, quantidade de avaliações por dia e por urgência** (conferir com a lista do enunciado e apontar cada item no e-mail).

## 7. Monitoramento (~1 min)

**Mostrar:** dashboard CloudWatch `feedback-platform-demo`; lista de alarmes.

**Falar:**
- Dashboard com requisições, avaliações críticas, erros, DLQs e bloqueios do WAF.
- Alarmes publicam no tópico SNS **Operational Alert** (e-mail aos Administrators); orçamento da conta também alerta.
- Instruções completas no README § *Monitoramento e procedimentos operacionais*.

## 8. Segurança (~30 s)

**Mostrar:** README § *Segurança dos dados e governança de acesso*.

**Falar:**
- Borda protegida (API key + usage plan, WAF com limite de tamanho e taxa), IAM mínimo, criptografia e retenção de 12 semanas (TTL).
- Logs **não** registram a descrição do aluno nem e-mails.

## 9. Código e encerramento (~30 s)

**Mostrar:** estrutura do monorepo no GitHub e o README.

**Falar:**
- Módulos `domain`, `email-support` e as três funções; testes unitários e de integração rodando na CI.
- Repositório público com README, ADRs e collection Postman.
- Agradecimento e encerramento.
