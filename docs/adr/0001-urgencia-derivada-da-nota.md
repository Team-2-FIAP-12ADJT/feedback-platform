# ADR 0001 — Urgência derivada da nota

- **Status:** aceita
- **Data:** 2026-10-14

## Contexto

O enunciado define a entrada como `{ "descricao": string, "nota": int 0–10 }`, mas o e-mail de aviso e o relatório
semanal pedem a **urgência** de cada avaliação. Ela não vem no payload, então precisa ser calculada pelo sistema.
O enunciado também pede aviso imediato aos administradores para itens **críticos**, sem dizer o que é crítico.

## Decisão

A urgência é calculada a partir da nota, no momento em que a avaliação é recebida, e gravada junto com ela:

| Nota | Urgência |
|---|---|
| 0–3 | `CRITICA` |
| 4–6 | `MEDIA` |
| 7–10 | `BAIXA` |

**É crítica a avaliação com urgência `CRITICA`** — só ela dispara o e-mail de aviso.

## Consequências

- O cliente não informa a urgência: ela é sempre calculada pelo sistema.
- A nota é validada na entrada (inteiro entre 0 e 10); fora disso a avaliação é rejeitada. Por isso toda nota aceita
  cai em exatamente uma faixa.
- A urgência é gravada, não recalculada na leitura: o relatório semanal agrupa pelo valor gravado, e uma mudança
  futura das faixas não reescreve o passado.
- Mudar as faixas é mudar o código da função que recebe a avaliação, registrado em nova ADR.
