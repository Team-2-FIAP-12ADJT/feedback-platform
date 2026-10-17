# ADR 0021 — Um ambiente só, `demo`

- **Status:** aceita
- **Data:** 2026-10-17

## Contexto

O avaliador assiste ao vídeo e não usa o ambiente: não há usuário real. O prazo é 17/11/2026, e a conta tem US$ 120 em
créditos, com orçamento de US$ 80 por mês. Cada ambiente na AWS repete o custo fixo do WAF
([ADR 0015](0015-rest-api-com-waf.md)) e o conjunto de verificações do SES e assinaturas do SNS
([ADR 0020](0020-lista-de-administrators-no-github.md)).

## Decisão

**Um ambiente só na AWS, `demo`, que recebe o deploy de cada merge na `main`** ([ADR 0013](0013-pipeline-deploy-automatico-oidc.md)).

O que segura um defeito antes dele chegar à `demo` não é um segundo ambiente:

- testes de integração com containers no `ci`, rodando também na máquina de quem desenvolve;
- PR obrigatória com `ci` verde na `main`;
- teste de fumaça depois de cada deploy, com rollback do CloudFormation se o deploy falhar.

## Alternativas consideradas

- **`dev` + `demo`**: protege a `demo` de um merge com defeito, mas dobra o custo fixo (dois WAF), exige dois estágios no
  pipeline e duas rodadas de confirmação de e-mail por Administrator — para repetir o que a fumaça já verifica.

## Consequências

- Um merge com defeito que passe pelo `ci` chega à `demo`. Sem usuário real, isso dura até o próximo deploy.
- Os dados da `demo` são de teste; a retenção de 12 semanas ([ADR 0009](0009-dados-gravados-retencao-e-logs.md)) os limpa
  sozinha.
- **Quando reavaliar:** se dois integrantes precisarem testar na AWS ao mesmo tempo. Um segundo ambiente entra
  parametrizando o nome da pilha; nada no desenho atual o impede.
