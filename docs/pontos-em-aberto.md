# Pontos em aberto

O que ainda não foi decidido, ou foi decidido e ainda não feito. Cada item sai daqui quando vira ADR, documento ou
entrega. As decisões tomadas estão em [`docs/adr/`](adr/) e no [README](../README.md#decisões-tomadas).

Atualizado em 2026-10-17.

## 1. Conta de cloud — resolvido

**Conta AWS própria do grupo, não o Learner Lab** (decidido em 2026-10-17). Acesso pelo usuário IAM `equipefiap`;
**US$ 120 em créditos**. O ID da conta fica fora do repositório (que é público): no pipeline, entra como variável do
GitHub.

O que isso destrava: uma role por função com privilégio mínimo, deploy pelo GitHub Actions sem chave guardada no
repositório e escolha da região — `sa-east-1` ([ADR 0011](adr/0011-regiao-sao-paulo.md)).

**Medido na conta em 2026-10-17** (só leitura):

- `equipefiap` é um **usuário** IAM, não um grupo, e era o único da conta. Tem `AdministratorAccess`: pode criar roles, o
  provedor OIDC do GitHub e orçamentos. A conta não pertence a uma AWS Organization, então nada restringe o usuário.
- Ainda não existe provedor OIDC.

**Em aberto:**

- **Governança de acesso — feito em 2026-10-17, falta fechar:** um usuário IAM por integrante (`deivis.vargas`,
  `leomir.nascimento`, `gustavo.santos`, `leonardo.souza`, `ricardo.lima`) no grupo `feedback-platform-admins`, com
  `AdministratorAccess` e uma política que **nega tudo sem MFA**, exceto cadastrar o próprio MFA e trocar a senha. Acesso
  só pelo console e por `aws login`, sem access key; senha inicial com troca obrigatória; política de senha da conta com
  14 caracteres, todos os tipos e sem reaproveitar as últimas 5. IAM Identity Center descartado: numa conta avulsa, só
  dá acesso à conta se ela virar a principal de uma AWS Organization.
  Provado com `ricardo.lima`: sem MFA, o console e o `aws login` são negados; com MFA cadastrado, o `aws login` leva o MFA
  para a credencial do CLI e as chamadas passam.
  O usuário compartilhado `equipefiap` fica **como acesso de emergência** (decidido em 2026-10-17), fora do grupo.
  Falta: todos os acessos de administrador com MFA, inclusive o de emergência; documentar no README.
- **Orçamento — feito em 2026-10-17:** `feedback-platform-mensal`, US$ 80 por mês, custo medido **sem** descontar os
  créditos (senão o alarme só dispararia quando os créditos acabassem). E-mail para um integrante do grupo aos 50%, 80%
  e 100% do gasto real e quando a previsão do mês passar de 100%.
- Quais serviços os créditos cobrem e até quando valem (Billing → Credits, no console).

## 2. Rodada de deploy — decidida

Todos os pontos são na conta AWS do item 1. Já decididos: banco, gravação → aviso, região, IaC, pipeline, e-mail, canal
do Operational Alert, gateway, monitoramento, filas de falha, criptografia, roles das funções, lista de Administrators e
ambiente único (ADRs 0010–0021). O agendador com fuso é o EventBridge Scheduler (ADR 0012) e a prova do SnapStart é o
teste de fumaça do deploy (ADR 0013).

| Ponto | O que decidir | Depende de |
|---|---|---|
| Remetente do SES | Endereço verificado que envia o Urgency Notice e o Weekly Report — de preferência um e-mail do grupo, não pessoal (ADR 0014) | — |
| Custo | Orçamento e alarme já criados (item 1). Falta conferir no 1º mês o custo fixo do WAF (ADR 0015) contra os US$ 120 de créditos | — |

## 3. Decidido, ainda não registrado em documento próprio

- **Estratégia de teste:** registrada no README §Testes; o roteiro ponta a ponta vira `docs/validacao-manual.md`
  quando houver o que validar.

## 4. Processo do grupo

- [x] Passar as ADRs pelo grupo — as ADRs 0001–0021 estão `aceita` (2026-10-17).
- [ ] Convidar o grupo para o repositório.
- [ ] Proteger a `main` — PR obrigatória e `ci` verde. **Pré-requisito do deploy automático** (ADR 0013).
- [ ] Dividir o trabalho entre os integrantes.

## 5. Entregáveis ainda não produzidos

O enunciado avalia a descrição do projeto com arquitetura, instruções de deploy, configuração do monitoramento e
documentação das funções, além do vídeo.

- [ ] Desenho da arquitetura, depois da rodada de deploy.
- [ ] Instruções de deploy no README.
- [ ] Configuração do monitoramento documentada.
- [ ] Documentação de cada função.
- [ ] `docs/validacao-manual.md` e collection Postman, que servem também de roteiro do vídeo.
- [ ] Vídeo: aplicação funcionando, funções ativas, configurações.
- [ ] Antes de entregar: provar o build em outro SO, ou com `core.autocrlf=true` (lição da Fase 3).
