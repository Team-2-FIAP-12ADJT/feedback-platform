# ADR 0024 — O teste de fumaça apaga a própria Evaluation

- **Status:** proposta
- **Data:** 2026-10-18

## Contexto

A [ADR 0013](0013-pipeline-deploy-automatico-oidc.md) termina cada deploy com um teste de fumaça: um `POST /avaliacao`
válido precisa responder `201`. É o que prova o caminho inteiro — WAF, gateway, `receive-evaluation` e a gravação com a
role dela ([ADR 0019](0019-uma-role-por-funcao.md)).

Essa Evaluation fica gravada como qualquer outra, por 12 semanas
([ADR 0009](0009-dados-gravados-retencao-e-logs.md)), e o Weekly Report lê tudo o que está gravado
([ADR 0008](0008-regras-do-weekly-report.md)): ela entra no total, nas contagens por dia e por Urgency e no Average
Score. Com um deploy a cada merge na `main`, uma semana de implementação acumula várias Evaluations falsas — e o
relatório mostrado no vídeo sairia distorcido.

## Decisão

**O teste de fumaça grava uma Evaluation de verdade e, em seguida, o próprio workflow `deploy` a remove da tabela.**

- **O `POST` não muda:** Score **9**, para a Urgency `LOW` não gerar Urgency Notice, e a Description
  `Teste de fumaça do deploy <commit>`, para o item ser reconhecível se escapar da remoção.
- **A remoção acha o item pelo `id`** da resposta: o workflow calcula o dia em `America/Sao_Paulo` a partir do
  `dataEnvio`, consulta essa partição filtrando pelo `id` e chama `DeleteItem` com as chaves encontradas. Não remonta o
  `submissionKey` a partir do `dataEnvio`, para não depender de o instante da resposta ter o mesmo formato do gravado.
- **Sem permissão nova:** quem apaga é a role `feedback-platform-deploy`, que já alcança a tabela
  ([ADR 0013](0013-pipeline-deploy-automatico-oidc.md)). A `receive-evaluation` continua só gravando.
- **Falha à vista:** se o item não for encontrado ou não for removido, o passo falha e o deploy fica vermelho.
- O `REMOVE` que a remoção gera no Streams é descartado pelo filtro da assinatura, como a expiração pelo TTL
  ([ADR 0010](0010-dynamodb-particionado-por-dia.md)).

## Alternativas consideradas

- **Aceitar e declarar no README:** nenhum trabalho, mas o Weekly Report deixa de ser confiável justamente na semana do
  vídeo.
- **Marcar a Evaluation de teste e a `send-weekly-report` ignorá-la** (por exemplo, por um header): o contrato recusa
  campo extra, e qualquer pessoa no endpoint anônimo usaria a marca para tirar Evaluations do relatório.
- **Teste de fumaça só com requisição inválida, esperando `400`:** não grava nada, mas deixa de provar a gravação — o
  `AccessDenied` que a ADR 0019 espera que o teste pegue.

## Consequências

- **O Weekly Report só conta Evaluations de Students.**
- **Sobra uma janela de segundos:** um Weekly Report que rode exatamente entre o `POST` e a remoção conta a Evaluation de
  teste. Só acontece com um deploy na segunda às 08:00 de Brasília.
- O workflow `deploy` depende do formato das chaves da tabela (ADR 0010): mudá-las exige mudar também a remoção.
