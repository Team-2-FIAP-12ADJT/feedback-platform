# ADR 0025 — O teste de fumaça remove por descrição

- **Status:** aceita
- **Data:** 2026-10-26
- **Substitui em parte:** [ADR 0024](0024-teste-de-fumaca-apaga-a-propria-evaluation.md) (o item sobre achar o item pelo id)

## Contexto

A [ADR 0024](0024-teste-de-fumaca-apaga-a-propria-evaluation.md) estabelece que o teste de fumaça remove a Evaluation que
ele próprio grava, consultando a partição pelo `id` devolvido na resposta do `POST /avaliacao`.

Entretanto, o passo de teste de fumaça do deploy repete requisições com falha de transporte ou resposta 5xx (até 5
tentativas). Como a `receive-evaluation` persiste o item no banco antes de devolver a resposta HTTP e gera um UUID novo a
cada requisição, um erro 5xx ou falha de conexão logo após a escrita faz a tentativa seguinte gravar outro item. Com
isso, uma Evaluation órfã fica gravada na tabela, invisível para a remoção que só busca o `id` da resposta bem-sucedida,
distorcendo as contagens do Weekly Report ([ADR 0008](0008-regras-do-weekly-report.md)).

## Decisão

**A remoção do teste de fumaça busca as Evaluations pela Description nas partições da execução e apaga todas as encontradas.**

- **Partições consultadas:** o workflow busca nas partições correspondentes ao dia de `dataEnvio` em `America/Sao_Paulo`
  (quando presente), ao dia da primeira tentativa do `POST` e ao dia da remoção, deduplicando os dias. O job tem timeout de
  40 minutos, então esses dois dias cobrem toda gravação da execução, inclusive na virada da meia-noite.
- **Busca por Description:** em cada partição, executa uma consulta fortemente consistente filtrando pela Description
  `Teste de fumaça do deploy <commit>` montada a partir do SHA curto do commit, coletando todos os itens encontrados.
- **Garantia da ADR 0024 mantida:** se o `POST` retornou um `id`, esse `id` **obrigatoriamente** deve estar entre os
  itens encontrados; se não estiver, o passo falha com `::error::`.
- **Remoção de cada item:** todo item localizado é apagado com `DeleteItem` condicionado à existência de
  `submissionKey`, e a exclusão é conferida com `GetItem` consistente. Se algum item permanecer na tabela, o deploy falha.
- **Execução mesmo em caso de falha do POST:** a limpeza roda sempre que o passo do `POST` tiver sido executado
  (`steps.post.outcome != 'skipped'`). Se o `POST` falhou e nada foi gravado, o passo registra que nada foi escrito e
  encerra com sucesso (o deploy já está vermelho pelo `POST`). Se encontrou órfãos deixados por tentativas anteriores,
  remove-os e informa a quantidade apagada.

## Alternativas consideradas

- **Não retentar o POST:** um 503 temporário logo após o deploy deixaria o pipeline vermelho sem necessidade.
- **Retentar apenas erros que comprovadamente não ocorrem após a gravação:** erros 502 do API Gateway podem ocorrer
  após a gravação ter sido realizada com sucesso (por exemplo, por timeout da Lambda ou quebra de conexão), de modo que
  não há lista segura de erros que garantam ausência de escrita prévia.

## Consequências

- **As retentativas do `POST` não deixam Evaluation de teste**: toda gravação desta execução cai nas partições
  consultadas e tem a mesma Description.
- **Limpeza de órfãos de execuções anteriores:** a remoção também limpa órfãos deixados por execuções anteriores **do
  mesmo commit e nas mesmas partições**. Órfão de outro commit, de outro dia ou de uma execução cancelada antes da
  remoção continua na tabela e é reconhecível pela Description.
- **Uma Evaluation legítima com essa Description seria removida:** risco aceito, uma vez que a Description identifica
  explicitamente o teste de fumaça e o commit da execução.
