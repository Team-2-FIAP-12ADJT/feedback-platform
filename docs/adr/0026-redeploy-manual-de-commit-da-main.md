# ADR 0026 — Redeploy manual de um commit da `main`; `ci` em toda PR e só no push da `main`

- **Status:** aceita
- **Data:** 2026-10-27
- **Substitui em parte:** [ADR 0013](0013-pipeline-deploy-automatico-oidc.md) (as linhas da tabela "Dois workflows" sobre quando o `ci` e o `deploy` rodam)

## Contexto

A [ADR 0013](0013-pipeline-deploy-automatico-oidc.md) diz que o `ci` roda em toda PR e todo push, e que o `deploy` roda
só no push da `main`, depois do `ci` verde. Duas coisas ficaram de fora. Voltar a pilha para um commit anterior, quando
um deploy sai ruim, não tem caminho previsto: refazer o commit na `main` só para disparar o pipeline é remendo. E o `ci`
em todo push duplica o trabalho nas branches com PR aberta, onde o run da PR já valida o mesmo código, enquanto o
`deploy` só depende do run do push na `main`.

## Decisão

### Redeploy manual

- O `deploy` ganha `workflow_dispatch` com o input **`ref`** (SHA ou ref de um commit da `main`), obrigatório, com
  default `main`.
- O workflow faz checkout desse `ref` e só segue se o commit for ancestral de `origin/main`
  (`git merge-base --is-ancestor`). Commit fora da `main` falha o job antes de qualquer credencial.
- O job só roda com `github.ref == refs/heads/main`: o disparo manual parte da `main`, e o `sub` do token OIDC continua
  `ref:refs/heads/main`, o que a role da [ADR 0013](0013-pipeline-deploy-automatico-oidc.md) exige. Não há `environment:`.
- Serve de **rollback**: aponta-se o `ref` para um commit anterior da `main`.
- O redeploy refaz o caminho inteiro (`mvn package`, `sam deploy`, teste de fumaça) e **não reroda os testes**. O
  workflow não exige `ci` verde no commit (a condição do `workflow_run` não vale no disparo manual): conferir no
  histórico do Actions que o `ci` daquele commit passou é **pré-requisito de quem dispara**.
- Continua valendo o **um deploy por vez** (`concurrency`, grupo `deploy`, sem cancelar): o redeploy manual espera o
  deploy em andamento.

### Quando o `ci` roda

- O `ci` roda em **toda PR** e em **push só na `main`**. Nas outras branches a PR já valida, e o `deploy` depende do run
  do push na `main`.

## Alternativas consideradas

- **Reverter o commit na `main` e deixar o pipeline automático rodar:** deixa histórico limpo, mas passa por PR e `ci`
  completos justo quando a pilha está ruim.
- **Aceitar qualquer `ref` no redeploy:** permitiria publicar código que nunca passou pela proteção da `main`.
- **`ci` em todo push:** run duplicado em toda branch com PR aberta, sem ganho para o `deploy`.

## Consequências

- **O redeploy pode publicar código mais antigo sobre o mais novo.** É o objetivo no rollback, mas o próximo push na
  `main` publica de novo o código novo; quem faz rollback precisa tratar a causa na `main` antes disso.
- Um redeploy manual e um deploy automático nunca rodam juntos; o segundo espera o primeiro.
- Branch sem PR aberta não roda `ci`.
- O redeploy não roda testes e não verifica o `ci` do commit; a única trava do workflow é o commit ser ancestral da
  `main`. Um commit antigo da `main`, de antes de o `ci` existir, pode ser republicado: quem dispara confere o `ci`
  verde no histórico do Actions.
