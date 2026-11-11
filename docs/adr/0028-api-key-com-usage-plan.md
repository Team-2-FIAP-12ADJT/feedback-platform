# ADR 0028 — `POST /avaliacao` exige API key com usage plan

- **Status:** aceita
- **Data:** 2026-11-11
- **Substitui em parte:** [ADR 0006](0006-endpoint-anonimo.md) (a decisão "o endpoint é anônimo")

## Contexto

A [ADR 0006](0006-endpoint-anonimo.md) deixou o `POST /avaliacao` anônimo e descartou a API key do gateway porque a
chave ficaria exposta num repositório público. A rubrica de avaliação da Fase 4, porém, pede explicitamente que
"serviços expostos publicamente possuam algum tipo de autenticação". O endpoint anônimo deixaria esse critério sem
atender.

## Decisão

- O método `POST /avaliacao` exige **API key** no header **`x-api-key`** (`Auth.ApiKeyRequired` no `template.yaml`).
  Sem a chave, ou com chave inválida, o API Gateway responde **`403`** sem invocar a Lambda.
- O SAM cria a API key, um **usage plan** próprio da API (`PER_API`) e o vínculo entre os dois. O usage plan aplica o
  mesmo throttle do estágio (20 req/s, burst 50) e uma **cota diária** (parâmetro `ApiKeyDailyQuota`, padrão 5.000).
- O **valor** da chave não fica no repositório nem em output da stack: o output `ApiKeyId` traz só o ID, e o valor é
  lido com `aws apigateway get-api-key --api-key <id> --include-value`. O teste de fumaça do deploy lê a chave assim
  e a mascara no log.
- O WAF (8 KB e 300 req/5 min por IP, [ADR 0015](0015-rest-api-com-waf.md)) continua na frente.

## Alternativas consideradas

- **Manter anônimo:** deixa o critério de autenticação da rubrica sem atender.
- **Login de Student (Cognito/JWT):** continua sendo a solução completa, mas exige cadastro e emissão de token que o
  enunciado não pede.

## Consequências

- A chave identifica o **cliente** (o front-end ou a ferramenta de envio), não o Student. Quem tiver a chave consegue
  enviar Evaluations; a cota e o throttle limitam o estrago, e trocar a chave revoga o acesso.
- Toda chamada à API (Postman, teste de fumaça, vídeo) precisa enviar `x-api-key`. A collection Postman usa a variável
  `apiKey`, preenchida localmente.
- O valor da chave não deve aparecer no vídeo nem em prints de evidência.

