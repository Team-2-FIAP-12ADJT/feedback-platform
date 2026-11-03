# ADR 0027 — O redeploy manual só aceita commit que já foi o estado da `main`

- **Status:** aceita
- **Data:** 2026-11-01
- **Substitui em parte:** [ADR 0026](0026-redeploy-manual-de-commit-da-main.md) (a trava do commit alvo: "ancestral de `origin/main`")

## Contexto

A [ADR 0026](0026-redeploy-manual-de-commit-da-main.md) trava o redeploy manual em commits ancestrais de `origin/main`
(`git merge-base --is-ancestor`), para não publicar código que nunca passou pela proteção da `main`. A trava não cumpre
isso: as PRs entram por merge commit, e os commits de dentro de cada branch também são ancestrais da `main` sem nunca
terem sido o estado dela. Medido em 01/11/2026: o `8c211f4` (o `template.yaml` da T-06 antes da correção) e o `87b4b38`
passam na trava. Um rollback que escolhe o SHA pelo `git log` da `main`, que lista esses commits, publicaria um deles.

## Decisão

- O commit alvo do redeploy manual precisa estar na **linha de primeiros pais** da `main`
  (`git rev-list --first-parent origin/main`): o resultado de cada merge de PR e cada push direto na `main`, e nada de
  dentro das branches.
- O resto da ADR 0026 continua valendo: input `ref`, checagem antes de qualquer credencial, job só a partir da `main`,
  um deploy por vez, sem rerodar testes e sem exigir o `ci` do commit.
- Para achar o commit de um rollback: `git log --first-parent --oneline origin/main`.

## Alternativas consideradas

- **Manter `--is-ancestor` e confiar em quem dispara:** o próprio `ci` só roda no push da `main` desde a ADR 0026, então
  um commit de dentro de uma branch nem tem run próprio para ser conferido.
- **Exigir merge por squash na `main`:** resolveria para as próximas PRs, mas não para as que já entraram por merge
  commit, e é configuração do repositório fora do workflow.

## Consequências

- Rollback para um ponto intermediário de uma PR deixa de ser possível; o menor passo é o merge anterior.
- Merge por rebase ou fast-forward põe os commits da branch na linha de primeiros pais; com esse tipo de merge, eles
  passam na trava. O grupo usa merge commit.
