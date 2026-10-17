# ADR 0012 — Infraestrutura como código com AWS SAM

- **Status:** aceita
- **Data:** 2026-10-17

## Contexto

O enunciado avalia o deploy automatizado dos componentes atualizáveis. A ferramenta de infraestrutura como código (IaC)
é o que o pipeline roda a cada deploy, e precisa expressar o que já foi decidido:

- SnapStart com publicação de versão e alias a cada deploy ([ADR 0004](0004-framework-escolhido-pelo-gatilho.md));
- DynamoDB com Streams, filtro na assinatura e fila de falhas ([ADR 0010](0010-dynamodb-particionado-por-dia.md));
- agendamento semanal no fuso `America/Sao_Paulo` ([ADR 0008](0008-regras-do-weekly-report.md));
- limites de requisição na borda ([ADR 0006](0006-endpoint-anonimo.md));
- tudo em `sa-east-1` ([ADR 0011](0011-regiao-sao-paulo.md)).

## Decisão

**AWS SAM**, um template para a plataforma.

- Cada item acima é nativo do SAM: `SnapStart` com `AutoPublishAlias` na função; evento `DynamoDB` com `FilterCriteria`
  e `DestinationConfig` de falha; evento `ScheduleV2` com `ScheduleExpressionTimezone`; throttling na API.
- O que não é serverless — fila de falhas, verificação de e-mail no SES, alarmes do CloudWatch — entra no mesmo template
  como recurso CloudFormation comum.
- **O estado fica no CloudFormation**, na própria conta: nada a montar antes do primeiro deploy.
- **O Maven gera os pacotes das três funções**; o SAM empacota e publica os artefatos prontos. O `sam build` não compila
  Quarkus e Spring Boot.

### Fora do template, de propósito

- **Usuários e grupo IAM do grupo** — criados à mão, uma vez, e documentados no README. São o acesso das pessoas, não da
  aplicação.
- **Provedor OIDC do GitHub e role de deploy** — uma pilha pequena separada, aplicada uma vez por um administrador: o
  pipeline não pode criar a própria permissão.

## Alternativas consideradas

- **Terraform**: padrão de mercado e multi-cloud, mas exige montar o backend de estado (bucket e trava) e escrever à mão o
  que o SAM dá pronto, como versão e alias do SnapStart e o filtro do Streams.
- **AWS CDK em Java**: mesma linguagem do projeto, mas exige `cdk bootstrap` (bucket, roles e repositório de imagens na
  conta) e Node.js no pipeline, por um ganho de abstração que três funções não pedem.
- **CloudFormation puro**: o mesmo motor do SAM, sem os atalhos de serverless — mais linhas para o mesmo resultado.

## Consequências

- A plataforma fica presa à AWS — já estava, pelas ADRs 0010 e 0011.
- Um deploy que falha volta sozinho ao estado anterior (rollback do CloudFormation), mas devagar: minutos, não segundos.
- O teste local das funções não depende do SAM: a estratégia de testes do README usa containers.
