# ADR 0005 — Urgency Notice entregue "pelo menos uma vez"; duplicatas aceitas

- **Status:** aceita
- **Substituída em parte por:** [ADR 0022](0022-send-urgency-notice-le-a-marca.md) — o item sobre lote com falha
- **Data:** 2026-10-14

## Contexto

Todo mecanismo que entrega eventos a uma função garante entrega **pelo menos uma vez**, não exatamente uma vez. Enviar
um e-mail é um efeito externo, fora de qualquer transação com o banco: entre "o e-mail saiu" e "registrei que saiu"
sempre existe um instante em que a função pode morrer. Só há duas escolhas: nunca perder e às vezes duplicar, ou nunca
duplicar e às vezes perder.

Separadamente, o payload do enunciado não tem identificador: a plataforma não distingue um `POST` reenviado pelo
cliente de dois Students escrevendo a mesma coisa.

## Decisão

- **O Urgency Notice é entregue pelo menos uma vez.** Para um aviso crítico, perder é pior que duplicar.
- **A janela de duplicata é estreitada por uma marca na própria Evaluation:** a `send-urgency-notice` ignora o
  evento se `urgencyNoticeSentAt` já estiver preenchido; senão, envia o e-mail e **depois** preenche a marca.
- **Um lote com falha reenvia só os itens que falharam**, nunca o lote inteiro (na AWS: `ReportBatchItemFailures`, ou
  lote de tamanho 1). Esse era o caso de duplicata mais provável, e se resolve por configuração.
- **Evaluation duplicada por reenvio do Student é aceita** e declarada no README.

## Alternativas consideradas

- **Registro de idempotência separado** (ex.: Powertools Idempotency, com escrita condicional): cobre também duas
  entregas simultâneas, ao custo de uma tabela e uma dependência a mais.
- **Fila que descarta repetidos** (FIFO com deduplicação): só cobre a duplicata na publicação, não a função
  reprocessando depois de enviar.
- **Header `Idempotency-Key` opcional no `POST`**: protege só quem manda a chave, e nenhum cliente do enunciado manda.
- **Deduplicação por conteúdo** (mesma Description e mesmo Score): descartaria Evaluations legítimas e iguais de
  Students diferentes.

## Consequências

- Sobra uma janela de duplicata do Urgency Notice: falha entre o envio e a marca, ou duas entregas simultâneas.
- A Evaluation guarda um campo a mais (`urgencyNoticeSentAt`), presente só em Critical Evaluation.
