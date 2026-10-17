# ADR 0019 — Uma role por função, com privilégio mínimo

- **Status:** aceita
- **Data:** 2026-10-17

## Contexto

A governança de acesso aos recursos da cloud é um critério do enunciado, e a [ADR 0006](0006-endpoint-anonimo.md) a
tratou no IAM, não na identidade do Student. A [ADR 0013](0013-pipeline-deploy-automatico-oidc.md) só deixa o deploy
criar roles com prefixo `feedback-platform-`, e a [ADR 0018](0018-criptografia-padrao-da-aws.md) deixou para as roles —
não para a chave — o controle de quem lê o dado.

## Decisão

**Uma role por função, só com as ações que ela usa, cada uma presa ao recurso exato.** Nenhum `Resource: "*"` fora do
log da própria função.

| Role | Pode | Não pode |
|---|---|---|
| `feedback-platform-receive-evaluation` | `dynamodb:PutItem` na tabela; escrever o próprio log | Ler a tabela, alterar item, enviar e-mail |
| `feedback-platform-send-urgency-notice` | Ler o Streams da tabela; `dynamodb:UpdateItem` **só no atributo `urgencyNoticeSentAt`**; `ses:SendEmail` **só a partir do remetente verificado**; `sqs:SendMessage` na própria DLQ; escrever o próprio log | Gravar Evaluation, consultar a tabela, alterar outros atributos |
| `feedback-platform-send-weekly-report` | `dynamodb:Query` na tabela; `ses:SendEmail` só a partir do remetente; escrever o próprio log | Gravar ou alterar qualquer item |
| `feedback-platform-scheduler` | `lambda:InvokeFunction` **só na `send-weekly-report`**; `sqs:SendMessage` na DLQ do agendador | Invocar outra função |

- **As restrições finas são condições do IAM:** `dynamodb:Attributes` limita o `UpdateItem` à marca do Urgency Notice
  ([ADR 0005](0005-entrega-pelo-menos-uma-vez.md)), e `ses:FromAddress` limita o envio ao remetente
  ([ADR 0014](0014-ses-para-funcoes-sns-para-alerta.md)). Um defeito na `send-urgency-notice` não altera o Score de uma
  Evaluation nem envia e-mail em nome de outro endereço.
- **A `receive-evaluation` só grava.** É a única porta pública: comprometida, não lê nenhuma Evaluation.
- **Statements escritos à mão**, não os modelos prontos do SAM ([ADR 0012](0012-iac-com-aws-sam.md)), que dão mais ações
  que o necessário — `DynamoDBCrudPolicy` dá leitura e remoção a quem só grava.
- A lista de Administrators chega por variável de ambiente ([ADR 0020](0020-lista-de-administrators-no-github.md)):
  nenhuma permissão a mais.

## Alternativas consideradas

- **Uma role compartilhada pelas três funções**: menos configuração, mas cada função passa a poder o que as outras podem —
  a porta pública leria todas as Evaluations.
- **Modelos de política do SAM**: mais curtos, mas mais largos que o uso de cada função.

## Consequências

- Uma ação nova no código exige mudar a role no template; sem isso, a função falha com `AccessDenied` no primeiro uso. O
  teste de fumaça do deploy ([ADR 0013](0013-pipeline-deploy-automatico-oidc.md)) pega isso no caminho do `POST`; os
  caminhos de e-mail só se provam com uma Evaluation `CRITICAL` e com o relatório.
- A divisão de permissões é o que o vídeo mostra como governança de acesso da aplicação.
