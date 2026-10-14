# Contrato — `POST /avaliacao`

Única rota pública da plataforma, atendida pela `receive-evaluation`. Anônima
([ADR 0006](../adr/0006-endpoint-anonimo.md)). Os campos ficam em português porque o contrato é do enunciado
([ADR 0002](../adr/0002-codigo-em-ingles-contrato-em-portugues.md)).

## Rota

`POST /avaliacao` — no singular, sem versão e **sem acento**. O enunciado escreve `/avaliação`, mas em HTTP o path só
admite ASCII: `ç` e `ã` trafegam codificados (`/avalia%C3%A7%C3%A3o`), e cada cliente HTTP e gateway trata isso de um
jeito. A rota com acento não é atendida.

## Requisição

`Content-Type: application/json`

```json
{
  "descricao": "A explicação de Lambda ficou confusa e o exemplo não rodou.",
  "nota": 2
}
```

O exemplo do enunciado tem uma vírgula sobrando depois de `nota`: é ilustração, não contrato. JSON com vírgula sobrando
é JSON malformado e é recusado.

### Validação

| Entrada | Resultado |
|---|---|
| `nota` ausente, `null`, menor que 0 ou maior que 10 | `400` |
| `nota` com casa decimal (`7.5`, `7.0`) ou como texto (`"7"`) | `400` — o Score precisa ser inteiro no JSON |
| `descricao` ausente, `null`, `""` ou só espaços | `400` |
| `descricao` com espaço nas pontas | Aceita; gravada sem os espaços das pontas |
| `descricao` com mais de **1.000 caracteres** depois de tirar os espaços das pontas | `400` |
| Campo desconhecido (ex.: `"urgencia"`) | `400` — a Urgency nunca é informada pelo Student ([ADR 0001](../adr/0001-urgencia-derivada-da-nota.md)) |
| Corpo que não é JSON, ou JSON malformado | `400` |

- "Caracteres" são os que a pessoa vê: um emoji conta 1 (`codePointCount`, não `String.length`).
- As conversões automáticas do Jackson que aceitariam `7.5` ou `"7"` como `7` ficam **desligadas**
  (`ACCEPT_FLOAT_AS_INT` e coerção de escalares).

## Resposta de sucesso

`201 Created`, **sem** header `Location` (não há rota de leitura de Evaluation).

```json
{
  "id": "0f3c9a52-7d1e-4b8a-9c64-2e5b8f1d7a90",
  "dataEnvio": "2026-11-09T14:30:00Z"
}
```

| Campo | Significado |
|---|---|
| `id` | UUID da Evaluation gravada |
| `dataEnvio` | Submission Time: instante em que a plataforma recebeu a Evaluation, ISO-8601 em UTC |

A Urgency **não** volta na resposta: é classificação para o Administrator, e expô-la a tornaria contrato público.

## Respostas de erro

Corpo em `application/problem+json` (RFC 9457):

```json
{
  "type": "about:blank",
  "title": "Avaliação inválida",
  "status": 400,
  "detail": "nota deve ser um inteiro entre 0 e 10",
  "field": "nota"
}
```

| Status | Quando | Quem responde |
|---|---|---|
| `400` | Qualquer linha da tabela de validação | `receive-evaluation` |
| `413` | Corpo acima do limite | Gateway |
| `415` | `Content-Type` diferente de `application/json` | `receive-evaluation` |
| `429` | Limite de requisições estourado | Gateway |
| `500` | Falha da plataforma (ex.: banco fora do ar) — sem stack trace nem mensagem interna | `receive-evaluation` |

O valor recusado nunca é ecoado no `detail` nem registrado em log
([ADR 0009](../adr/0009-dados-gravados-retencao-e-logs.md)).
