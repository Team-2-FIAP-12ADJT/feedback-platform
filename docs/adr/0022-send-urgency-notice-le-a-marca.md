# ADR 0022 — A `send-urgency-notice` lê a marca do Urgency Notice antes de enviar

- **Status:** aceita
- **Data:** 2026-10-18
- **Substitui em parte:** [ADR 0005](0005-entrega-pelo-menos-uma-vez.md) (o item sobre lote com falha) ·
  [ADR 0017](0017-tentativas-e-filas-de-falha.md) (a justificativa do tamanho do lote) ·
  [ADR 0019](0019-uma-role-por-funcao.md) (a linha da `feedback-platform-send-urgency-notice`)

## Contexto

A [ADR 0005](0005-entrega-pelo-menos-uma-vez.md) estreita a janela de duplicata do Urgency Notice com uma marca na
Evaluation: a `send-urgency-notice` ignora o evento se `urgencyNoticeSentAt` já estiver preenchido; senão, envia o e-mail
e **depois** preenche a marca.

Duas coisas impedem essa decisão de funcionar como está escrita:

1. **O evento não traz a marca.** A função é disparada pelo `INSERT` do Streams
   ([ADR 0010](0010-dynamodb-particionado-por-dia.md)), que carrega a Evaluation como foi gravada — sem
   `urgencyNoticeSentAt`, sempre. Saber se o aviso já saiu exige ler o item na tabela, e a
   [ADR 0019](0019-uma-role-por-funcao.md) diz que a função **não pode consultar a tabela**.
2. **No Streams, lote com falha não reenvia só o item que falhou.** A ADR 0005 e a
   [ADR 0017](0017-tentativas-e-filas-de-falha.md) supõem que `ReportBatchItemFailures` faz voltar só o item com falha.
   Isso vale para fila SQS. No Streams, a Lambda recomeça **do item que falhou** e entrega de novo **todos os que vêm
   depois dele no lote**, inclusive os que já tinham dado certo: num lote de 10 com falha no item 6, voltam os itens 6
   a 10 ([documentação da AWS](https://docs.aws.amazon.com/lambda/latest/dg/services-ddb-batchfailurereporting.html)).

O segundo ponto torna o primeiro sério: a reentrega dos itens 7 a 10 é justamente o caso de duplicata mais provável, e
quem a segura é a marca — que a função não consegue ler.

## Decisão

**Antes de enviar, a `send-urgency-notice` lê só a marca do item na tabela.**

- **Leitura:** `GetItem` com leitura fortemente consistente, pelas chaves que o evento do Streams já traz
  (`submissionDay`, `submissionKey`), projetando apenas `urgencyNoticeSentAt`. A consistência forte garante que uma marca
  gravada na entrega anterior seja vista na reentrega.
- **Marca preenchida → ignora o evento. Marca ausente → envia e depois grava a marca**, como na ADR 0005.
- **A role da `feedback-platform-send-urgency-notice` passa a ser:**

| Pode | Não pode |
|---|---|
| Ler o Streams da tabela; `dynamodb:GetItem` **só na marca**; `dynamodb:UpdateItem` **só na marca**; `ses:SendEmail` só a partir do remetente verificado; `sqs:SendMessage` na própria DLQ; escrever o próprio log | Gravar Evaluation; ler Description, Score ou qualquer outro atributo; listar ou consultar a tabela; alterar outros atributos |

- **As duas condições do IAM no `GetItem`:**
  - `dynamodb:Attributes` restrito a `urgencyNoticeSentAt` e às chaves da tabela;
  - `dynamodb:Select = SPECIFIC_ATTRIBUTES`. Sem ela, um `GetItem` **sem projeção** devolveria o item inteiro, porque a
    condição de atributos só confere os atributos nomeados no pedido
    ([documentação da AWS](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/specifying-conditions.html)).
- **Correção da ADR 0005 e da ADR 0017:** `ReportBatchItemFailures` continua ligado — com ele, a reentrega começa no
  item que falhou, e não no início do lote —, mas o que impede reenviar o e-mail dos itens que já tinham dado certo é a
  marca, não a configuração. O tamanho do lote segue 10.

## Alternativas consideradas

- **Lote de tamanho 1, sem ler a tabela:** acaba com a reentrega dos itens seguintes, mas a marca deixa de ser
  consultada, e o tamanho do lote da ADR 0017 e o teste do README mudam junto. Duas ADRs alteradas para evitar uma
  leitura por Critical Evaluation.
- **Gravar a marca antes de enviar** (`UpdateItem` condicional como reserva): nenhuma duplicata, mas a função morrendo
  entre a marca e o envio **perde** o aviso — o contrário do que a ADR 0005 escolheu.
- **Não checar nada e manter o lote de 10:** cada falha num lote reenvia até 9 avisos já enviados.
- **Registro de idempotência separado:** já descartado na ADR 0005, pela tabela e pela dependência a mais.

## Consequências

- **Uma leitura a mais por Critical Evaluation** — custo desprezível no volume esperado.
- **A janela de duplicata é a que a ADR 0005 já declarava:** falha entre o envio e a marca, ou duas entregas simultâneas
  do mesmo item.
- A função continua sem enxergar o conteúdo da Evaluation na tabela: o que ela lê do item é a marca. A Description que
  vai no e-mail vem do próprio evento do Streams, como antes.
- **O teste de negação entra no template:** um `GetItem` sem projeção, ou projetando `description`, precisa dar
  `AccessDenied` — sem a segunda condição, o privilégio mínimo é só aparente.
- O teste do README sobre falha no item 6 de um lote passa a descrever o que acontece de fato: os itens 6 a 10 voltam, e
  só o 6 tem o e-mail enviado de novo.
