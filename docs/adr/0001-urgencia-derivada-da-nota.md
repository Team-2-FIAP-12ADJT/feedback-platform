# ADR 0001 — Urgência derivada da nota

- **Status:** aceita
- **Data:** 2026-10-14

## Contexto

O enunciado define a entrada como `{ "descricao": string, "nota": int 0–10 }`, mas o e-mail de aviso e o relatório
semanal pedem a **urgência** de cada avaliação. Ela não vem no payload, então precisa ser calculada pelo sistema.
O enunciado também pede aviso imediato aos administradores para itens **críticos**, sem dizer o que é crítico.

## Decisão

A Urgency é calculada a partir do Score no momento em que a Evaluation é recebida, e gravada junto com ela:

| Score | Urgency |
|---|---|
| 0–3 | `CRITICAL` |
| 4–6 | `MEDIUM` |
| 7–10 | `LOW` |

**Critical Evaluation é a Evaluation com Urgency `CRITICAL`** — só ela dispara o Urgency Notice.

Os valores estão em inglês por causa da [ADR 0002](0002-codigo-em-ingles-contrato-em-portugues.md); nos e-mails eles
aparecem como "Crítica", "Média" e "Baixa".

## Consequências

- O cliente não informa a Urgency: ela é sempre calculada pelo sistema.
- O Score é validado na entrada (inteiro entre 0 e 10); fora disso a Evaluation é rejeitada. Por isso todo Score
  aceito cai em exatamente uma faixa.
- A Urgency é gravada, não recalculada na leitura: o Weekly Report agrupa pelo valor gravado, e uma mudança futura das
  faixas não reescreve o passado.
- Mudar as faixas é mudar o código do módulo `domain`, registrado em nova ADR.
