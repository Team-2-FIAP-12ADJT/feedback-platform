# ADR 0003 — Três funções, uma aplicação por função, e o aviso nasce da gravação

- **Status:** aceita
- **Data:** 2026-10-14

## Contexto

O enunciado exige no mínimo duas funções serverless, cada uma com responsabilidade única, e avalia a separação. O
sistema faz três coisas com gatilhos diferentes: receber uma Evaluation (HTTP), avisar sobre uma Critical Evaluation e
consolidar o Weekly Report (agendamento).

## Decisão

Três aplicações, cada uma implantada como **uma função Lambda**, mais um módulo de domínio compartilhado:

| Módulo | Gatilho | Responsabilidade |
|---|---|---|
| `receive-evaluation` | `POST /avaliacao` | Valida, calcula a Urgency, grava a Evaluation |
| `send-urgency-notice` | Gravação de uma Evaluation | Envia o Urgency Notice de cada Critical Evaluation |
| `send-weekly-report` | Agendamento semanal | Lê as Evaluations gravadas da Report Week e envia o Weekly Report |
| `domain` | — | Evaluation, Score, Urgency e a regra da [ADR 0001](0001-urgencia-derivada-da-nota.md), em Java puro |

**O Urgency Notice nasce da gravação da Evaluation, não de uma segunda chamada da `receive-evaluation`.** Se a
Recebe gravasse e depois publicasse um evento, uma falha entre as duas operações deixaria uma Critical Evaluation sem
aviso, sem ninguém perceber. O mecanismo está na [ADR 0010](0010-dynamodb-particionado-por-dia.md).

O **Weekly Report lê o que está gravado**, não os eventos.

O Operational Alert ([ADR 0007](0007-operational-alert-separado-do-urgency-notice.md)) não é função: é configuração do
monitoramento.

## Alternativas consideradas

- **Duas funções** (a Recebe também envia o e-mail): a Recebe teria duas responsabilidades, e uma falha do e-mail
  devolveria erro ao Student por uma Evaluation já gravada.
- **Quatro ou mais** (por exemplo, o relatório dividido em "agrega" e "envia"): separação que ninguém pediu.

## Consequências

- A `receive-evaluation` não depende do serviço de e-mail: o Student recebe sucesso mesmo com o e-mail fora do ar.
- Uma aplicação por função é o que permite um framework por função ([ADR 0004](0004-framework-escolhido-pelo-gatilho.md)).
