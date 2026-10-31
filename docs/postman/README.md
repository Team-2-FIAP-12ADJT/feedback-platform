# Collection Postman — feedback-platform

Artefato do **T-10**. Cobre o contrato HTTP de [`POST /avaliacao`](../contracts/receive-evaluation.md) contra o estágio **demo** em `sa-east-1`.

## Montar

1. Postman → **Import** → `feedback-platform.postman_collection.json`
2. Na collection, variável `baseUrl`: cole o output CloudFormation **`ApiEndpoint`** da stack `feedback-platform` (já termina em `/avaliacao`).
3. Variável `stageBase`: a mesma URL **sem** o sufixo `/avaliacao` (para os casos `403`/`404`).

Importar e preencher variáveis **não chama** a API. Só **Send** / **Run collection** (Runner) envia request para a demo.

## O que a collection cobre

| Pasta | Cenários |
|---|---|
| Felizes | `201` para Score 8 (LOW), 5 (MEDIUM), 2 (CRITICAL); sem `Location`; sem `urgency` no body |
| Rejeições 400 | nota/descrição inválidas, campo desconhecido, JSON malformado; `problem+json` sem ecoar valor recusado |
| Borda | `415` Content-Type; `413` corpo > 8 KB (WAF); `403`/`404` método/rota inexistente |

`429` (rate limit) **não** está na collection — forçar flood atrapalha o ambiente demo. Fica só no [`../validacao-manual.md`](../validacao-manual.md).
