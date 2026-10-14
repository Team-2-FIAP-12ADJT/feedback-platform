# feedback-platform

Tech Challenge da **Fase 4** da pós-graduação FIAP ADJT — Cloud Computing, Serverless e Deploy em nuvem.

Plataforma de feedback de aulas: estudantes avaliam as aulas, administradores recebem aviso imediato dos
itens críticos e um relatório semanal com a média das avaliações.

> **Estado:** desenho da aplicação proposto (ADRs 0001–0009, todas `proposta`); **cloud e deploy em aberto**, à
> espera da confirmação com a FIAP sobre a conta. **Nada implementado.** O que falta decidir e fazer está em
> [`docs/pontos-em-aberto.md`](docs/pontos-em-aberto.md).
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
| [0001](docs/adr/0001-urgencia-derivada-da-nota.md) | Urgency derivada do Score: 0–3 `CRITICAL`, 4–6 `MEDIUM`, 7–10 `LOW`; só `CRITICAL` gera o Urgency Notice | proposta |
| [0002](docs/adr/0002-codigo-em-ingles-contrato-em-portugues.md) | Código e termos canônicos em inglês; contrato HTTP e e-mails em português | proposta |
| [0003](docs/adr/0003-tres-funcoes-aviso-nasce-da-gravacao.md) | Três funções, uma aplicação por função; o aviso nasce da gravação da Evaluation | proposta |
| [0004](docs/adr/0004-framework-escolhido-pelo-gatilho.md) | Java 25; Quarkus na função HTTP, Spring Boot nas que enviam e-mail; monorepo Maven | proposta |
| [0005](docs/adr/0005-entrega-pelo-menos-uma-vez.md) | Urgency Notice "pelo menos uma vez", com marca na Evaluation; duplicatas aceitas | proposta |
| [0006](docs/adr/0006-endpoint-anonimo.md) | `POST /avaliacao` anônimo, com limites na borda | proposta |
| [0007](docs/adr/0007-operational-alert-separado-do-urgency-notice.md) | Operational Alert para falhas da plataforma, separado do Urgency Notice | proposta |
| [0008](docs/adr/0008-regras-do-weekly-report.md) | Report Week de segunda a domingo em Brasília; teto de 500 na lista; semana vazia também envia | proposta |
| [0009](docs/adr/0009-dados-gravados-retencao-e-logs.md) | Nada que identifique o Student; retenção de 12 semanas; Description fora do log | proposta |

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
   falha no item 6 de um lote reenvia só ele; Description com HTML sai escapada; rodar de novo a mesma semana dá o
   mesmo relatório.
4. **Nas regras críticas** (faixas, escape, fronteira de semana, teto), o teste é conferido quebrando o código de
   propósito: ele precisa ficar vermelho.
5. **Ponta a ponta** — roteiro de validação manual e collection Postman no ambiente da cloud, que servem também de
   roteiro do vídeo.
