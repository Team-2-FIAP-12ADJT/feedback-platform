# ADR 0002 — Código em inglês, contrato e e-mails em português

- **Status:** proposta
- **Data:** 2026-10-14

## Contexto

O enunciado está em português e fixa um contrato em português (`POST /avaliação` com `descricao` e `nota`). Os
leitores dos e-mails são administradores brasileiros. O vocabulário de frameworks e bibliotecas (`RequestHandler`,
`@Repository`) é em inglês de qualquer jeito.

## Decisão

- **Identificadores de código e termos canônicos do glossário são em inglês**: Evaluation, Score, Urgency
  (`CRITICAL`/`MEDIUM`/`LOW`), Urgency Notice, Weekly Report. O [CONTEXT-MAP](../../CONTEXT-MAP.md) mapeia cada termo
  para a palavra do enunciado.
- **O contrato HTTP fica em português**, porque é do enunciado: os campos `descricao`, `nota` e `dataEnvio`. A
  tradução acontece na borda, na `receive-evaluation` (`@JsonProperty("descricao") String description`).
- **O texto dos e-mails fica em português**: `CRITICAL` aparece como "Crítica".
- Documentação em português; mensagens de commit em inglês.
- Nenhum identificador com acento.

## Alternativas consideradas

- **Domínio em português e técnico em inglês** (`Avaliacao`, `AvaliacaoRepository`): elimina a tradução na borda, mas
  mistura idiomas dentro do código.
- **Tudo em português**: briga com o vocabulário dos frameworks (`ManipuladorRecebe implements RequestHandler`).

## Consequências

- Duas armadilhas de tradução ficam registradas no glossário: _nota_ é Score (não _Note_, não _Grade_), e _feedback_
  não é nome de entidade (Evaluation).
- Toda mudança no contrato HTTP exige mexer nos dois nomes: o campo em português e o atributo em inglês.
