# ADR 0020 — Lista de Administrators numa variável do GitHub, fonte única do deploy

- **Status:** aceita
- **Data:** 2026-10-17

## Contexto

Já estava decidido que a lista de Administrators é **fixa por ambiente e fica fora do repositório**: uma lista só para o
Urgency Notice, o Weekly Report e o Operational Alert, e a função falha ao subir se ela estiver vazia. O cadastro de
Administrators está fora do escopo — o avaliador assiste ao vídeo, não usa o ambiente.

A mesma lista é usada em três lugares:

1. pelas funções `send-urgency-notice` e `send-weekly-report`, como destinatários do SES;
2. pelas assinaturas do tópico SNS do Operational Alert ([ADR 0014](0014-ses-para-funcoes-sns-para-alerta.md));
3. pela verificação de endereços que o SES em sandbox exige ([ADR 0011](0011-regiao-sao-paulo.md)).

Com SnapStart ([ADR 0004](0004-framework-escolhido-pelo-gatilho.md)), o que a função lê ao iniciar fica gravado no
snapshot: uma lista lida do Parameter Store na inicialização só muda no próximo deploy.

## Decisão

**Uma *variable* do repositório no GitHub, `ADMINISTRATORS` — e-mails separados por vírgula —, passada como parâmetro
do template no `sam deploy` ([ADR 0013](0013-pipeline-deploy-automatico-oidc.md)).**

Do mesmo parâmetro, o template ([ADR 0012](0012-iac-com-aws-sam.md)) gera:

- a variável de ambiente lida pelas duas funções de e-mail;
- uma assinatura de e-mail no tópico SNS por endereço;
- uma identidade verificada no SES por endereço.

Uma lista, três usos: **não há listas a manter iguais à mão.** Mudar a lista é editar a variável e rodar o deploy.

**Confirmado na implementação (T-06):** gerar um recurso por endereço usa `Fn::ForEach`, da transformação
`AWS::LanguageExtensions` do CloudFormation. O SAM a aceita a partir do SAM CLI 1.161.0 com `--language-extensions` em
cada comando (ou `SAM_CLI_ENABLE_LANGUAGE_EXTENSIONS=1`), o que exige Python 3.10+ no pipeline; sem a flag, o
`sam deploy` quebra antes de chegar à AWS. O plano B (`Admin1` a `Admin5`) ficou dispensado.

## Alternativas consideradas

- **Parameter Store, lido pela função**: com SnapStart, ou a função relê o parâmetro a cada execução — permissão nova na
  role e cache para controlar —, ou a mudança também só vale no próximo deploy. E as assinaturas do SNS e as identidades
  do SES continuariam sendo mantidas à parte.
- **Lista no repositório**: o repositório é público, e a lista é de e-mails pessoais.

## Consequências

- Os e-mails ficam numa *variable*, não num *secret*: visíveis a quem colabora no repositório, não ao público.
- **Entrar na lista dispara dois e-mails da AWS para a pessoa** — verificação do SES e confirmação da assinatura do SNS.
  Sem os dois cliques, ela não recebe os avisos ou não recebe os alertas; o roteiro de deploy registra isso.
- Sair da lista remove a assinatura e a identidade no próximo deploy.
- Nenhuma permissão nova nas roles das funções ([ADR 0019](0019-uma-role-por-funcao.md)).
- O pipeline do deploy ([ADR 0013](0013-pipeline-deploy-automatico-oidc.md)) fixa SAM CLI >= 1.161.0 e liga a flag
  `--language-extensions` (ou `SAM_CLI_ENABLE_LANGUAGE_EXTENSIONS=1`).
- **Limitação declarada:** o ID lógico de cada recurso vem do endereço sem a pontuação, então dois endereços que só
  diferem por pontuação (`ana.lima@…` e `analima@…`) geram o mesmo ID e o deploy falha — improvável num grupo de cinco;
  se acontecer, trocar um dos endereços.
