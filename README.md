# feedback-platform

Tech Challenge da **Fase 4** da pós-graduação FIAP ADJT — Cloud Computing, Serverless e Deploy em nuvem.

Plataforma de feedback de aulas: estudantes avaliam as aulas, administradores recebem aviso imediato dos
itens críticos e um relatório semanal com a média das avaliações.

> **Estado:** desenho da aplicação e da cloud decidido (ADRs 0001–0021 `aceita`; 0022–0024 `proposta`). **Nada
> implementado.** Não há decisão em aberto: o que falta é implementar.
>
> **Prazo de entrega: 17/11/2026.**

Enunciado: [`docs/ADJT - Fase 4 - Tech Challenge_rev.pdf`](<docs/ADJT - Fase 4 - Tech Challenge_rev.pdf>).
Glossário: [`CONTEXT-MAP.md`](CONTEXT-MAP.md) — termos canônicos em inglês, mapeados para as palavras do enunciado.

## O que o enunciado exige

| Bloco | Exigência |
|---|---|
| Regras | Serverless e cloud **obrigatórios**; mínimo de **2 funções**, com responsabilidade única — a separação é avaliada |
| Entrada | `POST /avaliação` com `{ "descricao": string, "nota": int 0–10 }` |
| Aviso de urgência (e-mail) | Descrição, urgência, data de envio |
| Relatório semanal | Média das avaliações; descrição, urgência, data de envio; quantidade por dia; quantidade por urgência |
| Ambiente | Segurança dos dados e governança de acesso; componentes de suporte (banco etc.); deploy automatizado dos componentes atualizáveis; monitoramento; notificação automática aos administradores para problemas críticos |
| Entrega | Repositório aberto + **vídeo** mostrando a aplicação, as funções serverless ativas e as configurações |
| Avaliação | Modelo de cloud e componentes explicados; funcionamento; código documentado; descrição com arquitetura, instruções de deploy, configuração do monitoramento e documentação das funções; configuração de nuvem e segurança explicadas |

## Como decidimos

Toda decisão de desenho vira ADR em `docs/adr/`, com status **`proposta`**. Ela é avisada no canal do grupo com o link;
**sem objeção em 48 horas, passa a `aceita`**. Uma objeção dentro do prazo mantém a ADR em `proposta` até ser
discutida. Uma ADR aceita só é contrariada por outra ADR que a substitua.

## Decisões tomadas

| ADR | Decisão | Status |
|---|---|---|
| [0001](docs/adr/0001-urgencia-derivada-da-nota.md) | Urgency derivada do Score: 0–3 `CRITICAL`, 4–6 `MEDIUM`, 7–10 `LOW`; só `CRITICAL` gera o Urgency Notice | aceita |
| [0002](docs/adr/0002-codigo-em-ingles-contrato-em-portugues.md) | Código e termos canônicos em inglês; contrato HTTP e e-mails em português | aceita |
| [0003](docs/adr/0003-tres-funcoes-aviso-nasce-da-gravacao.md) | Três funções, uma aplicação por função; o aviso nasce da gravação da Evaluation | aceita |
| [0004](docs/adr/0004-framework-escolhido-pelo-gatilho.md) | Java 25; Quarkus na função HTTP, Spring Boot nas que enviam e-mail; monorepo Maven | aceita |
| [0005](docs/adr/0005-entrega-pelo-menos-uma-vez.md) | Urgency Notice "pelo menos uma vez", com marca na Evaluation; duplicatas aceitas | aceita |
| [0006](docs/adr/0006-endpoint-anonimo.md) | `POST /avaliacao` anônimo, com limites na borda | aceita |
| [0007](docs/adr/0007-operational-alert-separado-do-urgency-notice.md) | Operational Alert para falhas da plataforma, separado do Urgency Notice | aceita |
| [0008](docs/adr/0008-regras-do-weekly-report.md) | Report Week de segunda a domingo em Brasília; teto de 500 na lista; semana vazia também envia | aceita |
| [0009](docs/adr/0009-dados-gravados-retencao-e-logs.md) | Nada que identifique o Student; retenção de 12 semanas; Description fora do log | aceita |
| [0010](docs/adr/0010-dynamodb-particionado-por-dia.md) | DynamoDB particionado pelo dia da submissão em Brasília; TTL de 12 semanas; o Urgency Notice sai do Streams, filtrado na assinatura | aceita |
| [0011](docs/adr/0011-regiao-sao-paulo.md) | Tudo em `sa-east-1` (São Paulo); o SnapStart é provado no primeiro deploy | aceita |
| [0012](docs/adr/0012-iac-com-aws-sam.md) | Infraestrutura como código com AWS SAM; usuários IAM e a pilha de OIDC do pipeline ficam fora do template | aceita |
| [0013](docs/adr/0013-pipeline-deploy-automatico-oidc.md) | Deploy automático a cada merge na `main`, por OIDC, com role restrita a `sa-east-1` e a roles `feedback-platform-*` | aceita |
| [0014](docs/adr/0014-ses-para-funcoes-sns-para-alerta.md) | SES para o Urgency Notice e o Weekly Report; Operational Alert por CloudWatch → EventBridge → SNS, em texto não técnico | aceita |
| [0015](docs/adr/0015-rest-api-com-waf.md) | API Gateway REST com WAF: corpo até 8 KB, 300 requisições em 5 min por IP, teto de 20/s | aceita |
| [0016](docs/adr/0016-alarmes-do-operational-alert.md) | Métricas e limites dos alarmes do Operational Alert (DLQ ≥ 1, volume > 150 em 5 min, entre outros) e dashboard | aceita |
| [0017](docs/adr/0017-tentativas-e-filas-de-falha.md) | Urgency Notice: 3 tentativas, até 1 hora, depois DLQ; reenvio pela marca na tabela. Weekly Report: até 4 horas | aceita |
| [0018](docs/adr/0018-criptografia-padrao-da-aws.md) | Criptografia em repouso com as chaves padrão da AWS; TLS em trânsito; acesso ao dado controlado pelas roles | aceita |
| [0019](docs/adr/0019-uma-role-por-funcao.md) | Uma role por função com privilégio mínimo; a função pública só grava | aceita |
| [0020](docs/adr/0020-lista-de-administrators-no-github.md) | Lista de Administrators numa variável do GitHub, que gera os destinatários do SES, as assinaturas do SNS e as verificações | aceita |
| [0021](docs/adr/0021-um-ambiente-so.md) | Um ambiente só na AWS, `demo`; o `ci` e o teste de fumaça seguram o defeito antes dela | aceita |
| [0022](docs/adr/0022-send-urgency-notice-le-a-marca.md) | A `send-urgency-notice` lê só a marca do Urgency Notice antes de enviar; no Streams, lote com falha reentrega também os itens seguintes | proposta |
| [0023](docs/adr/0023-remetente-e-o-primeiro-administrator.md) | O remetente do SES é o primeiro endereço da lista de Administrators; o e-mail chega sem autenticação, e cada Administrator o tira do spam por regra | proposta |
| [0024](docs/adr/0024-teste-de-fumaca-apaga-a-propria-evaluation.md) | O teste de fumaça do deploy apaga a própria Evaluation, para ela não entrar no Weekly Report; falha se não conseguir | proposta |

Contrato da rota: [`docs/contracts/receive-evaluation.md`](docs/contracts/receive-evaluation.md).

### Desvios do enunciado, e por quê

- **A rota é `/avaliacao`, sem acento.** Em HTTP o path só admite ASCII: `ç` e `ã` trafegam codificados, e cada
  cliente HTTP e gateway trata isso de um jeito. Detalhe no [contrato](docs/contracts/receive-evaluation.md#rota).
- **O exemplo de JSON do enunciado tem uma vírgula sobrando**: é ilustração, não contrato.

## Limitações declaradas

- **Urgency Notice duplicado é possível**, raramente: o envio de e-mail não entra em transação com o banco
  ([ADR 0005](docs/adr/0005-entrega-pelo-menos-uma-vez.md)).
- **Evaluation duplicada por reenvio do cliente é aceita**: o payload não tem identificador para distinguir um
  reenvio de duas Evaluations iguais.
- **O endpoint é anônimo**: uma enxurrada de Evaluations com Score baixo gera uma enxurrada de Urgency Notices. O
  limite de taxa freia; o login de Student seria a solução ([ADR 0006](docs/adr/0006-endpoint-anonimo.md)).
- **Um Weekly Report de mais de 12 semanas atrás não pode ser gerado de novo**
  ([ADR 0009](docs/adr/0009-dados-gravados-retencao-e-logs.md)).

## Testes

A estratégia é por comportamento que precisa ser provado, não por meta de cobertura:

1. **`domain`** — unitários puros: os 11 Scores contra as faixas da ADR 0001; agregação do Weekly Report (semana
   vazia, Evaluation de domingo 23:30 em Brasília na semana certa, 7 dias e 3 Urgencies sempre presentes, teto de 500
   com contagens completas).
2. **`receive-evaluation`** — cada linha da tabela de validação do contrato vira um caso; resposta `201` e
   `problem+json`; gravação contra o banco emulado em container.
3. **`send-urgency-notice` e `send-weekly-report`** — só `CRITICAL` gera aviso; o mesmo evento duas vezes envia uma;
   falha no item 6 de um lote envia de novo só o e-mail dele, embora os itens 6 a 10 voltem; Description com HTML sai
   escapada; rodar de novo a mesma semana dá o mesmo relatório.
4. **Nas regras críticas** (faixas, escape, fronteira de semana, teto), o teste é conferido quebrando o código de
   propósito: ele precisa ficar vermelho.
5. **Ponta a ponta** — roteiro de validação manual e collection Postman no ambiente da cloud, que servem também de
   roteiro do vídeo.
