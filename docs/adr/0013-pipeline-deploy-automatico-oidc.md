# ADR 0013 — Pipeline: deploy automático a cada merge na `main`, por OIDC, com role restrita

- **Status:** aceita
- **Data:** 2026-10-17

## Contexto

O enunciado exige deploy automatizado dos componentes atualizáveis. O pipeline roda no GitHub Actions, entra na conta
AWS do grupo e aplica o template SAM ([ADR 0012](0012-iac-com-aws-sam.md)) em `sa-east-1`
([ADR 0011](0011-regiao-sao-paulo.md)). Falta decidir quando o deploy roda e com que permissão.

## Decisão

### Entrada na AWS sem chave guardada

- Uma **pilha de bootstrap**, fora do template da plataforma e aplicada uma vez por um administrador, cria o provedor
  OIDC do GitHub e a role `feedback-platform-deploy`.
- **Só o workflow da `main`** deste repositório assume a role: a confiança da role exige
  `repo:Team-2-FIAP-12ADJT/feedback-platform:ref:refs/heads/main`. Uma PR, outra branch ou um fork não recebem
  credencial.
- **Nenhum segredo no GitHub.** O ID da conta e o ARN da role ficam em *variables* do repositório, fora do código.

### Permissão da role

- `PowerUserAccess` — tudo menos IAM e organização — **restrita a `sa-east-1`**.
- No IAM, só criar, alterar e passar roles com prefixo **`feedback-platform-`**, que são as roles das próprias funções.
- O deploy cria o que o template precisa e não consegue criar usuário, mexer nos acessos do grupo nem se dar mais
  permissão.

### Dois workflows

| Workflow | Quando | O que faz |
|---|---|---|
| `ci` | Toda PR e todo push | Java 25; `mvn verify` (unitários e integração com containers); `sam validate --lint` |
| `deploy` | Push na `main`, depois do `ci` verde | Assume a role → `mvn package` → `sam deploy` → teste de fumaça |

- **Deploy automático a cada merge na `main`, sem aprovação manual.** Quem segura é a proteção da `main`: PR obrigatória
  e `ci` verde.
- **Teste de fumaça depois do deploy:** um `POST /avaliacao` válido responde `201`, e as três funções têm versão
  publicada com SnapStart — é a prova exigida pela [ADR 0011](0011-regiao-sao-paulo.md).
- **Um deploy por vez** (`concurrency`): dois merges seguidos não disputam a mesma pilha.
- O SAM guarda os pacotes num bucket que ele mesmo cria e gerencia (`--resolve-s3`).

## Alternativas consideradas

- **Aprovação manual antes do deploy** (GitHub Environment com revisor): protege mais, mas trava o grupo quando o revisor
  não está, e afasta o pipeline do deploy automatizado que o enunciado pede.
- **Role com `AdministratorAccess`**: mais simples de montar, mas um erro ou um ataque no pipeline vira controle total da
  conta.
- **Chave de acesso de um usuário IAM em segredo do GitHub**: credencial de longa duração fora da AWS, que precisa ser
  rotacionada e pode vazar.

## Consequências

- **A proteção da `main` vira pré-requisito do deploy**, não boa prática: sem ela, um push direto publica sem `ci`.
- A pilha de bootstrap é aplicada à mão por um administrador com MFA, e entra no README como passo único de preparação
  da conta.
- Uma role de função com nome fora do prefixo `feedback-platform-` faz o deploy falhar por permissão — o template segue a
  convenção.
